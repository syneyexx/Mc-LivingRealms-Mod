package dev.livingrealms;

import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.List;

/** Product gate: fresh worlds use sparse 2000-block settlement policy. */
public final class SparseWorldPolicyTest {
    private SparseWorldPolicyTest() {}

    public static void main(String[] args) {
        freshSeedIsSparseAndLegal();
        deterministicRepeatability();
        playerFoundingClearanceIs2000();
        System.out.println("PASS SparseWorldPolicyTest: 36 surface / 3 per realm / 2000 spacing");
    }

    private static void freshSeedIsSparseAndLegal() {
        SimulationState state = new SimulationState(0x2000L);
        DemoSeeder.seed(state);
        check(state.factions().size() == 13, "12 kingdoms + Wizard Trees");
        int surface = state.factions().stream()
                .filter(f -> !f.name().equals("Wizard Trees"))
                .mapToInt(f -> f.settlements().size()).sum();
        check(surface == SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS,
                "surface starters expected " + SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS + " got " + surface);
        check(SettlementDensitySeeder.TARGET_SETTLEMENTS_PER_REALM == 3, "TARGET=3");
        check(SettlementDensitySeeder.MAX_AUTHORED_SATELLITES == 1, "MAX_AUTHORED_SATELLITES=1");
        check(SettlementDensitySeeder.RURAL_HAMLETS_PER_REALM == 1, "RURAL_HAMLETS=1");
        check(SettlementDensitySeeder.MIN_SETTLEMENT_SPACING == 2000.0, "spacing 2000");
        check(SettlementDensitySeeder.SURFACE_STARTER_SETTLEMENTS == 36, "36 surface");

        for (var faction : state.factions()) {
            if (faction.name().equals("Wizard Trees")) {
                check(faction.settlements().size() == 3, "Wizard Trees unchanged");
                continue;
            }
            check(faction.settlements().size() == 3, faction.name() + " must have 3 starters, got " + faction.settlements().size());
            for (Settlement s : faction.settlements()) {
                check(s.origin() == SettlementOrigin.AUTHORED_SEED || s.origin() == SettlementOrigin.WIZARD_TREES,
                        "fresh seed origin for " + s.name());
            }
        }

        List<Settlement> all = state.factions().stream().flatMap(f -> f.settlements().stream()).toList();
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            double dist = all.get(i).position().distanceTo(all.get(j).position());
            check(dist >= SettlementDensitySeeder.MIN_SETTLEMENT_SPACING - 1.0,
                    "illegal spacing " + all.get(i).name() + " ↔ " + all.get(j).name() + " = " + Math.round(dist));
        }
        check(SettlementDensitySeeder.ensureStarterDensity(state) == 0, "idempotent densifier");
    }

    private static void deterministicRepeatability() {
        SimulationState a = new SimulationState(0xABCDL);
        SimulationState b = new SimulationState(0xABCDL);
        DemoSeeder.seed(a);
        DemoSeeder.seed(b);
        List<Settlement> sa = a.factions().stream().flatMap(f -> f.settlements().stream()).toList();
        List<Settlement> sb = b.factions().stream().flatMap(f -> f.settlements().stream()).toList();
        check(sa.size() == sb.size(), "same count");
        for (int i = 0; i < sa.size(); i++) {
            check(sa.get(i).name().equals(sb.get(i).name()), "name order");
            check(Math.abs(sa.get(i).position().x() - sb.get(i).position().x()) < 1e-6, "x");
            check(Math.abs(sa.get(i).position().z() - sb.get(i).position().z()) < 1e-6, "z");
        }
    }

    private static void playerFoundingClearanceIs2000() {
        check(PlayerSettlementFounder.MIN_SETTLEMENT_SPACING == 2000.0, "founder spacing");
        SimulationState state = new SimulationState(0xF00DL);
        DemoSeeder.seed(state);
        Settlement capital = state.factions().getFirst().settlements().getFirst();
        var blocked = PlayerSettlementFounder.found(state, "player:near", "Near", "Tooclose", capital.position());
        check(!blocked.success(), "must reject founding on capital");
        check(blocked.reason().contains("2000") || blocked.reason().contains("blocks"),
                "reason must cite 2000: " + blocked.reason());
        var ok = PlayerSettlementFounder.found(state, "player:far", "Far", "Newcamp",
                new SimPosition(capital.position().x() + 25_000, capital.position().z() + 25_000));
        check(ok.success(), "far founding should work: " + ok.reason());
        check(state.findSettlement(ok.settlementId()).orElseThrow().population()
                == PlayerSettlementFounder.FOUNDING_POPULATION, "founder camp population");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
