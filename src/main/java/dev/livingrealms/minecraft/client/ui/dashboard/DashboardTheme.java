package dev.livingrealms.minecraft.client.ui.dashboard;

import net.minecraft.network.chat.Component;

/** Shared colors and labels for dashboard panels (client presentation only). */
public final class DashboardTheme {
    private DashboardTheme() {}

    public static int text(int rgba) {
        return dev.livingrealms.minecraft.client.ui.DashboardAccessibility.textColor(rgba);
    }

    public static int header() { return text(0xFFF2E8C9); }
    public static int body() { return text(0xFFE0E0E0); }
    public static int dim() { return text(0xFFA5A5A5); }
    public static int warn() { return text(0xFFFFB56B); }
    public static int accent() { return text(0xFFB59A5A); }
    public static int panelScrim() {
        return dev.livingrealms.minecraft.client.ui.DashboardAccessibility.panelScrim();
    }
    public static int backdrop() {
        return dev.livingrealms.minecraft.client.ui.DashboardAccessibility.backdrop();
    }

    public static Component tabTooltip(String label) {
        return Component.translatable("tooltip.livingrealms.dashboard_tab", label);
    }
}
