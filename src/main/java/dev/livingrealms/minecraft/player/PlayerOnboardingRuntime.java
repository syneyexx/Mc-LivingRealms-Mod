package dev.livingrealms.minecraft.player;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

/** Concise first-contact onboarding when a player discovers a LivingRealms settlement. */
public final class PlayerOnboardingRuntime {
    private static final Set<UUID> GREETED=new HashSet<>();
    private static final double DISCOVERY_RADIUS=96;

    private PlayerOnboardingRuntime(){}

    public static void tick(MinecraftServer server,LivingRealmsSavedData data){
        var state=data.state();
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            if(GREETED.contains(player.getUUID()))continue;
            Settlement near=null;
            for(Faction f:state.factions()){
                for(Settlement s:f.settlements()){
                    double dx=s.position().x()-player.getX(),dz=s.position().z()-player.getZ();
                    if(dx*dx+dz*dz<=DISCOVERY_RADIUS*DISCOVERY_RADIUS){near=s;break;}
                }
                if(near!=null)break;
            }
            if(near==null)continue;
            GREETED.add(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.livingrealms.onboarding.first_settlement"));
            player.sendSystemMessage(Component.translatable("message.livingrealms.onboarding.map"));
            player.sendSystemMessage(Component.translatable("message.livingrealms.onboarding.dashboard"));
            player.sendSystemMessage(Component.translatable("message.livingrealms.onboarding.npc"));
        }
    }

    public static void clear(){GREETED.clear();}
}
