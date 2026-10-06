package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.Comparator;

/**
 * Land-bandit economy on top of {@link PirateBand}/{@link PirateHideout}/{@link RaidParty}:
 * typed causes, road extortion, and legend-worthy outlaw careers.
 */
public final class BanditEconomyEngine {
    private BanditEconomyEngine() {}

    /** Weekly: spawn land bands from hunger/tax/unemployment when pressure is high. */
    public static void spawnFromPressures(SimulationState state, DeterministicRng rng) {
        long day = state.clock().day();
        if (day <= 0 || day % 7 != 0) return;
        long activeBands = state.pirateBands().stream().filter(PirateBand::active).count();
        long activeHideouts = state.pirateHideouts().stream().filter(PirateHideout::active).count();
        if (activeBands >= SimulationState.MAX_PIRATE_BANDS || activeHideouts >= SimulationState.MAX_PIRATE_HIDEOUTS) return;

        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                if (activeBands >= SimulationState.MAX_PIRATE_BANDS) return;
                SettlementCivilizationState civ = state.ensureSettlementCivilization(settlement.id(), faction.id());
                boolean already = state.pirateBands().stream().anyMatch(b -> b.active() && b.originSettlementId() == settlement.id());
                if (already) continue;
                double pressure = civ.banditPressure() * .45 + (1 - settlement.foodSecurity()) * .25
                        + (1 - settlement.employment()) * .15 + settlement.unrest() * .15
                        + faction.government().taxRate() * .35;
                if (pressure < .62 || !rng.chance(pressure * .10)) continue;

                BanditArchetype kind = BanditArchetype.choose(settlement, faction, civ, day);
                int men = Math.min(36, Math.max(4, (int) Math.round(settlement.population() / 140.0 * kind.manpowerMul())));
                long bandId = state.nextId();
                SimPosition camp = landHideoutPosition(state, settlement, bandId);
                PirateBand band = new PirateBand(bandId, settlement.id(), day, camp, men);
                band.adjustMorale(kind.morale() - .62);
                state.addPirateBand(band);
                PirateHideout hideout = new PirateHideout(state.nextId(), band.id(), settlement.id(), day, camp);
                hideout.adjustDefense(kind.hideoutDefense());
                state.addPirateHideout(hideout);
                civ.adjustBanditPressure(-.10);
                state.history().add(new WorldEvent(day, "bandit_band_formed",
                        "kind=" + kind.key() + ", band=" + band.id() + ", settlement=" + settlement.id()
                                + ", faction=" + faction.id() + ", strength=" + men + ", hideout=" + hideout.id()));
                activeBands++;
            }
        }
    }

    /**
     * Strategic land-hideout placement: 250–650 blocks from the pressured settlement, biased toward
     * a nearby insecure/high-capacity land route. The PirateHideout record is intentionally reused
     * for save compatibility; only placement semantics distinguish this land-bandit use.
     */
    public static SimPosition landHideoutPosition(SimulationState state, Settlement origin, long bandId) {
        TransportRoute corridor = state.routes().stream()
                .filter(TransportRoute::operational)
                .filter(r -> r.mode() == TransportMode.ROAD || r.mode() == TransportMode.CARAVAN || r.mode() == TransportMode.RAIL)
                .filter(r -> routeDistance(state, r, origin.position()) <= 1_200)
                .min(Comparator
                        .comparingDouble((TransportRoute r) -> r.security() * 260.0
                                + routeDistance(state, r, origin.position())
                                - Math.min(180.0, r.capacityPerDay() * .08))
                        .thenComparingLong(TransportRoute::id))
                .orElse(null);

        long mixed = mix(state.seed() ^ bandId ^ (origin.id() * 0x9E3779B97F4A7C15L));
        double radius = 250.0 + (((mixed >>> 17) & 0xFFFFL) / 65535.0) * 400.0;
        double dx, dz;
        SimPosition midpoint = corridor == null ? null : routeMidpoint(state, corridor);
        if (midpoint != null && midpoint.distanceTo(origin.position()) > 1.0) {
            dx = midpoint.x() - origin.position().x();
            dz = midpoint.z() - origin.position().z();
        } else {
            double angle = ((mixed >>> 33) & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
        }
        double len = Math.max(1.0e-9, Math.hypot(dx, dz));
        double ux = dx / len, uz = dz / len;
        double side = ((((mixed >>> 7) & 0x3FFL) / 1023.0) * 2.0 - 1.0) * 70.0;
        SimPosition preferred = new SimPosition(
                origin.position().x() + ux * radius - uz * side,
                origin.position().z() + uz * radius + ux * side);

        if (clearsSettlementFootprints(state, preferred, origin.id())) return preferred;
        // Deterministic small angular search; never fall back inside a settlement footprint.
        for (int i = 1; i <= 8; i++) {
            double angle = i * Math.PI / 8.0;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double rx = ux * cos - uz * sin, rz = ux * sin + uz * cos;
            SimPosition candidate = new SimPosition(
                    origin.position().x() + rx * radius,
                    origin.position().z() + rz * radius);
            if (clearsSettlementFootprints(state, candidate, origin.id())) return candidate;
        }
        return preferred;
    }

    private static boolean clearsSettlementFootprints(SimulationState state, SimPosition candidate, long originId) {
        return state.factions().stream().flatMap(f -> f.settlements().stream())
                .filter(s -> s.id() != originId)
                .noneMatch(s -> s.position().distanceTo(candidate) < 180.0);
    }

    /** Daily: land bands shake down insecure roads (toll/extortion) and ambush caravan routes. */
    public static void extortRoutes(SimulationState state, DeterministicRng rng) {
        long day = state.clock().day();
        for (PirateBand band : state.pirateBands()) {
            if (!band.active() || band.strength() < 3) continue;
            // Prefer land bands (no nearby operational port origin).
            boolean coastal = state.ports().stream().anyMatch(p -> p.operational() && p.settlementId() == band.originSettlementId());
            if (coastal) continue;
            TransportRoute route = state.routes().stream()
                    .filter(TransportRoute::operational)
                    .filter(r -> r.security() < .62)
                    .min(Comparator.comparingDouble(r -> routeDistance(state, r, band.position())))
                    .orElse(null);
            if (route == null || routeDistance(state, route, band.position()) > 900) {
                PirateHideout home = state.findPirateHideoutByBand(band.id()).orElse(null);
                if (home != null) band.moveToward(home.position(), 40);
                continue;
            }
            SimPosition mid = routeMidpoint(state, route);
            if (mid == null) continue;
            band.moveToward(mid, 55 + band.strength());
            if (band.position().distanceTo(mid) > 140) continue;
            // Extortion: lower security, skim treasury from the route owner, store loot at hideout.
            route.adjustSecurity(-.008 - band.strength() * .0004);
            Faction owner = state.findFaction(route.ownerFactionId()).orElse(null);
            if (owner != null && owner.treasury() > 5 && rng.chance(.12 + band.morale() * .08)) {
                double toll = Math.min(owner.treasury(), 2.5 + band.strength() * .35);
                owner.addTreasury(-toll);
                band.addLoot(toll);
                state.findPirateHideoutByBand(band.id()).ifPresent(h -> h.addLoot(toll * .6));
                if (Math.floorMod(band.id() + day, 19) == 0) {
                    state.history().add(new WorldEvent(day, "bandit_extortion",
                            "band=" + band.id() + ", route=" + route.id() + ", faction=" + owner.id()
                                    + ", toll=" + Math.round(toll) + ", settlement=" + band.originSettlementId()));
                }
            }
            // Occasional ambush damages route quality (washout proxy for blocked road).
            if (rng.chance(.03)) {
                route.improve(-.02);
                band.addLoot(1.5);
                state.history().add(new WorldEvent(day, "bandit_ambush",
                        "band=" + band.id() + ", route=" + route.id() + ", settlement=" + band.originSettlementId()
                                + ", faction=" + route.ownerFactionId()));
            }
        }
    }

    /** Promote notorious outlaw leaders into legends when loot/hideouts grow large. */
    public static void promoteOutlawLegends(SimulationState state) {
        long day = state.clock().day();
        if (day % 30 != 0) return;
        for (PirateBand band : state.pirateBands()) {
            if (!band.active()) continue;
            PirateHideout hideout = state.findPirateHideoutByBand(band.id()).orElse(null);
            double wealth = band.loot() + (hideout == null ? 0 : hideout.storedLoot());
            if (wealth < 80 && band.strength() < 20) continue;
            String subject = "outlaw:" + band.id();
            if (state.legends().stream().anyMatch(l -> l.subjectKey().equals(subject))) continue;
            Faction owner = state.findSettlementOwner(band.originSettlementId()).orElse(null);
            if (owner == null) continue;
            String kind = state.history().recent(400).stream()
                    .filter(e -> e.message().contains("band=" + band.id()))
                    .map(e -> extractKind(e.message()))
                    .filter(k -> k != null)
                    .findFirst().orElse("highwaymen");
            String title = "The " + BanditArchetype.fromKey(kind).displayName() + " of band #" + band.id();
            String desc = "An outlaw band grew to strength " + band.strength() + " with loot worth "
                    + Math.round(wealth) + " near settlement " + band.originSettlementId() + ".";
            state.addLegend(new LegendRecord(state.nextId(), day, owner.id(), band.originSettlementId(),
                    subject, title, desc, Mathx.clamp(.45 + wealth / 400.0, .45, .92)));
            state.history().add(new WorldEvent(day, "outlaw_legend",
                    "legend_band=" + band.id() + ", faction=" + owner.id() + ", settlement=" + band.originSettlementId()
                            + ", kind=" + kind));
            if (state.legends().size() >= SimulationState.MAX_LEGENDS) break;
        }
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static String extractKind(String message) {
        int i = message.indexOf("kind=");
        if (i < 0) return null;
        int start = i + 5;
        int end = message.indexOf(',', start);
        return end < 0 ? message.substring(start).trim() : message.substring(start, end).trim();
    }

    private static double routeDistance(SimulationState state, TransportRoute route, SimPosition from) {
        SimPosition mid = routeMidpoint(state, route);
        return mid == null ? Double.POSITIVE_INFINITY : from.distanceTo(mid);
    }

    private static SimPosition routeMidpoint(SimulationState state, TransportRoute route) {
        Settlement a = state.findSettlement(route.fromSettlementId()).orElse(null);
        Settlement b = state.findSettlement(route.toSettlementId()).orElse(null);
        if (a == null || b == null) return null;
        return new SimPosition((a.position().x() + b.position().x()) * .5, (a.position().z() + b.position().z()) * .5);
    }
}
