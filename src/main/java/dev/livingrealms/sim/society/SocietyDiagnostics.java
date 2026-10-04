package dev.livingrealms.sim.society;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.Mathx;
import java.util.Objects;

/** Single source of truth for civilian needs used by both simulation and UI diagnostics. */
public final class SocietyDiagnostics {
    private SocietyDiagnostics() {}

    public static SocietyAssessment assess(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        int pop = Math.max(1, settlement.population());
        // Prefer local granary/barn; faction treasury is a thin strategic reserve, not the village larder.
        double localFood = settlement.stockpile().get(ResourceType.FOOD);
        double treasuryFood = faction.stockpile().get(ResourceType.FOOD) / Math.max(1, faction.settlements().size());
        double foodDays = (localFood + treasuryFood * .25) / Math.max(1, pop * .2);
        double localGoods = settlement.stockpile().get(ResourceType.TEXTILES) + settlement.stockpile().get(ResourceType.TOOLS) * 1.5;
        double factionGoods = (faction.stockpile().get(ResourceType.TEXTILES) + faction.stockpile().get(ResourceType.TOOLS) * 1.5)
                / Math.max(1, faction.settlements().size());
        double goods = (localGoods + factionGoods * .35) / Math.max(1, pop * .03);
        double food = Mathx.clamp(foodDays / 4.0, 0, 1);
        double housing = Mathx.clamp((double) settlement.housing() / Math.max(1, settlement.population()), 0, 1);
        double safety = Mathx.clamp(.35 + faction.government().lawEnforcement() * .55 - settlement.unrest() * .25, 0, 1);
        double employment = Mathx.clamp(jobCapacity(faction, settlement) / Math.max(1, settlement.population() * .52), 0, 1);
        double marketGoods = Mathx.clamp(goods / 2.0, 0, 1);
        SettlementNeeds needs = new SettlementNeeds(food, housing, safety, employment, marketGoods);
        SocietyPressure pressure = primaryPressure(needs);
        double severity = pressure == SocietyPressure.BALANCED ? 0 : 1 - valueFor(needs, pressure);
        return new SocietyAssessment(needs, needs.satisfaction(), pressure, severity);
    }

    /** Workplace-slot–aware job capacity: completed structures create real employment seats. */
    public static double jobCapacity(Faction faction, Settlement settlement) {
        int farms = count(settlement, "farm:");
        int pastures = count(settlement, "pasture:");
        int workshops = count(settlement, "workshop:") + count(settlement, "bakery:") + count(settlement, "brewery:") + count(settlement, "mill:");
        int mines = count(settlement, "mine:");
        int lumber = count(settlement, "lumber_camp:");
        int fisheries = count(settlement, "fishery:");
        int markets = count(settlement, "market:") + count(settlement, "warehouse:");
        int civic = count(settlement, "temple:") + count(settlement, "school:") + count(settlement, "clinic:")
                + count(settlement, "courthouse:") + count(settlement, "barracks:") + count(settlement, "keep:");
        int docks = count(settlement, "dock:");
        int factories = count(settlement, "factory:");
        // Implied hinterland work when physical markers are not yet completed (headless sim).
        double impliedFields = Math.max(farms, Math.ceil(settlement.population() / 160.0));
        double impliedPastures = Math.max(pastures, Math.ceil(settlement.population() / 280.0));
        double slots = impliedFields * 14 + impliedPastures * 8 + workshops * 18 + mines * 22 + lumber * 16
                + fisheries * 14 + markets * 12 + civic * 10 + docks * 14 + factories * 28;
        double tools = (settlement.stockpile().get(ResourceType.TOOLS) + faction.stockpile().get(ResourceType.TOOLS) * .2) * .05;
        double machinery = faction.stockpile().get(ResourceType.MACHINERY) * .04;
        double infrastructure = settlement.infrastructure() * settlement.population() * .04;
        return slots + tools + machinery + infrastructure;
    }

    private static int count(Settlement settlement, String prefix) {
        return (int) settlement.completedConstruction().stream().filter(k -> k.startsWith(prefix)).count();
    }

    private static SocietyPressure primaryPressure(SettlementNeeds n) {
        double min = Math.min(n.food(), Math.min(n.housing(), Math.min(n.safety(), Math.min(n.employment(), n.goods()))));
        if (min >= .82) return SocietyPressure.BALANCED;
        if (n.food() == min) return SocietyPressure.FOOD;
        if (n.housing() == min) return SocietyPressure.HOUSING;
        if (n.safety() == min) return SocietyPressure.SAFETY;
        if (n.employment() == min) return SocietyPressure.EMPLOYMENT;
        return SocietyPressure.GOODS;
    }

    private static double valueFor(SettlementNeeds n, SocietyPressure pressure) {
        return switch (pressure) {
            case FOOD -> n.food();
            case HOUSING -> n.housing();
            case SAFETY -> n.safety();
            case EMPLOYMENT -> n.employment();
            case GOODS -> n.goods();
            case BALANCED -> 1;
        };
    }
}
