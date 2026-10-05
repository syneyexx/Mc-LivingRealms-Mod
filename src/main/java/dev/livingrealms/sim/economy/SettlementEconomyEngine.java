package dev.livingrealms.sim.economy;

import dev.livingrealms.sim.civilization.CivilizationCalendar;
import dev.livingrealms.sim.civilization.FaithEconomyHooks;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Comparator;

/**
 * Local settlement production, consumption and tithe.
 * <p>
 * Farms produce {@link ResourceType#GRAIN} only with completed production-eligible {@code farm:} keys
 * (plus a tiny subsistence floor that cannot grow stores). Mills convert GRAIN→FLOUR with loss;
 * bakeries FLOUR→BREAD; breweries GRAIN→ALE. No FOOD multiplication chain.
 */
public final class SettlementEconomyEngine {
    public void simulateDay(SimulationState state) {
        long day = state.clock().day();
        CivilizationCalendar.Season season = CivilizationCalendar.season(day);
        int weekday = Math.floorMod((int) day, 7);
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                settlement.refreshStorageCapacity();
                double weatherMul = weatherMultiplier(day, settlement.id(), season);
                AgrarianProfile.Mix agronomy = AgrarianProfile.of(settlement, season);
                double rotation = AgrarianProfile.rotationMultiplier(day);
                produceFarms(settlement, season, faction.technology(), weatherMul, agronomy, rotation, day);
                produceLivestock(settlement, season, faction.technology(), weatherMul, agronomy);
                produceHinterland(state, settlement, faction.technology());
                produceWorkshops(settlement, faction.technology());
                consumeLocalNeeds(state, faction, settlement, day);
                applyMarketDayPressure(settlement, weekday);
                FaithEconomyHooks.applyHolyDayAndTithe(state, faction, settlement, day);
                titheToFaction(faction, settlement);
                updateFoodSecurity(settlement);
            }
        }
    }

    private static void produceFarms(Settlement settlement, CivilizationCalendar.Season season, double technology,
                                     double weatherMul, AgrarianProfile.Mix cropMix, double rotation, long day) {
        int farms = settlement.countProductionPrefix("farm:");
        int irrigation = settlement.countProductionPrefix("irrigation:") + settlement.countProductionPrefix("aqueduct:");
        double seasonMul = switch (season) {
            case WINTER -> 0.0;
            case SPRING -> 0.62;
            case SUMMER -> 0.92;
            case AUTUMN -> 1.40;
        };
        // Subsistence only — below daily consumption, never writes completion keys.
        double subsistence = settlement.population() * 0.04;
        if (seasonMul <= 0) {
            if (farms == 0) {
                settlement.stockpile().add(ResourceType.GRAIN, subsistence * 0.25);
                settlement.markGrainBooked(day);
            }
            return;
        }
        if (farms == 0) {
            settlement.stockpile().add(ResourceType.GRAIN, subsistence * seasonMul * 0.35);
            settlement.markGrainBooked(day);
            return;
        }
        double tech = .80 + .40 * Math.min(1.5, technology);
        double irrig = 1.0 + Math.min(.40, irrigation * .14);
        double agronomy = 1.0 + Math.min(.28, technology * .14) + (settlement.countProductionPrefix("workshop:") > 0 ? .06 : 0);
        double grain = farms * 14.0 * seasonMul * tech * irrig * weatherMul * agronomy * cropMix.yieldMul() * rotation;
        grain += settlement.population() * .12 * seasonMul * tech * weatherMul * Math.min(1.2, farms / Math.max(1.0, settlement.population() / 160.0));
        settlement.stockpile().add(ResourceType.GRAIN, grain);
        settlement.markGrainBooked(day);
        if (season == CivilizationCalendar.Season.AUTUMN || cropMix.primaryCrop() == AgrarianProfile.Crop.FLAX) {
            settlement.stockpile().add(ResourceType.WOOL,
                    farms * 0.35 * tech * weatherMul * cropMix.textileMul());
        }
        if (weatherMul < .55) settlement.adjustUnrest(.0015);
    }

    /**
     * Physical harvest animation hook. Returns added GRAIN only when the sim has not already booked
     * yield for {@code day}; otherwise 0 so entity harvest cannot double-count.
     */
    public static double physicalHarvestIfUnbooked(Settlement settlement, long day) {
        if (settlement.grainBookedForDay(day)) return 0;
        int farms = settlement.countProductionPrefix("farm:");
        if (farms <= 0) return 0;
        double amount = farms * 2.0;
        settlement.stockpile().add(ResourceType.GRAIN, amount);
        settlement.markGrainBooked(day);
        return amount;
    }

    private static void produceLivestock(Settlement settlement, CivilizationCalendar.Season season, double technology,
                                         double weatherMul, AgrarianProfile.Mix cropMix) {
        int pastures = settlement.countProductionPrefix("pasture:");
        double tech = .82 + .32 * Math.min(1.5, technology);
        double seasonMul = switch (season) {
            case WINTER -> 1.90;
            case SPRING -> 0.85;
            case SUMMER -> 1.05;
            case AUTUMN -> 1.40;
        };
        if (pastures == 0) {
            // Tiny subsistence herd — below consumption.
            settlement.stockpile().add(ResourceType.MEAT, settlement.population() * .008 * seasonMul);
            return;
        }
        double herdMul = switch (cropMix.primaryStock()) {
            case CATTLE -> 1.10;
            case SHEEP -> 0.92;
            case PIGS -> 1.15;
            case CHICKENS -> 0.85;
            case HORSES -> 0.80;
            case OXEN -> 0.95;
        };
        double meat = pastures * 4.5 * seasonMul * tech * weatherMul * herdMul
                + settlement.population() * .02 * seasonMul * tech * weatherMul * Math.min(1.3, pastures / Math.max(1.0, settlement.population() / 280.0));
        double wool = pastures * 1.2 * tech * cropMix.textileMul()
                * (season == CivilizationCalendar.Season.SPRING ? 1.25 : 1.0);
        settlement.stockpile().add(ResourceType.MEAT, meat);
        settlement.stockpile().add(ResourceType.WOOL, wool);
    }

    private static void produceHinterland(SimulationState state, Settlement settlement, double technology) {
        int lumber = settlement.countProductionPrefix("lumber_camp:");
        int mine = settlement.countProductionPrefix("mine:") + settlement.countProductionPrefix("quarry:");
        double woodWanted = lumber > 0
                ? lumber * 6.0 * (.7 + .3 * technology) + settlement.population() * .002
                : settlement.population() * .0015; // subsistence forage only
        double huntWanted = lumber > 0
                ? settlement.population() * .012 * (.55 + .45 * Math.min(1.0, lumber / 2.0))
                : settlement.population() * .006; // under consumption
        double stoneWanted = mine > 0
                ? mine * 5.0 * (.7 + .3 * technology)
                : 0;
        double woodMul = 1.0, huntMul = 1.0;
        if (lumber > 0) {
            var nearest = state.regions().stream()
                    .min(Comparator.comparingDouble(r -> r.center().distanceTo(settlement.position())))
                    .filter(r -> r.center().distanceTo(settlement.position()) <= 1_800)
                    .orElse(null);
            if (nearest != null) {
                long competitors = state.factions().stream().flatMap(f -> f.settlements().stream())
                        .filter(s -> s.countProductionPrefix("lumber_camp:") > 0)
                        .filter(s -> nearest.center().distanceTo(s.position()) <= 1_800).count();
                double share = 1.0 / Math.max(1, competitors);
                double plantTaken = nearest.consumePlants((woodWanted * .25 + lumber * .15) * share);
                double plantNeed = Math.max(0.01, (woodWanted * .25 + lumber * .15) * share);
                woodMul = Mathx.clamp(.75 + .25 * (plantTaken / plantNeed), .75, 1.08);
                double gameTaken = 0, gameWanted = Math.max(0.02, settlement.population() * .00008 * share);
                for (var group : nearest.populations()) {
                    if (group.extinct() || group.population() < 12) continue;
                    double take = Math.min(group.population() * .0015, gameWanted - gameTaken);
                    if (take <= 0) break;
                    group.addPopulation(-take);
                    gameTaken += take;
                }
                huntMul = Mathx.clamp(.85 + .15 * (gameTaken / Math.max(0.02, gameWanted)), .85, 1.08);
                if (woodMul < .82) settlement.adjustUnrest(.0002);
            }
        }
        settlement.stockpile().add(ResourceType.WOOD, woodWanted * woodMul);
        if (stoneWanted > 0) settlement.stockpile().add(ResourceType.STONE, stoneWanted);
        settlement.stockpile().add(ResourceType.MEAT, huntWanted * huntMul * 0.35);
    }

    private static double weatherMultiplier(long day, long settlementId, CivilizationCalendar.Season season) {
        long h = day * 0x9E3779B97F4A7C15L ^ settlementId * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33;
        double roll = (h >>> 11) * 0x1.0p-53;
        if (roll > 0.94) return season == CivilizationCalendar.Season.WINTER ? 0.35 : 0.42;
        if (roll > 0.88) return 0.58;
        if (roll > 0.78) return 0.78;
        return 1.0;
    }

    private static void produceWorkshops(Settlement settlement, double technology) {
        int workshops = settlement.countProductionPrefix("workshop:");
        int mills = settlement.countProductionPrefix("mill:")
                + settlement.countProductionPrefix("windmill:")
                + settlement.countProductionPrefix("watermill:");
        int bakeries = settlement.countProductionPrefix("bakery:");
        int breweries = settlement.countProductionPrefix("brewery:");
        int weavers = settlement.countProductionPrefix("weaver:") + settlement.countProductionPrefix("textile:");

        // TOOLS: IRON + WOOD → TOOLS
        if (workshops > 0) {
            double ironIn = settlement.stockpile().take(ResourceType.IRON, Math.min(workshops * 1.2, settlement.stockpile().get(ResourceType.IRON)));
            double woodIn = settlement.stockpile().take(ResourceType.WOOD, Math.min(workshops * 2.0, settlement.stockpile().get(ResourceType.WOOD)));
            double craft = Math.min(ironIn, woodIn / 1.5) * (.85 + .15 * technology);
            if (craft > 0) settlement.stockpile().add(ResourceType.TOOLS, craft);
            // MACHINERY: IRON + TOOLS
            double toolsForMachine = settlement.stockpile().take(ResourceType.TOOLS, Math.min(workshops * 0.4, settlement.stockpile().get(ResourceType.TOOLS)));
            double ironForMachine = settlement.stockpile().take(ResourceType.IRON, Math.min(toolsForMachine * 1.2, settlement.stockpile().get(ResourceType.IRON)));
            double machines = Math.min(toolsForMachine, ironForMachine / 1.2) * 0.9;
            if (machines > 0) settlement.stockpile().add(ResourceType.MACHINERY, machines);
            // AMMUNITION: IRON + COAL
            double coal = settlement.stockpile().take(ResourceType.COAL, Math.min(workshops * 0.5, settlement.stockpile().get(ResourceType.COAL)));
            double ironAmmo = settlement.stockpile().take(ResourceType.IRON, Math.min(coal, settlement.stockpile().get(ResourceType.IRON)));
            double ammo = Math.min(coal, ironAmmo) * 0.95;
            if (ammo > 0) settlement.stockpile().add(ResourceType.AMMUNITION, ammo);
        }

        // Mill: GRAIN → FLOUR with milling loss (output ≤ input).
        if (mills > 0) {
            double grain = settlement.stockpile().take(ResourceType.GRAIN, Math.min(settlement.stockpile().get(ResourceType.GRAIN), mills * 8.0));
            if (grain > 0) settlement.stockpile().add(ResourceType.FLOUR, grain * (0.88 + .04 * Math.min(1.0, technology)));
        }
        // Bakery: FLOUR → BREAD (food-value conserved or reduced).
        if (bakeries > 0) {
            double flour = settlement.stockpile().take(ResourceType.FLOUR, Math.min(settlement.stockpile().get(ResourceType.FLOUR), bakeries * 5.0));
            if (flour > 0) settlement.stockpile().add(ResourceType.BREAD, flour * (0.92 + .03 * Math.min(1.0, technology)));
        }
        // Brewery: GRAIN → ALE (no FOOD duplication).
        if (breweries > 0) {
            double malt = settlement.stockpile().take(ResourceType.GRAIN, Math.min(settlement.stockpile().get(ResourceType.GRAIN), breweries * 3.0));
            if (malt > 0) {
                settlement.stockpile().add(ResourceType.ALE, malt * 0.75);
                settlement.adjustProsperity(.0004 * breweries);
            }
        }
        // Weaving: WOOL → TEXTILES with loss.
        if (weavers > 0 || (workshops > 0 && settlement.stockpile().get(ResourceType.WOOL) > 2)) {
            int looms = Math.max(weavers, workshops > 0 ? 1 : 0);
            double wool = settlement.stockpile().take(ResourceType.WOOL, Math.min(settlement.stockpile().get(ResourceType.WOOL), looms * 2.5));
            if (wool > 0) settlement.stockpile().add(ResourceType.TEXTILES, wool * 0.85);
        }
    }

    private static void consumeLocalNeeds(SimulationState state, Faction faction, Settlement settlement, long day) {
        double foodNeed = settlement.population() * .20 * FaithEconomyHooks.foodNeedMultiplier(state, faction, settlement, day);
        double fed = takeEdible(settlement, foodNeed);
        double ratio = Mathx.clamp(Mathx.safeDiv(fed, Math.max(1, foodNeed)), 0, 1);
        settlement.setFoodSecurity(settlement.foodSecurity() * .7 + ratio * .3);
        if (ratio < .55) {
            settlement.adjustUnrest(.004 * (1 - ratio));
            // Population growth stopper is handled by CivilizationEngine; mark via foodSecurity.
        }
        if (ratio > .85) settlement.adjustProsperity(.001);
        settlement.enforceStorageCaps();
        double textilesNeed = settlement.population() * .0015;
        settlement.stockpile().take(ResourceType.TEXTILES, textilesNeed);
    }

    /** Prefer BREAD, MEAT, ALE, then GRAIN, then FLOUR, then legacy FOOD. */
    static double takeEdible(Settlement settlement, double need) {
        double remaining = need;
        remaining -= settlement.stockpile().take(ResourceType.BREAD, remaining);
        if (remaining <= 1e-9) return need;
        remaining -= settlement.stockpile().take(ResourceType.MEAT, remaining / ResourceType.foodValue(ResourceType.MEAT)) * ResourceType.foodValue(ResourceType.MEAT);
        if (remaining <= 1e-9) return need;
        remaining -= settlement.stockpile().take(ResourceType.ALE, remaining / ResourceType.foodValue(ResourceType.ALE)) * ResourceType.foodValue(ResourceType.ALE);
        if (remaining <= 1e-9) return need;
        remaining -= settlement.stockpile().take(ResourceType.GRAIN, remaining / ResourceType.foodValue(ResourceType.GRAIN)) * ResourceType.foodValue(ResourceType.GRAIN);
        if (remaining <= 1e-9) return need;
        remaining -= settlement.stockpile().take(ResourceType.FLOUR, remaining / ResourceType.foodValue(ResourceType.FLOUR)) * ResourceType.foodValue(ResourceType.FLOUR);
        if (remaining <= 1e-9) return need;
        remaining -= settlement.stockpile().take(ResourceType.FOOD, remaining);
        return need - remaining;
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
            double keepDays = type == ResourceType.GRAIN || type == ResourceType.BREAD || type == ResourceType.MEAT
                    || type == ResourceType.ALE || type == ResourceType.FOOD || type == ResourceType.FLOUR ? 50 : 20;
            double reserve = settlement.population() * dailyNeed(type) * keepDays;
            double surplus = Math.max(0, local - reserve);
            double take = surplus * Mathx.clamp(.08 + tax * .55, .05, .45);
            if (take <= 0) continue;
            double moved = settlement.stockpile().take(type, take);
            faction.stockpile().add(type, moved);
        }
    }

    private static void updateFoodSecurity(Settlement settlement) {
        double days = MarketEngine.localDaysOfSupply(settlement, ResourceType.BREAD)
                + MarketEngine.localDaysOfSupply(settlement, ResourceType.MEAT)
                + MarketEngine.localDaysOfSupply(settlement, ResourceType.GRAIN);
        // Rough combined days of supply across edible goods.
        double target = Mathx.clamp(days / 21.0, 0, 1);
        settlement.setFoodSecurity(settlement.foodSecurity() * .85 + target * .15);
        if (settlement.foodSecurity() < 0.55) settlement.adjustUnrest(.002);
    }

    private static double dailyNeed(ResourceType type) {
        return switch (type) {
            case FOOD, BREAD, GRAIN, FLOUR -> .20;
            case MEAT -> .05;
            case ALE -> .02;
            case TEXTILES, WOOL -> .0015;
            case TOOLS -> .0008;
            case WOOD -> .002;
            case STONE -> .001;
            default -> .0003;
        };
    }
}
