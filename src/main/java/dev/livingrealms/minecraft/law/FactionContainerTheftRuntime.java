package dev.livingrealms.minecraft.law;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.construction.CreateBlockLookup;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.property.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Server-side bridge between generated faction storage and the canonical law/economy model.
 *
 * <p>Only STORAGE placements authored by Living Realms blueprints are eligible. A player-placed
 * chest/barrel inside a realm therefore does not silently become faction property, and portable
 * inventories (including Traveler's Backpack) never enter this path.</p>
 */
public final class FactionContainerTheftRuntime {
    private static final long PENDING_TICKS=20L;
    private static final int PHYSICAL_STOCK_TARGET=24;
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static final Map<UUID,Session> OPEN=new HashMap<>();

    private record Pending(ResourceKey<Level> dimension,BlockPos pos,PropertyClaim claim,boolean authorized,long gameTime){}
    private record Session(PropertyClaim claim,Container storage,Map<String,ContainerTheftAssessment.Lot> before){}

    private FactionContainerTheftRuntime(){}

    public static void prepare(ServerPlayer player,BlockPos pos){
        if(player==null||pos==null)return;
        UUID id=player.getUUID();PENDING.remove(id);OPEN.remove(id);
        ServerLevel level=player.serverLevel();
        // Living Realms' own faction storage currently materializes as barrels. Restricting the
        // bridge to this authored slot avoids false positives on player/mod containers nearby.
        if(!level.getBlockState(pos).is(Blocks.BARREL)||level.getBlockEntity(pos)==null)return;
        var state=SimulationRuntime.data(level.getServer()).state();
        var claim=PropertyRightsEngine.resolveStorage(state.factions(),new SimPosition(pos.getX()+.5,pos.getZ()+.5)).orElse(null);
        if(claim==null)return;
        boolean authorized=authorized(state,CrimeRuntime.actorKey(player),claim.factionId());
        PENDING.put(id,new Pending(level.dimension(),pos.immutable(),claim,authorized,level.getGameTime()));
    }

    public static void opened(ServerPlayer player,AbstractContainerMenu menu){
        if(player==null||menu==null)return;
        Pending pending=PENDING.remove(player.getUUID());
        if(pending==null)return;
        ServerLevel level=player.serverLevel();
        if(!pending.dimension().equals(level.dimension())||level.getGameTime()-pending.gameTime()>PENDING_TICKS)return;
        // Confirm the authored storage block still exists after the interaction pipeline completed.
        if(!level.getBlockState(pending.pos()).is(Blocks.BARREL))return;
        Container storage=storageContainer(level,pending.pos());
        if(storage==null)return;
        stockFromFaction(player,menu,storage,pending.claim());
        if(pending.authorized())return;
        Map<String,ContainerTheftAssessment.Lot> before=snapshot(storage);
        if(before.isEmpty())return;
        OPEN.put(player.getUUID(),new Session(pending.claim(),storage,before));
    }

    public static void closed(ServerPlayer player,AbstractContainerMenu menu){
        if(player==null||menu==null)return;
        Session session=OPEN.remove(player.getUUID());
        if(session==null)return;
        var after=snapshot(session.storage());
        var result=ContainerTheftAssessment.assess(session.before(),after);
        if(!result.theftOccurred())return;
        CrimeRuntime.reportPropertyTheft(player,session.claim(),result.stolenValue(),
                "faction storage theft: "+result.removedItems()+" item(s), "+compact(result.removedByKey()));
    }

    public static void remove(UUID playerId){if(playerId!=null){PENDING.remove(playerId);OPEN.remove(playerId);}}
    public static void clear(){PENDING.clear();OPEN.clear();}

    private static boolean authorized(dev.livingrealms.sim.world.SimulationState state,String actor,long factionId){
        return state.findPlayerStanding(actor).filter(s->s.isMember()&&s.memberFactionId()==factionId).isPresent();
    }

    /** Converts a bounded amount of abstract stock into persistent physical goods in this barrel. */
    private static void stockFromFaction(ServerPlayer player,AbstractContainerMenu menu,Container storage,PropertyClaim claim){
        var data=SimulationRuntime.data(player.serverLevel().getServer());
        Faction faction=data.state().findFaction(claim.factionId()).orElse(null);
        if(faction==null)return;
        int physical=containerItemCount(storage);
        int budget=Math.max(0,PHYSICAL_STOCK_TARGET-physical);
        if(budget<=0)return;
        boolean changed=false;
        for(ResourceType resource:resourcesFor(claim.role())){
            if(budget<=0)break;
            int available=(int)Math.floor(faction.stockpile().get(resource));
            if(available<=0)continue;
            Item item=itemFor(resource);
            ItemStack probe=new ItemStack(item);
            int offered=Math.min(budget,Math.min(available,Math.max(1,probe.getMaxStackSize())));
            if(offered<=0)continue;
            ItemStack remainder=insertIntoContainer(storage,new ItemStack(item,offered));
            int inserted=offered-(remainder.isEmpty()?0:remainder.getCount());
            if(inserted<=0)continue;
            faction.stockpile().take(resource,inserted);
            budget-=inserted;changed=true;
        }
        if(changed){storage.setChanged();menu.broadcastChanges();data.setDirty();}
    }

    private static Container storageContainer(ServerLevel level,BlockPos pos){
        return level.getBlockEntity(pos) instanceof Container container?container:null;
    }

    private static ItemStack insertIntoContainer(Container storage,ItemStack stack){
        ItemStack remaining=stack.copy();
        for(int i=0;i<storage.getContainerSize()&&!remaining.isEmpty();i++){
            if(!storage.canPlaceItem(i,remaining))continue;
            ItemStack existing=storage.getItem(i);
            if(existing.isEmpty()||!ItemStack.isSameItemSameComponents(existing,remaining))continue;
            int limit=Math.min(storage.getMaxStackSize(existing),existing.getMaxStackSize());
            int moved=Math.min(Math.max(0,limit-existing.getCount()),remaining.getCount());
            if(moved<=0)continue;
            existing.grow(moved);
            remaining.shrink(moved);
            storage.setItem(i,existing);
        }
        for(int i=0;i<storage.getContainerSize()&&!remaining.isEmpty();i++){
            if(!storage.canPlaceItem(i,remaining)||!storage.getItem(i).isEmpty())continue;
            int limit=Math.min(storage.getMaxStackSize(remaining),remaining.getMaxStackSize());
            int moved=Math.min(limit,remaining.getCount());
            storage.setItem(i,remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        return remaining;
    }

    private static int containerItemCount(Container storage){
        int count=0;for(int i=0;i<storage.getContainerSize();i++)count+=storage.getItem(i).getCount();return count;
    }

    private static List<ResourceType> resourcesFor(StructureRole role){
        return switch(role){
            case KEEP -> List.of(ResourceType.GOLD,ResourceType.TOOLS,ResourceType.TEXTILES,ResourceType.FOOD);
            case BARRACKS -> List.of(ResourceType.AMMUNITION,ResourceType.TOOLS,ResourceType.IRON,ResourceType.FOOD);
            case MARKET -> List.of(ResourceType.FOOD,ResourceType.TEXTILES,ResourceType.TOOLS,ResourceType.COPPER,ResourceType.GOLD);
            case WAREHOUSE -> List.of(ResourceType.FOOD,ResourceType.WOOD,ResourceType.STONE,ResourceType.IRON,ResourceType.COAL,ResourceType.COPPER,ResourceType.GOLD,ResourceType.FUEL,ResourceType.AMMUNITION,ResourceType.TOOLS,ResourceType.MACHINERY,ResourceType.TEXTILES);
            default -> List.of(ResourceType.FOOD,ResourceType.WOOD,ResourceType.TOOLS);
        };
    }

    private static Item itemFor(ResourceType resource){
        return switch(resource){
            case FOOD, BREAD -> Items.BREAD;
            case GRAIN, FLOUR -> Items.WHEAT;
            case MEAT -> Items.BEEF;
            case ALE -> Items.HONEY_BOTTLE;
            case WOOL, TEXTILES -> Items.WHITE_WOOL;
            case WOOD -> Items.OAK_LOG;
            case STONE -> Items.COBBLESTONE;
            case IRON -> Items.IRON_INGOT;
            case COAL -> Items.COAL;
            case COPPER -> Items.COPPER_INGOT;
            case GOLD -> Items.GOLD_INGOT;
            case FUEL -> Items.CHARCOAL;
            case AMMUNITION -> Items.ARROW;
            case TOOLS -> Items.IRON_PICKAXE;
            case MACHINERY -> CreateBlockLookup.orElse("andesite_casing",Blocks.IRON_BLOCK).asItem();
        };
    }

    private static Map<String,ContainerTheftAssessment.Lot> snapshot(Container storage){
        Map<String,Integer> counts=new LinkedHashMap<>();
        Map<String,Double> values=new LinkedHashMap<>();
        for(int i=0;i<storage.getContainerSize();i++){
            ItemStack stack=storage.getItem(i);
            if(stack.isEmpty())continue;
            var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
            if(id==null)continue;
            String key=id.toString();
            counts.merge(key,stack.getCount(),Integer::sum);
            values.merge(key,unitValue(key,stack)*stack.getCount(),Double::sum);
        }
        Map<String,ContainerTheftAssessment.Lot> result=new LinkedHashMap<>();
        for(var e:counts.entrySet()){
            int count=e.getValue();double total=values.getOrDefault(e.getKey(),(double)count);
            result.put(e.getKey(),new ContainerTheftAssessment.Lot(count,count==0?0:total/count));
        }
        return result;
    }

    private static double unitValue(String key,ItemStack stack){
        String path=key.toLowerCase(Locale.ROOT);
        double value=1.0+Math.min(12.0,64.0/Math.max(1,stack.getMaxStackSize()));
        if(path.contains("netherite"))value+=35;
        else if(path.contains("diamond"))value+=24;
        else if(path.contains("gold"))value+=8;
        else if(path.contains("iron"))value+=4;
        if(path.contains("spell")||path.contains("tome")||path.contains("staff")||path.contains("armor")||path.contains("weapon"))value+=12;
        if(path.contains("casing")||path.contains("machine")||path.contains("engine"))value+=8;
        if(stack.isDamageableItem())value+=6.0;
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        if(id!=null&&!id.getNamespace().equals("minecraft"))value*=1.25;
        return Math.max(1.0,value);
    }

    private static String compact(Map<String,Integer> removed){
        StringBuilder out=new StringBuilder();int shown=0;
        for(var e:removed.entrySet()){
            if(shown++>0)out.append(", ");out.append(e.getKey()).append(" x").append(e.getValue());
            if(shown==4&&removed.size()>4){out.append(", +").append(removed.size()-4).append(" more");break;}
        }
        return out.toString();
    }
}
