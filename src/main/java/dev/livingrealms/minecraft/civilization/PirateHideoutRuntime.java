package dev.livingrealms.minecraft.civilization;

import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.construction.HistoricalSiteMaterializer;
import dev.livingrealms.minecraft.law.CrimeRuntime;
import dev.livingrealms.sim.world.WorldEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Player interaction bridge for projected pirate hideout blocks. */
public final class PirateHideoutRuntime {
    private PirateHideoutRuntime(){}
    public static boolean blockBroken(ServerPlayer player,BlockPos pos){var id=HistoricalSiteMaterializer.pirateHideoutIdAt(pos);if(id.isEmpty())return false;var data=SimulationRuntime.data(player.serverLevel().getServer());var hideout=data.state().pirateHideouts().stream().filter(h->h.id()==id.getAsLong()).findFirst().orElse(null);if(hideout==null||!hideout.active()){HistoricalSiteMaterializer.forget(pos);return false;}var standing=data.state().findPlayerStanding(CrimeRuntime.actorKey(player)).orElse(null);if(standing!=null&&standing.isMember())hideout.discover(standing.memberFactionId());hideout.adjustDefense(-.14);data.state().history().add(new WorldEvent(data.state().clock().day(),"pirate_hideout_damaged","hideout="+hideout.id()+", player="+player.getUUID()+", defense="+hideout.defense()));if(hideout.defense()<=.02){double recovered=hideout.takeLoot(hideout.storedLoot());hideout.destroy();data.state().findPirateBand(hideout.bandId()).ifPresent(b->{b.adjustMorale(-.45);if(b.morale()<.2)b.disband();});if(standing!=null&&standing.isMember())data.state().findFaction(standing.memberFactionId()).ifPresent(f->f.addTreasury(recovered*.5));data.state().history().add(new WorldEvent(data.state().clock().day(),"pirate_hideout_destroyed_by_player","hideout="+hideout.id()+", player="+player.getUUID()+", recovered="+Math.round(recovered*.5)));}data.setDirty();HistoricalSiteMaterializer.forget(pos);return true;}
}
