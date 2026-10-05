package dev.livingrealms.sim.world;

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
 * surface starters). Remaining authored Specs are retained as an expansion catalog for later causal
 * founding. Wizard Trees remain separate. Physical construction stays chunk-local.</p>
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
     */
    public static final double MIN_SETTLEMENT_SPACING = 2000.0;
    /** Preferred legal satellite radius band for fresh sparse placement. */
    public static final double SPARSE_SATELLITE_RADIUS_MIN = 2200.0;
    public static final double SPARSE_SATELLITE_RADIUS_MAX = 3000.0;
    /** Surface starter total excluding Wizard Trees: 12 × TARGET. */
    public static final int SURFACE_STARTER_SETTLEMENTS = 36;
    private static final String[] RURAL_SUFFIXES = {"Croft","End","Green","Thorp","Wick","Fold","Ley","Combe"};

    private static final List<RealmSpec> REALMS = List.of(
            realm("Kingdom of Aster", "Queen Elara I", "Asterhold", 0, 0, 3_300, 3_700, .28, 8_500, 150,
                    s("Willowmere",460,-310,430,500), s("Thornfield",-560,360,95,130), s("Greyford",860,470,920,1_050),
                    s("Sunreach",-980,-470,210,270), s("Highmere",1280,-690,1_420,1_600), s("Oakwatch",1390,250,520,610),
                    s("Brookhollow",420,910,82,115), s("Stonebridge",-1380,780,760,860), s("Dawnfield",1760,930,310,380),
                    s("Ravenwood",-1820,-850,62,88)),
            realm("Veyran Dominion", "King Oren IV", "Veyra", 5280, 660, 3_600, 4_000, .32, 9_200, 180,
                    s("Blackwater",-520,470,1_040,1_180), s("Redhaven",610,-390,360,430), s("Stonecross",960,420,1_480,1_660),
                    s("Emberfield",260,930,120,160), s("Ironvale",1420,-520,1_920,2_180), s("Duskmere",1650,590,560,650),
                    s("Eastgate",930,1130,88,125), s("Ashford",-1090,-760,680,780), s("Kestrel",-1510,660,260,330),
                    s("Northreach",1980,1020,58,82)),
            realm("Kingdom of Eldermere", "King Aldren II", "Elderkeep", -6600, 1870, 3_150, 3_550, .24, 7_900, 135,
                    s("Mossford",520,-380,460,540), s("Bellwater",-630,410,150,210), s("Kingsmead",900,520,1_180,1_330),
                    s("Lowfen",-980,-560,280,350), s("Whitegrove",1320,-740,780,910), s("Rosewatch",1460,320,390,470),
                    s("Alderbrook",420,980,105,145), s("Westmere",-1450,760,640,740), s("Goldfield",1740,1040,220,285),
                    s("Dunhollow",-1880,-920,72,98)),
            realm("Kingdom of Solenne", "Queen Maris III", "Solspire", 1430, -7040, 3_850, 4_250, .36, 9_800, 165,
                    s("Brightwater",480,-360,520,610), s("Sunford",-610,380,170,225), s("Valecross",870,500,1_260,1_430),
                    s("Amberfield",-960,-510,310,380), s("Highsun",1300,-720,1_520,1_730), s("Dawnwatch",1440,290,470,555),
                    s("Larkbrook",390,940,110,150), s("Southgate",-1390,740,700,815), s("Cinderfield",1710,1010,245,310),
                    s("Quietmere",-1810,-880,65,92)),
            realm("Dravik Iron Kingdom", "King Borin V", "Dravengard", 8030, -6270, 3_300, 3_720, .42, 10_500, 210,
                    s("Ironford",500,-400,610,700), s("Hammerfall",-650,360,190,250), s("Stonegate",910,490,1_360,1_520),
                    s("Coalbrook",-1020,-540,350,430), s("Anvilreach",1360,-700,1_600,1_820), s("Redcliff",1490,310,530,620),
                    s("Deepwell",420,960,125,170), s("Westforge",-1420,770,790,900), s("Steelmead",1760,1020,270,340),
                    s("Blackpine",-1860,-900,76,105)),
            realm("High Kingdom of Norwyn", "Queen Astrid II", "Northcrown", -8140, -6270, 3_050, 3_480, .29, 8_400, 160,
                    s("Frostford",480,-390,520,610), s("Pinewatch",-620,390,145,200), s("Ravenpass",890,520,1_100,1_260),
                    s("Snowmere",-970,-540,295,365), s("Highcliff",1310,-710,1_380,1_570), s("Wolfgate",1450,300,430,510),
                    s("Icebrook",410,950,98,138), s("Westwatch",-1400,760,670,780), s("Winterfield",1730,1030,235,300),
                    s("Firhollow",-1840,-910,68,94)),
            realm("Kingdom of Sablemere", "King Lucan I", "Sablefort", 12540, 2640, 3_250, 3_650, .34, 9_100, 175,
                    s("Nightford",480,-360,580,675), s("Crowmere",-620,390,175,230), s("Obsidian Gate",900,500,1_300,1_480),
                    s("Duskwater",-990,-520,320,395), s("Blackreach",1340,-730,1_490,1_700), s("Sablewatch",1470,310,485,570),
                    s("Moonbrook",400,960,116,158), s("Westshade",-1430,760,720,830), s("Greyfield",1750,1030,260,325),
                    s("Crowhollow",-1860,-900,74,102)),
            realm("Kingdom of Verdance", "Queen Ilyra IV", "Greenwall", -12540, 2750, 3_100, 3_520, .26, 8_200, 145,
                    s("Oakford",500,-370,550,640), s("Fernmere",-630,400,160,215), s("Greenbridge",910,510,1_220,1_390),
                    s("Meadowrun",-1000,-530,305,375), s("Elderwood",1350,-720,1_450,1_650), s("Leafwatch",1480,300,455,535),
                    s("Willowbrook",410,970,108,148), s("Westgrove",-1420,770,695,805), s("Springfield",1740,1040,250,315),
                    s("Mossgrove",-1850,-910,70,98)),
            realm("Kingdom of Aurenthal", "Queen Seraphine II", "Aurora Keep", 5940, 14300, 4_100, 4_600, .39, 10_200, 190,
                    s("Goldmere",520,-420,760,870), s("Suncrest",-650,390,210,270), s("Brightgate",940,520,1_520,1_720),
                    s("Amberbrook",-980,-560,360,430), s("Lionswatch",1380,-730,1_740,1_960), s("Eastcrown",1510,330,570,660),
                    s("Rosefield",430,980,140,185), s("Westlight",-1450,790,820,930), s("Summerford",1790,1060,300,370),
                    s("Dewhollow",-1900,-940,82,112)),
            realm("Redmarch Crown", "King Garran III", "Red Citadel", -18700, -4620, 3_700, 4_150, .37, 9_700, 205,
                    s("Crimson Ford",500,-380,690,790), s("Marchfield",-640,400,185,245), s("Warwick",920,510,1_460,1_650),
                    s("Ashmere",-1010,-540,330,405), s("Redwall",1370,-720,1_680,1_900), s("Spearwatch",1490,320,540,625),
                    s("Hearthbrook",420,970,125,170), s("Westmarch",-1440,780,770,885), s("Bannerfield",1770,1040,280,350),
                    s("Boarhollow",-1880,-920,78,108)),
            realm("Stormcoast Kingdom", "Queen Rhea I", "Stormhaven", 18040, 11440, 3_950, 4_420, .41, 10_800, 185,
                    s("Seaford",510,-390,720,830), s("Rainmere",-640,390,190,250), s("Gullwatch",930,520,1_410,1_610),
                    s("Saltfield",-1000,-550,350,425), s("Thunderbay",1360,-720,1_720,1_930), s("Cliffgate",1500,320,560,650),
                    s("Tidebrook",420,970,132,178), s("Westport",-1450,780,800,915), s("Stormfield",1780,1050,290,360),
                    s("Mistwood",-1890,-930,80,110)),
            realm("Kingdom of Glassmere", "King Caelan II", "Glasskeep", -5500, 15840, 3_500, 3_980, .43, 11_000, 170,
                    s("Clearwater",520,-410,650,750), s("Mirrorfen",-650,400,180,240), s("Crystal Gate",940,510,1_380,1_570),
                    s("Silverfield",-990,-550,340,415), s("Highglass",1370,-720,1_610,1_830), s("Prismwatch",1490,320,520,610),
                    s("Bluebrook",420,970,118,162), s("Westmirror",-1440,780,750,865), s("Shardfield",1760,1040,270,340),
                    s("Quietglass",-1880,-920,74,104))
    );

    /** Returns the number of canonical changes performed. */
    public static int ensureStarterDensity(SimulationState state) {
        Objects.requireNonNull(state, "state");
        int changes = 0;
        for (RealmSpec spec : REALMS) changes += ensureRealm(state, spec);
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

    private static int ensureRealm(SimulationState state, RealmSpec spec) {
        Faction faction = faction(state, spec.realmName());
        int changes = 0;
        if (faction == null) {
            faction = new Faction(state.nextId(), spec.realmName(), spec.ruler());
            faction.restoreTechnology(spec.technology());
            faction.restoreTreasury(spec.treasury());
            Settlement capital = new Settlement(state.nextId(), spec.capitalName(),
                    new SimPosition(spec.x(), spec.z()), spec.capitalPopulation(), spec.capitalHousing(),
                    SettlementOrigin.AUTHORED_SEED, false, DevelopmentMode.AUTO);
            faction.addSettlement(capital);
            faction.addArmy(new Army(state.nextId(), faction.id(), new SimPosition(spec.x() + 55, spec.z() + 35), spec.armyInfantry()));
            state.addFaction(faction);
            provision(faction, 4);
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
    private static int addStarterSatellite(SimulationState state, Faction faction, SimPosition origin, List<Spec> specs) {
        if (specs.isEmpty()) return 0;
        if (faction.settlements().size() >= TARGET_SETTLEMENTS_PER_REALM) return 0;
        // Already has a non-capital, non-rural settlement — treat as satellite present.
        long nonRuralExtras = faction.settlements().stream()
                .filter(s -> !s.name().equals(faction.settlements().getFirst().name()))
                .filter(s -> !isRuralHamletName(s.name()))
                .count();
        if (nonRuralExtras > 0) return 0;

        int pick = Math.floorMod((int) mix(state.seed() ^ faction.id()), specs.size());
        Spec spec = specs.get(pick);
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
    private static Spec s(String name,double dx,double dz,int population,int housing){return new Spec(name,dx,dz,population,housing);}
    private static RealmSpec realm(String realmName,String ruler,String capitalName,double x,double z,int capitalPopulation,int capitalHousing,double technology,double treasury,int armyInfantry,Spec... satellites){return new RealmSpec(realmName,ruler,capitalName,x,z,capitalPopulation,capitalHousing,technology,treasury,armyInfantry,List.of(satellites));}

    /** Capitals plus every authored Spec name (including expansion-catalog Specs not yet seeded). */
    public static List<String> authoredSettlementNames(){
        List<String> names=new ArrayList<>();
        for(RealmSpec realm:REALMS){
            names.add(realm.capitalName());
            for(Spec spec:realm.satellites())names.add(spec.name());
        }
        return List.copyOf(names);
    }

    /** Expansion catalog Specs available for later causal founding (not all seeded at start). */
    public static List<String> authoredExpansionCatalogNames(){
        List<String> names=new ArrayList<>();
        for(RealmSpec realm:REALMS){
            for(Spec spec:realm.satellites())names.add(spec.name());
        }
        return List.copyOf(names);
    }

    /** Authored Specs for a realm that are not yet present as settlements. */
    public static List<Spec> unusedExpansionSpecs(Faction faction, String realmName) {
        RealmSpec realm = null;
        for (RealmSpec r : REALMS) if (r.realmName().equals(realmName)) { realm = r; break; }
        if (realm == null) return List.of();
        List<Spec> unused = new ArrayList<>();
        for (Spec spec : realm.satellites()) {
            if (settlement(faction, spec.name()) == null) unused.add(spec);
        }
        return List.copyOf(unused);
    }

    public record Spec(String name,double dx,double dz,int population,int housing) {}
    private record RealmSpec(String realmName,String ruler,String capitalName,double x,double z,int capitalPopulation,int capitalHousing,double technology,double treasury,int armyInfantry,List<Spec> satellites) {}
}
