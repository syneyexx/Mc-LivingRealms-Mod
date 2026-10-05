package dev.livingrealms.sim.world;

import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.util.DeterministicRng;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Causal settlement founding from overpopulation, surplus, and strategy — never from player proximity.
 * Replaces {@link FrontierExplorationSeeder} exploration spawning for living-world continuity.
 */
public final class SettlementExpansionEngine {
    public static final double MIN_SETTLEMENT_SPACING = SettlementDensitySeeder.MIN_SETTLEMENT_SPACING;
    public static final double MIN_SPACING = MIN_SETTLEMENT_SPACING;
    public static final int MAX_CAUSAL_PER_REALM = 12;
    private static final String[] FALLBACK_SUFFIXES = {
            "Reach", "March", "Hollow", "Crossing", "Ford", "Ridge", "Glen", "Rest"
    };

    private SettlementExpansionEngine() {}

    /**
     * Player proximity must not create settlements. Always returns 0.
     * Physical catch-up/materialization of existing abstract colonies may use observer position later.
     */
    public static int ensureNear(SimulationState state, SimPosition observer) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(observer, "observer");
        return 0;
    }

    /** Daily causal expansion pass using the world seed for determinism. */
    public static int tick(SimulationState state) {
        Objects.requireNonNull(state, "state");
        return simulateDay(state, new DeterministicRng(state.seed() ^ state.clock().day() ^ 0xCA05A15L));
    }

    public static int simulateDay(SimulationState state, DeterministicRng rng) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rng, "rng");
        int founded = 0;
        for (Faction faction : state.factions()) {
            if (WizardTreesSeeder.isWizardTrees(faction) || faction.settlements().isEmpty()) continue;
            long causal = faction.settlements().stream()
                    .filter(s -> s.origin() == SettlementOrigin.CAUSAL_EXPANSION).count();
            if (causal >= MAX_CAUSAL_PER_REALM) continue;
            if (founded > 0 && !rng.chance(.35)) continue; // at most a couple per day worldwide
            if (tryFoundColony(state, faction, rng)) founded++;
            if (founded >= 2) break;
        }
        return founded;
    }

    private static boolean tryFoundColony(SimulationState state, Faction faction, DeterministicRng rng) {
        Settlement capital = faction.settlements().stream()
                .max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id))
                .orElse(null);
        if (capital == null) return false;
        double pressure = expansionPressure(faction, capital);
        if (pressure < .55 || !rng.chance(Math.min(.12, pressure * .08))) return false;

        // Prefer unused authored Spec catalog entries for this realm.
        List<SettlementDensitySeeder.Spec> unused = SettlementDensitySeeder.unusedExpansionSpecs(faction, faction.name());
        SettlementDensitySeeder.Spec spec = unused.isEmpty() ? null : unused.get(rng.nextInt(unused.size()));
        SimPosition position;
        String name;
        int pop;
        int housing;
        if (spec != null) {
            position = placeFromBias(state, capital.position(), faction.id(), spec.dx(), spec.dz(), rng);
            name = spec.name();
            pop = Math.max(40, Math.min(220, spec.population() / 4));
            housing = Math.max(pop, Math.min(260, spec.housing() / 4));
        } else {
            position = placeFromBias(state, capital.position(), faction.id(),
                    rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng);
            if (position == null) return false;
            name = uniqueFallbackName(state, faction, capital, position);
            pop = 48 + rng.nextInt(40);
            housing = (int) Math.ceil(pop * 1.2);
        }
        if (position == null || tooClose(state, position, MIN_SETTLEMENT_SPACING)) return false;
        if (faction.settlements().stream().anyMatch(s -> s.name().equals(name))) return false;

        Settlement colony = new Settlement(state.nextId(), name, position, pop, housing,
                SettlementOrigin.CAUSAL_EXPANSION, false, DevelopmentMode.AUTO);
        faction.addSettlement(colony);
        // Emigrants leave the crowded capital; surplus pays for the founding.
        int emigrants = Math.min(24, Math.max(8, capital.population() / 40));
        capital.addPopulation(-emigrants);
        faction.stockpile().take(ResourceType.GRAIN, Math.min(faction.stockpile().get(ResourceType.GRAIN), 80));
        faction.stockpile().take(ResourceType.WOOD, Math.min(faction.stockpile().get(ResourceType.WOOD), 60));
        faction.stockpile().add(ResourceType.GRAIN, 40);
        faction.stockpile().add(ResourceType.WOOD, 35);
        faction.stockpile().add(ResourceType.STONE, 50);
        faction.addTreasury(-Math.min(faction.treasury() * .04, 120));
        state.history().add(new WorldEvent(state.clock().day(), "causal_settlement_founded",
                faction.name() + " founded " + name + " from " + capital.name()
                        + " (pressure=" + String.format(java.util.Locale.ROOT, "%.2f", pressure) + ")"));
        return true;
    }

    private static double expansionPressure(Faction faction, Settlement capital) {
        double housingStress = capital.housingShortage() / Math.max(40.0, capital.population());
        double prosperity = capital.prosperity();
        double food = capital.foodSecurity();
        double treasury = Math.min(1.0, faction.treasury() / 8_000.0);
        double grain = Math.min(1.0, faction.stockpile().get(ResourceType.GRAIN) / 2_000.0);
        double unrestPenalty = capital.unrest() > .55 ? .35 : 0;
        return housingStress * 1.4 + prosperity * .35 + food * .25 + treasury * .3 + grain * .35 - unrestPenalty;
    }

    private static SimPosition placeFromBias(SimulationState state, SimPosition origin, long factionId,
                                             double dx, double dz, DeterministicRng rng) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            double a = rng.nextDouble() * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
            len = 1;
        }
        double nx = dx / len, nz = dz / len;
        double radius = MIN_SETTLEMENT_SPACING + 200 + rng.nextDouble() * 900;
        for (int ring = 0; ring < 18; ring++) {
            double r = radius + ring * 160;
            for (int attempt = 0; attempt < 10; attempt++) {
                double jitter = (attempt / 10.0) * Math.PI * 2 * .1;
                double cos = Math.cos(jitter), sin = Math.sin(jitter);
                double bx = nx * cos - nz * sin;
                double bz = nx * sin + nz * cos;
                SimPosition candidate = new SimPosition(origin.x() + bx * r, origin.z() + bz * r);
                if (!tooClose(state, candidate, MIN_SETTLEMENT_SPACING)) return candidate;
            }
        }
        return null;
    }

    private static boolean tooClose(SimulationState state, SimPosition p, double spacing) {
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                if (p.distanceTo(settlement.position()) < spacing) return true;
            }
        }
        return false;
    }

    private static String uniqueFallbackName(SimulationState state, Faction faction, Settlement capital, SimPosition at) {
        String prefix = capital.name().replace(" Keep", "").replace("haven", "").trim();
        int salt = Math.floorMod((int) Math.round(at.x() + at.z()), FALLBACK_SUFFIXES.length);
        for (int i = 0; i < FALLBACK_SUFFIXES.length; i++) {
            String name = prefix + " " + FALLBACK_SUFFIXES[(salt + i) % FALLBACK_SUFFIXES.length];
            if (faction.settlements().stream().noneMatch(s -> s.name().equals(name))
                    && state.factions().stream().flatMap(f -> f.settlements().stream())
                    .noneMatch(s -> s.name().equals(name))) {
                return name;
            }
        }
        return prefix + " Colony " + state.nextId();
    }
}
