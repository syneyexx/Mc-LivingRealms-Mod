package dev.livingrealms.minecraft.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Shared GUI helpers for Living Realms screens. Vanilla {@link Screen#renderBackground} applies a
 * world blur pass in 1.21+; every Living Realms overlay must suppress that so the world stays sharp.
 */
public final class LivingRealmsScreens {
    private LivingRealmsScreens() {}

    /** No-op background: do not blur or darken the world framebuffer. */
    public static void clearBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty — callers paint their own scrim in render().
    }

    /** Opaque/semitransparent fill used instead of the vanilla blurred backdrop. */
    public static void paintBackdrop(GuiGraphics graphics, int width, int height, int argb) {
        graphics.fill(0, 0, width, height, argb);
    }
}
