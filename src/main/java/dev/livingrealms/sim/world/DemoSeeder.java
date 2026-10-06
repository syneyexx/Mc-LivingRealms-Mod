package dev.livingrealms.sim.world;

import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.worldgen.StarterRegionalRouteBootstrap;
import dev.livingrealms.sim.worldgen.StarterRoadsideSiteBootstrap;

public final class DemoSeeder {
    private DemoSeeder(){}
    public static void seed(SimulationState s){
        if(!s.regions().isEmpty()||!s.factions().isEmpty()) return;
        var forest=new EcosystemRegion(s.nextId(),s.biomes().get("temperate_forest"),80,new SimPosition(420,240));
        addIfPresent(s,forest,"rabbit",new SimPosition(300,200),240);
        addIfPresent(s,forest,"red_deer",new SimPosition(450,180),80);
        addIfPresent(s,forest,"gray_wolf",new SimPosition(400,300),12);
        addIfPresent(s,forest,"brown_bear",new SimPosition(600,300),3);
        s.addRegion(forest);
        HabitatPopulationSeeder.seedMissingSpecies(s,forest,64);
        var savanna=new EcosystemRegion(s.nextId(),s.biomes().get("savanna"),120,new SimPosition(3400,500));
        addIfPresent(s,savanna,"zebra",new SimPosition(3200,400),180);
        addIfPresent(s,savanna,"wildebeest",new SimPosition(3400,600),330);
        addIfPresent(s,savanna,"lion",new SimPosition(3350,550),16);
        addIfPresent(s,savanna,"elephant",new SimPosition(3700,500),24);
        s.addRegion(savanna);
        HabitatPopulationSeeder.seedMissingSpecies(s,savanna,64);

        // Seed deterministic capital→town→village→hamlet civilization fabric for a fresh world.
        SettlementDensitySeeder.ensureStarterDensity(s);
        // Canonical starter road graph must exist before generated chunks begin projecting road fabric.
        StarterRegionalRouteBootstrap.ensure(s);
        WizardTreesSeeder.ensure(s);
        s.history().add(new WorldEvent(0,"world_created","Living Realms simulation initialized."));
        s.ensureNamedRosters();
        // Day-zero corridor anchors use stable route-derived IDs shared with true worldgen.
        // Later emergent roadside sites remain owned by RoadLifeEngine.
        StarterRoadsideSiteBootstrap.ensure(s);
    }

    private static void addIfPresent(SimulationState state,EcosystemRegion region,String speciesId,SimPosition position,double population){
        if(state.species().containsKey(speciesId)){
            region.add(new PopulationGroup(state.nextId(),speciesId,region.biome().id(),position,population));
        }
    }
}
