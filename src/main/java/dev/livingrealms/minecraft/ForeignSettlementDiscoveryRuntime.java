package dev.livingrealms.minecraft;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Adopts loaded vanilla/modded villager settlements into canonical Living Realms state.
 * The adapter never deletes or replaces the foreign village; it gives that physical place a
 * strategic owner/population so it can grow, trade, build routes and participate in diplomacy.
 */
public final class ForeignSettlementDiscoveryRuntime {
    private static final double SEARCH_RADIUS=150.0D;
    private static final double EXISTING_RADIUS=190.0D;
    private ForeignSettlementDiscoveryRuntime(){}

    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        for(var player:level.players()){
            List<Villager> villagers=level.getEntitiesOfClass(Villager.class,player.getBoundingBox().inflate(SEARCH_RADIUS),Villager::isAlive);
            if(villagers.size()<2)continue;
            List<List<Villager>> clusters=cluster(villagers);
            for(List<Villager> cluster:clusters)if(cluster.size()>=2)adopt(level,data,cluster);
        }
    }

    private static List<List<Villager>> cluster(List<Villager> villagers){
        List<List<Villager>> out=new ArrayList<>();
        for(Villager villager:villagers){
            List<Villager> found=null;
            for(List<Villager> c:out)if(c.stream().anyMatch(v->v.distanceToSqr(villager)<=96.0D*96.0D)){found=c;break;}
            if(found==null){found=new ArrayList<>();out.add(found);}found.add(villager);
        }
        return out;
    }

    private static void adopt(ServerLevel level,LivingRealmsSavedData data,List<Villager> cluster){
        double x=cluster.stream().mapToDouble(Villager::getX).average().orElse(0),z=cluster.stream().mapToDouble(Villager::getZ).average().orElse(0);SimPosition pos=new SimPosition(x,z);
        for(Faction faction:data.state().factions())for(Settlement settlement:faction.settlements())if(settlement.position().distanceTo(pos)<=EXISTING_RADIUS)return;
        Faction owner=data.state().factions().stream().min(Comparator.comparingDouble(f->f.settlements().stream().mapToDouble(s->s.position().distanceTo(pos)).min().orElse(Double.POSITIVE_INFINITY))).orElse(null);
        if(owner==null)return;
        int pop=Math.max(110,cluster.size()*55),housing=(int)Math.ceil(pop*1.18D);
        String biome=level.getBiome(net.minecraft.core.BlockPos.containing(x,level.getSeaLevel(),z)).unwrapKey().map(key->key.location().getPath()).orElse("frontier");
        String name=generatedName(biome,(int)Math.floor(x/16.0),(int)Math.floor(z/16.0));
        final String candidateName=name;
        if(owner.settlements().stream().anyMatch(s->s.name().equals(candidateName)))name=candidateName+" "+Math.floorMod((int)(x+z),97);
        Settlement adopted=new Settlement(data.state().nextId(),name,pos,pop,housing);
        owner.addSettlement(adopted);
        ForeignSettlementBootstrap.preserveExistingInfrastructure(data.state(),owner,adopted);
        owner.stockpile().add(ResourceType.FOOD,Math.max(250,cluster.size()*45));owner.stockpile().add(ResourceType.WOOD,180);owner.stockpile().add(ResourceType.TOOLS,20);
        data.state().history().add(new dev.livingrealms.sim.world.WorldEvent(data.state().clock().day(),"foreign_village_adopted",name+" integrated into "+owner.name()+" from a loaded vanilla/modded villager settlement; existing structures preserved, future growth enabled"));
        data.setDirty();
    }

    private static String generatedName(String biome,int chunkX,int chunkZ){
        String[] a={"Alder","Briar","Cedar","Dawn","Elm","Fox","Green","High","Iron","Juniper","Kings","Lark","Moss","Oak","Pine","River","Stone","Thorn","Willow","Wolf"};
        String[] b={"brook","cross","dale","field","ford","gate","haven","hollow","mere","stead","ton","vale","watch","wick","wood"};
        int h=31*chunkX+chunkZ*131+biome.toLowerCase(Locale.ROOT).hashCode();return a[Math.floorMod(h,a.length)]+b[Math.floorMod(h>>>8,b.length)];
    }
}
