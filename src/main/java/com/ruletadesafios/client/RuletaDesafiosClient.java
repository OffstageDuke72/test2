package com.ruletadesafios.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ruletadesafios.RuletaDesafiosMod;
import com.ruletadesafios.client.screen.RuletaConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Cliente: teclas (R = ruleta, K = configuracion; se pueden cambiar en Controles) y
 * comandos de cliente /ruletagui y /ruletaconfig.
 */
public class RuletaDesafiosClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RuletaDesafiosMod.MOD_ID, "main"));

	private static final int NONE = 0;
	private static final int WHEEL = 1;
	private static final int CONFIG = 2;

	/** Accion pendiente; se procesa en el siguiente tick para evitar que el chat cierre la interfaz. */
	private static volatile int pending = NONE;

	@Override
	public void onInitializeClient() {
		WheelOverlay.init();
		KeyMapping wheelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ruleta_desafios.open_wheel", InputConstants.Type.KEY, InputConstants.KEY_R, CATEGORY));
		KeyMapping configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ruleta_desafios.open_config", InputConstants.Type.KEY, InputConstants.KEY_K, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (wheelKey.consumeClick()) {
				pending = WHEEL;
			}
			while (configKey.consumeClick()) {
				pending = CONFIG;
			}

			if (pending != NONE && client.player != null && client.gui.screen() == null) {
				int which = pending;
				pending = NONE;
				if (which == WHEEL) {
					WheelOverlay.start();
				} else {
					client.gui.setScreen(new RuletaConfigScreen(null));
				}
			}
		});

		// Botones en los menus del juego: pausa (ESC) y pantalla de titulo.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof PauseScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("ruleta_desafios.menu.wheel"),
					btn -> { client.gui.setScreen(null); WheelOverlay.start(); }).bounds(6, 6, 150, 20).build());
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("ruleta_desafios.menu.config"),
					btn -> client.gui.setScreen(new RuletaConfigScreen(screen))).bounds(6, 30, 150, 20).build());
			} else if (screen instanceof TitleScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("ruleta_desafios.menu.config"),
					btn -> client.gui.setScreen(new RuletaConfigScreen(screen))).bounds(6, 6, 150, 20).build());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommands.literal("ruletagui").executes(ctx -> {
				pending = WHEEL;
				return 1;
			}));
			dispatcher.register(ClientCommands.literal("ruletaconfig").executes(ctx -> {
				pending = CONFIG;
				return 1;
			}));
		});
	}
}
