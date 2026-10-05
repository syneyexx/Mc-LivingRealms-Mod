package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.sim.cartography.TerrainKnowledge;
import dev.livingrealms.sim.cartography.TerrainMapSample;
import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Client-side cached top-down surface samples for the M-map. Samples loaded chunks only; never
 * force-loads. Unloaded land is UNKNOWN parchment or REGIONAL_ESTIMATE — never invented sine relief.
 */
public final class ClientTerrainMapCache {
    /** Bounded offline tile atlas — raised for denser M-map paint without unbounded growth. */
    private static final int MAX_ENTRIES=24_000;
    private static final Map<Long,Sample> CACHE=new LinkedHashMap<>(256,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Long,Sample> eldest){return size()>MAX_ENTRIES;}
    };

    public record Sample(int color,int height,boolean water,TerrainKnowledge knowledge){
        public Sample(int color,int height,boolean water){
            this(color,height,water,TerrainKnowledge.ACTUAL);
        }
    }

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
        Sample sample=new Sample(color,y,water,TerrainKnowledge.ACTUAL);
        CACHE.put(key,sample);
        return sample;
    }

    public static void clear(){CACHE.clear();}

    /**
     * Soft terrain wash for the F12 Map tab — ACTUAL when loaded, REGIONAL_ESTIMATE from ecology,
     * otherwise UNKNOWN parchment. Never invents hills with sine noise.
     */
    public static void paintIfAvailable(GuiGraphics g, RealmDashboardSnapshot snapshot, int x, int y, int w, int h) {
        if (g == null || snapshot == null || w <= 4 || h <= 4) return;
        var map = snapshot.map();
        double minX = map.minX(), maxX = map.maxX(), minZ = map.minZ(), maxZ = map.maxZ();
        if (!(maxX > minX) || !(maxZ > minZ)) return;
        var regions = snapshot.ecology().regions();
        final int tile = 5;
        for (int py = y; py < y + h; py += tile) {
            double wz = minZ + ((py - y + tile * 0.5) / Math.max(1.0, h)) * (maxZ - minZ);
            for (int px = x; px < x + w; px += tile) {
                double wx = minX + ((px - x + tile * 0.5) / Math.max(1.0, w)) * (maxX - minX);
                Sample surface = sample((int) Math.round(wx), (int) Math.round(wz));
                TerrainMapSample.ActualSample actual = surface == null ? null
                        : new TerrainMapSample.ActualSample(surface.color(), surface.height(), surface.water());
                String nearestBiome = null;
                if (actual == null && !regions.isEmpty()) {
                    RealmDashboardSnapshot.RegionEcologyView nearest = null;
                    double best = Double.POSITIVE_INFINITY;
                    for (var region : regions) {
                        double dx = region.x() - wx, dz = region.z() - wz, d = dx * dx + dz * dz;
                        if (d < best) { best = d; nearest = region; }
                    }
                    if (nearest != null) nearestBiome = nearest.biome();
                }
                TerrainMapSample.Resolved resolved = TerrainMapSample.resolve(actual, nearestBiome, nearestBiome != null);
                int color = resolved.knowledge() == TerrainKnowledge.UNKNOWN
                        ? TerrainMapSample.unknownFill(px, py)
                        : resolved.color();
                if (resolved.knowledge() == TerrainKnowledge.REGIONAL_ESTIMATE) {
                    // Low-detail hatch so estimates never read as surveyed topography.
                    if ((((px >> 3) + (py >> 3)) & 1) == 0) color = shade(color, 0.88);
                }
                int washed = (0x66 << 24) | (color & 0x00FFFFFF);
                g.fill(px, py, Math.min(x + w, px + tile), Math.min(y + h, py + tile), washed);
            }
        }
    }

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
