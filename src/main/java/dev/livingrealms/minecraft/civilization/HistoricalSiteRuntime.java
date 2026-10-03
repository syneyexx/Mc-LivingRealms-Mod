package dev.livingrealms.minecraft.civilization;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.construction.HistoricalSiteMaterializer;
import dev.livingrealms.sim.world.WorldEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Player-facing consequences for projected historical sites. */
public final class HistoricalSiteRuntime {
    private HistoricalSiteRuntime(){}
    public static void ruinBlockBroken(ServerPlayer player,BlockPos pos){
        var id=HistoricalSiteMaterializer.ruinIdAt(pos);if(id.isEmpty())return;
        var data=SimulationRuntime.data(player.serverLevel().getServer());
        var ruin=data.state().ruinSites().stream().filter(r->r.id()==id.getAsLong()).findFirst().orElse(null);
        if(ruin==null)return;
        if(!ruin.looted()){
            ruin.markLooted();data.state().history().add(new WorldEvent(data.state().clock().day(),"ruin_disturbed","ruin="+ruin.id()+", player="+player.getUUID()+", site="+ruin.originalName()));data.setDirty();
        }
        HistoricalSiteMaterializer.forget(pos);
    }
}
