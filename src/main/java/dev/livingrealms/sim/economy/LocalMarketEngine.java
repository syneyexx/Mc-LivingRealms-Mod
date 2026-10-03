package dev.livingrealms.sim.economy;

import dev.livingrealms.sim.civilization.CivilizationCalendar;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.Mathx;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Settlement-local scarcity pricing. Faction {@link MarketEngine} remains for realm-level quotes;
 * this engine answers "why is food expensive in this village?".
 */
public final class LocalMarketEngine {
    private LocalMarketEngine() {}

    public static MarketQuote quote(Settlement settlement, ResourceType resource, long day) {
        int pop = Math.max(1, settlement.population());
        double dailyNeed = MarketEngine.localDailyNeed(pop, resource);
        double supply = settlement.stockpile().get(resource);
        double days = dailyNeed <= 0 ? 999 : supply / dailyNeed;
        double scarcity = Mathx.clamp(1.45 - Math.log1p(days) / Math.log(7), .18, 2.8);
        int weekday = Math.floorMod((int) Math.max(0, day), 7);
        double marketDay = (weekday == 0 || weekday == 3) ? 1.08 : (weekday == 6 ? .94 : 1.0);
        CivilizationCalendar.Season season = CivilizationCalendar.season(day);
        double seasonMul = resource == ResourceType.FOOD
                ? switch (season) {
                    case WINTER -> 1.22;
                    case SPRING -> 1.06;
                    case SUMMER -> .96;
                    case AUTUMN -> .88;
                }
                : 1.0;
        double price = MarketEngine.basePrice(resource) * scarcity * marketDay * seasonMul
                * (.86 + .28 * settlement.prosperity());
        return new MarketQuote(resource, Math.max(.01, price), days, scarcity);
    }

    public static MarketQuote quote(Faction faction, Settlement settlement, ResourceType resource, long day) {
        MarketQuote local = quote(settlement, resource, day);
        // Blend a little realm corruption into local sticker price.
        double corrupt = 1 + faction.government().corruption() * .15;
        return new MarketQuote(resource, local.unitPrice() * corrupt, local.supplyDays(), local.scarcity());
    }

    public static Map<ResourceType, MarketQuote> all(Settlement settlement, long day) {
        EnumMap<ResourceType, MarketQuote> out = new EnumMap<>(ResourceType.class);
        for (ResourceType r : ResourceType.values()) out.put(r, quote(settlement, r, day));
        return Collections.unmodifiableMap(out);
    }
}
