package dev.livingrealms.sim.world;

import dev.livingrealms.sim.content.RealmDefinition;
import dev.livingrealms.sim.content.RealmDefinitionLoader;
import dev.livingrealms.sim.faction.SettlementRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure deterministic authority for the fresh-world surface civilization layout.
 *
 * <p>The planner owns starter identities as well as positions. IDs live in a reserved range so
 * Minecraft chunk generation never depends on mutable {@link SimulationState#nextId()} order.
 * Canonical bootstrap consumes these exact records.</p>
 */
public final class StarterCivilizationLayoutPlanner {
    public static final int LAYOUT_VERSION = 1;
    public static final long STARTER_ID_BASE = 1_000_000L;
    private static final int IDS_PER_REALM = 100;
    private static final double CAPITAL_LATTICE_SPACING = 3_800.0;
    private static final double HEX_Z = 0.8660254037844386;
    private static final String[] RURAL_SUFFIXES = {
            "Croft","End","Green","Thorp","Wick","Fold","Ley","Combe",
            "Heath","Dene","Brook","Garth","Moor","Field","Rest","Hollow"
    };

    private StarterCivilizationLayoutPlanner() {}

    public record SettlementPlan(
            long id,
            String stableKey,
            String realmId,
            String name,
            SimPosition position,
            int population,
            int housing,
            SettlementRole role,
            long parentSettlementId
    ) {
        public SettlementPlan {
            if (id <= 0) throw new IllegalArgumentException("id");
            if (stableKey == null || stableKey.isBlank()) throw new IllegalArgumentException("stableKey");
            if (realmId == null || realmId.isBlank()) throw new IllegalArgumentException("realmId");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name");
            position = Objects.requireNonNull(position, "position");
            role = Objects.requireNonNull(role, "role");
            if (population < 0 || housing < 0 || parentSettlementId < 0) throw new IllegalArgumentException("counts/parent");
        }
    }

    public record RealmPlan(
            RealmDefinition definition,
            long factionId,
            long armyId,
            List<SettlementPlan> settlements
    ) {
        public RealmPlan {
            definition = Objects.requireNonNull(definition, "definition");
            if (factionId <= 0 || armyId <= 0) throw new IllegalArgumentException("realm ids");
            settlements = List.copyOf(Objects.requireNonNull(settlements, "settlements"));
        }

        public SettlementPlan capital() {
            return settlements.stream()
                    .filter(s -> s.role() == SettlementRole.CAPITAL)
                    .findFirst().orElseThrow();
        }
    }

    public record Layout(long worldSeed, int layoutVersion, List<RealmPlan> realms) {
        public Layout {
            if (layoutVersion <= 0) throw new IllegalArgumentException("layoutVersion");
            realms = List.copyOf(Objects.requireNonNull(realms, "realms"));
        }

        public List<SettlementPlan> settlements() {
            return realms.stream().flatMap(r -> r.settlements().stream()).toList();
        }

        public RealmPlan realm(String realmId) {
            return realms.stream().filter(r -> r.definition().id().equals(realmId)).findFirst().orElseThrow();
        }

        public SettlementPlan settlement(long id) {
            return settlements().stream().filter(s -> s.id() == id).findFirst().orElseThrow();
        }
    }

    public static Layout plan(long worldSeed) {
        return plan(worldSeed, RealmDefinitionLoader.loadAll());
    }

    /** Deterministic overload for tests and callers that already hold immutable content definitions. */
    public static Layout plan(long worldSeed, List<RealmDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        List<SettlementPlan> placed = new ArrayList<>();
        List<RealmPlan> realms = new ArrayList<>();
        for (int realmOrdinal = 0; realmOrdinal < definitions.size(); realmOrdinal++) {
            RealmDefinition definition = definitions.get(realmOrdinal);
            RealmPlan realm = planRealm(worldSeed, realmOrdinal, definition, placed);
            realms.add(realm);
            placed.addAll(realm.settlements());
        }
        return new Layout(worldSeed, LAYOUT_VERSION, realms);
    }

    private static RealmPlan planRealm(long seed, int realmOrdinal, RealmDefinition realm, List<SettlementPlan> previouslyPlaced) {
        long realmBase = STARTER_ID_BASE + (long) realmOrdinal * IDS_PER_REALM;
        long factionId = realmBase + 1;
        long armyId = realmBase + 2;
        long capitalId = realmBase + 10;

        List<SettlementPlan> local = new ArrayList<>();
        List<SettlementPlan> collisionView = new ArrayList<>(previouslyPlaced);
        SettlementPlan capital = new SettlementPlan(
                capitalId, "realm:" + realm.id() + "/capital", realm.id(), realm.capitalName(),
                starterCapitalPosition(seed, realm), realm.capitalPopulation(), realm.capitalHousing(),
                SettlementRole.CAPITAL, 0);
        local.add(capital);
        collisionView.add(capital);

        List<RealmDefinition.SatelliteDefinition> specs = realm.satellites();
        if (specs.size() < SettlementDensitySeeder.AUTHORED_SATELLITES_PER_REALM) {
            throw new IllegalStateException(realm.id() + " requires "
                    + SettlementDensitySeeder.AUTHORED_SATELLITES_PER_REALM + " authored satellites, got " + specs.size());
        }

        long realmMix = mix(seed ^ ((long) realm.id().hashCode() * 0x9E3779B97F4A7C15L));
        int townTarget = SettlementDensitySeeder.MIN_TOWNS_PER_REALM
                + Math.floorMod((int) realmMix,
                SettlementDensitySeeder.MAX_TOWNS_PER_REALM - SettlementDensitySeeder.MIN_TOWNS_PER_REALM + 1);
        Set<String> townNames = new HashSet<>();
        specs.stream()
                .sorted(Comparator.comparingInt(RealmDefinition.SatelliteDefinition::population).reversed()
                        .thenComparing(RealmDefinition.SatelliteDefinition::name))
                .limit(townTarget)
                .forEach(s -> townNames.add(s.name()));

        List<SettlementPlan> towns = new ArrayList<>();
        int townOrdinal = 0;
        for (int i = 0; i < specs.size(); i++) {
            RealmDefinition.SatelliteDefinition authored = specs.get(i);
            if (!townNames.contains(authored.name())) continue;
            SimPosition position = townOrdinal == 0
                    ? placeInnerTownAnchor(seed, collisionView, capital.position(), authored.dx(), authored.dz(), factionId, i)
                    : placeChild(seed, collisionView, capital.position(), SettlementRole.CAPITAL,
                    authored.dx(), authored.dz(), factionId, i, SettlementRole.TOWN);
            townOrdinal++;
            if (position == null) continue;
            int population = Math.max(550, authored.population());
            SettlementPlan child = new SettlementPlan(
                    realmBase + 20 + i, "realm:" + realm.id() + "/authored:" + i, realm.id(), authored.name(),
                    position, population, Math.max(population + 20, authored.housing()),
                    SettlementRole.TOWN, capital.id());
            towns.add(child);
            local.add(child);
            collisionView.add(child);
        }
        if (towns.isEmpty()) throw new IllegalStateException("starter realm has no towns: " + realm.id());

        List<SettlementPlan> villages = new ArrayList<>();
        int villageOrdinal = 0;
        for (int i = 0; i < specs.size(); i++) {
            RealmDefinition.SatelliteDefinition authored = specs.get(i);
            if (townNames.contains(authored.name())) continue;
            SettlementPlan parent = towns.get(villageOrdinal % towns.size());
            villageOrdinal++;
            SimPosition position = placeChild(seed, collisionView, parent.position(), parent.role(),
                    authored.dx(), authored.dz(), factionId, 100 + i, SettlementRole.VILLAGE);
            if (position == null) continue;
            int population = Math.max(120, Math.min(480, authored.population()));
            SettlementPlan child = new SettlementPlan(
                    realmBase + 20 + i, "realm:" + realm.id() + "/authored:" + i, realm.id(), authored.name(),
                    position, population, Math.max(population + 20, authored.housing()),
                    SettlementRole.VILLAGE, parent.id());
            villages.add(child);
            local.add(child);
            collisionView.add(child);
        }
        if (villages.size() < SettlementDensitySeeder.MIN_VILLAGES_PER_REALM
                || villages.size() > SettlementDensitySeeder.MAX_VILLAGES_PER_REALM) {
            throw new IllegalStateException("starter village count out of target range for "
                    + realm.id() + ": " + villages.size());
        }

        long m = mix(seed ^ ((long) realm.id().hashCode() * 0xD1B54A32D192ED03L));
        int hamletTarget = SettlementDensitySeeder.MIN_RURAL_HAMLETS_PER_REALM
                + Math.floorMod((int) (m >>> 19),
                SettlementDensitySeeder.MAX_RURAL_HAMLETS_PER_REALM - SettlementDensitySeeder.MIN_RURAL_HAMLETS_PER_REALM + 1);
        String prefix = compactPrefix(realm.capitalName());
        Set<String> usedNames = new HashSet<>();
        for (SettlementPlan settlement : local) usedNames.add(settlement.name());
        for (int i = 0; i < hamletTarget; i++) {
            SettlementPlan parent = villages.get(i % villages.size());
            int suffixIndex = Math.floorMod((int) (m + i * 17L), RURAL_SUFFIXES.length);
            String name = prefix + " " + RURAL_SUFFIXES[suffixIndex];
            if (usedNames.contains(name)) name += " " + (i + 1);

            long angleBits = mix(seed ^ parent.id() ^ (i * 0x9E3779B97F4A7C15L));
            double angle = ((angleBits >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
            SimPosition position = placeChild(seed, collisionView, parent.position(), SettlementRole.VILLAGE,
                    Math.cos(angle), Math.sin(angle), factionId, 400 + i, SettlementRole.HAMLET);
            if (position == null) continue;
            int population = 36 + Math.floorMod((int) (angleBits >>> 33), 54);
            SettlementPlan hamlet = new SettlementPlan(
                    realmBase + 40 + i, "realm:" + realm.id() + "/hamlet:" + i, realm.id(), name,
                    position, population, Math.max(population + 8, (int) Math.ceil(population * 1.25)),
                    SettlementRole.HAMLET, parent.id());
            local.add(hamlet);
            collisionView.add(hamlet);
            usedNames.add(name);
        }
        return new RealmPlan(realm, factionId, armyId, local);
    }

    private static SimPosition placeInnerTownAnchor(long seed, List<SettlementPlan> placed, SimPosition parent,
                                                     double dx, double dz, long factionId, int salt) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            long mixed = mix(seed ^ factionId ^ salt);
            double angle = ((mixed >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            len = 1.0;
        }
        double nx = dx / len, nz = dz / len;
        long mixed = mix(seed ^ factionId ^ (salt * 0xD1B54A32D192ED03L));
        double radius = 700.0 + (((mixed >>> 21) & 0x3FFL) / 1023.0) * 100.0;
        for (int attempt = 0; attempt < 16; attempt++) {
            double jitter = (attempt - 7.5) * (Math.PI / 72.0);
            double cos = Math.cos(jitter), sin = Math.sin(jitter);
            SimPosition candidate = new SimPosition(
                    parent.x() + (nx * cos - nz * sin) * radius,
                    parent.z() + (nx * sin + nz * cos) * radius);
            if (!tooCloseAny(placed, candidate, SettlementRole.TOWN)) return candidate;
        }
        return placeChild(seed, placed, parent, SettlementRole.CAPITAL, dx, dz, factionId, salt, SettlementRole.TOWN);
    }

    private static SimPosition placeChild(long seed, List<SettlementPlan> placed, SimPosition parent,
                                          SettlementRole parentRole, double dx, double dz,
                                          long factionId, int salt, SettlementRole childRole) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            long m = mix(seed ^ factionId ^ salt);
            double a = ((m >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
            dx = Math.cos(a);
            dz = Math.sin(a);
            len = 1.0;
        }
        double nx = dx / len, nz = dz / len;
        long m = mix(seed ^ factionId ^ (salt * 0x9E3779B97F4A7C15L));
        SettlementSpacingPolicy.Range preferred = SettlementSpacingPolicy.preferredRange(parentRole, childRole);
        double radius = preferred.at(((m >>> 21) & 0x3FFL) / 1023.0);
        double ringStep = childRole == SettlementRole.HAMLET ? 24.0 : 36.0;
        for (int ring = 0; ring < 18; ring++) {
            double r = radius + ring * ringStep;
            for (int attempt = 0; attempt < 16; attempt++) {
                double jitter = (attempt - 7.5) * (Math.PI / 56.0);
                double cos = Math.cos(jitter), sin = Math.sin(jitter);
                SimPosition candidate = new SimPosition(
                        parent.x() + (nx * cos - nz * sin) * r,
                        parent.z() + (nx * sin + nz * cos) * r);
                if (!tooCloseAny(placed, candidate, childRole)) return candidate;
            }
        }
        return null;
    }

    private static boolean tooCloseAny(List<SettlementPlan> placed, SimPosition candidate, SettlementRole role) {
        for (SettlementPlan settlement : placed) {
            double floor = SettlementSpacingPolicy.minimumDistance(role, settlement.role());
            if (candidate.distanceTo(settlement.position()) < floor) return true;
        }
        return false;
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

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
