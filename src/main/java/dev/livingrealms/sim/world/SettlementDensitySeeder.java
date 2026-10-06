package dev.livingrealms.sim.world;

import dev.livingrealms.sim.content.RealmDefinition;
import dev.livingrealms.sim.content.RealmDefinitionLoader;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.faction.SettlementRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Canonical bootstrap/adoption layer for the deterministic fresh-world civilization layout.
 *
 * <p>Placement and starter identity are owned by {@link StarterCivilizationLayoutPlanner}. This
 * class mutates canonical simulation state only on the normal server/bootstrap path. Existing
 * realms are never relocated or mass-backfilled.</p>
 */
public final class SettlementDensitySeeder {
    private SettlementDensitySeeder() {}

    public static final int AUTHORED_SATELLITES_PER_REALM = 10;
    public static final int MIN_TOWNS_PER_REALM = 2;
    public static final int MAX_TOWNS_PER_REALM = 4;
    public static final int MIN_VILLAGES_PER_REALM = 6;
    public static final int MAX_VILLAGES_PER_REALM = 8;
    public static final int MIN_RURAL_HAMLETS_PER_REALM = 6;
    public static final int MAX_RURAL_HAMLETS_PER_REALM = 14;
    public static final int MIN_SURFACE_STARTER_SETTLEMENTS =
            12 * (1 + AUTHORED_SATELLITES_PER_REALM + MIN_RURAL_HAMLETS_PER_REALM);
    public static final int MAX_SURFACE_STARTER_SETTLEMENTS =
            12 * (1 + AUTHORED_SATELLITES_PER_REALM + MAX_RURAL_HAMLETS_PER_REALM);

    private static List<RealmDefinition> realms() {
        return RealmDefinitionLoader.loadAll();
    }

    /** Returns the number of canonical changes performed. */
    public static int ensureStarterDensity(SimulationState state) {
        Objects.requireNonNull(state, "state");
        return ensureStarterDensity(
                state, StarterCivilizationLayoutPlanner.plan(state.seed()));
    }

    public static int ensureStarterDensity(
            SimulationState state,
            StarterCivilizationLayoutPlanner.Layout layout) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(layout, "layout");
        int changes = 0;
        for (StarterCivilizationLayoutPlanner.RealmPlan realm : layout.realms()) {
            changes += ensureRealm(state, realm);
        }
        changes += initializeRelations(state);
        if (changes > 0) {
            state.history().add(new WorldEvent(state.clock().day(), "living_world_network_expanded",
                    "Twelve-realm hierarchical starter network ensured; realms=" + state.factions().size()
                            + ", settlements=" + state.factions().stream()
                            .mapToInt(f -> f.settlements().size()).sum()));
        }
        return changes;
    }

    /**
     * @deprecated Ordinary simulation must not teleport settlements. Legal placement is determined
     * before creation. Anchored settlements refuse relocate. Returns 0 always.
     */
    @Deprecated
    public static int enforceSpacing(SimulationState state) {
        Objects.requireNonNull(state, "state");
        return 0;
    }

    private static int ensureRealm(SimulationState state,
                                   StarterCivilizationLayoutPlanner.RealmPlan plan) {
        RealmDefinition spec = plan.definition();
        Faction faction = faction(state, spec.displayName());
        boolean freshRealm = faction == null;
        int changes = 0;

        if (freshRealm) {
            faction = new Faction(plan.factionId(), spec.displayName(), spec.rulerSeedName());
            faction.restoreTechnology(spec.technology());
            faction.restoreTreasury(spec.treasury());
            for (StarterCivilizationLayoutPlanner.SettlementPlan starter : plan.settlements()) {
                faction.addSettlement(new Settlement(
                        starter.id(), starter.name(), starter.position(),
                        starter.population(), starter.housing(),
                        SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, starter.role()));
            }
            StarterCivilizationLayoutPlanner.SettlementPlan capital = plan.capital();
            faction.addArmy(new Army(plan.armyId(), faction.id(),
                    new SimPosition(capital.position().x() + 55, capital.position().z() + 35),
                    spec.armyInfantry()));
            state.addFaction(faction);
            provision(faction, 4);
            int nonCapital = Math.max(0, plan.settlements().size() - 1);
            if (nonCapital > 0) provision(faction, Math.max(1, nonCapital / 4));
            applyCulturePackTraits(state, faction, spec);
            changes += plan.settlements().size();
        }

        Settlement capital = settlement(faction, spec.capitalName());
        if (capital == null) {
            // Legacy repair only. Do not claim a reserved starter id inside an already-populated save.
            StarterCivilizationLayoutPlanner.SettlementPlan plannedCapital = plan.capital();
            capital = new Settlement(state.nextId(), spec.capitalName(), plannedCapital.position(),
                    spec.capitalPopulation(), spec.capitalHousing(),
                    SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.CAPITAL);
            faction.addSettlement(capital);
            changes++;
            freshRealm = false;
        } else {
            capital.restoreRole(SettlementRole.CAPITAL);
            changes += ensureCapital(capital, spec.capitalPopulation(), spec.capitalHousing());
        }

        // New density is fresh-world only. Legacy/anchored worlds retain their canonical layout.
        if (!freshRealm) return changes;
        return changes;
    }

    private static int ensureCapital(Settlement settlement, int minPopulation, int minHousing) {
        int beforePopulation = settlement.population(), beforeHousing = settlement.housing();
        if (beforePopulation < minPopulation) settlement.addPopulation(minPopulation - beforePopulation);
        if (beforeHousing < minHousing) settlement.addHousing(minHousing - beforeHousing);
        return beforePopulation == settlement.population() && beforeHousing == settlement.housing() ? 0 : 1;
    }

    private static int initializeRelations(SimulationState state) {
        int changes = 0;
        List<Faction> factions = state.factions();
        for (int i = 0; i < factions.size(); i++) for (int j = i + 1; j < factions.size(); j++) {
            Faction a = factions.get(i), b = factions.get(j);
            boolean missingA = !a.relations().containsKey(b.id()), missingB = !b.relations().containsKey(a.id());
            if (!missingA && !missingB) continue;
            long mixed = StarterCivilizationLayoutPlanner.mix(
                    state.seed() ^ a.id() * 31L ^ b.id() * 131L);
            double opinion = ((mixed >>> 12) & 0xFFL) / 255.0 * 36.0 - 18.0;
            if ((a.name().equals("Kingdom of Aster") && b.name().equals("Veyran Dominion"))
                    || (b.name().equals("Kingdom of Aster") && a.name().equals("Veyran Dominion"))) {
                opinion = -35.0;
            }
            if (missingA) a.relationWith(b.id()).adjust(opinion);
            if (missingB) b.relationWith(a.id()).adjust(opinion);
            changes++;
        }
        return changes;
    }

    private static void applyCulturePackTraits(SimulationState state, Faction faction, RealmDefinition spec) {
        StarterCultureTraits.resolve(spec)
                .ifPresent(traits -> traits.applyTo(state.ensureFactionCivilization(faction.id())));
    }

    private static void provision(Faction faction, int scale) {
        faction.stockpile().add(ResourceType.GRAIN, scale * 400.0);
        faction.stockpile().add(ResourceType.BREAD, scale * 300.0);
        faction.stockpile().add(ResourceType.WOOD, scale * 330.0);
        faction.stockpile().add(ResourceType.STONE, scale * 500.0);
        faction.stockpile().add(ResourceType.IRON, scale * 95.0);
        faction.stockpile().add(ResourceType.TOOLS, scale * 25.0);
        faction.addTreasury(scale * 420.0);
    }

    private static Faction faction(SimulationState state, String name) {
        for (Faction faction : state.factions()) if (faction.name().equals(name)) return faction;
        return null;
    }

    private static Settlement settlement(Faction faction, String name) {
        for (Settlement settlement : faction.settlements()) if (settlement.name().equals(name)) return settlement;
        return null;
    }

    /** Capitals plus every authored satellite name. */
    public static List<String> authoredSettlementNames() {
        List<String> names = new ArrayList<>();
        for (RealmDefinition realm : realms()) {
            names.add(realm.capitalName());
            for (RealmDefinition.SatelliteDefinition spec : realm.satellites()) names.add(spec.name());
        }
        return List.copyOf(names);
    }

    /** Compatibility view: fresh worlds seed these authored satellites; old worlds may grow into them causally. */
    public static List<String> authoredExpansionCatalogNames() {
        List<String> names = new ArrayList<>();
        for (RealmDefinition realm : realms()) {
            for (RealmDefinition.SatelliteDefinition spec : realm.satellites()) names.add(spec.name());
        }
        return List.copyOf(names);
    }

    /** Authored specs absent from an existing realm; retained for safe causal expansion of legacy worlds. */
    public static List<Spec> unusedExpansionSpecs(Faction faction, String realmName) {
        RealmDefinition realm = RealmDefinitionLoader.byDisplayName(realmName);
        if (realm == null) return List.of();
        List<Spec> unused = new ArrayList<>();
        for (RealmDefinition.SatelliteDefinition sat : realm.satellites()) {
            if (settlement(faction, sat.name()) == null) {
                unused.add(new Spec(sat.name(), sat.dx(), sat.dz(), sat.population(), sat.housing()));
            }
        }
        return List.copyOf(unused);
    }

    /** Compatibility view of an authored satellite spec for legacy-world expansion engines. */
    public record Spec(String name, double dx, double dz, int population, int housing) {}
}
