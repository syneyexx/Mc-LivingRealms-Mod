package dev.livingrealms.sim.world;

import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Idempotent starter-world/kingdom migration.
 *
 * <p>Twelve persistent kingdoms each keep a capital plus every authored Spec satellite and a small
 * rural top-up. Far-world continuity beyond the authored belt is handled by
 * {@link FrontierExplorationSeeder}. Physical construction remains chunk-local. Completion keys are
 * never written here — only the materializer (or explicit foreign adoption) may complete structures.</p>
 */
public final class SettlementDensitySeeder {
    private SettlementDensitySeeder() {}
    /**
     * Capital + 10 authored Specs + 2 rural hamlets. Wizard Trees are separate and excluded from
     * surface-density accounting.
     */
    public static final int TARGET_SETTLEMENTS_PER_REALM = 13;
    /** Every Spec in {@link #REALMS} becomes a real settlement — no silent amputation. */
    public static final int MAX_AUTHORED_SATELLITES = 10;
    public static final int RURAL_HAMLETS_PER_REALM = 2;
    /**
     * Minimum clearance between settlements. 800 blocks keeps footprints from stacking while still
     * allowing all 10 authored Specs around each capital (a 2000m ring cannot hold 10 Specs).
     */
    public static final double MIN_SETTLEMENT_SPACING = 800.0;
    /** Surface starter total excluding Wizard Trees: 12 × TARGET. */
    public static final int SURFACE_STARTER_SETTLEMENTS = 156;
    private static final String[] RURAL_SUFFIXES = {"Croft","End","Green","Thorp","Wick","Fold","Ley","Combe"};
    private static final String[] FRONTIER_SUFFIXES = {"Millbrook","Pinecross","Ridgeham","Littlemere","Eastwick","Westfield","Northstead","Southmere","Foxbridge","Riverwatch","Greenhollow","Stonefield","Ashbrook","Kingsford","Meadowgate","Oakfield","Rosemere","Hillcross","Brighton","Westwick","Eastmere","Northfield","Southwatch","Brookstead","Pineford","Stoneham","Rivergate","Greenmere","Foxfield","Willowcross"};

    private static final List<RealmSpec> REALMS = List.of(
            realm("Kingdom of Aster", "Queen Elara I", "Asterhold", 0, 0, 3_300, 3_700, .28, 8_500, 150,
                    s("Willowmere",460,-310,430,500), s("Thornfield",-560,360,95,130), s("Greyford",860,470,920,1_050),
                    s("Sunreach",-980,-470,210,270), s("Highmere",1280,-690,1_420,1_600), s("Oakwatch",1390,250,520,610),
                    s("Brookhollow",420,910,82,115), s("Stonebridge",-1380,780,760,860), s("Dawnfield",1760,930,310,380),
                    s("Ravenwood",-1820,-850,62,88)),
            realm("Veyran Dominion", "King Oren IV", "Veyra", 2400, 300, 3_600, 4_000, .32, 9_200, 180,
                    s("Blackwater",-520,470,1_040,1_180), s("Redhaven",610,-390,360,430), s("Stonecross",960,420,1_480,1_660),
                    s("Emberfield",260,930,120,160), s("Ironvale",1420,-520,1_920,2_180), s("Duskmere",1650,590,560,650),
                    s("Eastgate",930,1130,88,125), s("Ashford",-1090,-760,680,780), s("Kestrel",-1510,660,260,330),
                    s("Northreach",1980,1020,58,82)),
            realm("Kingdom of Eldermere", "King Aldren II", "Elderkeep", -3000, 850, 3_150, 3_550, .24, 7_900, 135,
                    s("Mossford",520,-380,460,540), s("Bellwater",-630,410,150,210), s("Kingsmead",900,520,1_180,1_330),
                    s("Lowfen",-980,-560,280,350), s("Whitegrove",1320,-740,780,910), s("Rosewatch",1460,320,390,470),
                    s("Alderbrook",420,980,105,145), s("Westmere",-1450,760,640,740), s("Goldfield",1740,1040,220,285),
                    s("Dunhollow",-1880,-920,72,98)),
            realm("Kingdom of Solenne", "Queen Maris III", "Solspire", 650, -3200, 3_850, 4_250, .36, 9_800, 165,
                    s("Brightwater",480,-360,520,610), s("Sunford",-610,380,170,225), s("Valecross",870,500,1_260,1_430),
                    s("Amberfield",-960,-510,310,380), s("Highsun",1300,-720,1_520,1_730), s("Dawnwatch",1440,290,470,555),
                    s("Larkbrook",390,940,110,150), s("Southgate",-1390,740,700,815), s("Cinderfield",1710,1010,245,310),
                    s("Quietmere",-1810,-880,65,92)),
            realm("Dravik Iron Kingdom", "King Borin V", "Dravengard", 3650, -2850, 3_300, 3_720, .42, 10_500, 210,
                    s("Ironford",500,-400,610,700), s("Hammerfall",-650,360,190,250), s("Stonegate",910,490,1_360,1_520),
                    s("Coalbrook",-1020,-540,350,430), s("Anvilreach",1360,-700,1_600,1_820), s("Redcliff",1490,310,530,620),
                    s("Deepwell",420,960,125,170), s("Westforge",-1420,770,790,900), s("Steelmead",1760,1020,270,340),
                    s("Blackpine",-1860,-900,76,105)),
            realm("High Kingdom of Norwyn", "Queen Astrid II", "Northcrown", -3700, -2850, 3_050, 3_480, .29, 8_400, 160,
                    s("Frostford",480,-390,520,610), s("Pinewatch",-620,390,145,200), s("Ravenpass",890,520,1_100,1_260),
                    s("Snowmere",-970,-540,295,365), s("Highcliff",1310,-710,1_380,1_570), s("Wolfgate",1450,300,430,510),
                    s("Icebrook",410,950,98,138), s("Westwatch",-1400,760,670,780), s("Winterfield",1730,1030,235,300),
                    s("Firhollow",-1840,-910,68,94)),
            realm("Kingdom of Sablemere", "King Lucan I", "Sablefort", 5700, 1200, 3_250, 3_650, .34, 9_100, 175,
                    s("Nightford",480,-360,580,675), s("Crowmere",-620,390,175,230), s("Obsidian Gate",900,500,1_300,1_480),
                    s("Duskwater",-990,-520,320,395), s("Blackreach",1340,-730,1_490,1_700), s("Sablewatch",1470,310,485,570),
                    s("Moonbrook",400,960,116,158), s("Westshade",-1430,760,720,830), s("Greyfield",1750,1030,260,325),
                    s("Crowhollow",-1860,-900,74,102)),
            realm("Kingdom of Verdance", "Queen Ilyra IV", "Greenwall", -5700, 1250, 3_100, 3_520, .26, 8_200, 145,
                    s("Oakford",500,-370,550,640), s("Fernmere",-630,400,160,215), s("Greenbridge",910,510,1_220,1_390),
                    s("Meadowrun",-1000,-530,305,375), s("Elderwood",1350,-720,1_450,1_650), s("Leafwatch",1480,300,455,535),
                    s("Willowbrook",410,970,108,148), s("Westgrove",-1420,770,695,805), s("Springfield",1740,1040,250,315),
                    s("Mossgrove",-1850,-910,70,98)),
            realm("Kingdom of Aurenthal", "Queen Seraphine II", "Aurora Keep", 2700, 6500, 4_100, 4_600, .39, 10_200, 190,
                    s("Goldmere",520,-420,760,870), s("Suncrest",-650,390,210,270), s("Brightgate",940,520,1_520,1_720),
                    s("Amberbrook",-980,-560,360,430), s("Lionswatch",1380,-730,1_740,1_960), s("Eastcrown",1510,330,570,660),
                    s("Rosefield",430,980,140,185), s("Westlight",-1450,790,820,930), s("Summerford",1790,1060,300,370),
                    s("Dewhollow",-1900,-940,82,112)),
            realm("Redmarch Crown", "King Garran III", "Red Citadel", -8500, -2100, 3_700, 4_150, .37, 9_700, 205,
                    s("Crimson Ford",500,-380,690,790), s("Marchfield",-640,400,185,245), s("Warwick",920,510,1_460,1_650),
                    s("Ashmere",-1010,-540,330,405), s("Redwall",1370,-720,1_680,1_900), s("Spearwatch",1490,320,540,625),
                    s("Hearthbrook",420,970,125,170), s("Westmarch",-1440,780,770,885), s("Bannerfield",1770,1040,280,350),
                    s("Boarhollow",-1880,-920,78,108)),
            realm("Stormcoast Kingdom", "Queen Rhea I", "Stormhaven", 8200, 5200, 3_950, 4_420, .41, 10_800, 185,
                    s("Seaford",510,-390,720,830), s("Rainmere",-640,390,190,250), s("Gullwatch",930,520,1_410,1_610),
                    s("Saltfield",-1000,-550,350,425), s("Thunderbay",1360,-720,1_720,1_930), s("Cliffgate",1500,320,560,650),
                    s("Tidebrook",420,970,132,178), s("Westport",-1450,780,800,915), s("Stormfield",1780,1050,290,360),
                    s("Mistwood",-1890,-930,80,110)),
            realm("Kingdom of Glassmere", "King Caelan II", "Glasskeep", -2500, 7200, 3_500, 3_980, .43, 11_000, 170,
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
        changes += repairGlobalSpacing(state);
        changes += initializeRelations(state);
        if (changes > 0) {
            state.history().add(new WorldEvent(state.clock().day(), "living_world_network_expanded",
                    "Twelve-realm authored network ensured (capital+10 Specs+rural); realms=" + state.factions().size() + ", settlements="
                            + state.factions().stream().mapToInt(f -> f.settlements().size()).sum()));
        }
        return changes;
    }

    /** Public spacing pass for post-wizard / migration hooks. Returns 0 when already legal. */
    public static int enforceSpacing(SimulationState state) {
        Objects.requireNonNull(state, "state");
        return repairGlobalSpacing(state);
    }

    /**
     * Capitals are authored in parallel rings; a later capital can land inside an earlier satellite's
     * clearance. Snap the smaller settlement onto an expanding clearance ring around the larger one.
     * Idempotent once the lattice is already legal.
     */
    private static int repairGlobalSpacing(SimulationState state) {
        List<Settlement> all = new ArrayList<>();
        for (Faction f : state.factions()) all.addAll(f.settlements());
        int changes = 0;
        for (int pass = 0; pass < 256; pass++) {
            Settlement move = null, keep = null;
            double worst = 0;
            for (int i = 0; i < all.size(); i++) {
                for (int j = i + 1; j < all.size(); j++) {
                    Settlement a = all.get(i), b = all.get(j);
                    double dist = a.position().distanceTo(b.position());
                    if (dist >= MIN_SETTLEMENT_SPACING) continue;
                    double slack = MIN_SETTLEMENT_SPACING - dist;
                    if (slack <= worst) continue;
                    worst = slack;
                    if (a.population() < b.population() || (a.population() == b.population() && a.id() > b.id())) {
                        move = a;
                        keep = b;
                    } else {
                        move = b;
                        keep = a;
                    }
                }
            }
            if (move == null) break;
            long m = mix(state.seed() ^ move.id() ^ keep.id() ^ (pass * 0x9E3779B97F4A7C15L));
            double baseAng = ((m >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
            boolean placed = false;
            for (int ring = 0; ring < 24 && !placed; ring++) {
                double radius = MIN_SETTLEMENT_SPACING + 80.0 + ring * 120.0;
                for (int attempt = 0; attempt < 16; attempt++) {
                    double a = baseAng + attempt * (Math.PI * 2.0 / 16.0) + ring * 0.37;
                    SimPosition candidate = new SimPosition(
                            keep.position().x() + Math.cos(a) * radius,
                            keep.position().z() + Math.sin(a) * radius);
                    if (!tooCloseExcept(all, candidate, move.id(), MIN_SETTLEMENT_SPACING)) {
                        move.relocate(candidate);
                        placed = true;
                        break;
                    }
                }
            }
            if (!placed) {
                // Last resort: park far along a deterministic vector so later passes can separate the rest.
                double radius = MIN_SETTLEMENT_SPACING + 2_400.0 + (pass % 7) * 180.0;
                move.relocate(new SimPosition(
                        keep.position().x() + Math.cos(baseAng) * radius,
                        keep.position().z() + Math.sin(baseAng) * radius));
            }
            changes++;
        }
        return changes > 0 ? 1 : 0;
    }

    private static boolean tooCloseExcept(List<Settlement> all, SimPosition p, long ignoreId, double spacing) {
        for (Settlement s : all) {
            if (s.id() == ignoreId) continue;
            if (p.distanceTo(s.position()) < spacing) return true;
        }
        return false;
    }

    private static int ensureRealm(SimulationState state, RealmSpec spec) {
        Faction faction = faction(state, spec.realmName());
        int changes = 0;
        if (faction == null) {
            faction = new Faction(state.nextId(), spec.realmName(), spec.ruler());
            faction.restoreTechnology(spec.technology());
            faction.restoreTreasury(spec.treasury());
            Settlement capital = new Settlement(state.nextId(), spec.capitalName(), new SimPosition(spec.x(), spec.z()), spec.capitalPopulation(), spec.capitalHousing());
            faction.addSettlement(capital);
            faction.addArmy(new Army(state.nextId(), faction.id(), new SimPosition(spec.x() + 55, spec.z() + 35), spec.armyInfantry()));
            state.addFaction(faction);
            provision(faction, 12);
            changes++;
        }
        Settlement capital = settlement(faction, spec.capitalName());
        if (capital == null) {
            capital = new Settlement(state.nextId(), spec.capitalName(), new SimPosition(spec.x(), spec.z()), spec.capitalPopulation(), spec.capitalHousing());
            faction.addSettlement(capital);
            changes++;
        } else {
            changes += ensureCapital(capital, spec.capitalPopulation(), spec.capitalHousing());
        }
        int added = addSatellites(state, faction, capital.position(), spec.satellites());
        added += addRuralHamlets(state,faction,capital.position(),spec.capitalName());
        if (added > 0) { provision(faction, added); changes += added; }
        return changes;
    }

    private static int ensureCapital(Settlement settlement, int minPopulation, int minHousing) {
        int beforePopulation = settlement.population(), beforeHousing = settlement.housing();
        if (beforePopulation < minPopulation) settlement.addPopulation(minPopulation - beforePopulation);
        if (beforeHousing < minHousing) settlement.addHousing(minHousing - beforeHousing);
        return beforePopulation == settlement.population() && beforeHousing == settlement.housing() ? 0 : 1;
    }

    private static int addSatellites(SimulationState state, Faction faction, SimPosition origin, List<Spec> specs) {
        int added = 0;
        int limit = Math.min(specs.size(), MAX_AUTHORED_SATELLITES);
        for (int i = 0; i < limit; i++) {
            Spec spec = specs.get(i);
            if (settlement(faction, spec.name()) != null) continue;
            SimPosition position = jittered(state.seed(), faction.id(), i, origin, spec.dx(), spec.dz());
            position = avoidCrowding(state, position, faction.id(), i);
            if (tooCloseAny(state, position, MIN_SETTLEMENT_SPACING)) {
                position = ringSlot(state, origin, faction.id(), i, MIN_SETTLEMENT_SPACING + 120.0 + i * 140.0);
            }
            faction.addSettlement(new Settlement(state.nextId(), spec.name(), position, spec.population(), spec.housing()));
            added++;
        }
        return added;
    }

    private static int addFrontierSettlements(SimulationState state,Faction faction,SimPosition origin,String capitalName){
        // Reserve slots so rural hamlets are not crowded out by the frontier ring.
        int needed=Math.max(0,TARGET_SETTLEMENTS_PER_REALM-RURAL_HAMLETS_PER_REALM-faction.settlements().size()),added=0;
        int baseIndex=faction.settlements().size();
        // Skew frontier toward towns/villages/hamlets rather than extra cities.
        int[] populations={1_450,850,620,420,310,260,180,145,96,82,560,220};
        String prefix=capitalName.replace(" Keep","").replace("keep","").replace(" Citadel","").replace("haven","").trim();
        for(int attempt=0;added<needed&&attempt<needed*3;attempt++){
            String name=(prefix+" "+FRONTIER_SUFFIXES[Math.floorMod(attempt,FRONTIER_SUFFIXES.length)]).trim();
            if(attempt>=FRONTIER_SUFFIXES.length)name=(prefix+" "+FRONTIER_SUFFIXES[Math.floorMod(attempt,FRONTIER_SUFFIXES.length)]+" "+(attempt/FRONTIER_SUFFIXES.length+1)).trim();
            if(settlement(faction,name)!=null)continue;
            double angle=(baseIndex+attempt)*2.399963229728653;
            // Keep frontier ring outside capital clearance so wilderness belts stay founding-viable.
            double radius = MIN_SETTLEMENT_SPACING + 200.0 + (attempt % 5) * 180.0 + (attempt / 5) * 140.0;
            SimPosition position=new SimPosition(origin.x()+Math.cos(angle)*radius,origin.z()+Math.sin(angle)*radius);
            position=avoidCrowding(state,position,faction.id(),100+attempt);
            int pop=populations[Math.floorMod(attempt,populations.length)],housing=(int)Math.ceil(pop*1.13);
            faction.addSettlement(new Settlement(state.nextId(),name,position,pop,housing));added++;
        }
        return added;
    }

    /**
     * Places small rural hamlets toward fertile ecology regions (temperate forest / grassland proxies).
     * Seed-deterministic and idempotent by settlement name; does not replace the authored realm list.
     */
    private static int addRuralHamlets(SimulationState state,Faction faction,SimPosition origin,String capitalName){
        // Dense / already-migrated realms at or above target must not quietly grow.
        if(faction.settlements().size()>=TARGET_SETTLEMENTS_PER_REALM)return 0;
        int existingRural=(int)faction.settlements().stream().filter(s->isRuralHamletName(s.name())).count();
        int needed=Math.max(0,RURAL_HAMLETS_PER_REALM-existingRural);
        needed=Math.min(needed,Math.max(0,TARGET_SETTLEMENTS_PER_REALM-faction.settlements().size()));
        if(needed<=0)return 0;
        int added=0;
        String prefix=capitalName.replace(" Keep","").replace("keep","").replace(" Citadel","").replace("haven","").trim();
        for(int attempt=0;added<needed&&attempt<needed*4;attempt++){
            int suffixIndex=existingRural+attempt;
            String name=(prefix+" "+RURAL_SUFFIXES[Math.floorMod(suffixIndex,RURAL_SUFFIXES.length)]).trim();
            if(suffixIndex>=RURAL_SUFFIXES.length)name=name+" "+(suffixIndex/RURAL_SUFFIXES.length+1);
            if(settlement(faction,name)!=null)continue;
            SimPosition anchor = origin;
            double angle = (attempt + 3 + existingRural) * 2.399963229728653;
            double radius = MIN_SETTLEMENT_SPACING + 1_400.0 + (attempt % 3) * 220.0;
            SimPosition position = new SimPosition(anchor.x() + Math.cos(angle) * radius, anchor.z() + Math.sin(angle) * radius);
            position = avoidCrowding(state, position, faction.id(), 400 + attempt);
            if (tooCloseAny(state, position, MIN_SETTLEMENT_SPACING)) {
                position = ringSlot(state, origin, faction.id(), 50 + attempt, MIN_SETTLEMENT_SPACING + 1_600.0 + attempt * 200.0);
            }
            int pop=48+Math.floorMod((int)mix(state.seed()^faction.id()^(attempt*17L)),40);
            // Empty completion — farms/pastures/wells require materializer receipts.
            Settlement hamlet=new Settlement(state.nextId(),name,position,pop,(int)Math.ceil(pop*1.2));
            faction.addSettlement(hamlet);added++;
        }
        return added;
    }

    /** Whole-word rural suffix only — avoids false positives like "Eastwick" / "Greenhollow". */
    private static boolean isRuralHamletName(String name){
        for(String suffix:RURAL_SUFFIXES){
            if(name.endsWith(" "+suffix))return true;
            String marker=" "+suffix+" ";
            int idx=name.lastIndexOf(marker);
            if(idx>=0){
                String rest=name.substring(idx+marker.length());
                if(!rest.isEmpty()&&rest.chars().allMatch(Character::isDigit))return true;
            }
        }
        return false;
    }

    private static SimPosition avoidCrowding(SimulationState state, SimPosition initial, long factionId, int index) {
        final double spacing = MIN_SETTLEMENT_SPACING;
        SimPosition p = initial;
        for (int attempt = 0; attempt < 32; attempt++) {
            Settlement nearest = null;
            double best = Double.POSITIVE_INFINITY;
            for (Faction f : state.factions()) {
                for (Settlement s : f.settlements()) {
                    double d = p.distanceTo(s.position());
                    if (d < best) {
                        best = d;
                        nearest = s;
                    }
                }
            }
            if (nearest == null || best >= spacing) return p;
            double dx = p.x() - nearest.position().x(), dz = p.z() - nearest.position().z();
            if (dx * dx + dz * dz < 1.0) {
                long m = mix(state.seed() ^ factionId ^ (index * 31L + attempt));
                double a = ((m >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2;
                dx = Math.cos(a);
                dz = Math.sin(a);
            }
            double len = Math.max(1.0, Math.hypot(dx, dz));
            double push = spacing - best + 120.0 + attempt * 40.0;
            p = new SimPosition(p.x() + dx / len * push, p.z() + dz / len * push);
        }
        return p;
    }

    private static boolean tooCloseAny(SimulationState state, SimPosition p, double spacing) {
        for (Faction f : state.factions()) {
            for (Settlement s : f.settlements()) {
                if (p.distanceTo(s.position()) < spacing) return true;
            }
        }
        return false;
    }

    private static SimPosition ringSlot(SimulationState state, SimPosition origin, long factionId, int index, double baseRadius) {
        long m = mix(state.seed() ^ factionId ^ (index * 0x9E3779B97F4A7C15L));
        double baseAng = ((m >>> 11) & 0xFFFFL) / 65535.0 * Math.PI * 2.0 + index * 2.399963229728653;
        for (int ring = 0; ring < 20; ring++) {
            double radius = baseRadius + ring * 160.0;
            for (int attempt = 0; attempt < 12; attempt++) {
                double a = baseAng + attempt * (Math.PI * 2.0 / 12.0);
                SimPosition candidate = new SimPosition(origin.x() + Math.cos(a) * radius, origin.z() + Math.sin(a) * radius);
                if (!tooCloseAny(state, candidate, MIN_SETTLEMENT_SPACING)) return candidate;
            }
        }
        double radius = baseRadius + 3_200.0;
        return new SimPosition(origin.x() + Math.cos(baseAng) * radius, origin.z() + Math.sin(baseAng) * radius);
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

    private static SimPosition jittered(long seed, long factionId, int index, SimPosition origin, double dx, double dz) {
        // Scale authored offsets out so even the first satellite ring clears MIN_SETTLEMENT_SPACING.
        double scale = Math.max(1.0, MIN_SETTLEMENT_SPACING / Math.max(1.0, Math.hypot(dx, dz)));
        if (scale < 1.15) scale = 1.15;
        long mixed = mix(seed ^ factionId * 0x9E3779B97F4A7C15L ^ (long) (index + 1) * 0xD1B54A32D192ED03L);
        double xJitter = (((mixed >>> 11) & 0x3FFL) / 1023.0 - .5) * 48.0;
        double zJitter = (((mixed >>> 31) & 0x3FFL) / 1023.0 - .5) * 48.0;
        return new SimPosition(origin.x() + dx * scale + xJitter, origin.z() + dz * scale + zJitter);
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

    /** Every authored capital + Spec name that must exist after densifier. */
    public static List<String> authoredSettlementNames(){
        List<String> names=new ArrayList<>();
        for(RealmSpec realm:REALMS){
            names.add(realm.capitalName());
            for(Spec spec:realm.satellites())names.add(spec.name());
        }
        return List.copyOf(names);
    }

    private record Spec(String name,double dx,double dz,int population,int housing) {}
    private record RealmSpec(String realmName,String ruler,String capitalName,double x,double z,int capitalPopulation,int capitalHousing,double technology,double treasury,int armyInfantry,List<Spec> satellites) {}
}
