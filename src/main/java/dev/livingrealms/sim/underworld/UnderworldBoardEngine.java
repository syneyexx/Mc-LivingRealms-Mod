package dev.livingrealms.sim.underworld;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Comparator;
import java.util.Locale;

/**
 * Seeds AVAILABLE underworld contracts from realm corruption / disorder —
 * never fabricates crime completions. Player accepts via dashboard / tavern contact.
 */
public final class UnderworldBoardEngine {
    private UnderworldBoardEngine() {}

    public static void simulateDay(SimulationState state, DeterministicRng rng) {
        if (state == null || rng == null) return;
        UnderworldActions.expireDue(state);
        long day = state.clock().day();
        // Weekly board refresh; keep open contracts alive between refreshes.
        if (day % 7 != 0) return;
        for (Faction faction : state.factions()) {
            double corruption = faction.government().corruption();
            if (corruption < .28) continue;
            long openHere = state.underworldContracts().stream()
                    .filter(c -> c.open() && c.jurisdictionFactionId() == faction.id())
                    .count();
            int slots = corruption > .55 ? 3 : corruption > .4 ? 2 : 1;
            if (openHere >= slots) continue;
            Settlement hub = faction.settlements().stream()
                    .max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id))
                    .orElse(null);
            if (hub == null) continue;
            int toOffer = (int) Math.min(slots - openHere, 2);
            for (int i = 0; i < toOffer; i++) {
                if (!rng.chance(.35 + corruption * .45)) continue;
                UnderworldContractType type = pickType(rng, corruption);
                String target = nonSecretTarget(type, hub, rng);
                double minValue = 8 + rng.nextInt(40) + corruption * 20;
                double reward = 25 + minValue * (.8 + corruption) + rng.nextInt(30);
                UnderworldActions.offerContract(state, type, faction.id(), target, minValue, reward, 14 + rng.nextInt(14));
            }
        }
        if (day % 21 == 0) UnderworldActions.pruneClosedContracts(state);
    }

    private static UnderworldContractType pickType(DeterministicRng rng, double corruption) {
        UnderworldContractType[] pool = corruption > .5
                ? new UnderworldContractType[]{
                UnderworldContractType.THEFT, UnderworldContractType.BURGLARY,
                UnderworldContractType.SMUGGLING, UnderworldContractType.CARAVAN_HEIST,
                UnderworldContractType.SABOTAGE, UnderworldContractType.ASSASSINATION}
                : new UnderworldContractType[]{
                UnderworldContractType.THEFT, UnderworldContractType.BURGLARY,
                UnderworldContractType.SMUGGLING};
        return pool[rng.nextInt(pool.length)];
    }

    /** Non-secret board label — never reveals acceptor identity. */
    private static String nonSecretTarget(UnderworldContractType type, Settlement hub, DeterministicRng rng) {
        return switch (type) {
            case THEFT -> "merchant";
            case BURGLARY -> "warehouse";
            case SMUGGLING -> "fence";
            case CARAVAN_HEIST -> "caravan";
            case SABOTAGE -> "workshop";
            case ASSASSINATION -> "rival";
        } + ":" + hub.name().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
