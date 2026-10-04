package dev.livingrealms.sim.faction;

import java.util.Locale;
import java.util.Objects;

/**
 * Settlement economic/geographic specialization derived from stores, geography and infrastructure.
 * Labels the existing settlement for planners and UI — not a parallel economy.
 */
public enum SettlementSpecialization {
    AGRICULTURAL,
    MINING,
    TIMBER,
    FISHING,
    TRADE,
    RELIGIOUS,
    ACADEMIC,
    MILITARY,
    INDUSTRIAL,
    NAVAL,
    ADMINISTRATIVE,
    MIXED;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SettlementSpecialization derive(
            Settlement settlement,
            boolean capital,
            double technology,
            boolean hasTemple,
            boolean hasSchool,
            boolean hasBarracks,
            boolean hasFactory,
            boolean hasDock
    ) {
        Objects.requireNonNull(settlement, "settlement");
        SettlementGeographyProfile geo = settlement.geography();
        Stockpile stock = settlement.stockpile();
        double food = stock.get(ResourceType.FOOD);
        double wood = stock.get(ResourceType.WOOD);
        double stone = stock.get(ResourceType.STONE);
        double iron = stock.get(ResourceType.IRON);
        double tools = stock.get(ResourceType.TOOLS);
        double machinery = stock.get(ResourceType.MACHINERY);
        double textiles = stock.get(ResourceType.TEXTILES);
        double tradeGoods = tools + machinery + textiles;

        if (capital && settlement.tier().ordinal() >= Settlement.Tier.CITY.ordinal()) {
            return ADMINISTRATIVE;
        }
        if (hasDock && (geo.shipSuitable() || geo.coastal())) {
            return geo.shipSuitable() && settlement.tier().ordinal() >= Settlement.Tier.TOWN.ordinal() ? NAVAL : FISHING;
        }
        if (hasFactory && technology >= 0.45) {
            return INDUSTRIAL;
        }
        if (hasBarracks && settlement.publicOrder() >= 0.55 && settlement.tier().ordinal() >= Settlement.Tier.TOWN.ordinal()) {
            return MILITARY;
        }
        if (hasSchool && settlement.prosperity() >= 0.55) {
            return ACADEMIC;
        }
        if (hasTemple && settlement.prosperity() >= 0.5) {
            return RELIGIOUS;
        }
        if (tradeGoods > food * 0.45 && settlement.tier().ordinal() >= Settlement.Tier.VILLAGE.ordinal()) {
            return TRADE;
        }
        if (iron + stone > food * 0.7 && geo.miningPotential() >= 0.45) {
            return MINING;
        }
        if (wood > food * 0.6 && geo.forest() >= 0.4) {
            return TIMBER;
        }
        if (geo.coastal() || geo.riverSuitable()) {
            if (food >= wood && food >= iron) return FISHING;
        }
        if (food >= Math.max(wood, Math.max(stone, iron)) || geo.fertility() >= 0.55) {
            return AGRICULTURAL;
        }
        return MIXED;
    }
}
