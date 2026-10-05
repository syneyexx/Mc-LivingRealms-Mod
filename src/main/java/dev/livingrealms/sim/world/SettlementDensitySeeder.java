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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Idempotent starter-world/kingdom migration.
 *
 * <p>Twelve persistent kingdoms each seed a capital, one distant authored satellite, and one rural
 * hamlet ({@value #TARGET_SETTLEMENTS_PER_REALM} per realm → {@value #SURFACE_STARTER_SETTLEMENTS}
 * surface starters). Realm content is data-driven via {@link RealmDefinitionLoader}; spacing and
 * density targets remain Java policy invariants. Remaining authored Specs are retained as an
 * expansion catalog for later causal founding. Wizard Trees remain separate. Physical construction
 * stays chunk-local.</p>
 */
public final class SettlementDensitySeeder {
    private SettlementDensitySeeder() {}

    /** Capital + 1 authored satellite + 1 rural hamlet. Wizard Trees excluded from surface counts. */
    public static final int TARGET_SETTLEMENTS_PER_REALM = 3;
    /** Fresh worlds seed only one authored Spec satellite; remaining Specs stay in the expansion catalog. */
    public static final int MAX_AUTHORED_SATELLITES = 1;
    public static final int RURAL_HAMLETS_PER_REALM = 1;
    /**
     * Authoritative product floor for settlement-to-settlement spacing. Shared by seeding, founding,
     * causal expansion, foreign adoption, migration founding, tests, and dashboard guidance.
     * Never encoded in datapack JSON.
     */
    public static final double MIN_SETTLEMENT_SPACING = 2000.0;
    /** Preferred legal satellite radius band for fresh sparse placement. */
    public static final double SPARSE_SATELLITE_RADIUS_MIN = 2200.0;
    public static final double SPARSE_SATELLITE_RADIUS_MAX = 3000.0;
    /** Surface starter total excluding Wizard Trees: 12 × TARGET. */
    public static final int SURFACE_STARTER_SETTLEMENTS = 36;
    private static final String[] RURAL_SUFFIXES = {"Croft","End","Green","Thorp","Wick","Fold","Ley","Combe"};

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
                    "Twelve-realm sparse network ensured (capital+1 Spec+rural); realms=" + state.factions().size()
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
        int changes = 0;
        if (faction == null) {
            faction = new Faction(state.nextId(), spec.displayName(), spec.rulerSeedName());
            faction.restoreTechnology(spec.technology());
            faction.restoreTreasury(spec.treasury());
            Settlement capital = new Settlement(state.nextId(), spec.capitalName(),
                    new SimPosition(spec.x(), spec.z()), spec.capitalPopulation(), spec.capitalHousing(),
                    SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO);
            faction.addSettlement(capital);
            faction.addArmy(new Army(state.nextId(), faction.id(), new SimPosition(spec.x() + 55, spec.z() + 35), spec.armyInfantry()));
            state.addFaction(faction);
            provision(faction, 4);
            applyCulturePackTraits(state, faction, spec);
            changes++;
        }
        Settlement capital = settlement(faction, spec.capitalName());
        if (capital == null) {
            capital = new Settlement(state.nextId(), spec.capitalName(),
                    new SimPosition(spec.x(), spec.z()), spec.capitalPopulation(), spec.capitalHousing(),
                    SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO);
            faction.addSettlement(capital);
            changes++;
        } else {
            changes += ensureCapital(capital, spec.capitalPopulation(), spec.capitalHousing());
        }
        // Existing dense/migrated realms at or above target must not quietly grow.
        if (faction.settlements().size() >= TARGET_SETTLEMENTS_PER_REALM) return changes;
        int added = addStarterSatellite(state, faction, capital.position(), spec.satellites());
        added += addRuralHamlets(state, faction, capital.position(), spec.capitalName());
        if (added > 0) { provision(faction, added); changes += added; }
        return changes;
    }

    private static int ensureCapital(Settlement settlement, int minPopulation, int minHousing) {
        int beforePopulation = settlement.population(), beforeHousing = settlement.housing();
        if (beforePopulation < minPopulation) settlement.addPopulation(minPopulation - beforePopulation);
        if (beforeHousing < minHousing) settlement.addHousing(minHousing - beforeHousing);
        return beforePopulation == settlement.population() && beforeHousing == settlement.housing() ? 0 : 1;
    }

    /** Seeds exactly one authored satellite at a legal sparse radius using Spec directional bias. */
    private static int addStarterSatellite(SimulationState state, Faction faction, SimPosition origin,
                                           List<RealmDefinition.SatelliteDefinition> specs) {
        if (specs.isEmpty()) return 0;
        if (faction.settlements().size() >= TARGET_SETTLEMENTS_PER_REALM) return 0;
        // Already has a non-capital, non-rural settlement — treat as satellite present.
        long nonRuralExtras = faction.settlements().stream()
                .filter(s -> !s.name().equals(faction.settlements().getFirst().name()))
                .filter(s -> !isRuralHamletName(s.name()))
                .count();
        if (nonRuralExtras > 0) return 0;

        int pick = Math.floorMod((int) mix(state.seed() ^ faction.id()), specs.size());
        RealmDefinition.SatelliteDefinition spec = specs.get(pick);
        if (settlement(faction, spec.name()) != null) return 0;

        SimPosition position = placeSparseSatellite(state, origin, faction.id(), spec.dx(), spec.dz(), 0);
        if (position == null || tooCloseAny(state, position, MIN_SETTLEMENT_SPACING)) return 0;
        faction.addSettlement(new Settlement(state.nextId(), spec.name(), position, spec.population(), spec.housing(),
                SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO));
        return 1;
    }

    private static int addRuralHamlets(SimulationState state, Faction faction, SimPosition origin, String capitalName) {
        if (faction.settlements().size() >= TARGET_SETTLEMENTS_PER_REALM) return 0;
        int existingRural = (int) faction.settlements().stream().filter(s -> isRuralHamletName(s.name())).count();
        int needed = Math.max(0, RURAL_HAMLETS_PER_REALM - existingRural);
        needed = Math.min(needed, Math.max(0, TARGET_SETTLEMENTS_PER_REALM - faction.settlements().size()));
        if (needed <= 0) return 0;
        int added = 0;
        String prefix = capitalName.replace(" Keep", "").replace("keep", "").replace(" Citadel", "").replace("haven", "").trim();
        for (int attempt = 0; added < needed && attempt < needed * 8; attempt++) {
            int suffixIndex = existingRural + attempt;
            String name = (prefix + " " + RURAL_SUFFIXES[Math.floorMod(suffixIndex, RURAL_SUFFIXES.length)]).trim();
            if (suffixIndex >= RURAL_SUFFIXES.length) name = name + " " + (suffixIndex / RURAL_SUFFIXES.length + 1);
            if (settlement(faction, name) != null) continue;
            // Different sector from satellite: offset angle by ~2.0 rad from satellite bias.
            SimPosition position = placeSparseSatellite(state, origin, faction.id(),
                    Math.cos(2.0 + attempt), Math.sin(2.0 + attempt), 50 + attempt);
            if (position == null || tooCloseAny(state, position, MIN_SETTLEMENT_SPACING)) continue;
            int pop = 48 + Math.floorMod((int) mix(state.seed() ^ faction.id() ^ (attempt * 17L)), 40);
            Settlement hamlet = new Settlement(state.nextId(), name, position, pop, (int) Math.ceil(pop * 1.2),
                    SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO);
            faction.addSettlement(hamlet);
            added++;
        }
        return added;
    }

    /**
     * Place using authored directional bias normalized to a legal sparse radius (2200–3000).
     * Verifies against every already-planned settlement before returning.
     */
    private static SimPosition placeSparseSatellite(SimulationState state, SimPosition origin, long factionId,
                                                    double dx, double dz, int salt) {
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
        double radius = SPARSE_SATELLITE_RADIUS_MIN
                + (((m >>> 21) & 0x3FFL) / 1023.0) * (SPARSE_SATELLITE_RADIUS_MAX - SPARSE_SATELLITE_RADIUS_MIN);
        for (int ring = 0; ring < 16; ring++) {
            double r = radius + ring * 140.0;
            for (int attempt = 0; attempt < 12; attempt++) {
                double angJitter = attempt * (Math.PI * 2.0 / 12.0) * 0.08;
                double cos = Math.cos(angJitter), sin = Math.sin(angJitter);
                double bx = nx * cos - nz * sin;
                double bz = nx * sin + nz * cos;
                SimPosition candidate = new SimPosition(origin.x() + bx * r, origin.z() + bz * r);
                if (!tooCloseAny(state, candidate, MIN_SETTLEMENT_SPACING)) return candidate;
            }
        }
        return null;
    }

    private static boolean isRuralHamletName(String name) {
        for (String suffix : RURAL_SUFFIXES) {
            if (name.endsWith(" " + suffix)) return true;
            String marker = " " + suffix + " ";
            int idx = name.lastIndexOf(marker);
            if (idx >= 0) {
                String rest = name.substring(idx + marker.length());
                if (!rest.isEmpty() && rest.chars().allMatch(Character::isDigit)) return true;
            }
        }
        return false;
    }

    private static boolean tooCloseAny(SimulationState state, SimPosition p, double spacing) {
        for (Faction f : state.factions()) {
            for (Settlement s : f.settlements()) {
                if (p.distanceTo(s.position()) < spacing) return true;
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
    private static long mix(long z) { z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31); }

    /** Capitals plus every authored Spec name (including expansion-catalog Specs not yet seeded). */
    public static List<String> authoredSettlementNames(){
        List<String> names=new ArrayList<>();
        for(RealmDefinition realm:realms()){
            names.add(realm.capitalName());
            for(RealmDefinition.SatelliteDefinition spec:realm.satellites())names.add(spec.name());
        }
        return List.copyOf(names);
    }

    /** Expansion catalog Specs available for later causal founding (not all seeded at start). */
    public static List<String> authoredExpansionCatalogNames(){
        List<String> names=new ArrayList<>();
        for(RealmDefinition realm:realms()){
            for(RealmDefinition.SatelliteDefinition spec:realm.satellites())names.add(spec.name());
        }
        return List.copyOf(names);
    }

    /** Authored Specs for a realm that are not yet present as settlements. */
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

    /** Compatibility view of an authored satellite Spec for expansion engines. */
    public record Spec(String name,double dx,double dz,int population,int housing) {}
}
