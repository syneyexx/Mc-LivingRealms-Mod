package dev.livingrealms.sim.world;

import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Causal settlement expansion. New settlements require real simulation causes — never player
 * proximity alone. Player movement may materialize/present existing abstract colonies but must not
 * be the root cause of foundation.
 */
public final class SettlementExpansionEngine {
    public static final double MIN_SPACING = SettlementDensitySeeder.MIN_SETTLEMENT_SPACING;
    public static final int MAX_CAUSAL_PER_REALM = 12;
    private static final String[] SUFFIXES = {
            "Colony", "March", "Reach", "Holding", "Plantation", "Borough", "Township", "Estates"
    };

    private SettlementExpansionEngine() {}

    public enum Cause {
        OVERPOPULATION, HOUSING_PRESSURE, FOOD_SURPLUS, RESOURCE_DISCOVERY,
        TRADE_FRONTIER, STRATEGIC_BORDER, MILITARY_COLONIZATION, MIGRATION_DESTINATION,
        REFUGEE_STABILIZATION, PORT_OPPORTUNITY, REALM_POLICY
    }

    /**
     * Daily causal expansion pass. Returns number of settlements founded.
     * Observer position is ignored for foundation — kept only for API compatibility with old seeder hooks.
     */
    public static int tick(SimulationState state) {
        Objects.requireNonNull(state, "state");
        int founded = 0;
        for (Faction faction : state.factions()) {
            if (WizardTreesSeeder.isWizardTrees(faction)) continue;
            if (faction.settlements().isEmpty()) continue;
            long causal = faction.settlements().stream()
                    .filter(s -> s.origin() == SettlementOrigin.CAUSAL_EXPANSION).count();
            if (causal >= MAX_CAUSAL_PER_REALM) continue;
            Cause cause = evaluateCause(faction);
            if (cause == null) continue;
            Settlement parent = faction.settlements().stream()
                    .max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id))
                    .orElse(null);
            if (parent == null) continue;
            if (!hostCanProvision(faction, parent)) continue;
            SimPosition candidate = placeColony(state, parent);
            if (candidate == null) continue;
            if (tooClose(state, candidate, MIN_SPACING)) continue;

            // Prefer unused authored Spec names when available.
            String name = pickName(state, faction, candidate);
            int pop = 42 + Math.floorMod((int) mix(state.seed() ^ faction.id() ^ Math.round(candidate.x())), 36);
            Settlement colony = new Settlement(state.nextId(), name, candidate, pop, (int) Math.ceil(pop * 1.15),
                    SettlementOrigin.CAUSAL_EXPANSION, false, DevelopmentMode.AUTO);
            faction.addSettlement(colony);
            faction.stockpile().add(ResourceType.GRAIN, 180);
            faction.stockpile().add(ResourceType.BREAD, 120);
            faction.stockpile().add(ResourceType.WOOD, 140);
            faction.stockpile().add(ResourceType.STONE, 160);
            parent.addPopulation(-Math.min(12, parent.population() / 20));
            state.history().add(new WorldEvent(state.clock().day(), "causal_settlement_founded",
                    faction.name() + " founded " + name + " (" + cause.name().toLowerCase() + ") at "
                            + Math.round(candidate.x()) + "," + Math.round(candidate.z())));
            founded++;
            if (founded >= 2) break; // soft daily bound
        }
        return founded;
    }

    /**
     * Discovery/materialization hook only — never creates settlements from player presence.
     * @return always 0 (no exploration-triggered foundation)
     */
    public static int ensureNear(SimulationState state, SimPosition observer) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(observer, "observer");
        // Player proximity may later trigger physical catch-up for abstract colonies; never foundation.
        return 0;
    }

    private static Cause evaluateCause(Faction faction) {
        Settlement capital = faction.settlements().stream()
                .max(Comparator.comparingInt(Settlement::population)).orElse(null);
        if (capital == null) return null;
        if (capital.housingShortage() > Math.max(40, capital.population() / 8)) return Cause.HOUSING_PRESSURE;
        if (capital.population() > capital.housing() * 1.15 && capital.population() >= 800) return Cause.OVERPOPULATION;
        if (capital.foodSecurity() > .82 && faction.stockpile().get(ResourceType.GRAIN)
                + faction.stockpile().get(ResourceType.BREAD) > capital.population() * 12) {
            return Cause.FOOD_SURPLUS;
        }
        if (capital.prosperity() > .72 && faction.technology() > .35) return Cause.TRADE_FRONTIER;
        return null;
    }

    private static boolean hostCanProvision(Faction faction, Settlement parent) {
        if (parent.population() < 120) return false;
        double food = faction.stockpile().get(ResourceType.GRAIN) + faction.stockpile().get(ResourceType.BREAD)
                + faction.stockpile().get(ResourceType.FOOD);
        return food >= 200 && faction.treasury() >= 80;
    }

    private static SimPosition placeColony(SimulationState state, Settlement parent) {
        long m = mix(state.seed() ^ parent.id() ^ state.clock().day());
        double baseAng = ((m >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
        for (int ring = 0; ring < 20; ring++) {
            double radius = MIN_SPACING + 200.0 + ring * 160.0;
            for (int attempt = 0; attempt < 10; attempt++) {
                double a = baseAng + attempt * (Math.PI * 2.0 / 10.0);
                SimPosition p = new SimPosition(parent.position().x() + Math.cos(a) * radius,
                        parent.position().z() + Math.sin(a) * radius);
                if (!tooClose(state, p, MIN_SPACING)) return p;
            }
        }
        return null;
    }

    private static boolean tooClose(SimulationState state, SimPosition p, double spacing) {
        for (Faction f : state.factions())
            for (Settlement s : f.settlements())
                if (p.distanceTo(s.position()) < spacing) return true;
        return false;
    }

    private static String pickName(SimulationState state, Faction faction, SimPosition at) {
        List<SettlementDensitySeeder.Spec> unused = SettlementDensitySeeder.unusedExpansionSpecs(faction, faction.name());
        if (!unused.isEmpty()) {
            int idx = Math.floorMod((int) mix(state.seed() ^ faction.id() ^ Math.round(at.x())), unused.size());
            return unused.get(idx).name();
        }
        String prefix = faction.name()
                .replace("Kingdom of ", "").replace("High Kingdom of ", "")
                .replace(" Dominion", "").replace(" Crown", "").trim();
        int salt = Math.floorMod((int) mix(state.seed() ^ Math.round(at.x()) ^ Math.round(at.z())), SUFFIXES.length);
        String candidate = prefix + " " + SUFFIXES[salt];
        boolean taken = state.factions().stream().flatMap(f -> f.settlements().stream())
                .anyMatch(s -> s.name().equalsIgnoreCase(candidate));
        return taken ? candidate + " " + (1 + Math.floorMod((int) Math.abs(at.x()), 9)) : candidate;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
