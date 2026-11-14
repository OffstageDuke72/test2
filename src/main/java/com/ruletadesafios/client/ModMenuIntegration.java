package com.ruletadesafios.client;

import com.ruletadesafios.client.screen.RuletaConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Integracion opcional con Mod Menu: añade el boton de configuracion en la lista de mods.
 * Solo se carga si Mod Menu esta instalado.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return RuletaConfigScreen::new;
	}
}
