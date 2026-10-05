package dev.livingrealms;

import dev.livingrealms.sim.civilization.HiddenCache;
import dev.livingrealms.sim.civilization.JusticeCase;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.sim.world.DemoSeeder;
import dev.livingrealms.sim.world.StateRetentionCompactor;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.Map;

/** Wave 28 — retention/compaction policies bound collections without erasing active gameplay truth. */
public final class StateRetentionCompactorTest {
    private StateRetentionCompactorTest() {}

    public static void main(String[] args) {
        monthlyPrunesInactiveRows();
        historySummarizesSnapshots();
        constructionKeysRetained();
        System.out.println("PASS StateRetentionCompactor: monthly/quarterly retention + history summarize + construction retained");
    }

    private static void monthlyPrunesInactiveRows() {
        SimulationState state = new SimulationState(0x8E701L);
        DemoSeeder.seed(state);
        var settlement = state.factions().getFirst().settlements().getFirst();
        state.advanceDays(200);
        long day = state.clock().day();

        JusticeCase closed = new JusticeCase(state.nextId(), state.factions().getFirst().id(), settlement.id(),
                Math.max(0, day - 150), "player:x", CrimeType.THEFT);
        closed.dismiss();
        state.addJusticeCase(closed);
        JusticeCase active = new JusticeCase(state.nextId(), state.factions().getFirst().id(), settlement.id(),
                Math.max(0, day - 10), "player:y", CrimeType.ASSAULT);
        state.addJusticeCase(active);

        HiddenCache recovered = new HiddenCache(state.nextId(), state.factions().getFirst().id(), settlement.id(),
                Math.max(0, day - 120), settlement.position(), Map.of(ResourceType.GOLD, 12.0));
        recovered.recover();
        state.addHiddenCache(recovered);

        int constructionBefore = StateRetentionCompactor.constructionRecordsRetained(state);
        var report = StateRetentionCompactor.compactMonthly(state);
        check(report.justicePruned() >= 1, "inactive justice pruned");
        check(state.justiceCases().stream().anyMatch(JusticeCase::active), "active justice retained");
        check(report.cachesPruned() >= 1, "recovered caches pruned");
        check(StateRetentionCompactor.constructionRecordsRetained(state) == constructionBefore,
                "construction keys never pruned by retention");
    }

    private static void historySummarizesSnapshots() {
        SimulationState state = new SimulationState(0x8E702L);
        for (int i = 0; i < 12; i++) {
            long d = i * 30L;
            state.history().add(new WorldEvent(d, "monthly_snapshot", "day=" + d + ", people=" + (100 + i)));
            state.history().add(new WorldEvent(d + 1, "battle", "important battle " + i));
        }
        int before = state.history().size();
        int folded = StateRetentionCompactor.summarizeOldMonthlySnapshots(state.history(), 400);
        check(folded > 0, "old monthly snapshots summarized");
        check(state.history().all().stream().anyMatch(e -> e.type().equals("battle")), "important history retained");
        check(state.history().size() < before, "history shrank after summarize");
        check(state.history().all().stream().anyMatch(e -> e.type().equals("era_summary")), "era_summary present");
    }

    private static void constructionKeysRetained() {
        SimulationState state = new SimulationState(0x8E703L);
        DemoSeeder.seed(state);
        var s = state.factions().getFirst().settlements().getFirst();
        s.markConstructionCompleted("house:0");
        s.markConstructionCompleted("well:0");
        int keys = StateRetentionCompactor.constructionRecordsRetained(state);
        StateRetentionCompactor.compactQuarterly(state);
        check(StateRetentionCompactor.constructionRecordsRetained(state) >= keys, "construction retained after quarterly");
        check(s.isConstructionCompleted("house:0"), "house:0 still present");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
