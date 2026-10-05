package dev.livingrealms.minecraft.civilization;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.construction.HistoricalSiteMaterializer;
import dev.livingrealms.minecraft.law.CrimeRuntime;
import dev.livingrealms.sim.civilization.HiddenCache;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.world.WorldEvent;
import dev.livingrealms.sim.law.CrimeType;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Atomic player interaction bridge for canonical hidden caches. The barrel never owns the loot state. */
public final class HiddenCacheRuntime {
    private HiddenCacheRuntime(){}

    public static InteractionResult interact(ServerPlayer player,BlockPos pos){
        OptionalLong id=HistoricalSiteMaterializer.cacheIdAt(pos);
        if(id.isEmpty())return InteractionResult.PASS;
        var data=SimulationRuntime.data(player.serverLevel().getServer());
        HiddenCache cache=data.state().hiddenCaches().stream().filter(c->c.id()==id.getAsLong()).findFirst().orElse(null);
        if(cache==null||cache.recovered()){HistoricalSiteMaterializer.forget(pos);return InteractionResult.PASS;}
        double value=cache.value();int stacks=0;
        for(var entry:cache.goods().entrySet()){
            int count=(int)Math.floor(entry.getValue());
            if(count<=0)continue;
            Item item=itemFor(entry.getKey());int max=new ItemStack(item).getMaxStackSize();
            while(count>0){
                int moved=Math.min(count,max);ItemStack stack=new ItemStack(item,moved);
                if(!player.getInventory().add(stack))player.drop(stack,false);
                count-=moved;stacks++;
            }
        }
        cache.recover();
        data.state().history().add(new WorldEvent(data.state().clock().day(),"hidden_cache_retrieved","cache="+cache.id()+", player="+player.getUUID()+", value="+Math.round(value)));
        data.setDirty();
        boolean ownerMember=data.state().findPlayerStanding(CrimeRuntime.actorKey(player)).filter(s->s.isMember()&&s.memberFactionId()==cache.factionId()).isPresent();
        if(!ownerMember)CrimeRuntime.report(player,cache.factionId(),CrimeType.THEFT,Math.max(1,value),null,"recovered hidden cache "+cache.id());
        player.sendSystemMessage(Component.literal("Hidden cache recovered: "+stacks+" stack(s), value ~"+Math.round(value)+"."));
        player.serverLevel().removeBlock(pos,false);HistoricalSiteMaterializer.forget(pos);
        return InteractionResult.SUCCESS;
    }

    public static boolean broken(ServerPlayer player,BlockPos pos){
        return HistoricalSiteMaterializer.cacheIdAt(pos).isPresent()&&interact(player,pos).consumesAction();
    }

    private static Item itemFor(ResourceType resource){return switch(resource){
        case FOOD,BREAD->Items.BREAD;case GRAIN,FLOUR->Items.WHEAT;case MEAT->Items.BEEF;case ALE->Items.HONEY_BOTTLE;
        case WOOL,TEXTILES->Items.WHITE_WOOL;case WOOD->Items.OAK_LOG;case STONE->Items.COBBLESTONE;case IRON->Items.IRON_INGOT;
        case COAL->Items.COAL;case COPPER->Items.COPPER_INGOT;case GOLD->Items.GOLD_INGOT;case FUEL->Items.CHARCOAL;
        case AMMUNITION->Items.ARROW;case TOOLS->Items.IRON_PICKAXE;case MACHINERY->Items.IRON_BLOCK;
    };}
}
