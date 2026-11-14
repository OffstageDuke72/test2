package com.ruletadesafios;

import com.ruletadesafios.config.RuletaConfig;
import com.ruletadesafios.server.RuletaCommands;
import com.ruletadesafios.server.RuletaManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada comun (cliente + servidor). Aqui vive toda la logica de la ruleta:
 * configuracion, comandos y el temporizador del desafio activo.
 */
public class RuletaDesafiosMod implements ModInitializer {
	public static final String MOD_ID = "ruleta_desafios";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RuletaConfig.load();
		RuletaManager.init();
		RuletaCommands.register();
		LOGGER.info("Ruleta de Desafíos cargada ({} desafíos).", RuletaConfig.get().challenges.size());
	}
}
