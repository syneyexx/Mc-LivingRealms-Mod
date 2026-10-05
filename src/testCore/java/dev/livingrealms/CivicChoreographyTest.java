package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilization.FaithCatalog;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.presentation.CivicChoreographyDirector;
import dev.livingrealms.sim.presentation.CivicChoreographyDirector.CooldownBook;
import dev.livingrealms.sim.presentation.CivicChoreographyDirector.LoadedCitizen;
import dev.livingrealms.sim.presentation.CivicChoreographyDirector.Order;
import dev.livingrealms.sim.presentation.CivicChoreographyPlanner;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentation;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentationPlan;
import dev.livingrealms.sim.presentation.SeasonalFarmPresentationPlan.CropVisual;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.ArrayList;
import java.util.List;

/** Wave 9–10: planner→runtime choreography decisions + seasonal farm visuals (headless). */
public final class CivicChoreographyTest {
    private CivicChoreographyTest() {}

    public static void main(String[] args) {
        plannerEmitsMarketOrFamineWithoutMutatingStockpile();
        holyDayUsesFaithCatalogNotParallelCadence();
        directorRespectsBudgetsAndCooldowns();
        directorMapsKindsToSitesAndPrefersRoles();
        seasonalFarmPlanIsReversibleCueOnly();
        System.out.println("PASS CivicChoreographyTest");
    }

    private static void plannerEmitsMarketOrFamineWithoutMutatingStockpile() {
        SimulationState state = new SimulationState(0xC10C1CL);
        Faction faction = new Faction(1, "Cue Realm", "Cue");
        Settlement town = new Settlement(2, "Marketville", new SimPosition(100, 100), 800, 900);
        town.addPopulation(200);
        town.addHousing(200);
        town.setFoodSecurity(0.2);
        faction.addSettlement(town);
        state.addFaction(faction);

        while (state.clock().day() % 7 != 0) {
            state.clock().advance(dev.livingrealms.sim.world.SimClock.TICKS_PER_DAY);
        }

        var events = CivicChoreographyPlanner.planSettlement(state, faction, town);
        check(!events.isEmpty(), "expected presentation events");
        check(events.stream().anyMatch(e -> e.kind() == CivicChoreographyPlanner.EventKind.MARKET_DAY
                        || e.kind() == CivicChoreographyPlanner.EventKind.FAMINE_QUEUE),
                "market or famine cue present");

        double foodBefore = town.stockpile().get(dev.livingrealms.sim.faction.ResourceType.GRAIN);
        var farm = SeasonalFarmPresentation.forSettlement(state, town);
        check(farm.look() != null, "farm look");
        check(town.stockpile().get(dev.livingrealms.sim.faction.ResourceType.GRAIN) == foodBefore,
                "presentation must not mutate stockpile");
    }

    private static void holyDayUsesFaithCatalogNotParallelCadence() {
        SimulationState state = new SimulationState(0x10C1DA11L);
        Faction faction = new Faction(3, "Lantern Realm", "Lantern");
        Settlement town = new Settlement(4, "Templeton", new SimPosition(200, 200), 1200, 1400);
        town.addPopulation(400);
        town.addHousing(400);
        faction.addSettlement(town);
        state.addFaction(faction);
        var civ = state.ensureFactionCivilization(faction.id());
        FaithCatalog.FaithProfile faith = FaithCatalog.of(civ.faithName());
        long holyDay = -1;
        for (int doy = 0; doy < 360; doy++) {
            if (faith.isHolyDay(doy)) {
                holyDay = doy;
                break;
            }
        }
        check(holyDay >= 0, "faith must declare at least one holy day");
        while (Math.floorMod(state.clock().day(), 360) != holyDay) {
            state.clock().advance(dev.livingrealms.sim.world.SimClock.TICKS_PER_DAY);
            if (state.clock().day() > 800) break;
        }
        check(CivicChoreographyPlanner.isFaithHolyDay(state, faction.id(), state.clock().day()),
                "planner holy-day helper must reuse faith calendar");
        var events = CivicChoreographyPlanner.planSettlement(state, faction, town);
        check(events.stream().anyMatch(e -> e.kind() == CivicChoreographyPlanner.EventKind.HOLY_DAY),
                "holy-day cue must appear on faith calendar day, not day%14");

        // Non-holy day must not emit HOLY_DAY solely from settlement-id cadence.
        SimulationState other = new SimulationState(0xA011011AL);
        Faction f2 = new Faction(5, "Plain Realm", "Plain");
        Settlement s2 = new Settlement(6, "NoTemple", new SimPosition(0, 0), 1200, 1400);
        s2.addPopulation(400);
        s2.addHousing(400);
        f2.addSettlement(s2);
        other.addFaction(f2);
        other.ensureFactionCivilization(f2.id());
        FaithCatalog.FaithProfile faith2 = FaithCatalog.of(other.ensureFactionCivilization(f2.id()).faithName());
        long nonHoly = -1;
        for (long d = 0; d < 360; d++) {
            if (!faith2.isHolyDay(d) && !faith2.isHolyDay(d + 1) && d % 7 != 0) {
                nonHoly = d;
                break;
            }
        }
        check(nonHoly >= 0, "need a non-holy day");
        while (other.clock().day() != nonHoly) {
            other.clock().advance(dev.livingrealms.sim.world.SimClock.TICKS_PER_DAY);
            if (other.clock().day() > 800) break;
        }
        var quiet = CivicChoreographyPlanner.planSettlement(other, f2, s2);
        check(quiet.stream().noneMatch(e -> e.kind() == CivicChoreographyPlanner.EventKind.HOLY_DAY),
                "HOLY_DAY must not fire off faith calendar");
    }

    private static void directorRespectsBudgetsAndCooldowns() {
        SimulationState state = new SimulationState(0xB0D6E700L);
        Faction faction = new Faction(7, "Budget Realm", "Budget");
        Settlement town = new Settlement(8, "Budgeton", new SimPosition(50, 50), 900, 1000);
        town.addPopulation(300);
        town.addHousing(300);
        town.setFoodSecurity(0.15);
        town.setActiveConstructionKey("house:0");
        faction.addSettlement(town);
        state.addFaction(faction);
        while (state.clock().day() % 7 != 0) {
            state.clock().advance(dev.livingrealms.sim.world.SimClock.TICKS_PER_DAY);
        }

        List<LoadedCitizen> citizens = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            CitizenRole role = switch (i % 5) {
                case 0 -> CitizenRole.TRADER;
                case 1 -> CitizenRole.FARMER;
                case 2 -> CitizenRole.BUILDER;
                case 3 -> CitizenRole.GUARD;
                default -> CitizenRole.OFFICIAL;
            };
            citizens.add(new LoadedCitizen(1000L + i, i, role));
        }

        CooldownBook book = new CooldownBook();
        List<Order> first = CivicChoreographyDirector.planOrders(state, faction, town, citizens, 1000L, book);
        check(!first.isEmpty(), "director should emit orders when cues exist");
        check(first.size() <= CivicChoreographyDirector.MAX_PARTICIPANTS_PER_SETTLEMENT,
                "participant settlement budget");
        long distinctEvents = first.stream().map(Order::kind).distinct().count();
        check(distinctEvents <= CivicChoreographyDirector.MAX_ACTIVE_EVENTS_PER_SETTLEMENT,
                "active event budget");
        for (CivicChoreographyPlanner.EventKind kind : first.stream().map(Order::kind).distinct().toList()) {
            long count = first.stream().filter(o -> o.kind() == kind).count();
            check(count <= CivicChoreographyDirector.MAX_PARTICIPANTS_PER_EVENT, "per-event participant budget");
        }

        List<Order> immediate = CivicChoreographyDirector.planOrders(state, faction, town, citizens, 1001L, book);
        check(immediate.isEmpty(), "cooldown must suppress immediate redispatches");

        List<Order> afterCooldown = CivicChoreographyDirector.planOrders(
                state, faction, town, citizens, 1000L + CivicChoreographyDirector.DISPATCH_COOLDOWN_TICKS, book);
        check(!afterCooldown.isEmpty(), "orders resume after cooldown");
    }

    private static void directorMapsKindsToSitesAndPrefersRoles() {
        check(CivicChoreographyDirector.siteFor(CivicChoreographyPlanner.EventKind.MARKET_DAY) == StructureRole.MARKET,
                "market site");
        check(CivicChoreographyDirector.siteFor(CivicChoreographyPlanner.EventKind.HOLY_DAY) == StructureRole.TEMPLE,
                "temple site");
        check(CivicChoreographyDirector.siteFor(CivicChoreographyPlanner.EventKind.FAMINE_QUEUE) == StructureRole.WAREHOUSE,
                "granary/warehouse site");
        check(CivicChoreographyDirector.siteFor(CivicChoreographyPlanner.EventKind.EPIDEMIC_CARE) == StructureRole.CLINIC,
                "clinic site");
        check(CivicChoreographyDirector.siteFor(CivicChoreographyPlanner.EventKind.JUSTICE_PROCESSION) == StructureRole.COURTHOUSE,
                "court site");
        check(CivicChoreographyDirector.siteFor(CivicChoreographyPlanner.EventKind.ARMY_MUSTER) == StructureRole.GATE,
                "gate site");
        check(CivicChoreographyDirector.siteFor(CivicChoreographyPlanner.EventKind.SUCCESSION_COURT) == StructureRole.KEEP,
                "keep site");
        check(CivicChoreographyDirector.preferredRoles(CivicChoreographyPlanner.EventKind.HOLY_DAY)
                        .contains(CitizenRole.PRIEST),
                "priests preferred for holy day");
        check(CivicChoreographyDirector.preferredRoles(CivicChoreographyPlanner.EventKind.ARMY_MUSTER)
                        .contains(CitizenRole.GUARD),
                "guards preferred for muster");
    }

    private static void seasonalFarmPlanIsReversibleCueOnly() {
        SimulationState state = new SimulationState(0xFA2A100CL);
        Faction faction = new Faction(9, "Farm Realm", "Farm");
        Settlement town = new Settlement(10, "Fields", new SimPosition(10, 10), 500, 600);
        town.addPopulation(100);
        town.addHousing(100);
        town.setFoodSecurity(0.9);
        faction.addSettlement(town);
        state.addFaction(faction);

        // Advance into summer (season 1) for possible LUSH.
        while (Math.floorMod(state.clock().day(), 360) / 90 != 1) {
            state.clock().advance(dev.livingrealms.sim.world.SimClock.TICKS_PER_DAY);
            if (state.clock().day() > 500) break;
        }
        var look = SeasonalFarmPresentation.forSettlement(state, town);
        var plan = SeasonalFarmPresentationPlan.fromLook(look);
        check(plan.look() == look.look(), "plan preserves look");
        check(plan.yieldHint() == look.yieldHint(), "yield hint is presentation-only passthrough");
        check(plan.crop() != null, "crop visual");

        double grain = town.stockpile().get(dev.livingrealms.sim.faction.ResourceType.GRAIN);
        // Mapping every look must be total and non-throwing.
        for (SeasonalFarmPresentation.CropLook cropLook : SeasonalFarmPresentation.CropLook.values()) {
            var mapped = SeasonalFarmPresentationPlan.fromLook(
                    new SeasonalFarmPresentation.FarmLook(cropLook, "test", 1.0));
            check(mapped.crop() != null, "visual for " + cropLook);
            if (mapped.crop() == CropVisual.WHEAT_AGE_7
                    || mapped.crop() == CropVisual.WHEAT_AGE_4
                    || mapped.crop() == CropVisual.WHEAT_AGE_1
                    || mapped.crop() == CropVisual.WHEAT_AGE_2
                    || mapped.crop() == CropVisual.WHEAT_AGE_0) {
                check(SeasonalFarmPresentationPlan.wheatAge(mapped.crop()) >= 0, "wheat age");
            } else {
                check(SeasonalFarmPresentationPlan.wheatAge(mapped.crop()) < 0, "non-wheat visual");
            }
        }
        check(town.stockpile().get(dev.livingrealms.sim.faction.ResourceType.GRAIN) == grain,
                "farm plan must not harvest/duplicate stockpile");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
