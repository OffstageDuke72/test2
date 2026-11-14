package com.ruletadesafios.client.screen;

import com.ruletadesafios.config.Challenge;
import com.ruletadesafios.config.RuletaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Pantalla de configuracion: pestaña "General" (opciones globales) y pestaña "Desafios"
 * (lista paginada con activar/desactivar, editar y añadir). Los cambios solo se guardan con "Guardar".
 */
public class RuletaConfigScreen extends RuletaBaseScreen {
	private static final int PAGE_SIZE = 5;
	private static final int ROW = 24;
	private static final int TOP = 56;

	private final RuletaConfig working = RuletaConfig.get().copy();
	private int tab = 0;
	private int page = 0;

	public RuletaConfigScreen(Screen parent) {
		super(Component.translatable("ruleta_desafios.config.title"), parent);
	}

	@Override
	protected void init() {
		int cx = this.width / 2;

		// Pestañas
		this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.tab.general"), btn -> {
			this.tab = 0;
			this.rebuildWidgets();
		}).bounds(cx - 155, 26, 150, 20).build()).active = this.tab != 0;

		this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.tab.challenges"), btn -> {
			this.tab = 1;
			this.rebuildWidgets();
		}).bounds(cx + 5, 26, 150, 20).build()).active = this.tab != 1;

		if (this.tab == 0) {
			buildGeneral(cx);
		} else {
			buildChallenges(cx);
		}

		// Pie
		int y = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.save"), btn -> {
			RuletaConfig.replace(this.working);
			this.onClose();
		}).bounds(cx - 155, y, 150, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.cancel"), btn -> this.onClose())
			.bounds(cx + 5, y, 150, 20).build());
	}

	// ---------------------------------------------------------------- pestaña general

	private void buildGeneral(int cx) {
		int left = cx - 155;
		int right = cx + 5;
		int w = 150;
		RuletaConfig c = this.working;

		addToggle(left, TOP, w, "ruleta_desafios.opt.auto_spin", () -> c.autoSpin, v -> c.autoSpin = v);
		addStepper(left, TOP + ROW, w, "ruleta_desafios.opt.auto_minutes", () -> c.autoSpinMinutes, v -> c.autoSpinMinutes = v, 1, 1, 1440);
		addStepper(left, TOP + ROW * 2, w, "ruleta_desafios.opt.default_duration", () -> c.defaultDurationSeconds, v -> c.defaultDurationSeconds = v, 30, 30, 86400);
		addStepper(left, TOP + ROW * 3, w, "ruleta_desafios.opt.anim", () -> c.spinAnimationSeconds, v -> c.spinAnimationSeconds = v, 1, 2, 30);
		addToggle(left, TOP + ROW * 4, w, "ruleta_desafios.opt.everyone", () -> c.everyoneCanSpin, v -> c.everyoneCanSpin = v);

		addToggle(right, TOP, w, "ruleta_desafios.opt.effects", () -> c.applyEffects, v -> c.applyEffects = v);
		addToggle(right, TOP + ROW, w, "ruleta_desafios.opt.all_players", () -> c.affectAllPlayers, v -> c.affectAllPlayers = v);
		addToggle(right, TOP + ROW * 2, w, "ruleta_desafios.opt.chat", () -> c.announceInChat, v -> c.announceInChat = v);
		addToggle(right, TOP + ROW * 3, w, "ruleta_desafios.opt.title", () -> c.showTitle, v -> c.showTitle = v);
		addToggle(right, TOP + ROW * 4, w, "ruleta_desafios.opt.sounds", () -> c.playSounds, v -> c.playSounds = v);
	}

	// ---------------------------------------------------------------- pestaña desafios

	private int pageCount() {
		return Math.max(1, (this.working.challenges.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private void buildChallenges(int cx) {
		List<Challenge> list = this.working.challenges;
		this.page = Math.max(0, Math.min(this.page, pageCount() - 1));

		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = this.page * PAGE_SIZE + i;
			if (index >= list.size()) {
				break;
			}
			Challenge challenge = list.get(index);
			int y = TOP + i * ROW;

			String shown = challenge.name.isBlank() ? challenge.id : challenge.name;
			Component name = Component.literal(shown).withStyle(challenge.enabled ? ChatFormatting.GREEN : ChatFormatting.RED);

			this.addRenderableWidget(Button.builder(name,
					btn -> this.minecraft.gui.setScreen(new ChallengeEditScreen(this, list, challenge, false)))
				.bounds(cx - 155, y, 205, 20).build());

			this.addRenderableWidget(Button.builder(
					Component.translatable(challenge.enabled ? "ruleta_desafios.state.enabled" : "ruleta_desafios.state.disabled"),
					btn -> {
						challenge.enabled = !challenge.enabled;
						this.rebuildWidgets();
					}).bounds(cx + 55, y, 100, 20).build());
		}

		int y = TOP + PAGE_SIZE * ROW;

		this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
			this.page--;
			this.rebuildWidgets();
		}).bounds(cx - 155, y, 30, 20).build()).active = this.page > 0;

		this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.add"), btn -> {
			Challenge fresh = new Challenge();
			fresh.id = "custom_" + Long.toString(System.currentTimeMillis(), 36);
			fresh.name = Component.translatable("ruleta_desafios.edit.new_name").getString();
			this.minecraft.gui.setScreen(new ChallengeEditScreen(this, list, fresh, true));
		}).bounds(cx - 75, y, 150, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
			this.page++;
			this.rebuildWidgets();
		}).bounds(cx + 125, y, 30, 20).build()).active = this.page < pageCount() - 1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawCentered(graphics, this.getTitle().getString(), this.width / 2, 10, 0xFFFFD700);

		if (this.tab == 1) {
			String pageText = (this.page + 1) + "/" + pageCount();
			drawCentered(graphics, pageText, this.width / 2 + 100, TOP + PAGE_SIZE * ROW + 6, 0xFFAAAAAA);
		}
	}
}
