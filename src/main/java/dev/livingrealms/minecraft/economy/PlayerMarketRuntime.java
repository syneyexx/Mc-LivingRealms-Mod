package dev.livingrealms.minecraft.economy;

import dev.livingrealms.sim.construction.SettlementPlanner;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.economy.MarketTransactionEngine;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.territory.TerritoryEngine;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Physical singleplayer bridge for canonical scarcity-driven realm markets. */
public final class PlayerMarketRuntime {
    private static final double MARKET_USE_RADIUS=96.0;
    private PlayerMarketRuntime(){}

    public static Result trade(SimulationState state,String actorKey,ServerPlayer player,SimPosition position,boolean buy,long encodedResource){
        Objects.requireNonNull(state);Objects.requireNonNull(player);Objects.requireNonNull(position);
        if(actorKey==null||actorKey.isBlank())return new Result(false,false,"invalid_actor");
        ResourceType resource=decodeResource(encodedResource);if(resource==null||!MarketTransactionEngine.playerTradable().contains(resource))return new Result(false,false,"invalid_resource");
        var jurisdiction=TerritoryEngine.resolve(state.factions(),position,state.config().borderDisputeThreshold());
        if(!jurisdiction.claimed()||jurisdiction.contested())return new Result(false,false,"no_local_market");
        Faction faction=state.findFaction(jurisdiction.primaryFactionId()).orElse(null);if(faction==null)return new Result(false,false,"market_faction_missing");
        if(state.activeCustody(actorKey,faction.id()).isPresent())return new Result(false,false,"in_custody");
        if(!nearCompletedMarket(faction,position))return new Result(false,false,"market_not_nearby");
        Item commodity=item(resource);if(commodity==null)return new Result(false,false,"unsupported_commodity");
        var side=buy?MarketTransactionEngine.Side.BUY_FROM_REALM:MarketTransactionEngine.Side.SELL_TO_REALM;
        var offer=MarketTransactionEngine.quote(faction,resource,side);if(!offer.available())return new Result(false,false,offer.reason());
        int units=(int)Math.round(offer.units());Inventory inventory=player.getInventory();
        if(buy){
            if(count(inventory,Items.EMERALD)<offer.emeralds())return new Result(false,false,"not_enough_emeralds");
            if(!canFit(inventory,commodity,units))return new Result(false,false,"inventory_full");
            int added=add(inventory,commodity,units);if(added!=units){remove(inventory,commodity,added);return new Result(false,false,"inventory_changed");}
            if(remove(inventory,Items.EMERALD,offer.emeralds())!=offer.emeralds()){remove(inventory,commodity,units);return new Result(false,false,"emeralds_changed");}
            var committed=MarketTransactionEngine.commit(faction,offer);
            if(!committed.success()){add(inventory,Items.EMERALD,offer.emeralds());remove(inventory,commodity,units);return new Result(false,false,committed.reason());}
            return new Result(true,true,"Bought "+units+" "+resource.name().toLowerCase()+" for "+offer.emeralds()+" emeralds.");
        }
        if(count(inventory,commodity)<units)return new Result(false,false,"not_enough_goods");
        if(!canFit(inventory,Items.EMERALD,offer.emeralds()))return new Result(false,false,"inventory_full");
        if(remove(inventory,commodity,units)!=units)return new Result(false,false,"goods_changed");
        var committed=MarketTransactionEngine.commit(faction,offer);
        if(!committed.success()){add(inventory,commodity,units);return new Result(false,false,committed.reason());}
        int paid=add(inventory,Items.EMERALD,offer.emeralds());
        if(paid!=offer.emeralds())throw new IllegalStateException("validated emerald payout did not fit inventory");
        return new Result(true,true,"Sold "+units+" "+resource.name().toLowerCase()+" for "+offer.emeralds()+" emeralds.");
    }

    private static boolean nearCompletedMarket(Faction faction,SimPosition position){
        for(var settlement:faction.settlements()){
            if(!settlement.isConstructionCompleted("market:0"))continue;
            for(var intent:SettlementPlanner.plan(faction,settlement))if(intent.role()==StructureRole.MARKET&&intent.key().equals("market:0")&&intent.center().distanceTo(position)<=MARKET_USE_RADIUS)return true;
        }
        return false;
    }
    private static ResourceType decodeResource(long encoded){if(encoded<=0||encoded>ResourceType.values().length)return null;return ResourceType.values()[(int)encoded-1];}
    private static Item item(ResourceType r){return switch(r){
        case FOOD,BREAD -> Items.BREAD;
        case GRAIN,FLOUR -> Items.WHEAT;
        case MEAT -> Items.BEEF;
        case ALE -> Items.HONEY_BOTTLE;
        case WOOL,TEXTILES -> Items.WHITE_WOOL;
        case WOOD -> Items.OAK_LOG;
        case STONE -> Items.STONE;
        case IRON -> Items.IRON_INGOT;
        case COAL -> Items.COAL;
        case COPPER -> Items.COPPER_INGOT;
        case GOLD -> Items.GOLD_INGOT;
        case FUEL -> Items.CHARCOAL;
        case AMMUNITION -> Items.ARROW;
        case TOOLS,MACHINERY -> null;
    };}
    private static int count(Inventory inv,Item item){int n=0;for(int i=0;i<inv.getContainerSize();i++){ItemStack s=inv.getItem(i);if(!s.isEmpty()&&s.getItem()==item)n+=s.getCount();}return n;}
    private static int remove(Inventory inv,Item item,int count){int remaining=count;for(int i=0;i<inv.getContainerSize()&&remaining>0;i++){ItemStack s=inv.getItem(i);if(s.isEmpty()||s.getItem()!=item)continue;int n=Math.min(remaining,s.getCount());s.shrink(n);remaining-=n;}return count-remaining;}
    private static boolean canFit(Inventory inv,Item item,int count){int capacity=0;int max=new ItemStack(item).getMaxStackSize();for(int i=0;i<inv.getContainerSize();i++){ItemStack s=inv.getItem(i);if(s.isEmpty())capacity+=max;else if(s.getItem()==item)capacity+=Math.max(0,s.getMaxStackSize()-s.getCount());if(capacity>=count)return true;}return false;}
    private static int add(Inventory inv,Item item,int count){ItemStack stack=new ItemStack(item,count);inv.add(stack);return count-stack.getCount();}

    public record Result(boolean success,boolean dirty,String message){public Result{message=message==null?"":message;}}
}
