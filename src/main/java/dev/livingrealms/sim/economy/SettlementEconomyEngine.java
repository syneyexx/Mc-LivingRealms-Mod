package dev.livingrealms.sim.economy;

import dev.livingrealms.sim.civilization.CivilizationCalendar;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;

/**
 * Local settlement production, consumption and tithe. Replaces free {@code pop * constant}
 * faction magic with farm/pasture/structure-backed stockpiles.
 * <p>
 * Headless/sim worlds often have no Minecraft-completed construction markers yet; agrarian
 * capacity therefore uses {@code max(completed farms, implied fields from population)} so the
 * canonical economy still runs. Completing physical farms/pastures/mills raises yield above the
 * subsistence floor. Faction treasury stockpile receives tax transfers only.
 */
public final class SettlementEconomyEngine {
    public void simulateDay(SimulationState state) {
        long day = state.clock().day();
        CivilizationCalendar.Season season = CivilizationCalendar.season(day);
        int weekday = Math.floorMod((int) day, 7); // 0..6 market cycle
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                settlement.refreshStorageCapacity();
                double weatherMul = weatherMultiplier(day, settlement.id(), season);
                produceFarms(settlement, season, faction.technology(), weatherMul);
                produceLivestock(settlement, season, faction.technology(), weatherMul);
                produceHinterland(settlement, faction.technology());
                produceWorkshops(settlement, faction.technology());
                consumeLocalNeeds(settlement);
                applyMarketDayPressure(settlement, weekday);
                titheToFaction(faction, settlement);
                updateFoodSecurity(settlement);
            }
        }
    }

    private static void produceFarms(Settlement settlement, CivilizationCalendar.Season season, double technology, double weatherMul) {
        int completedFarms = count(settlement, "farm:");
        int impliedFarms = Math.max(1, (int) Math.ceil(settlement.population() / 160.0));
        int farms = Math.max(completedFarms, impliedFarms);
        int irrigation = count(settlement, "irrigation:") + count(settlement, "aqueduct:");
        // Winter has no harvest; autumn is peak; spring/summer grow stores more slowly.
        double seasonMul = switch (season) {
            case WINTER -> 0.0;
            case SPRING -> 0.62;
            case SUMMER -> 0.92;
            case AUTUMN -> 1.40;
        };
        if (seasonMul <= 0) return;
        double tech = .80 + .40 * Math.min(1.5, technology);
        double irrig = 1.0 + Math.min(.40, irrigation * .14);
        // Completed fields above the subsistence floor raise yield; missing physical farms stay near floor.
        double intensification = .72 + .28 * Mathx.clamp(completedFarms / (double) Math.max(1, impliedFarms), 0, 1.6);
        // Three-field / plow / collar innovation proxies.
        double agronomy = 1.0 + Math.min(.28, technology * .14) + (count(settlement, "workshop:") > 0 ? .06 : 0);
        // Scale with population so spring is near break-even and autumn stocks winter.
        double food = settlement.population() * .48 * seasonMul * tech * irrig * weatherMul * intensification * agronomy;
        // Mill/bakery chain bonuses applied later; granary-first storage stays local until tithe.
        settlement.stockpile().add(ResourceType.FOOD, food);
        if (season == CivilizationCalendar.Season.AUTUMN) {
            settlement.stockpile().add(ResourceType.TEXTILES, settlement.population() * .0012 * tech * weatherMul * intensification); // flax proxy
        }
        if (weatherMul < .55) settlement.adjustUnrest(.0015);
    }

    private static void produceLivestock(Settlement settlement, CivilizationCalendar.Season season, double technology, double weatherMul) {
        int completedPastures = count(settlement, "pasture:");
        int impliedPastures = Math.max(1, (int) Math.ceil(settlement.population() / 280.0));
        int pastures = Math.max(completedPastures, impliedPastures);
        double tech = .82 + .32 * Math.min(1.5, technology);
        double seasonMul = switch (season) {
            case WINTER -> 1.90; // slaughter/dairy season keeps villages alive when fields sleep
            case SPRING -> 0.85;
            case SUMMER -> 1.05;
            case AUTUMN -> 1.40;
        };
        double intensity = .70 + .30 * Mathx.clamp(completedPastures / (double) Math.max(1, impliedPastures), 0, 1.5);
        double meat = settlement.population() * .055 * seasonMul * tech * weatherMul * intensity
                * Math.min(1.4, pastures / (double) Math.max(1, impliedPastures));
        double wool = settlement.population() * .0020 * tech * intensity
                * (season == CivilizationCalendar.Season.SPRING ? 1.25 : 1.0);
        settlement.stockpile().add(ResourceType.FOOD, meat);
        settlement.stockpile().add(ResourceType.TEXTILES, wool);
    }

    /** Hunting/forestry hinterland supplements wood (and a little food) without free faction minting. */
    private static void produceHinterland(Settlement settlement, double technology) {
        int lumber = count(settlement, "lumber_camp:");
        double forest = .55 + .45 * Math.min(1.0, lumber / 2.0) + .15 * Math.min(1.0, technology);
        settlement.stockpile().add(ResourceType.WOOD, settlement.population() * .006 * forest);
        settlement.stockpile().add(ResourceType.STONE, settlement.population() * .0022 * (.7 + .3 * technology));
        // Hunting/foraging year-round; critical winter buffer when fields are fallow.
        settlement.stockpile().add(ResourceType.FOOD, settlement.population() * .028 * forest);
    }

    /** Deterministic weather stress: drought/flood/frost/hail reduce yields on bad years. */
    private static double weatherMultiplier(long day, long settlementId, CivilizationCalendar.Season season) {
        long h = day * 0x9E3779B97F4A7C15L ^ settlementId * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33;
        double roll = (h >>> 11) * 0x1.0p-53; // [0,1)
        if (roll > 0.94) return season == CivilizationCalendar.Season.WINTER ? 0.35 : 0.42; // hard frost / hail
        if (roll > 0.88) return 0.58; // drought / flood
        if (roll > 0.78) return 0.78; // mild blight
        return 1.0;
    }

    private static void produceWorkshops(Settlement settlement, double technology) {
        int workshops = count(settlement, "workshop:");
        int mills = count(settlement, "mill:") + count(settlement, "windmill:") + count(settlement, "watermill:");
        int bakeries = count(settlement, "bakery:");
        int breweries = count(settlement, "brewery:");
        // Implied mill for villages+ once population supports grain processing.
        if (mills == 0 && settlement.population() >= 100) mills = 1;
        if (workshops > 0) {
            double craft = Math.max(4, settlement.population() * .01) * workshops * (.7 + .4 * technology);
            settlement.stockpile().add(ResourceType.TOOLS, craft * .08);
            settlement.stockpile().add(ResourceType.TEXTILES, craft * .05);
        }
        // Mill/bakery/brewery deepen the farm→food chain using existing FOOD proxies (no ResourceType enum break).
        if (mills > 0) {
            double grain = settlement.stockpile().take(ResourceType.FOOD, Math.min(settlement.stockpile().get(ResourceType.FOOD) * .08, mills * 6.0));
            if (grain > 0) settlement.stockpile().add(ResourceType.FOOD, grain * (1.12 + .08 * technology)); // milling yield bonus
        }
        if (bakeries > 0) {
            double flour = settlement.stockpile().take(ResourceType.FOOD, Math.min(settlement.stockpile().get(ResourceType.FOOD) * .05, bakeries * 4.0));
            if (flour > 0) settlement.stockpile().add(ResourceType.FOOD, flour * (1.08 + .05 * technology));
        }
        if (breweries > 0) {
            double malt = settlement.stockpile().take(ResourceType.FOOD, Math.min(settlement.stockpile().get(ResourceType.FOOD) * .03, breweries * 3.0));
            if (malt > 0) {
                settlement.stockpile().add(ResourceType.FOOD, malt * .85); // beer as preserved food proxy
                settlement.adjustProsperity(.0004 * breweries);
            }
        }
    }

    private static void consumeLocalNeeds(Settlement settlement) {
        double foodNeed = settlement.population() * .20;
        double fed = settlement.stockpile().take(ResourceType.FOOD, foodNeed);
        double ratio = Mathx.clamp(Mathx.safeDiv(fed, Math.max(1, foodNeed)), 0, 1);
        settlement.setFoodSecurity(settlement.foodSecurity() * .7 + ratio * .3);
        if (ratio < .55) settlement.adjustUnrest(.004 * (1 - ratio));
        if (ratio > .85) settlement.adjustProsperity(.001);
        settlement.enforceStorageCaps();
        double textilesNeed = settlement.population() * .0015;
        settlement.stockpile().take(ResourceType.TEXTILES, textilesNeed);
    }

    private static void applyMarketDayPressure(Settlement settlement, int weekday) {
        if (weekday == 0 || weekday == 3) {
            settlement.adjustProsperity(.0008);
            settlement.setEmployment(Math.min(1, settlement.employment() + .002));
        } else if (weekday == 6) {
            settlement.setEmployment(Math.max(0, settlement.employment() - .001));
        }
    }

    private static void titheToFaction(Faction faction, Settlement settlement) {
        double tax = faction.government().taxRate();
        for (ResourceType type : ResourceType.values()) {
            double local = settlement.stockpile().get(type);
            // Keep a full season of food locally so winter/tithe cannot empty every granary.
            double keepDays = type == ResourceType.FOOD ? 50 : 20;
            double reserve = settlement.population() * dailyNeed(type) * keepDays;
            double surplus = Math.max(0, local - reserve);
            double take = surplus * Mathx.clamp(.08 + tax * .55, .05, .45);
            if (take <= 0) continue;
            double moved = settlement.stockpile().take(type, take);
            faction.stockpile().add(type, moved);
        }
    }

    private static void updateFoodSecurity(Settlement settlement) {
        double days = MarketEngine.localDaysOfSupply(settlement, ResourceType.FOOD);
        double target = Mathx.clamp(days / 14.0, 0, 1);
        settlement.setFoodSecurity(settlement.foodSecurity() * .85 + target * .15);
    }

    private static double dailyNeed(ResourceType type) {
        return switch (type) {
            case FOOD -> .20;
            case TEXTILES -> .0015;
            case TOOLS -> .0008;
            case WOOD -> .002;
            case STONE -> .001;
            default -> .0003;
        };
    }

    private static int count(Settlement settlement, String prefix) {
        return (int) settlement.completedConstruction().stream().filter(k -> k.startsWith(prefix)).count();
    }
}
