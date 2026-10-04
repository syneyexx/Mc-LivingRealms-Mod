package dev.livingrealms.minecraft.client.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Client-side cached top-down surface samples for the M-map. Samples loaded chunks only; never
 * force-loads. Discovery is not required — any loaded column can paint terrain immediately.
 */
public final class ClientTerrainMapCache {
    private static final int MAX_ENTRIES=12_000;
    private static final Map<Long,Sample> CACHE=new LinkedHashMap<>(256,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Long,Sample> eldest){return size()>MAX_ENTRIES;}
    };

    public record Sample(int color,int height,boolean water){}

    private ClientTerrainMapCache(){}

    public static Sample sample(int blockX,int blockZ){
        long key=(((long)(blockX>>2))<<32)^((blockZ>>2)&0xffffffffL);
        Sample cached=CACHE.get(key);
        if(cached!=null)return cached;
        Minecraft mc=Minecraft.getInstance();
        Level level=mc.level;
        if(level==null)return null;
        int x=(blockX>>2)<<2,z=(blockZ>>2)<<2;
        BlockPos pos=new BlockPos(x,level.getSeaLevel(),z);
        if(!level.hasChunk(x>>4,z>>4))return null;
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        boolean water=!level.getFluidState(new BlockPos(x,Math.max(level.getMinBuildHeight()+1,y),z)).isEmpty()
                ||!level.getFluidState(new BlockPos(x,level.getSeaLevel(),z)).isEmpty();
        Holder<Biome> biome=level.getBiome(new BlockPos(x,Math.max(y,level.getSeaLevel()),z));
        int color=biomeColor(biome);
        if(water)color=0xFF245E87;
        else{
            // Elevation shading so mountains read darker without a second overlay pass.
            double elev=Math.max(0,Math.min(1,(y-(level.getSeaLevel()-8))/96.0));
            color=shade(color,0.78+elev*0.28);
        }
        Sample sample=new Sample(color,y,water);
        CACHE.put(key,sample);
        return sample;
    }

    public static void clear(){CACHE.clear();}

    private static int biomeColor(Holder<Biome> biome){
        String path=biome.unwrapKey().map(k->k.location().getPath()).orElse("");
        String b=path.toLowerCase(java.util.Locale.ROOT);
        if(b.contains("ocean")||b.contains("river")||b.contains("beach"))return 0xFF245E87;
        if(b.contains("desert")||b.contains("badland"))return 0xFF9B7C3E;
        if(b.contains("snow")||b.contains("ice")||b.contains("frozen"))return 0xFFAFC8D6;
        if(b.contains("jungle")||b.contains("forest")||b.contains("taiga")||b.contains("grove"))return 0xFF2E6E45;
        if(b.contains("swamp")||b.contains("mangrove"))return 0xFF496B52;
        if(b.contains("mountain")||b.contains("peak")||b.contains("stony"))return 0xFF777C82;
        if(b.contains("savanna")||b.contains("plains")||b.contains("meadow"))return 0xFF6B8F4E;
        return 0xFF55735C;
    }

    private static int shade(int argb,double factor){
        int r=(int)(((argb>>16)&255)*factor),g=(int)(((argb>>8)&255)*factor),b=(int)((argb&255)*factor);
        return 0xFF000000|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b);
    }
}
