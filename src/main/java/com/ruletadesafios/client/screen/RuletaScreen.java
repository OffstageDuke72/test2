package com.ruletadesafios.client.screen;

import com.ruletadesafios.config.Challenge;
import com.ruletadesafios.config.RuletaConfig;
import com.ruletadesafios.server.RuletaManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * La ruleta: una tira horizontal de tarjetas que gira y se frena con un puntero dorado en el centro.
 * Al detenerse, el desafio elegido se activa con el comando {@code /ruleta aplicar <id>}.
 * <p>
 * Animaciones: aparicion suave, tarjetas que se atenuan con la distancia y se "levantan" al pasar bajo
 * el puntero, puntero que rebota en cada clic, parpadeo dorado del ganador, confeti y texto que aparece
 * con fundido.
 */
public class RuletaScreen extends RuletaBaseScreen {
	private static final int CARD_W = 120;
	private static final int CARD_H = 60;
	private static final int GAP = 6;
	private static final int STEP = CARD_W + GAP;
	private static final int[] COLORS = {
		0xFF3B6EA8, 0xFF2E8B57, 0xFF9B4DCA, 0xFFC0702A,
		0xFFB03A48, 0xFF2A9D9D, 0xFF8A7F1E, 0xFF5B5FC7
	};
	private static final int[] CONFETTI = {
		0xFFFFD700, 0xFFFF5555, 0xFF55FF55, 0xFF55AAFF, 0xFFFF77FF, 0xFFFFFFFF, 0xFFFFAA00
	};

	private enum State {IDLE, SPINNING, DONE}

	private static final class Particle {
		float x, y, vx, vy, life, maxLife;
		int color;
	}

	private final long openedMs = System.currentTimeMillis();
	private List<Challenge> pool = new ArrayList<>();
	private State state = State.IDLE;
	private Challenge winner;
	private int winnerCardIndex = -1;

	private double offset;
	private double startOffset;
	private double targetOffset;
	private long startMs;
	private long durationMs;
	private long doneMs;
	private int lastIndex = Integer.MIN_VALUE;

	// Animaciones
	private long lastFrameMs = System.currentTimeMillis();
	private float dt;
	private float pointerKick;
	private final List<Particle> confetti = new ArrayList<>();

	private Button spinButton;

	public RuletaScreen(Screen parent) {
		super(Component.translatable("ruleta_desafios.title"), parent);
	}

	// ---------------------------------------------------------------- ciclo de vida

	@Override
	protected void init() {
		if (this.state != State.SPINNING) {
			this.pool = new ArrayList<>(RuletaConfig.get().enabledChallenges());
		}

		int cx = this.width / 2;
		int y = this.height - 28;

		this.spinButton = this.addRenderableWidget(Button.builder(spinLabel(), btn -> startSpin())
			.bounds(cx - 60, y, 120, 20).build());
		this.spinButton.active = this.state != State.SPINNING && !this.pool.isEmpty();

		this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.config"),
				btn -> this.minecraft.gui.setScreen(new RuletaConfigScreen(this)))
			.bounds(cx - 190, y, 120, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("ruleta_desafios.button.close"), btn -> this.onClose())
			.bounds(cx + 70, y, 120, 20).build());
	}

	private Component spinLabel() {
		return Component.translatable(this.state == State.DONE ? "ruleta_desafios.button.spin_again" : "ruleta_desafios.button.spin");
	}

	/** No pausar el juego mientras gira (en un mundo local pausaria el servidor y el comando no se ejecutaria). */
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.state == State.SPINNING && System.currentTimeMillis() - this.startMs >= this.durationMs) {
			finishSpin();
		}
	}

	// ---------------------------------------------------------------- giro

	private void startSpin() {
		if (this.state == State.SPINNING || this.pool.isEmpty()) {
			return;
		}
		int n = this.pool.size();
		int w = RuletaConfig.pickIndexWeighted(this.pool);

		long now = System.currentTimeMillis();
		this.startOffset = this.offset;
		this.startMs = now;
		this.durationMs = RuletaConfig.get().spinAnimationSeconds * 1000L;

		// Indice de tarjeta (en la tira infinita) donde se va a detener: al menos 24 tarjetas mas alla,
		// y que corresponda al ganador (indice % n == w).
		int minIndex = (int) Math.ceil(this.startOffset / STEP) + 24;
		int target = minIndex + ((w - minIndex) % n + n) % n;
		double jitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * (CARD_W * 0.7);

		this.targetOffset = (double) target * STEP + jitter;
		this.winner = this.pool.get(w);
		this.winnerCardIndex = target;
		this.state = State.SPINNING;
		this.lastIndex = Integer.MIN_VALUE;
		this.confetti.clear();
		this.spinButton.active = false;
	}

	private void finishSpin() {
		this.state = State.DONE;
		this.doneMs = System.currentTimeMillis();
		this.offset = this.targetOffset;
		this.pointerKick = 1.0F;
		this.spinButton.setMessage(spinLabel());
		this.spinButton.active = true;
		spawnConfetti();

		// Activar el desafio en el servidor (en un mundo local es el servidor integrado).
		ClientPacketListener connection = this.minecraft.getConnection();
		if (connection != null && this.winner != null) {
			connection.sendCommand("ruleta aplicar " + this.winner.id);
		}
		playSound(false);
	}

	private void playSound(boolean tick) {
		if (!RuletaConfig.get().playSounds) {
			return;
		}
		if (tick) {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.5F));
		} else {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
		}
	}

	private void spawnConfetti() {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int cx = this.width / 2;
		for (int i = 0; i < 90; i++) {
			Particle p = new Particle();
			p.x = cx + (float) rnd.nextDouble(-20, 20);
			p.y = 44;
			double angle = Math.toRadians(rnd.nextDouble(200, 340)); // abanico hacia arriba
			double speed = rnd.nextDouble(120, 340);
			p.vx = (float) (Math.cos(angle) * speed);
			p.vy = (float) (Math.sin(angle) * speed);
			p.maxLife = p.life = (float) rnd.nextDouble(1.6, 2.8);
			p.color = CONFETTI[rnd.nextInt(CONFETTI.length)];
			this.confetti.add(p);
		}
	}

	private void updateAnimations() {
		long now = System.currentTimeMillis();
		this.dt = Math.min(0.05F, (now - this.lastFrameMs) / 1000.0F);
		this.lastFrameMs = now;

		this.pointerKick = Math.max(0.0F, this.pointerKick - this.dt * 5.0F);

		// Offset de la tira
		if (this.state == State.SPINNING) {
			double p = Math.min(1.0, (now - this.startMs) / (double) this.durationMs);
			double eased = 1.0 - Math.pow(1.0 - p, 3.0);
			this.offset = this.startOffset + (this.targetOffset - this.startOffset) * eased;

			int index = (int) Math.round(this.offset / STEP);
			if (index != this.lastIndex) {
				this.lastIndex = index;
				this.pointerKick = 1.0F;
				playSound(true);
			}
		} else if (this.state == State.IDLE) {
			this.offset = (now - this.openedMs) * 0.02; // desplazamiento lento mientras espera
		}

		// Confeti
		for (int i = this.confetti.size() - 1; i >= 0; i--) {
			Particle p = this.confetti.get(i);
			p.vy += 520.0F * this.dt; // gravedad
			p.vx *= 1.0F - 0.6F * this.dt; // rozamiento
			p.x += p.vx * this.dt;
			p.y += p.vy * this.dt;
			p.life -= this.dt;
			if (p.life <= 0 || p.y > this.height + 10) {
				this.confetti.remove(i);
			}
		}
	}

	// ---------------------------------------------------------------- dibujo

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		updateAnimations();

		long now = System.currentTimeMillis();
		float sinceOpen = (now - this.openedMs) / 1000.0F;
		float intro = easeOut(Math.min(1.0F, sinceOpen / 0.45F)); // 0 -> 1 al abrir la pantalla

		int cx = this.width / 2;
		drawCentered(graphics, this.getTitle().getString(), cx, 14, withAlpha(0xFFFFD700, intro));

		if (this.pool.isEmpty()) {
			drawCentered(graphics, Component.translatable("ruleta_desafios.wheel.empty").getString(), cx, this.height / 2 - 4, 0xFFFF7777);
			return;
		}

		int left = 14;
		int right = this.width - 14;
		int stripTop = 36 - Math.round((1.0F - intro) * 14); // la tira "baja" al abrir
		int stripBottom = stripTop + CARD_H + 12;
		int cardTop = stripTop + 6;

		// Marco (late en dorado cuando hay ganador)
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 160.0);
		int frameColor = this.state == State.DONE ? lerpColor(0xFF8A6F00, 0xFFFFD700, pulse) : 0xFF000000;
		graphics.fill(left - 2, stripTop - 2, right + 2, stripBottom + 2, withAlpha(frameColor, intro));
		graphics.fill(left, stripTop, right, stripBottom, withAlpha(0xFF1B1B1B, intro));

		int n = this.pool.size();
		double half = (right - left) / 2.0;
		int first = Math.max(0, (int) Math.floor((this.offset - half) / STEP) - 1);
		int last = (int) Math.ceil((this.offset + half) / STEP) + 1;

		for (int i = first; i <= last; i++) {
			Challenge challenge = this.pool.get(i % n);
			int cardX = (int) Math.round(cx + (double) i * STEP - this.offset - CARD_W / 2.0);

			int x1 = Math.max(cardX, left);
			int x2 = Math.min(cardX + CARD_W, right);
			if (x1 >= x2) {
				continue;
			}

			// closeness: 1 = justo bajo el puntero, 0 = lejos
			double distance = Math.abs(cardX + CARD_W / 2.0 - cx);
			float closeness = (float) Math.max(0.0, 1.0 - distance / (STEP * 1.6));
			float c2 = closeness * closeness;

			int lift = Math.round(c2 * 5.0F);
			int y1 = cardTop - lift;
			int y2 = y1 + CARD_H;

			int base = COLORS[Math.floorMod(challenge.id.hashCode(), COLORS.length)];
			graphics.fill(x1, y1, x2, y2, withAlpha(base, intro));

			// Brillo superior para dar relieve
			graphics.fill(x1, y1, x2, y1 + 2, withAlpha(0x66FFFFFF, intro));

			// Atenuar las tarjetas lejanas (oscurece hasta ~60%)
			int dim = Math.round((1.0F - c2) * 150.0F);
			if (dim > 4) {
				graphics.fill(x1, y1, x2, y2, dim << 24);
			}

			boolean fullyVisible = cardX >= left && cardX + CARD_W <= right;
			if (fullyVisible) {
				drawCard(graphics, challenge, cardX, y1, closeness);
			}

			if (this.state == State.DONE && i == this.winnerCardIndex) {
				int borderColor = lerpColor(0xFFFFD700, 0xFFFFFFFF, pulse);
				int grow = 2 + Math.round(pulse * 2.0F);
				drawBorder(graphics, cardX, y1, CARD_W, CARD_H, grow, borderColor);
			} else if (this.state == State.SPINNING && closeness > 0.92F) {
				drawBorder(graphics, cardX, y1, CARD_W, CARD_H, 1, 0xCCFFFFFF);
			}
		}

		// Puntero central con "rebote" en cada clic
		int kick = Math.round(this.pointerKick * 4.0F);
		graphics.fill(cx - 1, stripTop - 8, cx + 1, stripBottom + 8, 0xFFFFD700);
		graphics.fill(cx - 4 - kick, stripTop - 8 - kick, cx + 4 + kick, stripTop - 6, 0xFFFFD700);
		graphics.fill(cx - 4 - kick, stripBottom + 6, cx + 4 + kick, stripBottom + 8 + kick, 0xFFFFD700);

		// Texto inferior
		int textY = stripBottom + 18;
		if (this.state == State.DONE && this.winner != null) {
			float appear = easeOut(Math.min(1.0F, (now - this.doneMs) / 600.0F));
			int rise = Math.round((1.0F - appear) * 10.0F); // el texto "sube" mientras aparece

			drawCentered(graphics, Component.translatable("ruleta_desafios.wheel.result").getString(),
				cx, textY + rise, withAlpha(0xFFAAAAAA, appear));
			drawCentered(graphics, this.winner.name, cx, textY + 12 + rise, withAlpha(lerpColor(0xFFFFD700, 0xFFFFF2A0, pulse), appear));

			int lineY = textY + 26 + rise;
			for (String line : wrap(this.winner.description, Math.min(this.width - 40, 320), 3)) {
				drawCentered(graphics, line, cx, lineY, withAlpha(0xFFFFFFFF, appear));
				lineY += 11;
			}
			int seconds = this.winner.effectiveDuration(RuletaConfig.get());
			drawCentered(graphics,
				Component.translatable("ruleta_desafios.wheel.duration", RuletaManager.formatTime(seconds)).getString(),
				cx, lineY + 2, withAlpha(0xFF99CCFF, appear));
		} else if (this.state == State.IDLE) {
			// Texto de ayuda que "respira"
			float breathe = 0.65F + 0.35F * (float) Math.sin(now / 450.0);
			drawCentered(graphics, Component.translatable("ruleta_desafios.wheel.hint").getString(), cx, textY, withAlpha(0xFFAAAAAA, breathe));
		}

		drawConfetti(graphics);
	}

	private void drawCard(GuiGraphicsExtractor graphics, Challenge challenge, int cardX, int cardTop, float closeness) {
		int centerX = cardX + CARD_W / 2;
		List<String> lines = wrap(challenge.name, CARD_W - 12, 2);
		int textY = cardTop + (CARD_H - (lines.size() * 11 + 12)) / 2;
		int textColor = lerpColor(0xFFB8B8B8, 0xFFFFFFFF, closeness);
		for (String line : lines) {
			drawCentered(graphics, line, centerX, textY, textColor);
			textY += 11;
		}
		String time = RuletaManager.formatTime(challenge.effectiveDuration(RuletaConfig.get()));
		drawCentered(graphics, time, centerX, textY + 2, lerpColor(0xFF999999, 0xFFEEEEEE, closeness));
	}

	private void drawConfetti(GuiGraphicsExtractor graphics) {
		for (Particle p : this.confetti) {
			float fade = Math.min(1.0F, p.life / (p.maxLife * 0.4F)); // se desvanece al final
			int size = 3;
			int x = Math.round(p.x);
			int y = Math.round(p.y);
			graphics.fill(x, y, x + size, y + size, withAlpha(p.color, fade));
		}
	}

	// ---------------------------------------------------------------- utilidades de animacion

	private static float easeOut(float t) {
		return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
	}

	/** Multiplica el alfa de un color ARGB por {@code factor} (0..1). Nunca baja de 5 (alfa < 4 se dibuja opaco). */
	private static int withAlpha(int argb, float factor) {
		int a = (argb >>> 24) & 0xFF;
		int na = Math.max(5, Math.min(255, Math.round(a * Math.max(0.0F, Math.min(1.0F, factor)))));
		return (na << 24) | (argb & 0x00FFFFFF);
	}

	private static int lerpColor(int from, int to, float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		int a = Math.round(((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
		int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
		int g = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
		int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int thickness, int color) {
		graphics.fill(x - thickness, y - thickness, x + w + thickness, y, color);
		graphics.fill(x - thickness, y + h, x + w + thickness, y + h + thickness, color);
		graphics.fill(x - thickness, y, x, y + h, color);
		graphics.fill(x + w, y, x + w + thickness, y + h, color);
	}
}
