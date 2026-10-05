package dev.livingrealms.sim.underworld;

import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.List;
import java.util.Objects;

/**
 * Fence for stolen goods. Does not accept arbitrary inventory dumps — only ledger lots from real crimes.
 */
public final class BlackMarketService {
    private BlackMarketService() {}

    public record SaleResult(boolean success, double paid, String reason) {
        public static SaleResult ok(double paid, String reason) {
            return new SaleResult(true, paid, reason == null ? "" : reason);
        }
        public static SaleResult fail(String reason) {
            return new SaleResult(false, 0, reason == null ? "" : reason);
        }
    }

    public static List<StolenGoodsEntry> listUnsold(SimulationState state, String actorKey) {
        Objects.requireNonNull(state, "state");
        if (!UnderworldActions.canAccessBlackMarket(state, actorKey)) return List.of();
        return state.stolenGoodsLedger().unsoldFor(actorKey);
    }

    public static SaleResult sell(SimulationState state, String actorKey, long entryId) {
        Objects.requireNonNull(state, "state");
        if (actorKey == null || actorKey.isBlank()) return SaleResult.fail("invalid_actor");
        if (!UnderworldActions.canAccessBlackMarket(state, actorKey)) return SaleResult.fail("no_access");
        StolenGoodsEntry entry = state.stolenGoodsLedger().find(entryId).orElse(null);
        if (entry == null) return SaleResult.fail("unknown_lot");
        if (!entry.actorKey().equals(actorKey)) return SaleResult.fail("not_owner");
        if (entry.sold()) return SaleResult.fail("already_sold");
        UnderworldProfile profile = state.underworldProfile(actorKey);
        double fenceCut = .55 + Math.min(.25, profile.streetCred() * .002);
        double paid = entry.value() * fenceCut;
        if (!entry.sell(state.clock().day(), paid)) return SaleResult.fail("sell_failed");
        profile.recordFenceSale(1.0 + Math.min(4, paid * .02));
        state.history().add(new WorldEvent(state.clock().day(), "black_market_sale",
                "actor=" + actorKey + ", lot=" + entryId + ", paid=" + Math.round(paid)));
        return SaleResult.ok(paid, "sold");
    }
}
