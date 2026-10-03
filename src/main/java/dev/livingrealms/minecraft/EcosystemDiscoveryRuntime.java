package dev.livingrealms.minecraft;

import dev.livingrealms.sim.biome.BiomeClassifier;
import dev.livingrealms.sim.world.SimPosition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Discovers bounded canonical ecology cells only where players actually explore; never force-loads chunks. */
public final class EcosystemDiscoveryRuntime {
    private static final int CELL_BLOCKS=768;
    private static final double CELL_AREA_KM2=36.0;
    private EcosystemDiscoveryRuntime() {}

    public static void tick(MinecraftServer server,LivingRealmsSavedData data){
        ServerLevel overworld=server.overworld();
        var state=data.state();
        int before=state.regions().size();
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            if(player.serverLevel()!=overworld)continue;
            int cellX=Math.floorDiv(player.getBlockX(),CELL_BLOCKS)*CELL_BLOCKS+CELL_BLOCKS/2;
            int cellZ=Math.floorDiv(player.getBlockZ(),CELL_BLOCKS)*CELL_BLOCKS+CELL_BLOCKS/2;
            var observation=MinecraftBiomeBridge.observe(overworld,player.blockPosition());
            String archetype=BiomeClassifier.classifyId(observation);
            state.ensureEcosystemRegion(archetype,new SimPosition(cellX,cellZ),CELL_AREA_KM2,CELL_BLOCKS*.45);
        }
        if(state.regions().size()!=before)data.setDirty();
    }
}
