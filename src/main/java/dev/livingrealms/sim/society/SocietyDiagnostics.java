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
        int totalPop = Math.max(1, faction.population());
        double foodDays = faction.stockpile().get(ResourceType.FOOD) / Math.max(1, totalPop * .2);
        double goods = (faction.stockpile().get(ResourceType.TEXTILES) + faction.stockpile().get(ResourceType.TOOLS) * 1.5) / Math.max(1, totalPop * .03);
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

    public static double jobCapacity(Faction faction, Settlement settlement) {
        double base = settlement.population() * .38;
        double industry = (faction.stockpile().get(ResourceType.MACHINERY) + faction.stockpile().get(ResourceType.TOOLS)) * .08;
        double infrastructure = settlement.infrastructure() * settlement.population() * .08;
        return base + industry + infrastructure;
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
