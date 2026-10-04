package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import java.util.Locale;

/**
 * Bandit typology for land raids and hideouts. Kind is carried in history/event text
 * (no extra save field); behaviour differs by manpower, morale and hideout defense.
 */
public enum BanditArchetype {
    HIGHWAYMEN("highwaymen", 1.00, .50, .10),
    DESERTERS("deserters", 1.15, .42, .18),
    HUNGRY_PEASANTS("hungry_peasants", .85, .38, .06),
    TAX_REBELS("tax_rebels", 1.05, .55, .12),
    SMUGGLERS("smugglers", .70, .60, .22),
    FALLEN_MERCENARIES("fallen_mercenaries", 1.25, .48, .20),
    ESCAPED_PRISONERS("escaped_prisoners", .90, .35, .08),
    OUTLAW_SECT("outlaw_sect", .95, .52, .16);

    private final String key;
    private final double manpowerMul;
    private final double morale;
    private final double hideoutDefense;

    BanditArchetype(String key, double manpowerMul, double morale, double hideoutDefense) {
        this.key = key;
        this.manpowerMul = manpowerMul;
        this.morale = morale;
        this.hideoutDefense = hideoutDefense;
    }

    public String key() { return key; }
    public double manpowerMul() { return manpowerMul; }
    public double morale() { return morale; }
    public double hideoutDefense() { return hideoutDefense; }

    public String displayName() {
        return key.replace('_', ' ');
    }

    /** Pick a cause from local pressures; deterministic from settlement + day. */
    public static BanditArchetype choose(Settlement settlement, Faction owner, SettlementCivilizationState civ, long day) {
        double hunger = 1.0 - settlement.foodSecurity();
        double tax = owner.government().taxRate();
        double jobless = 1.0 - settlement.employment();
        double order = 1.0 - settlement.publicOrder();
        double bandit = civ.banditPressure();
        long h = day * 0x9E3779B97F4A7C15L ^ settlement.id() * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 33;
        double roll = ((h >>> 11) & 0x1FFFFFFFFFFFFFL) * 0x1.0p-53;
        if (hunger > .55 && roll < .28) return HUNGRY_PEASANTS;
        if (tax > .28 && settlement.unrest() > .35 && roll < .45) return TAX_REBELS;
        if (jobless > .45 && roll < .55) return HIGHWAYMEN;
        if (order > .55 && bandit > .5 && roll < .68) return ESCAPED_PRISONERS;
        if (owner.government().corruption() > .45 && roll < .78) return SMUGGLERS;
        if (settlement.completedConstruction().stream().anyMatch(k -> k.startsWith("temple:"))
                && settlement.unrest() > .4 && roll < .88) return OUTLAW_SECT;
        if (roll < .92) return FALLEN_MERCENARIES;
        return HIGHWAYMEN;
    }

    public static BanditArchetype fromKey(String key) {
        if (key == null || key.isBlank()) return HIGHWAYMEN;
        String k = key.toLowerCase(Locale.ROOT);
        for (BanditArchetype a : values()) if (a.key.equals(k) || k.contains(a.key)) return a;
        return HIGHWAYMEN;
    }
}
