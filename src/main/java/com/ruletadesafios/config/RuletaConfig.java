package com.ruletadesafios.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.ruletadesafios.RuletaDesafiosMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuracion global del mod. Se guarda en {@code config/ruleta-desafios.json}.
 * Se edita desde la pantalla de configuracion, con {@code /ruleta recargar} o a mano.
 */
public class RuletaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ruleta-desafios.json");
	private static RuletaConfig instance;

	/** Girar la ruleta sola cada cierto tiempo (cuando no hay un desafio activo). */
	public boolean autoSpin = false;
	public int autoSpinMinutes = 10;
	/** Duracion por defecto de un desafio (segundos). */
	public int defaultDurationSeconds = 300;
	/** Duracion de la animacion de la ruleta en pantalla (segundos). */
	public int spinAnimationSeconds = 6;
	/** Aplicar los efectos de pocion de los desafios que tengan uno. */
	public boolean applyEffects = true;
	/** true = el desafio afecta a todos los jugadores, false = solo a quien giro. */
	public boolean affectAllPlayers = true;
	public boolean announceInChat = true;
	public boolean showTitle = true;
	public boolean playSounds = true;
	/** true = cualquier jugador puede girar; false = solo operadores (o el dueño en un mundo local). */
	public boolean everyoneCanSpin = true;
	public List<Challenge> challenges = new ArrayList<>();

	// ---------------------------------------------------------------- acceso

	public static synchronized RuletaConfig get() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	public static synchronized void load() {
		RuletaConfig loaded = null;

		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, RuletaConfig.class);
			} catch (IOException | JsonParseException e) {
				RuletaDesafiosMod.LOGGER.error("No se pudo leer {}. Se guarda una copia (.bak) y se usan los valores por defecto.", FILE, e);
				try {
					Files.move(FILE, FILE.resolveSibling("ruleta-desafios.json.bak"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
					// si no se puede respaldar, simplemente se sobrescribe despues
				}
			}
		}

		boolean fresh = loaded == null;
		if (fresh) {
			loaded = new RuletaConfig();
			loaded.challenges = defaultChallenges();
		}
		loaded.sanitize();
		instance = loaded;

		if (fresh) {
			save();
		}
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(instance), StandardCharsets.UTF_8);
		} catch (IOException e) {
			RuletaDesafiosMod.LOGGER.error("No se pudo guardar {}", FILE, e);
		}
	}

	/** Reemplaza la configuracion activa (usado por la pantalla de configuracion) y la guarda. */
	public static synchronized void replace(RuletaConfig newConfig) {
		newConfig.sanitize();
		instance = newConfig;
		save();
	}

	/** Copia profunda, para editar en la pantalla de configuracion sin tocar la real hasta pulsar "Guardar". */
	public RuletaConfig copy() {
		return GSON.fromJson(GSON.toJson(this), RuletaConfig.class);
	}

	// ---------------------------------------------------------------- utilidades

	public List<Challenge> enabledChallenges() {
		List<Challenge> out = new ArrayList<>();
		for (Challenge c : challenges) {
			if (c.enabled && c.weight > 0) {
				out.add(c);
			}
		}
		return out;
	}

	public Challenge findById(String id) {
		for (Challenge c : challenges) {
			if (c.id.equalsIgnoreCase(id)) {
				return c;
			}
		}
		return null;
	}

	/** Elige el indice de un desafio de la lista respetando los pesos. -1 si la lista esta vacia. */
	public static int pickIndexWeighted(List<Challenge> pool) {
		if (pool.isEmpty()) {
			return -1;
		}
		int total = 0;
		for (Challenge c : pool) {
			total += Math.max(1, c.weight);
		}
		int roll = ThreadLocalRandom.current().nextInt(total);
		for (int i = 0; i < pool.size(); i++) {
			roll -= Math.max(1, pool.get(i).weight);
			if (roll < 0) {
				return i;
			}
		}
		return pool.size() - 1;
	}

	public static Challenge pickWeighted(List<Challenge> pool) {
		int index = pickIndexWeighted(pool);
		return index < 0 ? null : pool.get(index);
	}

	private void sanitize() {
		autoSpinMinutes = clamp(autoSpinMinutes, 1, 1440);
		defaultDurationSeconds = clamp(defaultDurationSeconds, 5, 86400);
		spinAnimationSeconds = clamp(spinAnimationSeconds, 2, 30);

		if (challenges == null) {
			challenges = defaultChallenges();
		}
		challenges.removeIf(c -> c == null);

		Set<String> usedIds = new HashSet<>();
		for (Challenge c : challenges) {
			if (c.name == null) c.name = "";
			if (c.description == null) c.description = "";
			if (c.effect == null) c.effect = "";
			c.weight = clamp(c.weight, 1, 100);
			c.durationSeconds = clamp(c.durationSeconds, 0, 86400);
			c.amplifier = clamp(c.amplifier, 0, 255);

			String base = (c.id == null || c.id.isBlank()) ? "desafio" : c.id.trim().replaceAll("\\s+", "_");
			String unique = base;
			int n = 2;
			while (!usedIds.add(unique.toLowerCase())) {
				unique = base + "_" + n++;
			}
			c.id = unique;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	// ---------------------------------------------------------------- desafios por defecto

	public static List<Challenge> defaultChallenges() {
		List<Challenge> list = new ArrayList<>();
		// Con efecto de pocion (se aplican solos)
		list.add(new Challenge("ceguera", "Ceguera total", "No ves nada. ¡Confía en tus amigos!", 1, 30, "minecraft:blindness", 0));
		list.add(new Challenge("tortuga", "Modo tortuga", "Vas muy lento durante un rato.", 2, 120, "minecraft:slowness", 1));
		list.add(new Challenge("manos_mantequilla", "Manos de mantequilla", "Picar y cortar cuesta muchísimo.", 2, 120, "minecraft:mining_fatigue", 1));
		list.add(new Challenge("brazos_fideo", "Brazos de fideo", "Tus golpes casi no hacen daño.", 2, 120, "minecraft:weakness", 1));
		list.add(new Challenge("estomago_sin_fondo", "Estómago sin fondo", "Tienes un hambre voraz.", 2, 90, "minecraft:hunger", 2));
		list.add(new Challenge("mareado", "Mareado", "Todo da vueltas...", 1, 45, "minecraft:nausea", 0));
		list.add(new Challenge("cuevas_oscuras", "Oscuridad", "La visión se apaga y vuelve a ratos.", 1, 60, "minecraft:darkness", 0));
		list.add(new Challenge("rana", "Rana saltarina", "¡Saltas altísimo! Cuidado con la caída.", 2, 60, "minecraft:jump_boost", 4));
		list.add(new Challenge("brillante", "Brillante", "Todos pueden verte a través de las paredes.", 2, 120, "minecraft:glowing", 0));
		list.add(new Challenge("flash", "Modo Flash", "Súper velocidad. Intenta no estrellarte.", 2, 60, "minecraft:speed", 3));
		// De palabra (modo honor): solo se anuncian
		list.add(new Challenge("sin_sprint", "Sin correr", "No puedes correr (sprint) hasta que acabe el tiempo.", 2, 180, "", 0));
		list.add(new Challenge("solo_madera", "Solo madera", "Solo puedes usar herramientas y armas de madera.", 2, 300, "", 0));
		list.add(new Challenge("silencio", "Silencio total", "No puedes hablar (ni chat ni voz).", 2, 120, "", 0));
		list.add(new Challenge("agachado", "Siempre agachado", "Debes ir agachado todo el tiempo.", 2, 120, "", 0));
		list.add(new Challenge("sin_inventario", "Sin inventario", "No puedes abrir tu inventario.", 1, 120, "", 0));
		list.add(new Challenge("casita_expres", "Casita exprés", "Construye una casita con lo que tengas a mano.", 1, 180, "", 0));
		list.add(new Challenge("suelo_lava", "El suelo es lava", "No puedes pisar bloques naturales, solo los que coloques tú.", 1, 120, "", 0));
		list.add(new Challenge("sin_armadura", "Sin armadura", "Quítate la armadura y el escudo.", 2, 300, "", 0));
		return list;
	}
}
