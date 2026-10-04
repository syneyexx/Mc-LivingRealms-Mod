package dev.livingrealms.sim.civilization;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Concrete medieval faith profiles derived from the faction's {@code faithName}.
 * Deterministic catalog — no extra save fields; identity is the faith string already persisted.
 */
public final class FaithCatalog {
    private FaithCatalog() {}

    public record FaithProfile(
            String name,
            String primaryDeity,
            String dogma,
            String symbol,
            String scripture,
            int[] holyDaysOfYear,
            int[] fastingDaysOfYear,
            double churchTitheShare,
            double heresySeverity,
            boolean meatFast,
            String holyOrder
    ) {
        public boolean isHolyDay(long day) {
            int doy = CivilizationCalendar.dayOfYear(day);
            for (int h : holyDaysOfYear) if (h == doy) return true;
            return false;
        }

        public boolean isFastingDay(long day) {
            int doy = CivilizationCalendar.dayOfYear(day);
            for (int f : fastingDaysOfYear) if (f == doy) return true;
            // Weekly fast: every 7th day for meat-fasting faiths.
            return meatFast && Math.floorMod(doy, 7) == 0;
        }
    }

    private static final Map<String, FaithProfile> BY_NAME = new LinkedHashMap<>();

    static {
        put("The Hearth Covenant", "Hearthmother", "Keep the home fire and share bread with kin", "hearth-flame",
                "Book of Embers", days(15, 90, 180, 270), days(14, 89), .04, .35, true, "Keepers of the Hearth");
        put("The Lantern Faith", "The Lantern Bearer", "Carry light into dark places and swear no false oath", "lantern",
                "Lantern Canticle", days(30, 120, 210, 300), days(29, 119), .05, .55, true, "Lantern Wardens");
        put("The Old Stars", "The Star Choir", "Read the heavens; do not fear the night road", "seven-star",
                "Astral Codex", days(0, 180, 359), days(179), .03, .25, false, "Star Pilgrims");
        put("The Green Oath", "The Green Lord", "Honor soil, forest and the turning of seasons", "oak-leaf",
                "Verdant Litany", days(45, 135, 225, 315), days(44, 134), .035, .30, true, "Green Wardens");
        put("The River Saints", "Saint of Fords", "Cross cleanly; give alms at every bridge", "wave-cross",
                "River Martyrology", days(60, 150, 240, 330), days(59, 149), .045, .40, true, "Bridge Brothers");
        put("The Forge Rite", "The Anvil Saint", "Labor is prayer; waste is sin", "hammer",
                "Iron Psalms", days(20, 100, 200, 280), days(19, 99), .05, .60, false, "Forge Templars");
        put("The Crown Creed", "The Crowned Judge", "Order under lawful crowns is holy", "crowned-sun",
                "Regal Testament", days(1, 91, 181, 271), days(90, 180), .06, .70, true, "Crown Inquisitors");
        put("The Moon Chapel", "The Silver Moon", "Mercy by night; silence and healing", "crescent",
                "Moon Hours", days(75, 165, 255, 345), days(74, 164), .04, .35, true, "Night Hospitallers");
        put("The Ancestor Path", "The First Ancestors", "Name the dead; keep their bargains", "ancestral-stone",
                "Bone Chronicle", days(10, 100, 190, 280), days(9, 189), .035, .45, false, "Ancestor Keepers");
        put("The Dawn Assembly", "The Rising Dawn", "Begin each day in truth and charity", "sunrise",
                "Dawn Homilies", days(5, 95, 185, 275), days(4, 94), .04, .40, true, "Dawn Preachers");
        put("The Stone Testament", "The Mountain Judge", "Endure; build lasting works", "keystone",
                "Stone Tablets", days(40, 130, 220, 310), days(39, 129), .05, .65, false, "Stone Guardians");
        put("The Quiet Flame", "The Quiet Flame", "Speak little; tend the sick and the poor", "veiled-flame",
                "Silent Offices", days(50, 140, 230, 320), days(49, 139), .03, .20, true, "Silent Brothers");
    }

    public static FaithProfile of(String faithName) {
        Objects.requireNonNull(faithName, "faithName");
        FaithProfile profile = BY_NAME.get(faithName);
        if (profile != null) return profile;
        // Stable fallback for player/custom faiths.
        int h = Math.floorMod(faithName.toLowerCase(Locale.ROOT).hashCode(), 360);
        return new FaithProfile(faithName, "Local Patron", "Keep faith with your people", "simple-cross",
                "Local Catechism", new int[]{h, Math.floorMod(h + 90, 360)}, new int[]{Math.floorMod(h - 1, 360)},
                .04, .40, true, "Local Order");
    }

    public static boolean known(String faithName) {
        return BY_NAME.containsKey(faithName);
    }

    private static void put(String name, String deity, String dogma, String symbol, String scripture,
                            int[] holy, int[] fast, double tithe, double heresy, boolean meatFast, String order) {
        BY_NAME.put(name, new FaithProfile(name, deity, dogma, symbol, scripture, holy, fast, tithe, heresy, meatFast, order));
    }

    private static int[] days(int... values) {
        return values;
    }
}
