package dev.livingrealms.sim.world;

import dev.livingrealms.sim.civilization.MigrationGroup;
import dev.livingrealms.sim.civilization.MigrationStatus;
import dev.livingrealms.sim.ecology.EcosystemRegion;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.military.SiegeState;
import dev.livingrealms.sim.underworld.UnderworldActions;
import dev.livingrealms.sim.util.DeterministicRng;
import java.util.ArrayList;

/**
 * Data-first day orchestration for {@link SimulationState}.
 * Engines remain owned by the state; this class sequences their existing calls
 * without changing seeds, order, or mutation semantics.
 */
public final class SimulationEngine {
    private SimulationEngine() {}

    /**
     * Safe mid-day presentation pulse: shipment progress + migration column crawl.
     * Does <em>not</em> run full {@link #advanceDays} engines or advance the world clock.
     */
    public static void advancePresentationPulse(SimulationState state, double dayFraction) {
        if (!(dayFraction > 0) || !Double.isFinite(dayFraction)) return;
        double frac = Math.min(1.0, dayFraction);
        long day = state.clock().day();
        state.tradeEngine().presentationPulse(state, new DeterministicRng(state.seed() ^ day ^ 0x51ED0015EL ^ (Double.doubleToLongBits(frac))), frac);
        for (MigrationGroup group : state.migrationGroups()) {
            if (group.status() != MigrationStatus.TRAVELING) continue;
            group.advance(frac * .12);
        }
        // Subtle siege pressure presentation: nudge attacker armies a few blocks toward the target.
        for (SiegeState siege : state.sieges()) {
            if (!siege.active()) continue;
            state.findFaction(siege.attackerFactionId()).ifPresent(attacker -> {
                state.findSettlement(siege.settlementId()).ifPresent(target -> {
                    for (Army army : attacker.armies()) {
                        if (army.destroyed()) continue;
                        double dist = army.position().distanceTo(target.position());
                        if (dist < 8 || dist > 400) continue;
                        army.moveToward(target.position(), Math.min(dist * .08, 18 * frac));
                    }
                });
            });
        }
    }

    public static void advanceDays(SimulationState state, int days) {
        if (days < 0) throw new IllegalArgumentException("days");
        long seed = state.seed();
        for (int d = 0; d < days; d++) {
            long day = state.clock().day();
            for (EcosystemRegion r : state.regions()) state.ecologyEngine().simulate(r, 1, new DeterministicRng(seed ^ (day * 0x9E3779B97F4A7C15L) ^ r.id()));
            state.factionEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0xC0FFEE1234L));
            state.primaryEconomyEngine().simulateDay(state);
            // Local farms/workshops/consumption/tithe after primary industry so mines/fisheries enter settlement stores before levy.
            state.settlementEconomyEngine().simulateDay(state);
            state.industryEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0x243F6A8885A308D3L));
            for (Faction faction : new ArrayList<>(state.factions())) state.societyEngine().simulateDay(faction);
            state.socialPopulationEngine().simulateDay(state);
            state.socialMobilityEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0xA5A5A5A5A5A5A5A5L));
            state.civilizationEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0xD1B54A32D192ED03L));
            state.governmentEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0x6A09E667F3BCC909L));
            state.sovereignDebtEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0x243F6A8885A308D3L ^ 0x51L));
            state.grandProjectEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0x85EBCA77C2B2AE63L));
            state.heroEngine().simulateDay(state);
            state.militaryCommandEngine().simulateDay(state);
            state.diplomacyEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0xBB67AE8584CAA73BL));
            state.tradeEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0x7A4DE5B19L));
            state.transportEngine().simulateDay(state);
            state.aviationEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0x3C6EF372FE94F82BL));
            state.navalEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0x510E527FADE682D1L));
            state.crimeEngine().simulateDay(state, state.config().crimeHeatDecayPerDay(), state.config().reputationDecayPerDay());
            UnderworldActions.expireDue(state);
            if (day % 7 == 0) UnderworldActions.pruneClosedContracts(state);
            state.bountyOfficeEngine().simulateDay(state);
            state.lawEnforcementEngine().releaseExpired(state);
            state.reputationEngine().simulateDay(state);
            state.rebellionEngine().simulateDay(state, new DeterministicRng(seed ^ day ^ 0xA54FF53A5F1D36F1L), state.config().rebellionThreshold());
            RoadLifeEngine.simulateDay(state, new DeterministicRng(seed ^ day ^ 0xC2B2AE3D27D4EB4FL));
            if (day % 30 == 15) SettlementExpansionEngine.simulateDay(state, new DeterministicRng(seed ^ day ^ 0xCA05A15L));
            state.clock().advance(SimClock.TICKS_PER_DAY);
            if (day % 30 == 0) state.history().add(new WorldEvent(day, "monthly_snapshot", state.summary()));
        }
    }
}
