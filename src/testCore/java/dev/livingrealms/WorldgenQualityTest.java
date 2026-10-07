package dev.livingrealms;

import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.transport.*;
import dev.livingrealms.sim.world.*;
import java.util.*;
// StarterCivilizationLayoutPlanner + validation used for multi-seed gate below.

/** Regression gate for the physical-layout complaints fixed by content revision 6. */
public final class WorldgenQualityTest {
    private WorldgenQualityTest() {}

    public static void main(String[] args) {
        SimulationState state=new SimulationState(60606L);
        DemoSeeder.seed(state);
        SettlementDensitySeeder.ensureStarterDensity(state);
        Faction aster=state.factions().stream().filter(f->f.name().equals("Kingdom of Aster")).findFirst().orElseThrow();
        Settlement capital=aster.settlements().stream().filter(s->s.name().equals("Asterhold")).findFirst().orElseThrow();
        check(capital.tier().ordinal()>=Settlement.Tier.CITY.ordinal(),"spawn capital must be city-scale");

        List<ConstructionIntent> plan=SettlementPlanner.plan(aster,capital);
        ConstructionIntent keep=plan.stream().filter(i->i.role()==StructureRole.KEEP).findFirst().orElseThrow();
        check(keep.width()>=31&&keep.depth()>=27,"capital must request a real castle footprint");
        StructureBlueprint castle=StructureBlueprintFactory.create(keep);
        check(castle.id().contains("castle"),"capital keep must resolve to castle blueprint");

        List<ConstructionIntent> roads=plan.stream().filter(i->i.role()==StructureRole.ROAD).toList();
        check(roads.size()>=5,"city must have a connected street/side-street network");
        check(roads.stream().filter(ConstructionIntent::hasPath).count()>=5,"city roads must be graph polyline intents");
        SettlementStreetGraph streetGraph=SettlementStreetGraph.fromRoadIntents(capital.id(),roads);
        check(streetGraph.hasConnectedCore(),"city street graph must be fully connected");
        check(streetGraph.segments().stream().anyMatch(SettlementStreetGraph.RoadSegment::hasNonAxisGeometry),
                "city morphology must physically support non-axis streets");
        check(roads.stream().anyMatch(i->i.width()==5)&&roads.stream().anyMatch(i->i.width()>=7),
                "city must contain both residential streets and arterials");

        List<ConstructionIntent> houses=plan.stream().filter(i->i.role()==StructureRole.HOUSE).toList();
        check(houses.size()>=60,"city must materially expand housing stock: planned="+houses.size());
        ConstructionIntent apartment=houses.stream().filter(i->i.width()>=11||i.depth()>=11).findFirst().orElseThrow();
        check(StructureBlueprintFactory.create(apartment).id().startsWith("apartment_block_"),"city housing must include multi-storey blocks");
        // CultureArchitecture raises minimum footprints; validate floors on a non-apartment home.
        StructureBlueprint sampleHouse=StructureBlueprintFactory.create(houses.stream()
                .filter(i->!StructureBlueprintFactory.create(i).id().startsWith("apartment_block_"))
                .findFirst().orElse(houses.getFirst()));
        check(sampleHouse.placements().stream().anyMatch(p->p.slot()==PaletteSlot.FLOOR&&p.dy()==0),"house walkable floor must be at foundation/door level, not one block too high");

        Settlement growth=new Settlement(state.nextId(),"Growth Test",new SimPosition(500,500),420,500);
        Faction growthFaction=new Faction(state.nextId(),"Growth Realm","Builder");growthFaction.addSettlement(growth);state.addFaction(growthFaction);
        Set<String> villageRoadKeys=new HashSet<>();for(var i:SettlementPlanner.plan(growthFaction,growth))if(i.role()==StructureRole.ROAD)villageRoadKeys.add(i.key());
        growth.addPopulation(1800);growth.addHousing(2100);
        Set<String> cityRoadKeys=new HashSet<>();for(var i:SettlementPlanner.plan(growthFaction,growth))if(i.role()==StructureRole.ROAD)cityRoadKeys.add(i.key());
        check(growth.tier().ordinal()>=Settlement.Tier.CITY.ordinal(),"growth fixture must reach city tier");
        check(!cityRoadKeys.equals(villageRoadKeys)&&cityRoadKeys.stream().anyMatch(k->!villageRoadKeys.contains(k)),"tier growth must create new road completion keys so the city physically expands");

        TransportRoute route=new TransportRoute(state.nextId(),aster.id(),capital.id(),aster.settlements().get(1).id(),TransportMode.ROAD,140,0.7,0.8,500);
        SimPosition from=new SimPosition(0,0),to=new SimPosition(120,70);
        List<RouteProjectionPlanner.RoutePoint> points=RouteProjectionPlanner.plan(route,from,to,List.of(new SimPosition(60,35)),500,500);
        check(points.size()>20,"route fixture must produce enough projected points");
        double vx=to.x()-from.x(),vz=to.z()-from.z();
        for(var p:points){double cross=(p.x()-from.x())*vz-(p.z()-from.z())*vx;check(Math.abs(cross)<=Math.max(Math.abs(vx),Math.abs(vz)),"strategic road without terrain sampler must remain straight rather than sine-bending");}

        // With a terrain sampler, the corridor may leave the geometric line to avoid cliffs.
        TerrainCorridorPlanner.TerrainSample ridge=new TerrainCorridorPlanner.TerrainSample(){
            @Override public int height(int x,int z){return (x>40&&x<90&&Math.abs(z-35)<20)?120:64;}
            @Override public boolean water(int x,int z){return false;}
            @Override public boolean blocked(int x,int z){return false;}
        };
        List<RouteProjectionPlanner.RoutePoint> terrainPoints=RouteProjectionPlanner.plan(route,from,to,List.of(new SimPosition(60,35)),500,500,ridge);
        check(!terrainPoints.isEmpty(),"terrain-aware route must still project near observers");

        // Continuous road mask: diagonal polylines must not leave corner gaps before dilation.
        List<SimPosition> diagonal = List.of(new SimPosition(0, 0), new SimPosition(12, 12));
        var roadMask = dev.livingrealms.sim.worldgen.RoadSurfaceMask.rasterize(diagonal, 3);
        check(roadMask.size() >= 30, "diagonal road mask must be continuous: " + roadMask.size());

        // Multi-seed strategic validation (topology + hierarchy + road access).
        for (long seed : List.of(1L, 42L, 60606L)) {
            var layout = StarterCivilizationLayoutPlanner.plan(seed);
            var report = dev.livingrealms.sim.worldgen.StarterWorldgenValidationEngine.validate(layout);
            check(report.ok(), "starter validation seed=" + seed + " " + report.issues());
            check(report.metrics().disconnectedStreetGraphs() == 0, "disconnected streets seed=" + seed);
        }

        System.out.println("PASS worldgen quality: city castle + graph-first polyline streets + apartments + accessible floors + tier growth + terrain-aware routes + road mask + multi-seed validation");
    }

    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
