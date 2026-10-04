package dev.livingrealms.sim.civilian;

import java.util.Locale;
import java.util.Objects;

/**
 * Path/name heuristics for mapping foreign civilian entities into Living Realms roles.
 * Kept in sim so core tests can lock pack-adapter behaviour without Minecraft classes.
 */
public final class CivilianRoleInference {
    private CivilianRoleInference() {}

    public static CitizenRole fromSignals(String entityPath, String customName) {
        String path = entityPath == null ? "" : entityPath.toLowerCase(Locale.ROOT);
        String name = customName == null ? "" : customName.toLowerCase(Locale.ROOT);
        String signal = path + " " + name;
        if (containsAny(signal, "guard", "watchman", "soldier", "militia")) return CitizenRole.GUARD;
        if (containsAny(signal, "priest", "cleric", "monk", "bishop", "chapel")) return CitizenRole.PRIEST;
        if (containsAny(signal, "farmer", "peasant", "croft")) return CitizenRole.FARMER;
        if (containsAny(signal, "fisher", "fisherman", "angler")) return CitizenRole.FISHER;
        if (containsAny(signal, "miner", "prospector")) return CitizenRole.MINER;
        if (containsAny(signal, "lumber", "woodcutter", "forester")) return CitizenRole.LUMBERJACK;
        if (containsAny(signal, "hunter", "ranger", "tracker")) return CitizenRole.HUNTER;
        if (containsAny(signal, "baker", "butcher")) return CitizenRole.BUTCHER;
        if (containsAny(signal, "carpenter", "builder", "mason")) return CitizenRole.BUILDER;
        if (containsAny(signal, "blacksmith", "smith", "artisan", "craftsman", "toolsmith", "weaponsmith", "armorer")) return CitizenRole.ARTISAN;
        if (containsAny(signal, "scholar", "librarian", "teacher", "sage", "cartographer")) return CitizenRole.SCHOLAR;
        if (containsAny(signal, "healer", "doctor", "medic", "apothecary")) return CitizenRole.HEALER;
        if (containsAny(signal, "official", "mayor", "bailiff", "clerk", "noble")) return CitizenRole.OFFICIAL;
        if (containsAny(signal, "sailor", "dock", "harbor", "harbour", "captain")) return CitizenRole.SAILOR;
        if (containsAny(signal, "merchant", "trader", "vendor", "shopkeep")) return CitizenRole.TRADER;
        return CitizenRole.TRADER;
    }

    public static CitizenRole fromSignals(String entityPath) {
        return fromSignals(entityPath, "");
    }

    private static boolean containsAny(String signal, String... tokens) {
        Objects.requireNonNull(signal);
        for (String t : tokens) if (signal.contains(t)) return true;
        return false;
    }
}
