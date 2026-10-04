package dev.livingrealms.minecraft.client.ui;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client-only dashboard accessibility preferences (UI scale + high contrast).
 * Does not touch canonical simulation state.
 */
public final class DashboardAccessibility {
    public enum Scale {
        COMPACT(520, 320, 11, 12),
        NORMAL(640, 420, 12, 14),
        LARGE(780, 520, 14, 16);

        public final int panelWidth;
        public final int panelHeight;
        public final int lineHeight;
        public final int pageLines;

        Scale(int panelWidth, int panelHeight, int lineHeight, int pageLines) {
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.lineHeight = lineHeight;
            this.pageLines = pageLines;
        }
    }

    private static Scale scale = Scale.NORMAL;
    private static boolean highContrast;
    private static boolean loaded;

    private DashboardAccessibility() {}

    public static Scale scale() {
        ensureLoaded();
        return scale;
    }

    public static boolean highContrast() {
        ensureLoaded();
        return highContrast;
    }

    public static void cycleScale() {
        ensureLoaded();
        Scale[] values = Scale.values();
        scale = values[(scale.ordinal() + 1) % values.length];
        save();
    }

    public static void toggleHighContrast() {
        ensureLoaded();
        highContrast = !highContrast;
        save();
    }

    public static int textColor(int normal) {
        if (!highContrast()) return normal;
        int a = (normal >>> 24) & 0xFF;
        int r = (normal >>> 16) & 0xFF;
        int g = (normal >>> 8) & 0xFF;
        int b = normal & 0xFF;
        if (r + g + b < 420) {
            r = Math.min(255, r + 70);
            g = Math.min(255, g + 70);
            b = Math.min(255, b + 40);
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int panelScrim() {
        return highContrast() ? 0xCC0A0C10 : 0x99101418;
    }

    public static int backdrop() {
        return highContrast() ? 0xE005070A : 0xC805080B;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = configPath();
        if (path == null || !Files.isRegularFile(path)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            String scaleName = props.getProperty("scale", Scale.NORMAL.name());
            try {
                scale = Scale.valueOf(scaleName);
            } catch (IllegalArgumentException ignored) {
                scale = Scale.NORMAL;
            }
            highContrast = Boolean.parseBoolean(props.getProperty("highContrast", "false"));
        } catch (IOException ignored) {
            // Keep defaults when the client config is missing or unreadable.
        }
    }

    private static void save() {
        Path path = configPath();
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Properties props = new Properties();
            props.setProperty("scale", scale.name());
            props.setProperty("highContrast", Boolean.toString(highContrast));
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "Living Realms dashboard accessibility");
            }
        } catch (IOException ignored) {
            // Accessibility prefs are best-effort; gameplay continues with in-memory values.
        }
    }

    private static Path configPath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return null;
        return mc.gameDirectory.toPath().resolve("config").resolve("livingrealms").resolve("dashboard_a11y.properties");
    }
}
