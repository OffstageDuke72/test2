package com.ruletadesafios.config;

/**
 * Un desafio de la ruleta. Se guarda tal cual en config/ruleta-desafios.json.
 * <p>
 * - Si {@code effect} esta vacio, el desafio es "de palabra" (modo honor): solo se anuncia.
 * - Si {@code effect} tiene un id de efecto (ej. "minecraft:blindness"), ademas se aplica ese efecto.
 */
public class Challenge {
	public String id = "";
	public String name = "";
	public String description = "";
	public boolean enabled = true;
	/** Probabilidad relativa dentro de la ruleta (1 = normal, 3 = tres veces mas probable). */
	public int weight = 1;
	/** Duracion en segundos. 0 = usar la duracion por defecto de la configuracion. */
	public int durationSeconds = 0;
	/** Id del efecto de pocion (vacio = ninguno). */
	public String effect = "";
	/** Nivel del efecto (0 = nivel I, 1 = nivel II ...). */
	public int amplifier = 0;

	public Challenge() {
	}

	public Challenge(String id, String name, String description, int weight, int durationSeconds, String effect, int amplifier) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.weight = weight;
		this.durationSeconds = durationSeconds;
		this.effect = effect;
		this.amplifier = amplifier;
	}

	public int effectiveDuration(RuletaConfig cfg) {
		return durationSeconds > 0 ? durationSeconds : cfg.defaultDurationSeconds;
	}

	public boolean hasEffect() {
		return effect != null && !effect.isBlank();
	}
}
