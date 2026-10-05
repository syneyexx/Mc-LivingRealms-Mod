package dev.livingrealms;

import dev.livingrealms.api.LivingRealmsApi;
import dev.livingrealms.api.event.ConstructionCompleted;
import dev.livingrealms.api.event.LifecycleEvent;
import dev.livingrealms.api.event.SettlementFounded;
import dev.livingrealms.api.event.SettlementTierChanged;
import dev.livingrealms.sim.compat.adapter.DefaultApiProviders;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.List;

/** Wave 21 smoke: public API providers + immutable lifecycle event publish. */
public final class ApiLifecycleSmokeTest {
    public static void main(String[] args) {
        LivingRealmsApi.clearListeners();
        LivingRealmsApi.installDefaults(
                DefaultApiProviders.species(),
                DefaultApiProviders.cultures(),
                DefaultApiProviders.structures(),
                DefaultApiProviders.tradeGoods());
        check(!LivingRealmsApi.species().all().isEmpty(), "species provider");
        check(LivingRealmsApi.tradeGoods().goodsIds().contains("FOOD"), "trade goods");
        check(LivingRealmsApi.cultures().all().size() >= 1, "cultures");

        List<LifecycleEvent> seen = new ArrayList<>();
        LivingRealmsApi.subscribe(seen::add);
        LivingRealmsApi.publish(new SettlementFounded(3, 1, 2, "Testford", "PLAYER_FOUNDED"));
        Settlement s = new Settlement(9, "TierTown", new SimPosition(0, 0), 20, 40);
        s.addPopulation(90); // CAMP -> HAMLET/VILLAGE transition
        s.markConstructionCompleted("well:0");
        check(seen.stream().anyMatch(e -> e instanceof SettlementFounded), "founded event");
        check(seen.stream().anyMatch(e -> e instanceof SettlementTierChanged), "tier event");
        check(seen.stream().anyMatch(e -> e instanceof ConstructionCompleted), "construction event");
        LivingRealmsApi.clearListeners();
        System.out.println("ApiLifecycleSmokeTest OK");
    }

    private static void check(boolean c, String m) {
        if (!c) throw new AssertionError(m);
    }
}
