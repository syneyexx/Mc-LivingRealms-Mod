package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.DevelopmentModeGuard;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Objects;

/**
 * Source-aware housing authority. Minecraft entities never mint housing; capacity is derived from
 * explicit sources and can decrease when a source disappears.
 *
 * <p>{@link Settlement#housing()} remains the cached effective capacity used by demography. Call
 * {@link #reconcileCanonical} after player-structure or physical-housing changes so the cache stays
 * coherent. Never treat {@code max(oldHousing, newPhysical)} as the sole rule — that only ratchets up.
 */
public final class HousingCapacity {
    private HousingCapacity() {}

    public record Sources(
            int plannedCanonical,
            int livingRealmsMaterialized,
            int playerRegistered,
            int foreignPhysical,
            int temporary,
            int effective
    ) {}

    public static int representedResidents(ConstructionIntent intent) {
        Objects.requireNonNull(intent, "intent");
        if (intent.role() != StructureRole.HOUSE) return 0;
        return representedResidents(intent.width(), intent.depth());
    }

    public static int representedResidents(int width, int depth) {
        int w = Math.max(1, width), d = Math.max(1, depth);
        if (w >= 11 || d >= 11) return 48; // apartment block
        if (w >= 9 || d >= 9) return 18;   // townhouse / longhouse scale
        return 8;                          // cottage / rural house
    }

    public static int physicalHousingEstimate(Iterable<ConstructionIntent> completedHouseIntents) {
        int total = 0;
        for (ConstructionIntent intent : completedHouseIntents) total += representedResidents(intent);
        return total;
    }

    public static int playerRegistered(Settlement settlement, SimulationState state) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(state, "state");
        int player = 0;
        for (RegisteredPlayerStructure s : state.registeredPlayerStructures()) {
            if (s.settlementId() == settlement.id()) player += s.housingCredit();
        }
        return player;
    }

    public static int foreignPhysical(Settlement settlement, SimulationState state) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(state, "state");
        int outlying = 0;
        for (var site : state.outlyingSites()) {
            if (site.active() && site.settlementId() == settlement.id()) {
                outlying += (int) Math.floor(site.housingCredit());
            }
        }
        return outlying;
    }

    /** Living Realms authored/materialized house completions (not player-registered). */
    public static int livingRealmsMaterialized(Settlement settlement, SimulationState state) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(state, "state");
        int total = 0;
        var owner = state.findSettlementOwner(settlement.id()).orElse(null);
        if (owner != null) {
            for (ConstructionIntent intent : SettlementPlanCache.plan(owner, settlement)) {
                if (intent.role() != StructureRole.HOUSE) continue;
                if (!settlement.isConstructionCompleted(intent.key())) continue;
                total += representedResidents(intent);
            }
        }
        if (total == 0) {
            long houses = settlement.completedConstruction().stream().filter(k -> k.startsWith("house:")).count();
            if (houses > 0) total = (int) houses * representedResidents(7, 7);
        }
        return total;
    }

    /**
     * Temporary shelter (founding camp / refugee tents). Does not become permanent city housing once
     * real registered or materialized houses exist. Uses fixed founding credit — never the possibly
     * inflated {@link Settlement#housing()} cache.
     */
    public static int temporary(Settlement settlement, int playerRegistered, int materialized) {
        Objects.requireNonNull(settlement, "settlement");
        if (playerRegistered > 0 || materialized > 0) return 0;
        if (settlement.origin() == SettlementOrigin.PLAYER_FOUNDED) {
            return PlayerSettlementFounder.FOUNDING_HOUSING;
        }
        if (settlement.name().startsWith("Refugee Camp ")) {
            return Math.max(0, Math.min(settlement.housing(), Math.max(settlement.population(), 4)));
        }
        return 0;
    }

    public static Sources sources(Settlement settlement, SimulationState state) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(state, "state");
        int player = playerRegistered(settlement, state);
        int foreign = foreignPhysical(settlement, state);
        int materialized = livingRealmsMaterialized(settlement, state);
        int temp = temporary(settlement, player, materialized);
        int planned = Math.max(0, settlement.housing());
        int effective = switch (settlement.developmentMode()) {
            case PLAYER_LED -> player + foreign + temp;
            case HYBRID -> player + foreign + materialized + temp;
            case AUTO -> {
                // Distant abstract AI may use planned housing. Player credit is never permanently
                // absorbed — when player structures vanish, only planned/materialized/foreign remain.
                if (player > 0 || foreign > 0) {
                    yield Math.max(planned, materialized) + player + foreign;
                }
                yield Math.max(planned, materialized + temp);
            }
        };
        return new Sources(planned, materialized, player, foreign, temp, Math.max(0, effective));
    }

    /** Authoritative effective housing for demography, immigration, UI and planners. */
    public static int calculate(Settlement settlement, SimulationState state) {
        return sources(settlement, state).effective();
    }

    /**
     * Writes effective capacity into {@link Settlement#housing()} for player-led / hybrid settlements
     * so demography consumers stay coherent. AUTO authored settlements keep their planned housing
     * field untouched — player credit is still visible via {@link #calculate}.
     */
    public static void reconcileCanonical(Settlement settlement, SimulationState state) {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(state, "state");
        if (settlement.developmentMode() == DevelopmentMode.AUTO
                && settlement.origin() != SettlementOrigin.PLAYER_FOUNDED) {
            return;
        }
        Sources src = sources(settlement, state);
        // Compose without treating the housing field as a ratchet floor.
        int effective = src.playerRegistered() + src.foreignPhysical()
                + src.livingRealmsMaterialized() + src.temporary();
        if (settlement.housing() != effective) {
            settlement.setHousing(effective);
        }
    }

    public static int housingShortage(Settlement settlement, SimulationState state) {
        return Math.max(0, settlement.population() - calculate(settlement, state));
    }

    /** True when PLAYER_LED must not receive ordinary auto housing generation. */
    public static boolean blocksAutoHousing(Settlement settlement) {
        return settlement != null && !DevelopmentModeGuard.allowsAutoHousing(settlement);
    }
}
