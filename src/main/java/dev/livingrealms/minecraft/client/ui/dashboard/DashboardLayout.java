package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.minecraft.client.ui.DashboardAccessibility;

/** Geometry helpers shared by dashboard panels. */
public final class DashboardLayout {
    public final int width;
    public final int height;
    public final int panelWidth;
    public final int panelHeight;
    public final int left;
    public final int top;
    public final int contentY;
    public final int contentBottom;
    public final int footerY;
    public final int lineHeight;
    public final int pageLines;

    public DashboardLayout(int screenWidth, int screenHeight, int tabCount) {
        this.width = screenWidth;
        this.height = screenHeight;
        int prefW = DashboardAccessibility.scale().panelWidth;
        int prefH = DashboardAccessibility.scale().panelHeight;
        int pw = Math.min(prefW, screenWidth - 20);
        int ph = Math.min(prefH, screenHeight - 20);
        ph = Math.min(ph, Math.max(260, screenHeight - 24));
        pw = Math.min(pw, Math.max(360, screenWidth - 24));
        this.panelWidth = pw;
        this.panelHeight = ph;
        this.left = (screenWidth - pw) / 2;
        this.top = Math.max(8, (screenHeight - ph) / 2);
        this.lineHeight = DashboardAccessibility.scale().lineHeight;
        this.pageLines = DashboardAccessibility.scale().pageLines;
        int columns = tabColumns(pw);
        int rows = (tabCount + columns - 1) / columns;
        this.contentY = top + 28 + rows * 20 + 7;
        this.contentBottom = top + ph - 30;
        this.footerY = top + ph - 26;
    }

    public static int tabColumns(int panelWidth) {
        return Math.max(3, Math.min(7, Math.max(1, (panelWidth - 16) / 70)));
    }

    public int clipHeight() {
        return Math.max(40, contentBottom - contentY);
    }
}
