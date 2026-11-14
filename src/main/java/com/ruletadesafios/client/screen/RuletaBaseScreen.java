package com.ruletadesafios.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;

/**
 * Base comun de las pantallas del mod: volver a la pantalla anterior y pequeñas ayudas de UI
 * (boton Si/No, boton con - / +, texto centrado y texto con saltos de linea).
 */
abstract class RuletaBaseScreen extends Screen {
	protected final Screen parent;

	protected RuletaBaseScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	// ---------------------------------------------------------------- widgets

	protected static Component yesNo(String labelKey, boolean value) {
		return Component.translatable(labelKey)
			.append(Component.literal(": "))
			.append(Component.translatable(value ? "ruleta_desafios.yes" : "ruleta_desafios.no"));
	}

	/** Boton que alterna un valor Si/No. */
	protected void addToggle(int x, int y, int width, String labelKey, BooleanSupplier getter, Consumer<Boolean> setter) {
		Button button = Button.builder(yesNo(labelKey, getter.getAsBoolean()), btn -> {
			boolean next = !getter.getAsBoolean();
			setter.accept(next);
			btn.setMessage(yesNo(labelKey, next));
		}).bounds(x, y, width, 20).build();
		this.addRenderableWidget(button);
	}

	/**
	 * Fila "[-] texto [+]". {@code labelKey} es una clave de traduccion con un %s para el valor actual.
	 */
	protected void addStepper(int x, int y, int width, String labelKey, IntSupplier getter, IntConsumer setter, int step, int min, int max) {
		Button[] center = new Button[1];

		center[0] = Button.builder(Component.translatable(labelKey, getter.getAsInt()), btn -> {
		}).bounds(x + 22, y, width - 44, 20).build();

		Button minus = Button.builder(Component.literal("-"), btn -> {
			int value = Math.max(min, getter.getAsInt() - step);
			setter.accept(value);
			center[0].setMessage(Component.translatable(labelKey, value));
		}).bounds(x, y, 20, 20).build();

		Button plus = Button.builder(Component.literal("+"), btn -> {
			int value = Math.min(max, getter.getAsInt() + step);
			setter.accept(value);
			center[0].setMessage(Component.translatable(labelKey, value));
		}).bounds(x + width - 20, y, 20, 20).build();

		this.addRenderableWidget(minus);
		this.addRenderableWidget(center[0]);
		this.addRenderableWidget(plus);
	}

	// ---------------------------------------------------------------- texto

	protected void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
		graphics.text(this.font, text, x, y, color, true);
	}

	protected void drawCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color) {
		graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, color, true);
	}

	/** Parte un texto en lineas que caben en {@code maxWidth} pixeles (maximo {@code maxLines}). */
	protected List<String> wrap(String text, int maxWidth, int maxLines) {
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		for (String word : text.trim().split("\\s+")) {
			if (word.isEmpty()) {
				continue;
			}
			String candidate = current.isEmpty() ? word : current + " " + word;
			if (current.isEmpty() || this.font.width(candidate) <= maxWidth) {
				current = new StringBuilder(candidate);
			} else {
				lines.add(current.toString());
				current = new StringBuilder(word);
				if (lines.size() >= maxLines) {
					return lines;
				}
			}
		}
		if (!current.isEmpty() && lines.size() < maxLines) {
			lines.add(current.toString());
		}
		return lines;
	}
}
