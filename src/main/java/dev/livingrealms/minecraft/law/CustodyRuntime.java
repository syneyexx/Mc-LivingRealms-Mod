package dev.livingrealms.minecraft.law;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.law.CustodyRecord;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Keeps an arrested player near the issuing realm's jail anchor until the simulated release day. */
public final class CustodyRuntime {
    private static final double JAIL_RADIUS_SQR=10.0D*10.0D;
    private CustodyRuntime() {}

    public static void tick(MinecraftServer server, LivingRealmsSavedData data){
        var state=data.state();
        ServerLevel overworld=server.overworld();
        for(var player:server.getPlayerList().getPlayers()){
            String actor=CrimeRuntime.actorKey(player);
            CustodyRecord record=state.custody().stream().filter(CustodyRecord::active).filter(c->c.actorKey().equals(actor))
                    .min(Comparator.comparingLong(CustodyRecord::releaseDay)).orElse(null);
            if(record==null)continue;
            var faction=state.findFaction(record.factionId()).orElse(null);
            if(faction==null||faction.settlements().isEmpty())continue;
            var jail=faction.settlements().getFirst();
            int x=(int)Math.round(jail.position().x()),z=(int)Math.round(jail.position().z());
            int y=overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1;
            double dx=player.getX()-(x+.5D),dz=player.getZ()-(z+.5D);
            if(player.serverLevel()==overworld && dx*dx+dz*dz>JAIL_RADIUS_SQR){
                player.setPos(x+.5D,y,z+.5D);
                player.setDeltaMovement(0,0,0);
                player.sendSystemMessage(Component.literal("You are in custody for "+record.daysRemaining(state.clock().day())+" more simulated day(s)."));
            }
        }
    }
}
