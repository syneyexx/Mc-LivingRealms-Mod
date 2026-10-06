package dev.livingrealms.minecraft;

import dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import net.minecraft.server.level.ServerLevel;

/** Ensures the actual vanilla spawn area belongs to a real Living Realms kingdom. */
public final class SpawnKingdomRuntime {
    private static final double COVER_RADIUS=360.0D;
    private SpawnKingdomRuntime(){}

    public static void ensure(ServerLevel level,LivingRealmsSavedData data){
        if (data.starterWorldgenEnabled()) {
            // Fresh saves already have one deterministic day-zero surface authority: true chunk
            // worldgen. Never promote/create a spawn settlement afterward and trigger visible catchup.
            return;
        }
        var spawn=level.getSharedSpawnPos();SimPosition p=new SimPosition(spawn.getX(),spawn.getZ());
        Settlement nearest=null;Faction nearestOwner=null;double best=Double.POSITIVE_INFINITY;
        for(Faction faction:data.state().factions())for(Settlement settlement:faction.settlements()){
            double d=settlement.position().distanceTo(p);if(d<best){best=d;nearest=settlement;nearestOwner=faction;}
        }
        if(nearest!=null&&best<=COVER_RADIUS){
            // World spawn must be an actual capital-scale kingdom, not merely territory on paper.
            // Promoting the local seat to CITY guarantees the capital castle, walls, gates, dense
            // housing and civic buildings are part of the normal authoritative construction plan.
            boolean changed=false;if(nearest.population()<3_200){nearest.addPopulation(3_200-nearest.population());changed=true;}if(nearest.housing()<3_800){nearest.addHousing(3_800-nearest.housing());changed=true;}
            if(nearestOwner!=null){nearestOwner.stockpile().add(ResourceType.FOOD,changed?4_000:0);nearestOwner.stockpile().add(ResourceType.WOOD,changed?3_000:0);nearestOwner.stockpile().add(ResourceType.STONE,changed?5_000:0);nearestOwner.stockpile().add(ResourceType.IRON,changed?650:0);nearestOwner.stockpile().add(ResourceType.TOOLS,changed?260:0);}
            if(changed){
                data.state().history().add(new dev.livingrealms.sim.world.WorldEvent(data.state().clock().day(),"spawn_capital_promoted",nearest.name()+" promoted to a full capital-scale seat at world spawn"));
                // Kick physical catch-up so keep/roads/housing enqueue while the player is at spawn.
                SettlementConstructionMaterializer.requestCatchup(45);
                data.setDirty();
            }
            return;
        }
        Faction crown=data.state().factions().stream().filter(f->f.name().equals("Kingdom of Aster")).findFirst().orElseGet(()->data.state().factions().stream().findFirst().orElse(null));
        if(crown==null)return;
        if(crown.settlements().stream().anyMatch(s->s.name().equals("Crownspawn")))return;
        Settlement city=new Settlement(data.state().nextId(),"Crownspawn",p,3_250,3_700);
        crown.addSettlement(city);crown.stockpile().add(ResourceType.FOOD,6_000);crown.stockpile().add(ResourceType.WOOD,3_500);crown.stockpile().add(ResourceType.STONE,4_500);crown.stockpile().add(ResourceType.TOOLS,240);
        data.state().history().add(new dev.livingrealms.sim.world.WorldEvent(data.state().clock().day(),"spawn_kingdom_established","Crownspawn established at the Minecraft world spawn under "+crown.name()));
        SettlementConstructionMaterializer.requestCatchup(60);
        data.setDirty();
    }
}
