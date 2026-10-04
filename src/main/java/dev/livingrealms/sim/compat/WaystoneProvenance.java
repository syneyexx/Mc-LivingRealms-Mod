package dev.livingrealms.sim.compat;

/** Pure helpers for Living Realms-authored Waystone identity (no Minecraft types). */
public final class WaystoneProvenance {
    public static final String NAME_PREFIX = "LR · ";

    private WaystoneProvenance() {}

    public static boolean isLivingRealmsName(String name) {
        return name != null && name.startsWith(NAME_PREFIX);
    }

    public static String authoredName(String settlementName) {
        if (settlementName == null || settlementName.isBlank()) return NAME_PREFIX + "Settlement";
        return NAME_PREFIX + settlementName;
    }
}
