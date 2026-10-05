package dev.livingrealms.sim.world;

import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.faction.*;
import java.util.*;

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

        Faction a=new Faction(s.nextId(),"Kingdom of Aster","Queen Elara I");
        a.addSettlement(new Settlement(s.nextId(),"Asterhold",new SimPosition(0,0),420,470));
        a.stockpile().add(ResourceType.FOOD,700);a.stockpile().add(ResourceType.WOOD,240);a.stockpile().add(ResourceType.IRON,80);
        Army aa=new Army(s.nextId(),a.id(),new SimPosition(80,30),90); aa.addArtillery(2); a.addArmy(aa); s.addFaction(a);

        Faction b=new Faction(s.nextId(),"Veyran Dominion","King Oren IV");
        b.addSettlement(new Settlement(s.nextId(),"Veyra",new SimPosition(2400,300),510,540));
        b.stockpile().add(ResourceType.FOOD,580);b.stockpile().add(ResourceType.WOOD,300);b.stockpile().add(ResourceType.IRON,120);
        Army ba=new Army(s.nextId(),b.id(),new SimPosition(2200,260),120);ba.addArtillery(3);b.addArmy(ba);s.addFaction(b);
        a.relationWith(b.id()).adjust(-35); b.relationWith(a.id()).adjust(-35);
        SettlementDensitySeeder.ensureStarterDensity(s);
        WizardTreesSeeder.ensure(s);
        // Wizard colonies are authored after the surface lattice — re-enforce spacing clearance.
        SettlementDensitySeeder.enforceSpacing(s);
        s.history().add(new WorldEvent(0,"world_created","Living Realms simulation initialized."));
        s.ensureNamedRosters();
    }

    private static void addIfPresent(SimulationState state,EcosystemRegion region,String speciesId,SimPosition position,double population){
        if(state.species().containsKey(speciesId)){
            region.add(new PopulationGroup(state.nextId(),speciesId,region.biome().id(),position,population));
        }
    }
}
