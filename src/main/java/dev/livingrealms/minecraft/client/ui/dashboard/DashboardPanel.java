package dev.livingrealms.minecraft.client.ui.dashboard;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/** One dashboard tab's widgets + text lines. Authorization stays server-side. */
public interface DashboardPanel {
    void rebuildWidgets(PanelHost host, RealmDashboardSnapshot snapshot, DashboardLayout layout, int page);

    List<DashboardLine> lines(RealmDashboardSnapshot snapshot, int page);

    default void renderCustom(GuiGraphics graphics, Font font, RealmDashboardSnapshot snapshot,
                              DashboardLayout layout, int page) {}

    default int maxPage(RealmDashboardSnapshot snapshot, int pageLines) {
        int size = lines(snapshot, 0).size();
        return Math.max(0, (size - 1) / Math.max(1, pageLines));
    }

    /** Host that can register Minecraft widgets without exposing Screen internals. */
    interface PanelHost {
        void addWidget(AbstractWidget widget);
        Font font();
        int screenHeight();
    }

    record DashboardLine(String text, int color, int indent) {
        public static DashboardLine header(String value) {
            return new DashboardLine(value, DashboardTheme.header(), 0);
        }
        public static DashboardLine text(String value) {
            return new DashboardLine(value, DashboardTheme.body(), 0);
        }
        public static DashboardLine warn(String value) {
            return new DashboardLine(value, DashboardTheme.warn(), 0);
        }
        public static DashboardLine dim(String value, int indent) {
            return new DashboardLine(value, DashboardTheme.dim(), indent);
        }
    }

    static String whole(double value) { return String.format(Locale.ROOT, "%.0f", value); }
    static String one(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    static String pct(double value) { return String.format(Locale.ROOT, "%.0f%%", value * 100.0); }
    static String titleCase(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
