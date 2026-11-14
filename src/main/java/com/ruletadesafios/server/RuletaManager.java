package com.ruletadesafios.server;

import com.ruletadesafios.config.Challenge;
import com.ruletadesafios.config.RuletaConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Estado del desafio activo (lado servidor): temporizador, efectos, anuncios y giro automatico.
 * Funciona igual en un mundo local (servidor integrado) que en un servidor dedicado.
 */
public final class RuletaManager {
	private static Challenge active;
	private static long endTick;
	private static long nextAutoTick;
	private static UUID spinnerId;

	private RuletaManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RuletaManager::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> reset());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	private static void reset() {
		active = null;
		spinnerId = null;
		endTick = 0;
		nextAutoTick = 0;
	}

	// ---------------------------------------------------------------- consulta

	public static Challenge getActive() {
		return active;
	}

	public static int remainingSeconds(MinecraftServer server) {
		if (active == null) {
			return 0;
		}
		return (int) Math.max(0, Math.ceil((endTick - server.getTickCount()) / 20.0));
	}

	public static String formatTime(int totalSeconds) {
		int m = totalSeconds / 60;
		int s = totalSeconds % 60;
		return m + ":" + (s < 10 ? "0" : "") + s;
	}

	// ---------------------------------------------------------------- acciones

	/** Activa un desafio (reemplaza al que hubiera). {@code spinner} puede ser null (giro automatico / consola). */
	public static void activate(MinecraftServer server, Challenge challenge, ServerPlayer spinner) {
		RuletaConfig cfg = RuletaConfig.get();

		if (active != null) {
			clearEffects(server);
		}

		active = challenge;
		spinnerId = spinner == null ? null : spinner.getUUID();
		int seconds = challenge.effectiveDuration(cfg);
		endTick = server.getTickCount() + seconds * 20L;

		announceStart(server, cfg, challenge, seconds, spinner);
		refresh(server, cfg, server.getTickCount());
	}

	/** Termina el desafio activo. {@code natural} = true si se acabo el tiempo. */
	public static void stop(MinecraftServer server, boolean natural) {
		if (active == null) {
			return;
		}
		RuletaConfig cfg = RuletaConfig.get();
		clearEffects(server);

		if (cfg.announceInChat) {
			MutableComponent msg = Component.literal("[Ruleta] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal(natural ? "¡Desafío terminado! " : "Desafío detenido. ").withStyle(ChatFormatting.GREEN))
				.append(Component.literal(active.name).withStyle(ChatFormatting.GRAY));
			server.getPlayerList().broadcastSystemMessage(msg, false);
		}
		if (natural && cfg.playSounds) {
			playSound(server);
		}

		active = null;
		spinnerId = null;
	}

	// ---------------------------------------------------------------- tick

	private static void tick(MinecraftServer server) {
		RuletaConfig cfg = RuletaConfig.get();
		long now = server.getTickCount();

		if (active != null) {
			if (now >= endTick) {
				stop(server, true);
			} else if (now % 20 == 0) {
				refresh(server, cfg, now);
			}
			return;
		}

		if (!cfg.autoSpin) {
			nextAutoTick = 0;
			return;
		}
		if (nextAutoTick <= 0) {
			nextAutoTick = now + cfg.autoSpinMinutes * 60L * 20L;
			return;
		}
		if (now >= nextAutoTick && !server.getPlayerList().getPlayers().isEmpty()) {
			nextAutoTick = 0;
			Challenge picked = RuletaConfig.pickWeighted(cfg.enabledChallenges());
			if (picked != null) {
				activate(server, picked, null);
			}
		}
	}

	/** Cada segundo: barra de accion con el tiempo restante y (re)aplicar el efecto a quien no lo tenga (respawn, entrar al mundo...). */
	private static void refresh(MinecraftServer server, RuletaConfig cfg, long now) {
		if (active == null) {
			return;
		}
		long remainingTicks = Math.max(1, endTick - now);
		int remainingSeconds = (int) Math.ceil(remainingTicks / 20.0);

		Component bar = Component.literal(active.name).withStyle(ChatFormatting.GOLD)
			.append(Component.literal("  " + formatTime(remainingSeconds)).withStyle(ChatFormatting.WHITE));

		Holder<MobEffect> effect = cfg.applyEffects ? resolveEffect(active) : null;

		for (ServerPlayer player : targets(server)) {
			player.sendSystemMessage(bar, true);
			if (effect != null && !player.hasEffect(effect)) {
				int duration = (int) Math.min(remainingTicks, Integer.MAX_VALUE / 2);
				player.addEffect(new MobEffectInstance(effect, duration, Math.max(0, active.amplifier), false, true, true));
			}
		}
	}

	// ---------------------------------------------------------------- ayudas

	private static List<ServerPlayer> targets(MinecraftServer server) {
		List<ServerPlayer> all = server.getPlayerList().getPlayers();
		if (RuletaConfig.get().affectAllPlayers || spinnerId == null) {
			return all;
		}
		List<ServerPlayer> only = new ArrayList<>();
		for (ServerPlayer player : all) {
			if (player.getUUID().equals(spinnerId)) {
				only.add(player);
			}
		}
		return only;
	}

	private static void clearEffects(MinecraftServer server) {
		Holder<MobEffect> effect = resolveEffect(active);
		if (effect == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.hasEffect(effect)) {
				player.removeEffect(effect);
			}
		}
	}

	/** Busca el efecto por su id (acepta "blindness" o "minecraft:blindness" y efectos de otros mods). */
	public static Holder<MobEffect> resolveEffect(Challenge challenge) {
		if (challenge == null || !challenge.hasEffect()) {
			return null;
		}
		String raw = challenge.effect.trim();
		Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
		if (id == null) {
			return null;
		}
		return BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
	}

	private static void announceStart(MinecraftServer server, RuletaConfig cfg, Challenge challenge, int seconds, ServerPlayer spinner) {
		if (cfg.announceInChat) {
			String who = spinner != null ? spinner.getName().getString() + " giró la ruleta: " : "Nuevo desafío: ";
			MutableComponent line1 = Component.literal("[Ruleta] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal(who).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(challenge.name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
				.append(Component.literal("  (" + formatTime(seconds) + ")").withStyle(ChatFormatting.GRAY));
			server.getPlayerList().broadcastSystemMessage(line1, false);

			if (!challenge.description.isBlank()) {
				server.getPlayerList().broadcastSystemMessage(
					Component.literal("  " + challenge.description).withStyle(ChatFormatting.WHITE), false);
			}
		}

		if (cfg.showTitle) {
			Component title = Component.literal(challenge.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
			Component subtitle = Component.literal(challenge.description).withStyle(ChatFormatting.YELLOW);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
				player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
				player.connection.send(new ClientboundSetTitleTextPacket(title));
			}
		}

		if (cfg.playSounds) {
			playSound(server);
		}
	}

	/** Si esta linea da problemas al compilar, puedes borrar el cuerpo del metodo: los sonidos son opcionales. */
	private static void playSound(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
		}
	}
}
