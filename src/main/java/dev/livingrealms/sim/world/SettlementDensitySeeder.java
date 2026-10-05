package dev.livingrealms.sim.world;

import dev.livingrealms.sim.content.CultureDefinition;
import dev.livingrealms.sim.content.CultureDefinitionRegistry;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic fresh-world civilization seeder.
 *
 * <p>New surface realms receive one capital, all ten authored satellite definitions, and a
 * deterministic rural hamlet belt. The highest-population authored satellites become 2–4 towns;
 * the remaining authored satellites become 6–8 villages. Another 6–14 small hamlets are placed
 * around those villages. Existing realms are never relocated or silently backfilled by this
 * starter-layout policy: old/anchored worlds keep their canonical settlement positions and grow
 * only through normal causal expansion.</p>
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
    public static final int MIN_SURFACE_STARTER_SETTLEMENTS = 12 * (1 + AUTHORED_SATELLITES_PER_REALM + MIN_RURAL_HAMLETS_PER_REALM);
    public static final int MAX_SURFACE_STARTER_SETTLEMENTS = 12 * (1 + AUTHORED_SATELLITES_PER_REALM + MAX_RURAL_HAMLETS_PER_REALM);

    private static final double CAPITAL_LATTICE_SPACING = 3_800.0;
    private static final double HEX_Z = 0.8660254037844386;
    private static final String[] RURAL_SUFFIXES = {
            "Croft","End","Green","Thorp","Wick","Fold","Ley","Combe",
            "Heath","Dene","Brook","Garth","Moor","Field","Rest","Hollow"
    };

    private static List<RealmDefinition> realms() {
        return RealmDefinitionLoader.loadAll();
    }

    /** Returns the number of canonical changes performed. */
    public static int ensureStarterDensity(SimulationState state) {
        Objects.requireNonNull(state, "state");
        int changes = 0;
        for (RealmDefinition spec : realms()) changes += ensureRealm(state, spec);
        changes += initializeRelations(state);
        if (changes > 0) {
            state.history().add(new WorldEvent(state.clock().day(), "living_world_network_expanded",
                    "Twelve-realm hierarchical starter network ensured; realms=" + state.factions().size()
                            + ", settlements=" + state.factions().stream().mapToInt(f -> f.settlements().size()).sum()));
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

    private static int ensureRealm(SimulationState state, RealmDefinition spec) {
        Faction faction = faction(state, spec.displayName());
        boolean freshRealm = faction == null;
        int changes = 0;

        if (freshRealm) {
            faction = new Faction(state.nextId(), spec.displayName(), spec.rulerSeedName());
            faction.restoreTechnology(spec.technology());
            faction.restoreTreasury(spec.treasury());
            SimPosition capitalPos = starterCapitalPosition(state.seed(), spec);
            Settlement capital = new Settlement(state.nextId(), spec.capitalName(), capitalPos,
                    spec.capitalPopulation(), spec.capitalHousing(),
                    SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.CAPITAL);
            faction.addSettlement(capital);
            faction.addArmy(new Army(state.nextId(), faction.id(),
                    new SimPosition(capitalPos.x() + 55, capitalPos.z() + 35), spec.armyInfantry()));
            state.addFaction(faction);
            provision(faction, 4);
            applyCulturePackTraits(state, faction, spec);
            changes++;
        }

        Settlement capital = settlement(faction, spec.capitalName());
        if (capital == null) {
            SimPosition capitalPos = starterCapitalPosition(state.seed(), spec);
            capital = new Settlement(state.nextId(), spec.capitalName(), capitalPos,
                    spec.capitalPopulation(), spec.capitalHousing(),
                    SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.CAPITAL);
            faction.addSettlement(capital);
            changes++;
            // Missing capital in an existing world is repaired, but the world is not wholesale reseeded.
            freshRealm = false;
        } else {
            capital.restoreRole(SettlementRole.CAPITAL);
            changes += ensureCapital(capital, spec.capitalPopulation(), spec.capitalHousing());
        }

        // New density policy is intentionally fresh-world only. Never teleport or mass-backfill an old realm.
        if (!freshRealm) return changes;

        int added = addAuthoredHierarchy(state, faction, capital, spec);
        added += addRuralHamlets(state, faction, capital, spec);
        if (added > 0) {
            provision(faction, Math.max(1, added / 4));
            changes += added;
        }
        return changes;
    }

    private static int ensureCapital(Settlement settlement, int minPopulation, int minHousing) {
        int beforePopulation = settlement.population(), beforeHousing = settlement.housing();
        if (beforePopulation < minPopulation) settlement.addPopulation(minPopulation - beforePopulation);
        if (beforeHousing < minHousing) settlement.addHousing(minHousing - beforeHousing);
        return beforePopulation == settlement.population() && beforeHousing == settlement.housing() ? 0 : 1;
    }

    private static int addAuthoredHierarchy(SimulationState state, Faction faction, Settlement capital,
                                            RealmDefinition realm) {
        List<RealmDefinition.SatelliteDefinition> specs = realm.satellites();
        if (specs.size() < AUTHORED_SATELLITES_PER_REALM) {
            throw new IllegalStateException(realm.id() + " requires " + AUTHORED_SATELLITES_PER_REALM
                    + " authored satellites, got " + specs.size());
        }

        long realmMix = mix(state.seed() ^ ((long) realm.id().hashCode() * 0x9E3779B97F4A7C15L));
        int townTarget = MIN_TOWNS_PER_REALM
                + Math.floorMod((int) realmMix, MAX_TOWNS_PER_REALM - MIN_TOWNS_PER_REALM + 1);

        Set<String> townNames = new HashSet<>();
        specs.stream()
                .sorted(Comparator.comparingInt(RealmDefinition.SatelliteDefinition::population).reversed()
                        .thenComparing(RealmDefinition.SatelliteDefinition::name))
                .limit(townTarget)
                .forEach(s -> townNames.add(s.name()));

        int added = 0;
        List<Settlement> towns = new ArrayList<>();
        List<Settlement> villages = new ArrayList<>();

        // Towns first so villages can be parented spatially around them.
        for (int i = 0; i < specs.size(); i++) {
            RealmDefinition.SatelliteDefinition authored = specs.get(i);
            if (!townNames.contains(authored.name())) continue;
            Settlement created = addAuthoredChild(state, faction, capital, authored, SettlementRole.TOWN, i);
            if (created != null) {
                towns.add(created);
                added++;
            }
        }
        if (towns.isEmpty()) throw new IllegalStateException("starter realm has no towns: " + realm.id());

        for (int i = 0; i < specs.size(); i++) {
            RealmDefinition.SatelliteDefinition authored = specs.get(i);
            if (townNames.contains(authored.name())) continue;
            Settlement parent = towns.get(Math.floorMod(authored.name().hashCode(), towns.size()));
            Settlement created = addAuthoredChild(state, faction, parent, authored, SettlementRole.VILLAGE, 100 + i);
            if (created != null) {
                villages.add(created);
                added++;
            }
        }
        int villageCount = villages.size();
        if (villageCount < MIN_VILLAGES_PER_REALM || villageCount > MAX_VILLAGES_PER_REALM) {
            throw new IllegalStateException("starter village count out of target range for " + realm.id()
                    + ": " + villageCount);
        }
        return added;
    }

    private static Settlement addAuthoredChild(SimulationState state, Faction faction, Settlement parent,
                                                RealmDefinition.SatelliteDefinition authored,
                                                SettlementRole role, int salt) {
        if (settlement(faction, authored.name()) != null) return null;
        SimPosition position = placeChild(state, parent.position(), parent.role(),
                authored.dx(), authored.dz(), faction.id(), salt, role);
        if (position == null) return null;

        int population = role == SettlementRole.TOWN
                ? Math.max(550, authored.population())
                : Math.max(120, Math.min(480, authored.population()));
        int housing = Math.max(population + 20, authored.housing());
        Settlement child = new Settlement(state.nextId(), authored.name(), position, population, housing,
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, role);
        faction.addSettlement(child);
        return child;
    }

    private static int addRuralHamlets(SimulationState state, Faction faction, Settlement capital,
                                       RealmDefinition realm) {
        List<Settlement> villages = faction.settlements().stream()
                .filter(s -> s.role() == SettlementRole.VILLAGE)
                .sorted(Comparator.comparingLong(Settlement::id))
                .toList();
        if (villages.isEmpty()) return 0;

        long m = mix(state.seed() ^ ((long) realm.id().hashCode() * 0xD1B54A32D192ED03L));
        int target = MIN_RURAL_HAMLETS_PER_REALM
                + Math.floorMod((int) (m >>> 19), MAX_RURAL_HAMLETS_PER_REALM - MIN_RURAL_HAMLETS_PER_REALM + 1);
        String prefix = compactPrefix(capital.name());
        int added = 0;

        for (int i = 0; i < target; i++) {
            Settlement parent = villages.get(i % villages.size());
            int suffixIndex = Math.floorMod((int) (m + i * 17L), RURAL_SUFFIXES.length);
            String name = prefix + " " + RURAL_SUFFIXES[suffixIndex];
            if (settlement(faction, name) != null) name += " " + (i + 1);

            long angleBits = mix(state.seed() ^ parent.id() ^ (i * 0x9E3779B97F4A7C15L));
            double angle = ((angleBits >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
            SimPosition position = placeChild(state, parent.position(), SettlementRole.VILLAGE,
                    Math.cos(angle), Math.sin(angle), faction.id(), 400 + i, SettlementRole.HAMLET);
            if (position == null) continue;

            int pop = 36 + Math.floorMod((int) (angleBits >>> 33), 54);
            Settlement hamlet = new Settlement(state.nextId(), name, position, pop,
                    Math.max(pop + 8, (int) Math.ceil(pop * 1.25)),
                    SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO, SettlementRole.HAMLET);
            faction.addSettlement(hamlet);
            added++;
        }
        return added;
    }

    /** Place a deterministic child in its parent/child preferred band, widening only when collisions require it. */
    private static SimPosition placeChild(SimulationState state, SimPosition parent,
                                          SettlementRole parentRole, double dx, double dz,
                                          long factionId, int salt, SettlementRole childRole) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            long m = mix(state.seed() ^ factionId ^ salt);
            double a = ((m >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
            dx = Math.cos(a);
            dz = Math.sin(a);
            len = 1.0;
        }
        double nx = dx / len, nz = dz / len;
        long m = mix(state.seed() ^ factionId ^ (salt * 0x9E3779B97F4A7C15L));
        SettlementSpacingPolicy.Range preferred = SettlementSpacingPolicy.preferredRange(parentRole, childRole);
        double radius = preferred.at(((m >>> 21) & 0x3FFL) / 1023.0);
        double ringStep = childRole == SettlementRole.HAMLET ? 24.0 : 36.0;

        for (int ring = 0; ring < 18; ring++) {
            double r = radius + ring * ringStep;
            for (int attempt = 0; attempt < 16; attempt++) {
                double jitter = (attempt - 7.5) * (Math.PI / 56.0);
                double cos = Math.cos(jitter), sin = Math.sin(jitter);
                double bx = nx * cos - nz * sin;
                double bz = nx * sin + nz * cos;
                SimPosition candidate = new SimPosition(parent.x() + bx * r, parent.z() + bz * r);
                if (!tooCloseAny(state, candidate, childRole)) return candidate;
            }
        }
        return null;
    }

    private static SimPosition starterCapitalPosition(long seed, RealmDefinition realm) {
        int[] axial = switch (realm.id()) {
            case "aster" -> new int[]{0, 0};
            case "veyran" -> new int[]{1, 0};
            case "sablemere" -> new int[]{2, 0};
            case "stormcoast" -> new int[]{2, 1};
            case "aurenthal" -> new int[]{1, 1};
            case "glassmere" -> new int[]{0, 1};
            case "eldermere" -> new int[]{-1, 0};
            case "verdance" -> new int[]{-2, 0};
            case "redmarch" -> new int[]{-3, 0};
            case "norwyn" -> new int[]{-1, -1};
            case "solenne" -> new int[]{0, -1};
            case "dravik" -> new int[]{1, -1};
            default -> throw new IllegalArgumentException("unmapped starter realm " + realm.id());
        };
        double x = CAPITAL_LATTICE_SPACING * (axial[0] + axial[1] * 0.5);
        double z = CAPITAL_LATTICE_SPACING * HEX_Z * axial[1];
        if ("aster".equals(realm.id())) return new SimPosition(0, 0);

        long m = mix(seed ^ ((long) realm.id().hashCode() * 0x94D049BB133111EBL));
        double jx = ((((m >>> 12) & 0x3FFL) / 1023.0) * 2.0 - 1.0) * 140.0;
        double jz = ((((m >>> 32) & 0x3FFL) / 1023.0) * 2.0 - 1.0) * 140.0;
        return new SimPosition(x + jx, z + jz);
    }

    private static String compactPrefix(String capitalName) {
        String prefix = capitalName.replace(" Keep", "").replace("keep", "")
                .replace(" Citadel", "").replace("haven", "").trim();
        return prefix.isBlank() ? "Rural" : prefix;
    }

    private static boolean tooCloseAny(SimulationState state, SimPosition p, SettlementRole role) {
        for (Faction faction : state.factions()) {
            for (Settlement settlement : faction.settlements()) {
                double floor = SettlementSpacingPolicy.minimumDistance(role, settlement.role());
                if (p.distanceTo(settlement.position()) < floor) return true;
            }
        }
        return false;
    }

    private static int initializeRelations(SimulationState state) {
        int changes = 0;
        List<Faction> factions = state.factions();
        for (int i = 0; i < factions.size(); i++) for (int j = i + 1; j < factions.size(); j++) {
            Faction a = factions.get(i), b = factions.get(j);
            boolean missingA = !a.relations().containsKey(b.id()), missingB = !b.relations().containsKey(a.id());
            if (!missingA && !missingB) continue;
            long mixed = mix(state.seed() ^ a.id() * 31L ^ b.id() * 131L);
            double opinion = ((mixed >>> 12) & 0xFFL) / 255.0 * 36.0 - 18.0;
            if ((a.name().equals("Kingdom of Aster") && b.name().equals("Veyran Dominion"))
                    || (b.name().equals("Kingdom of Aster") && a.name().equals("Veyran Dominion"))) opinion = -35.0;
            if (missingA) a.relationWith(b.id()).adjust(opinion);
            if (missingB) b.relationWith(a.id()).adjust(opinion);
            changes++;
        }
        return changes;
    }

    private static void applyCulturePackTraits(SimulationState state, Faction faction, RealmDefinition spec) {
        if (spec.cultureId() == null || spec.cultureId().isBlank()) return;
        CultureDefinition culture = CultureDefinitionRegistry.find(spec.cultureId()).orElse(null);
        if (culture == null) return;
        var civ = state.ensureFactionCivilization(faction.id());
        double agrarian = Math.max(0.2, 0.75 - culture.economicTendency() * 0.35);
        civ.setCultureTraits(
                culture.economicTendency(),
                culture.martialTendency(),
                agrarian,
                culture.artisticTendency(),
                0.55,
                Math.min(1.0, 0.35 + culture.economicTendency() * 0.4));
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

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
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

    /** Compatibility view: fresh worlds now seed these authored satellites; old worlds may still use them causally. */
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
