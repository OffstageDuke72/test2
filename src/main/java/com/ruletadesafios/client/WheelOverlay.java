package com.ruletadesafios.client;

import net.minecraft.client.renderer.RenderPipelines;
import com.ruletadesafios.config.Challenge;
import com.ruletadesafios.config.RuletaConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ruleta circular dibujada directamente sobre el HUD, sin abrir una pantalla
 * ni ocultar el mundo. La tecla R inicia el giro.
 */
public final class WheelOverlay {
    private static final Identifier WHEEL_TEXTURE =
            Identifier.fromNamespaceAndPath("ruleta_desafios", "textures/gui/ruleta_wheel.png");
    private static final Identifier POINTER_TEXTURE =
            Identifier.fromNamespaceAndPath("ruleta_desafios", "textures/gui/ruleta_pointer.png");

    private static final long SPIN_DURATION_MS = 6500L;
    private static final long RESULT_DURATION_MS = 5000L;
    private static final int WHEEL_SIZE = 196;

    private static boolean spinning;
    private static boolean showingResult;
    private static Challenge winner;
    private static long startMs;
    private static long resultMs;
    private static long spinDurationMs = SPIN_DURATION_MS;
    private static double startRotation;
    private static double targetRotation;
    private static double currentRotation;

    private WheelOverlay() {}

    public static void init() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("ruleta_desafios", "wheel_overlay"),
                WheelOverlay::render
        );
    }

    public static void start() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null || spinning) {
            return;
        }

        List<Challenge> pool = RuletaConfig.get().enabledChallenges();
        if (pool.isEmpty()) {
            client.player.displayClientMessage(
                    Component.literal("§cNo hay desafíos activos en la ruleta."),
                    true
            );
            return;
        }

        int picked = RuletaConfig.pickIndexWeighted(pool);
        if (picked < 0) return;

        winner = pool.get(picked);
        startMs = Util.getMillis();
        spinDurationMs = RuletaConfig.get().spinAnimationSeconds * 1000L;
        startRotation = currentRotation;
        // Seis a nueve vueltas completas y una posición final aleatoria.
        double turns = ThreadLocalRandom.current().nextDouble(6.0, 9.0);
        double finalAngle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        targetRotation = startRotation + turns * Math.PI * 2.0 + finalAngle;
        spinning = true;
        showingResult = false;

        if (RuletaConfig.get().playSounds) {
            client.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
        }
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) return;
        if (!spinning && !showingResult) return;

        long now = Util.getMillis();
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2 - 8;

        if (spinning) {
            double progress = Math.min(1.0, (now - startMs) / (double) spinDurationMs);
            // Desaceleración progresiva, similar a una ruleta física.
            double eased = 1.0 - Math.pow(1.0 - progress, 4.0);
            currentRotation = startRotation + (targetRotation - startRotation) * eased;
            if (progress >= 1.0) {
                currentRotation = targetRotation;
                finish();
                now = Util.getMillis();
            }
        }

        int x = centerX - WHEEL_SIZE / 2;
        int y = centerY - WHEEL_SIZE / 2;

        // Sombra ligera detrás de la ruleta.
        graphics.fill(x - 8, y - 8, x + WHEEL_SIZE + 8, y + WHEEL_SIZE + 8, 0x55000000);

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate((float) currentRotation);
        graphics.pose().translate(-centerX, -centerY);
        graphics.blit(RenderPipelines.GUI_TEXTURED, WHEEL_TEXTURE,
                x, y, 0, 0, WHEEL_SIZE, WHEEL_SIZE, 512, 512);
        graphics.pose().popMatrix();

        // El puntero permanece fijo mientras gira la rueda.
        graphics.blit(RenderPipelines.GUI_TEXTURED, POINTER_TEXTURE,
                centerX - 24, y - 28, 0, 0, 48, 63, 64, 84);

        if (showingResult && winner != null) {
            long elapsed = now - resultMs;
            if (elapsed >= RESULT_DURATION_MS) {
                showingResult = false;
                winner = null;
                return;
            }

            int panelX = 10;
            int panelY = 10;
            int panelW = Math.min(230, graphics.guiWidth() - 20);
            int panelH = 62;
            float fade = Math.min(1.0F, Math.min(elapsed / 250.0F,
                    (RESULT_DURATION_MS - elapsed) / 450.0F));
            int alpha = Math.max(0, Math.min(255, Math.round(fade * 235)));
            int bg = (alpha << 24) | 0x17100A;
            int gold = (Math.round(fade * 255) << 24) | 0xFFC83D;
            int white = (Math.round(fade * 255) << 24) | 0xFFFFFF;

            graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, bg);
            graphics.fill(panelX, panelY, panelX + 3, panelY + panelH, gold);
            graphics.text(client.font, "¡CAMBIO DE DESAFÍO!", panelX + 10, panelY + 7, gold, true);
            graphics.text(client.font, winner.name, panelX + 10, panelY + 21, white, true);

            String description = winner.description == null ? "" : winner.description;
            int maxChars = Math.max(18, (panelW - 20) / 6);
            if (description.length() > maxChars) {
                description = description.substring(0, maxChars - 3) + "...";
            }
            graphics.text(client.font, description, panelX + 10, panelY + 38, white, true);
        }
    }

    private static void finish() {
        Minecraft client = Minecraft.getInstance();
        spinning = false;
        showingResult = true;
        resultMs = Util.getMillis();

        if (winner != null) {
            ClientPacketListener connection = client.getConnection();
            if (connection != null) {
                connection.sendCommand("ruleta aplicar " + winner.id);
            }
        }

        if (RuletaConfig.get().playSounds) {
            client.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F)
            );
        }
    }
}
