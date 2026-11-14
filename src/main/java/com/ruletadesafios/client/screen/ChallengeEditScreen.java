package com.ruletadesafios.client.screen;

import com.ruletadesafios.config.Challenge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Editor de un desafio: nombre, descripcion, efecto (id), nivel, duracion, peso y activo.
 * Los cambios se aplican al pulsar "Guardar".
 */
public class ChallengeEditScreen extends RuletaBaseScreen {
	private final List<Challenge> list;
	private final Challenge challenge;
	private final boolean isNew;

	// Valores en edicion (se conservan si cambia el tamaño de la ventana)
	private String nameText;
	private String descText;
	private String effectText;
	private int amplifier;
	private int duration;
	private int weight;
	private boolean enabled;

	private Component error;

	public ChallengeEditScreen(Screen parent, List<Challenge> list, Challenge challenge, boolean isNew) {
		super(Component.translatable("ruleta_desafios.edit.title"), parent);
		this.list = list;
		this.challenge = challenge;
		this.isNew = isNew;

		this.nameText = challenge.name;
		this.descText = challenge.description;
		this.effectText = challenge.effect == null ? "" : challenge.effect;
		this.amplifier = challenge.amplifier;
		this.duration = challenge.durationSeconds;
		this.weight = challenge.weight;
		this.enabled = challenge.enabled;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int left = cx - 155;
		int right = cx + 5;

		EditBox nameBox = new EditBox(this.font, left, 44, 150, 20, Component.translatable("ruleta_desafios.edit.name"));
		nameBox.setMaxLength(40);
		nameBox.setValue(this.nameText);
		nameBox.setResponder(text -> this.nameText = text);
		this.addRenderableWidget(nameBox);

		EditBox effectBox = new EditBox(this.font, right, 44, 150, 20, Component.translatable("ruleta_desafios.edit.effect"));
		effectBox.setMaxLength(100);
		effectBox.setValue(this.effectText);
		effectBox.setResponder(text -> this.effectText = text);
		this.addRenderableWidget(effectBox);

		EditBox descBox = new EditBox(this.font, left, 80, 310, 20, Component.translatable("ruleta_desafios.edit.description"));
		descBox.setMaxLength(200);
		descBox.setValue(this.descText);
		descBox.setResponder(text -> this.descText = text);
		this.addRenderableWidget(descBox);

		addStepper(left, 106, 150, "ruleta_desafios.edit.amplifier", () -> this.amplifier + 1, v -> this.amplifier = v - 1, 1, 1, 256);
		addStepper(right, 106, 150, "ruleta_desafios.edit.duration", () -> this.duration, v -> this.duration = v, 30, 0, 86400);
		addStepper(left, 130, 150, "ruleta_desafios.edit.weight", () -> this.weight, v -> this.weight = v, 1, 1, 100);
		addToggle(right, 130, 150, "ruleta_desafios.edit.enabled", () -> this.enabled, v -> this.enabled = v);

		int y = this.height - 28;
		if (this.isNew) {
			this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.save"), btn -> save())
				.bounds(left, y, 150, 20).build());
			this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.cancel"), btn -> this.onClose())
				.bounds(right, y, 150, 20).build());
		} else {
			this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.save"), btn -> save())
				.bounds(left, y, 100, 20).build());
			this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.delete"), btn -> {
				this.list.remove(this.challenge);
				this.onClose();
			}).bounds(cx - 50, y, 100, 20).build());
			this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.cancel"), btn -> this.onClose())
				.bounds(cx + 55, y, 100, 20).build());
		}
	}

	private void save() {
		String name = this.nameText.trim();
		if (name.isEmpty()) {
			this.error = Component.translatable("ruleta_desafios.edit.error.name");
			return;
		}

		String effect = this.effectText.trim();
		if (!effect.isEmpty()) {
			Identifier id = Identifier.tryParse(effect.contains(":") ? effect : "minecraft:" + effect);
			if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
				this.error = Component.translatable("ruleta_desafios.edit.error.effect", effect);
				return;
			}
			effect = id.toString();
		}

		this.challenge.name = name;
		this.challenge.description = this.descText.trim();
		this.challenge.effect = effect;
		this.challenge.amplifier = this.amplifier;
		this.challenge.durationSeconds = this.duration;
		this.challenge.weight = this.weight;
		this.challenge.enabled = this.enabled;

		if (this.isNew && !this.list.contains(this.challenge)) {
			this.list.add(this.challenge);
		}
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int cx = this.width / 2;
		int left = cx - 155;
		int right = cx + 5;

		drawCentered(graphics, this.getTitle().getString(), cx, 12, 0xFFFFD700);
		drawText(graphics, Component.translatable("ruleta_desafios.edit.name").getString(), left, 33, 0xFFA0A0A0);
		drawText(graphics, Component.translatable("ruleta_desafios.edit.effect").getString(), right, 33, 0xFFA0A0A0);
		drawText(graphics, Component.translatable("ruleta_desafios.edit.description").getString(), left, 69, 0xFFA0A0A0);

		if (this.error != null) {
			drawCentered(graphics, this.error.getString(), cx, 158, 0xFFFF5555);
		}
	}
}
