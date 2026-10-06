package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.content.BuildingDefinition;
import dev.livingrealms.sim.content.BuildingTemplateRegistry;
import dev.livingrealms.sim.content.SettlementIdentityProfile;
import dev.livingrealms.sim.faction.DevelopmentMode;
import dev.livingrealms.sim.faction.DevelopmentModeGuard;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.faction.SettlementOrigin;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic settlement planner. Street morphology is derived from geography, tier and capital
 * status via {@link SettlementMorphology}. Connectivity remains walkable and street-facing;
 * variation comes from spines, rings, radials and organic offsets rather than one universal grid.
 */
public final class SettlementPlanner {
    private SettlementPlanner() {}

    public static List<ConstructionIntent> plan(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        SettlementMorphology morph = SettlementMorphology.derive(faction, settlement);
        CultureArchitectureProfile cultureProfile = CultureArchitectureProfile.derive(faction, settlement);
        CultureArchitectureProfile.bind(settlement.id(), cultureProfile.architecture());
        List<ConstructionIntent> out = new ArrayList<>();
        int tier = settlement.tier().ordinal();
        int baseRotation = Math.floorMod((int) mix(settlement.id() ^ 0x4F1BBCDCBFA54001L), 2);

        SettlementStreetGraph baseStreetGraph = SettlementRoadPlanner.planGraph(
                faction, settlement, morph, baseRotation);
        SettlementBoundary boundary = null;
        SettlementStreetGraph streetGraph = baseStreetGraph;
        if (tier >= Settlement.Tier.CITY.ordinal()) {
            boundary = SettlementBoundary.plan(faction, settlement, baseStreetGraph, baseRotation);
            streetGraph = baseStreetGraph.withAdditionalSegments(boundary.approachSegments());
        }
        SettlementRoadPlanner.addRoadNetwork(out, faction, settlement, streetGraph);

        boolean capital = settlement.role() == dev.livingrealms.sim.faction.SettlementRole.CAPITAL
                || faction.settlements().stream()
                .max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id))
                .map(s -> s.id() == settlement.id()).orElse(false);
        boolean playerFounded = settlement.origin() == SettlementOrigin.PLAYER_FOUNDED;
        // Player-founded camps start with a town hall charter — not an instant castle.
        // Keep/castle unlocks at TOWN+ (or later) for player realms; authored capitals still get KEEP.
        if (playerFounded) {
            SimPosition hall = civicPoint(settlement, morph, StructureRole.TOWN_HALL, baseRotation);
            addAt(out, faction, settlement, StructureRole.TOWN_HALL, 0, hall, 11, 9, baseRotation, 160);
            if (tier >= Settlement.Tier.TOWN.ordinal()) {
                int keepW = tier >= Settlement.Tier.CITY.ordinal() ? 23 : 19;
                int keepD = tier >= Settlement.Tier.CITY.ordinal() ? 21 : 17;
                SimPosition keep = civicPoint(settlement, morph, StructureRole.KEEP, baseRotation);
                addAt(out, faction, settlement, StructureRole.KEEP, 0, keep, keepW, keepD, baseRotation, 140);
            }
        } else {
            int keepW = capital && tier >= Settlement.Tier.CITY.ordinal() ? 31 : capital ? 23 : tier >= Settlement.Tier.TOWN.ordinal() ? 19 : 15;
            int keepD = capital && tier >= Settlement.Tier.CITY.ordinal() ? 27 : capital ? 21 : tier >= Settlement.Tier.TOWN.ordinal() ? 17 : 15;
            SimPosition keep = civicPoint(settlement, morph, StructureRole.KEEP, baseRotation);
            addAt(out, faction, settlement, StructureRole.KEEP, 0, keep, keepW, keepD, baseRotation, capital ? 190 : 120);
        }

        SettlementHousingPlanner.addHousing(out, faction, settlement, morph, baseRotation, streetGraph, cultureProfile.architecture());
        addFarms(out, faction, settlement, morph, baseRotation);
        addPastures(out, faction, settlement, morph, baseRotation);

        if (tier >= Settlement.Tier.HAMLET.ordinal()) addCivic(out, faction, settlement, morph, baseRotation, StructureRole.WELL, 0, 5, 5, 116);
        if (tier >= Settlement.Tier.VILLAGE.ordinal()) {
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.IRRIGATION, 0, 7, 31, 74);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.MILL, 0, 11, 11, 93);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.MARKET, 0, 15, 13, 122);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.PLAZA, 0, 13, 13, 118);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.WAREHOUSE, 0, 13, 11, 91);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.TAVERN, 0, 13, 11, 94);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.TEMPLE, 0, 13, 15, 88);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.BAKERY, 0, 11, 9, 90);
        }
        if (tier >= Settlement.Tier.TOWN.ordinal()) {
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.BARRACKS, 0, 15, 11, 104);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.WORKSHOP, 0, 13, 11, 92);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.BREWERY, 0, 13, 11, 88);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.CLINIC, 0, 13, 11, 90);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.SCHOOL, 0, 15, 11, 86);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.COURTHOUSE, 0, 15, 13, 99);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.PRISON, 0, 13, 13, 96);
        }
        if (tier >= Settlement.Tier.CITY.ordinal()) {
            if (faction.technology() >= .55) addCivic(out, faction, settlement, morph, baseRotation, StructureRole.AQUEDUCT, 0, 7, 41, 79);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.ORPHANAGE, 0, 15, 13, 79);
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.MONUMENT, 0, 9, 9, 76);
            if (faction.technology() >= .55) addCivic(out, faction, settlement, morph, baseRotation, StructureRole.OBSERVATORY, 0, 15, 15, 72);
            addCityWalls(out, faction, settlement, Objects.requireNonNull(boundary, "boundary"));
        }
        if (tier >= Settlement.Tier.TOWN.ordinal() && faction.technology() >= .35) {
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.FACTORY, 0, 19, 15, 78);
        }
        if (tier >= Settlement.Tier.VILLAGE.ordinal() && settlement.geography().shipSuitable()) {
            addCivic(out, faction, settlement, morph, baseRotation, StructureRole.DOCK, 0, 17, 11, 118);
        }
        if (tier >= Settlement.Tier.CITY.ordinal() && faction.technology() >= .75) {
            SimPosition edge = local(settlement, baseRotation, 132, -96);
            addAt(out, faction, settlement, StructureRole.AIRFIELD, 0, edge, 25, 70, baseRotation, 64);
        }

        out.replaceAll(intent -> intent.withPriority(
                adjustPriority(settlement.developmentPriority(), intent.role(), intent.priority())));
        out.sort(Comparator.comparingInt(ConstructionIntent::priority).reversed().thenComparing(ConstructionIntent::key));
        return List.copyOf(out);
    }

    /** Deterministic CITY+ boundary view for transport/world projection. */
    public static Optional<SettlementBoundary> boundary(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        if (settlement.tier().ordinal() < Settlement.Tier.CITY.ordinal()) return Optional.empty();
        // Foreign/legacy physical footprints are authoritative. Do not invent graph-era gates for
        // settlements whose core fabric is intentionally excluded from automatic reconciliation.
        if (!SettlementConstructionPolicy.allowsAutomaticCoreFabric(settlement)) return Optional.empty();
        SettlementMorphology morph = SettlementMorphology.derive(faction, settlement);
        int baseRotation = Math.floorMod((int) mix(settlement.id() ^ 0x4F1BBCDCBFA54001L), 2);
        SettlementStreetGraph base = SettlementRoadPlanner.planGraph(faction, settlement, morph, baseRotation);
        return Optional.of(SettlementBoundary.plan(faction, settlement, base, baseRotation));
    }

    public static List<ConstructionIntent> pending(Faction faction, Settlement settlement) {
        return plan(faction, settlement).stream().filter(i -> !settlement.isConstructionCompleted(i.key())).toList();
    }

    public static String layoutArchetype(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        return SettlementMorphology.derive(settlement).wireName();
    }

    public static String layoutArchetype(Faction faction, Settlement settlement) {
        return SettlementMorphology.derive(faction, settlement).wireName();
    }

    private static void addFarms(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                 SettlementMorphology morph, int baseRotation) {
        int farms = Math.min(72, Math.max(2, (int) Math.ceil(settlement.population() / 160.0)));
        int urbanRadius = switch (settlement.tier()) {
            case CAMP -> 48; case HAMLET -> 64; case VILLAGE -> 88; case TOWN -> 132; case CITY -> 188; case METROPOLIS -> 244;
        };
        for (int i = 0; i < farms; i++) {
            int side = i & 3, band = i / 4;
            double along = (band - (farms / 8.0)) * 32.0;
            double edge = urbanRadius + 34 + (band % 2) * 18;
            // Keep farms off the harbor/dock face for coastal towns.
            if (morph == SettlementMorphology.COASTAL_PORT && side == 2) side = 0;
            double lx = switch (side) { case 0 -> edge; case 1 -> -edge; default -> along; };
            double lz = switch (side) { case 2 -> edge; case 3 -> -edge; default -> along; };
            int size = 11 + 2 * Math.floorMod(i + (int) settlement.id(), 3);
            addAt(out, faction, settlement, StructureRole.FARM, i, local(settlement, baseRotation, lx, lz), size, size, baseRotation, 62);
        }
    }

    private static void addPastures(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                    SettlementMorphology morph, int baseRotation) {
        int pastures = Math.min(24, Math.max(1, (int) Math.ceil(settlement.population() / 280.0)));
        int urbanRadius = switch (settlement.tier()) {
            case CAMP -> 48; case HAMLET -> 64; case VILLAGE -> 88; case TOWN -> 132; case CITY -> 188; case METROPOLIS -> 244;
        };
        for (int i = 0; i < pastures; i++) {
            int side = (i + 1) & 3;
            double along = (i - (pastures / 4.0)) * 28.0;
            double edge = urbanRadius + 58 + (i % 2) * 14;
            if (morph == SettlementMorphology.COASTAL_PORT && side == 2) side = 1;
            double lx = switch (side) { case 0 -> edge; case 1 -> -edge; default -> along; };
            double lz = switch (side) { case 2 -> edge; case 3 -> -edge; default -> along; };
            addAt(out, faction, settlement, StructureRole.PASTURE, i, local(settlement, baseRotation, lx, lz), 13, 13, baseRotation, 58);
        }
    }

    private static void addCityWalls(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                     SettlementBoundary boundary) {
        int wallIndex = 0;
        for (SettlementBoundary.WallRun run : boundary.wallRuns()) {
            int length = Math.max(3, (int) Math.round(run.length()) + 1);
            addAt(out, faction, settlement, StructureRole.WALL, wallIndex++,
                    run.center(), 5, length, run.rotationQuarterTurns(), 108);
        }
        int gateIndex = 0;
        for (SettlementBoundary.GateNode gate : boundary.gates()) {
            addAt(out, faction, settlement, StructureRole.GATE, gateIndex++,
                    gate.position(), 11, 7, gate.rotationQuarterTurns(), 150);
        }
    }

    private static void addCivic(List<ConstructionIntent> out, Faction faction, Settlement settlement,
                                 SettlementMorphology morph, int baseRotation, StructureRole role, int index, int w, int d, int priority) {
        SimPosition center = civicPoint(settlement, morph, role, baseRotation);
        addAt(out, faction, settlement, role, index, center, w, d, baseRotation + orientationFor(role), priority);
    }

    private static SimPosition civicPoint(Settlement settlement, SettlementMorphology morph, StructureRole role, int baseRotation) {
        double[] p = switch (role) {
            case KEEP -> new double[]{-18, -18};
            case TOWN_HALL -> new double[]{0, -12};
            case MARKET -> new double[]{18, 18};
            case PLAZA -> new double[]{8, 8};
            case WAREHOUSE -> new double[]{-30, 28};
            case BARRACKS -> new double[]{30, -30};
            case WORKSHOP -> new double[]{54, 18};
            case FACTORY -> new double[]{86, 54};
            case WELL -> new double[]{0, 18};
            case IRRIGATION -> new double[]{92, 44};
            case AQUEDUCT -> new double[]{-126, 32};
            case TAVERN -> new double[]{30, 28};
            case TEMPLE -> new double[]{-32, -30};
            case CLINIC -> new double[]{54, -18};
            case SCHOOL -> new double[]{-54, 18};
            case COURTHOUSE -> new double[]{-18, 54};
            case PRISON -> new double[]{54, 54};
            case ORPHANAGE -> new double[]{-54, 54};
            case MONUMENT -> new double[]{18, 0};
            case OBSERVATORY -> new double[]{-96, -78};
            case MILL -> new double[]{72, -28};
            case BAKERY -> new double[]{42, 42};
            case BREWERY -> new double[]{-42, 42};
            case DOCK -> new double[]{0, 72};
            default -> new double[]{0, 0};
        };
        p = switch (morph) {
            case COASTAL_PORT -> role == StructureRole.DOCK ? new double[]{0, 88}
                    : role == StructureRole.WAREHOUSE ? new double[]{-24, 56}
                    : role == StructureRole.MARKET || role == StructureRole.PLAZA ? new double[]{12, 40}
                    : new double[]{p[0] * 0.85, p[1] * 0.7 + 18};
            case RIVER_TOWN, LINEAR_VALLEY -> new double[]{p[0] * 1.15, p[1] * 0.55};
            case HILL_TOWN -> role == StructureRole.KEEP ? new double[]{0, 0} : new double[]{p[0] * 0.75, p[1] * 0.75};
            case RADIAL_CAPITAL, WALLED_CORE -> new double[]{p[0] * 0.9, p[1] * 0.9};
            case INDUSTRIAL_EDGE -> role == StructureRole.FACTORY || role == StructureRole.WORKSHOP
                    ? new double[]{Math.abs(p[0]) + 40, p[1]} : p;
            case PLANNED_BOULEVARD -> new double[]{p[0] * 0.7, p[1] * 1.1};
            case MARKET_CROSS -> role == StructureRole.MARKET || role == StructureRole.PLAZA ? new double[]{0, 22} : p;
            case ORGANIC_MEDIEVAL -> new double[]{p[0] + organicShift(settlement, role.ordinal()), p[1] + organicShift(settlement, role.ordinal() + 11)};
        };
        return local(settlement, baseRotation, p[0], p[1]);
    }

    private static int orientationFor(StructureRole role) {
        return switch (role) { case BARRACKS, PRISON, OBSERVATORY -> 1; default -> 0; };
    }

    static void addRoad(List<ConstructionIntent> out, Faction f, Settlement s, int i, SimPosition c, int w, int d, int rot, int p) {
        addRoad(out, f, s, i, c, w, d, rot, p, -1);
    }

    static void addRoad(List<ConstructionIntent> out, Faction f, Settlement s, int i, SimPosition c, int w, int d, int rot, int p, int ring) {
        SettlementMorphology morph = SettlementMorphology.derive(s);
        SettlementGrowthLayer layer = ring >= 0
                ? SettlementGrowthLayer.forRing(ring, morph, s.tier().ordinal())
                : SettlementGrowthLayer.HISTORIC_CORE;
        StreetType preferred = layer.preferredStreet();
        boolean market = preferred == StreetType.MARKET_STREET || preferred == StreetType.COMMERCIAL_STREET;
        boolean regional = preferred == StreetType.REGIONAL_ROAD || preferred == StreetType.ROYAL_ROAD;
        StreetType street = StreetType.forWidth(w, false, market, regional);
        // Growth-layer preference may refine the label when it does not inflate the planner's budgeted width.
        if (preferred.width() <= w && preferred.trafficImportance() >= street.trafficImportance()) street = preferred;
        String key = "road:" + street.name().toLowerCase(java.util.Locale.ROOT) + ":" + layer.name().toLowerCase(java.util.Locale.ROOT) + ":" + i;
        out.add(new ConstructionIntent(key, f.id(), s.id(), StructureRole.ROAD, c, Math.max(street.width(), w), d, rot, p));
    }

    private static int houseFacing(int x, int z, int spacing, int baseRotation) {
        int dx = signedStreetDelta(x, spacing), dz = signedStreetDelta(z, spacing);
        int local;
        if (Math.abs(dx) <= Math.abs(dz)) local = dx > 0 ? 3 : 1;
        else local = dz > 0 ? 0 : 2;
        return Math.floorMod(baseRotation + local, 4);
    }

    private static int signedStreetDelta(int value, int spacing) {
        int nearest = (int) Math.round(value / (double) spacing) * spacing;
        return nearest - value;
    }

    private static int distanceToStreet(int value, int spacing) {
        return Math.abs(signedStreetDelta(value, spacing));
    }

    private static int[] spiral(int n) {
        if (n == 0) return new int[]{0, 0};
        int k = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2.0), t = 2 * k + 1, m = t * t;
        t--;
        if (n >= m - t) return new int[]{k - (m - n), -k};
        m -= t;
        if (n >= m - t) return new int[]{-k, -k + (m - n)};
        m -= t;
        if (n >= m - t) return new int[]{-k + (m - n), k};
        return new int[]{k, k - (m - t - n)};
    }

    static SimPosition local(Settlement s, int quarterTurns, double x, double z) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new SimPosition(s.position().x() + x, s.position().z() + z);
            case 1 -> new SimPosition(s.position().x() - z, s.position().z() + x);
            case 2 -> new SimPosition(s.position().x() - x, s.position().z() - z);
            default -> new SimPosition(s.position().x() + z, s.position().z() - x);
        };
    }

    private static int adjustPriority(dev.livingrealms.sim.faction.DevelopmentPriority policy, StructureRole role, int base) {
        int bonus = switch (policy) {
            case BALANCED -> 0;
            case FOOD -> {
                if (role == StructureRole.FARM || role == StructureRole.FISHERY || role == StructureRole.IRRIGATION
                        || role == StructureRole.AQUEDUCT || role == StructureRole.WELL || role == StructureRole.MILL
                        || role == StructureRole.BAKERY || role == StructureRole.PASTURE) {
                    yield 70;
                }
                if (role == StructureRole.PLAZA || role == StructureRole.MONUMENT || role == StructureRole.TAVERN
                        || role == StructureRole.TEMPLE) {
                    yield -45;
                }
                yield 0;
            }
            case HOUSING -> role == StructureRole.HOUSE ? 60 : 0;
            case INDUSTRY -> (role == StructureRole.WORKSHOP || role == StructureRole.FACTORY || role == StructureRole.MINE
                    || role == StructureRole.LUMBER_CAMP || role == StructureRole.BREWERY) ? 35 : 0;
            case DEFENSE -> (role == StructureRole.KEEP || role == StructureRole.BARRACKS || role == StructureRole.WALL
                    || role == StructureRole.GATE || role == StructureRole.AIRFIELD) ? 35 : 0;
        };
        return Math.max(1, Math.min(240, base + bonus));
    }

    static int spacing(SettlementMorphology morph) {
        return switch (morph) {
            case ORGANIC_MEDIEVAL -> 40;
            case MARKET_CROSS -> 48;
            case RADIAL_CAPITAL, WALLED_CORE -> 50;
            case LINEAR_VALLEY, RIVER_TOWN -> 46;
            case COASTAL_PORT -> 44;
            case HILL_TOWN -> 38;
            case INDUSTRIAL_EDGE -> 52;
            case PLANNED_BOULEVARD -> 56;
        };
    }

    static int organicShift(Settlement settlement, int salt) {
        return Math.floorMod((int) mix(settlement.id() ^ (salt * 0x85EBCA77C2B2AE63L)), 11) - 5;
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static void addAt(List<ConstructionIntent> out, Faction faction, Settlement settlement, StructureRole role,
                              int index, SimPosition center, int width, int depth, int rotation, int priority) {
        out.add(new ConstructionIntent(key(settlement, role, index), faction.id(), settlement.id(), role, center, width, depth, Math.floorMod(rotation, 4), priority));
    }

    static void addAtParcel(List<ConstructionIntent> out, Faction faction, Settlement settlement, StructureRole role,
                                    int index, SettlementParcelPlanner.ParcelPlan parcel, int width, int depth,
                                    int rotation, int priority) {
        Objects.requireNonNull(parcel, "parcel");
        out.add(new ConstructionIntent(
                key(settlement, role, index), faction.id(), settlement.id(), role, parcel.center(),
                width, depth, Math.floorMod(rotation, 4), priority,
                parcel.id(), parcel.width(), parcel.depth()));
    }

    private static String key(Settlement settlement, StructureRole role, int index) {
        String base = role.name().toLowerCase(Locale.ROOT);
        return switch (role) {
            case ROAD, KEEP, WALL, GATE -> base + ":" + settlement.tier().ordinal() + ":" + index;
            default -> base + ":" + index;
        };
    }

    /**
     * Wave 31: prefer authored building templates when culture/role/tier/wealth/footprint match,
     * falling back through family → generic LR. Never imports external schematics.
     */
    static int[] houseFootprintFromTemplate(Faction faction, Settlement settlement,
                                                    CultureArchitecture culture, int width, int depth) {
        String cultureId = SettlementIdentityProfile.resolveCultureId(faction);
        Optional<BuildingDefinition> template = BuildingTemplateRegistry.find(new BuildingTemplateRegistry.Query(
                cultureId.isBlank() ? "generic" : cultureId,
                StructureRole.HOUSE,
                settlement.tier(),
                SettlementDistrict.RESIDENTIAL,
                settlement.prosperity(),
                Math.max(width + 4, culture.minHouseWidth() + 4),
                Math.max(depth + 4, culture.minHouseDepth() + 4)));
        if (template.isEmpty()) return new int[]{width, depth};
        BuildingDefinition b = template.get();
        return new int[]{
                Math.max(width, b.footprintWidth()),
                Math.max(depth, b.footprintDepth())
        };
    }
}
