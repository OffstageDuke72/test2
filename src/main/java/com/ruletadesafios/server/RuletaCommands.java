package com.ruletadesafios.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruletadesafios.config.Challenge;
import com.ruletadesafios.config.RuletaConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/**
 * Comandos del servidor:
 * <pre>
 * /ruleta                 estado del desafio activo
 * /ruleta girar           elige un desafio al azar (el servidor decide) y lo activa
 * /ruleta aplicar &lt;id&gt;    activa un desafio concreto (lo usa la pantalla de la ruleta al terminar de girar)
 * /ruleta parar           detiene el desafio activo
 * /ruleta lista           lista los desafios
 * /ruleta recargar        vuelve a leer config/ruleta-desafios.json
 * </pre>
 */
public final class RuletaCommands {
	private RuletaCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("ruleta")
				.executes(ctx -> status(ctx.getSource()))
				.then(Commands.literal("girar")
					.executes(ctx -> spin(ctx.getSource())))
				.then(Commands.literal("aplicar")
					.then(Commands.argument("id", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(challengeIds(), builder))
						.executes(ctx -> apply(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
				.then(Commands.literal("parar")
					.executes(ctx -> stop(ctx.getSource())))
				.then(Commands.literal("lista")
					.executes(ctx -> list(ctx.getSource())))
				.then(Commands.literal("recargar")
					.executes(ctx -> reload(ctx.getSource())))));
	}

	// ---------------------------------------------------------------- permisos

	/** Operador, o dueño de un mundo local (donde no hay trucos activados pero eres "el admin"). */
	private static boolean isAdmin(CommandSourceStack source) {
		return source.getServer().isSingleplayer() || source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
	}

	private static boolean canSpin(CommandSourceStack source) {
		return RuletaConfig.get().everyoneCanSpin || isAdmin(source);
	}

	private static List<String> challengeIds() {
		return RuletaConfig.get().challenges.stream().map(c -> c.id).toList();
	}

	// ---------------------------------------------------------------- comandos

	private static int status(CommandSourceStack source) {
		Challenge active = RuletaManager.getActive();
		if (active == null) {
			source.sendSuccess(() -> Component.literal("[Ruleta] No hay ningún desafío activo. Usa /ruleta girar.").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		int left = RuletaManager.remainingSeconds(source.getServer());
		source.sendSuccess(() -> Component.literal("[Ruleta] Activo: ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(active.name).withStyle(ChatFormatting.YELLOW))
			.append(Component.literal(" (" + RuletaManager.formatTime(left) + ") - " + active.description).withStyle(ChatFormatting.GRAY)), false);
		return 1;
	}

	private static int spin(CommandSourceStack source) {
		if (!canSpin(source)) {
			source.sendFailure(Component.literal("Solo los operadores pueden girar la ruleta."));
			return 0;
		}
		Challenge picked = RuletaConfig.pickWeighted(RuletaConfig.get().enabledChallenges());
		if (picked == null) {
			source.sendFailure(Component.literal("No hay desafíos activados en la configuración."));
			return 0;
		}
		ServerPlayer player = source.getPlayer();
		RuletaManager.activate(source.getServer(), picked, player);
		return 1;
	}

	private static int apply(CommandSourceStack source, String id) {
		if (!canSpin(source)) {
			source.sendFailure(Component.literal("Solo los operadores pueden activar desafíos."));
			return 0;
		}
		Challenge challenge = RuletaConfig.get().findById(id);
		if (challenge == null || !challenge.enabled) {
			source.sendFailure(Component.literal("Ese desafío no existe o está desactivado: " + id));
			return 0;
		}
		RuletaManager.activate(source.getServer(), challenge, source.getPlayer());
		return 1;
	}

	private static int stop(CommandSourceStack source) {
		if (!isAdmin(source)) {
			source.sendFailure(Component.literal("Solo los operadores pueden detener el desafío."));
			return 0;
		}
		if (RuletaManager.getActive() == null) {
			source.sendFailure(Component.literal("No hay ningún desafío activo."));
			return 0;
		}
		RuletaManager.stop(source.getServer(), false);
		return 1;
	}

	private static int list(CommandSourceStack source) {
		List<Challenge> all = RuletaConfig.get().challenges;
		source.sendSuccess(() -> Component.literal("[Ruleta] " + all.size() + " desafíos:").withStyle(ChatFormatting.GOLD), false);
		for (Challenge c : all) {
			source.sendSuccess(() -> Component.literal(" - " + c.id + ": " + c.name + " (peso " + c.weight + ")")
				.withStyle(c.enabled ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
		}
		return all.size();
	}

	private static int reload(CommandSourceStack source) {
		if (!isAdmin(source)) {
			source.sendFailure(Component.literal("Solo los operadores pueden recargar la configuración."));
			return 0;
		}
		RuletaConfig.load();
		source.sendSuccess(() -> Component.literal("[Ruleta] Configuración recargada.").withStyle(ChatFormatting.GREEN), true);
		return 1;
	}
}
