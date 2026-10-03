package dev.livingrealms.minecraft.compat;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.compat.WaystoneProvenance;
import dev.livingrealms.sim.faction.Settlement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;

/**
 * Optional, reflection-isolated Waystones integration: one named global waystone per loaded settlement.
 * Deduplication destroys only Living Realms-authored waystones recorded in outer save provenance.
 * Player-created / foreign waystones are never automatically removed.
 */
public final class WaystoneSettlementRuntime {
    private static final double MATERIALIZE_RADIUS_SQR=420.0D*420.0D;
    private static volatile boolean disabled;
    private static volatile Api api;
    private WaystoneSettlementRuntime(){}

    public static void tick(ServerLevel level,LivingRealmsSavedData data){
        if(disabled||level==null||data==null||!ModList.get().isLoaded("waystones")||level.players().isEmpty())return;
        try{
            Api resolved=api();
            int placed=0;
            java.util.List<BlockPos> allWaystones=new java.util.ArrayList<>(resolved.positions(level));
            Set<Long> owned=new HashSet<>(data.livingRealmsWaystonePositions());
            for(var faction:data.state().factions())for(Settlement settlement:faction.settlements()){
                if(placed>=2) return; // never create a burst of foreign block entities in one maintenance pass
                if(!nearPlayer(level,settlement))continue;
                BlockPos center=BlockPos.containing(settlement.position().x(),64,settlement.position().z());
                if(!level.hasChunkAt(center))continue;
                Long ownedPacked=data.waystoneForSettlement(settlement.id());
                if(ownedPacked!=null){
                    BlockPos ownedPos=unpack(ownedPacked);
                    if(level.hasChunkAt(ownedPos)&&!level.getBlockState(ownedPos).isAir()){
                        // Canonical LR stone still present — never place another for this settlement.
                        continue;
                    }
                    // Authored stone missing (destroyed/world-edit): clear provenance and allow one replacement.
                    data.clearWaystone(settlement.id());
                    owned.remove(ownedPacked);
                }
                java.util.List<BlockPos> lrDuplicates=new java.util.ArrayList<>();
                for(BlockPos p:allWaystones){
                    if(nearestSettlementId(data,p)!=settlement.id())continue;
                    if(owned.contains(pack(p))||resolved.isLivingRealmsNamed(level,p))lrDuplicates.add(p);
                }
                lrDuplicates.sort(java.util.Comparator.comparingDouble((BlockPos p)->center.distSqr(p)));
                if(lrDuplicates.size()>1){
                    for(int i=1;i<lrDuplicates.size();i++){
                        BlockPos duplicate=lrDuplicates.get(i);
                        if(level.hasChunkAt(duplicate))level.destroyBlock(duplicate,false);
                        allWaystones.remove(duplicate);
                        data.clearWaystoneAt(pack(duplicate));
                        owned.remove(pack(duplicate));
                    }
                }
                if(!lrDuplicates.isEmpty()){
                    data.recordWaystone(settlement.id(),pack(lrDuplicates.getFirst()));
                    owned.add(pack(lrDuplicates.getFirst()));
                    continue;
                }
                // Do not place a new LR waystone if any waystone already sits in the settlement core —
                // adopt the nearest as foreign-compatible travel node without destroying it.
                BlockPos foreignNear=null;double best=80.0D*80.0D;
                for(BlockPos p:allWaystones){
                    double d=center.distSqr(p);if(d<best&&nearestSettlementId(data,p)==settlement.id()){best=d;foreignNear=p;}
                }
                if(foreignNear!=null){
                    // Record nothing as LR-owned; settlement already has travel access.
                    continue;
                }
                BlockPos target=findSite(level,center);
                if(target==null)continue;
                Optional<?> result=resolved.place(level,target);
                if(result.isPresent()){
                    resolved.name(level,result.get(),WaystoneProvenance.authoredName(settlement.name()));
                    allWaystones.add(target);
                    data.recordWaystone(settlement.id(),pack(target));
                    owned.add(pack(target));
                    placed++;
                }
            }
        }catch(ReflectiveOperationException|LinkageError|RuntimeException ex){
            disabled=true;
            LivingRealms.LOGGER.warn("Disabling optional settlement Waystone materialization after compatibility failure",ex);
        }
    }

    public static boolean isLivingRealmsName(String name){
        return WaystoneProvenance.isLivingRealmsName(name);
    }

    public static long pack(BlockPos pos){return BlockPos.asLong(pos.getX(),pos.getY(),pos.getZ());}
    public static BlockPos unpack(long packed){return BlockPos.of(packed);}

    private static long nearestSettlementId(LivingRealmsSavedData data,BlockPos pos){
        long id=-1;double best=Double.POSITIVE_INFINITY;
        for(var faction:data.state().factions())for(Settlement settlement:faction.settlements()){
            double dx=pos.getX()-settlement.position().x(),dz=pos.getZ()-settlement.position().z(),d=dx*dx+dz*dz;
            if(d<best){best=d;id=settlement.id();}
        }
        return id;
    }

    private static boolean nearPlayer(ServerLevel level,Settlement settlement){
        for(var player:level.players()){
            double dx=player.getX()-settlement.position().x(),dz=player.getZ()-settlement.position().z();
            if(dx*dx+dz*dz<=MATERIALIZE_RADIUS_SQR)return true;
        }
        return false;
    }

    private static BlockPos findSite(ServerLevel level,BlockPos center){
        int[][] offsets={{5,5},{-5,5},{5,-5},{-5,-5},{9,0},{0,9},{-9,0},{0,-9},{13,4},{-13,-4}};
        for(int[] o:offsets){
            int x=center.getX()+o[0],z=center.getZ()+o[1];
            BlockPos probe=new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
            if(!level.hasChunkAt(probe))continue;
            var here=level.getBlockState(probe);var above=level.getBlockState(probe.above());var below=level.getBlockState(probe.below());
            if(!here.canBeReplaced()||!above.canBeReplaced()||!level.getFluidState(probe).isEmpty()||!level.getFluidState(probe.below()).isEmpty())continue;
            if(!below.isFaceSturdy(level,probe.below(),Direction.UP))continue;
            return probe;
        }
        return null;
    }

    private static Api api() throws ReflectiveOperationException{
        Api existing=api;if(existing!=null)return existing;
        synchronized(WaystoneSettlementRuntime.class){
            if(api!=null)return api;
            Class<?> apiClass=Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
            Class<?> styleClass=Class.forName("net.blay09.mods.waystones.api.WaystoneStyle");
            Class<?> mutableClass=Class.forName("net.blay09.mods.waystones.api.MutableWaystone");
            Class<?> visibilityClass=Class.forName("net.blay09.mods.waystones.api.WaystoneVisibility");
            Constructor<?> styleCtor=styleClass.getConstructor(ResourceLocation.class);
            Object style=styleCtor.newInstance(ResourceLocation.fromNamespaceAndPath("waystones","waystone"));
            Method place=apiClass.getMethod("placeWaystone",Level.class,BlockPos.class,styleClass);
            Method getAll=apiClass.getMethod("getAllWaystones",net.minecraft.server.MinecraftServer.class);
            Class<?> waystoneClass=Class.forName("net.blay09.mods.waystones.api.Waystone");
            Class<?> managerClass=Class.forName("net.blay09.mods.waystones.core.WaystoneManagerImpl");
            Method managerGet=managerClass.getMethod("get",net.minecraft.server.MinecraftServer.class);
            Method managerUpdate=managerClass.getMethod("updateWaystone",waystoneClass);
            Method getPos=waystoneClass.getMethod("getPos");
            Method getDimension=waystoneClass.getMethod("getDimension");
            Method setName=mutableClass.getMethod("setName",Component.class);
            Method setVisibility=mutableClass.getMethod("setVisibility",visibilityClass);
            Method getName=null;
            try{getName=waystoneClass.getMethod("getName");}catch(NoSuchMethodException ignored){}
            Object global=Arrays.stream(visibilityClass.getEnumConstants()).filter(v->v instanceof Enum<?> e&&e.name().equals("GLOBAL")).findFirst().orElseThrow();
            api=new Api(place,getAll,getPos,getDimension,managerGet,managerUpdate,style,mutableClass,setName,setVisibility,getName,global);
            return api;
        }
    }

    private record Api(Method place,Method getAll,Method getPos,Method getDimension,Method managerGet,Method managerUpdate,Object style,Class<?> mutable,Method setName,Method setVisibility,Method getName,Object global){
        Optional<?> place(Level level,BlockPos pos) throws ReflectiveOperationException{return (Optional<?>)place.invoke(null,level,pos,style);}
        java.util.List<BlockPos> positions(ServerLevel level) throws ReflectiveOperationException{
            Object raw=getAll.invoke(null,level.getServer());if(!(raw instanceof Stream<?> stream))return java.util.List.of();
            try(stream){return stream.filter(w->{try{return level.dimension().equals(getDimension.invoke(w));}catch(ReflectiveOperationException ex){throw new WaystoneReflectionException(ex);}}).map(w->{try{return (BlockPos)getPos.invoke(w);}catch(ReflectiveOperationException ex){throw new WaystoneReflectionException(ex);}}).toList();}
            catch(WaystoneReflectionException ex){throw (ReflectiveOperationException)ex.getCause();}
        }
        void name(ServerLevel level,Object waystone,String name) throws ReflectiveOperationException{if(!mutable.isInstance(waystone))return;setName.invoke(waystone,Component.literal(name));setVisibility.invoke(waystone,global);Object manager=managerGet.invoke(null,level.getServer());if(manager!=null)managerUpdate.invoke(manager,waystone);}
        boolean isLivingRealmsNamed(ServerLevel level,BlockPos pos) throws ReflectiveOperationException{
            if(getName==null)return false;
            Object raw=getAll.invoke(null,level.getServer());if(!(raw instanceof Stream<?> stream))return false;
            try(stream){
                return stream.anyMatch(w->{
                    try{
                        if(!level.dimension().equals(getDimension.invoke(w)))return false;
                        if(!pos.equals(getPos.invoke(w)))return false;
                        Object nameObj=getName.invoke(w);
                        String text=nameObj instanceof Component c?c.getString():String.valueOf(nameObj);
                        return WaystoneProvenance.isLivingRealmsName(text);
                    }catch(ReflectiveOperationException ex){throw new WaystoneReflectionException(ex);}
                });
            }catch(WaystoneReflectionException ex){throw (ReflectiveOperationException)ex.getCause();}
        }
    }
    private static final class WaystoneReflectionException extends RuntimeException{WaystoneReflectionException(ReflectiveOperationException cause){super(cause);}}

    /** NBT helpers used by {@link LivingRealmsSavedData}. */
    public static void writeProvenance(CompoundTag tag,java.util.Map<Long,Long> settlementToPos){
        ListTag list=new ListTag();
        for(var e:settlementToPos.entrySet()){
            CompoundTag row=new CompoundTag();
            row.putLong("SettlementId",e.getKey());
            row.putLong("Pos",e.getValue());
            list.add(row);
        }
        tag.put("LrWaystones",list);
    }

    public static java.util.Map<Long,Long> readProvenance(CompoundTag tag){
        java.util.LinkedHashMap<Long,Long> out=new java.util.LinkedHashMap<>();
        if(!tag.contains("LrWaystones",Tag.TAG_LIST))return out;
        ListTag list=tag.getList("LrWaystones",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            CompoundTag row=list.getCompound(i);
            long sid=row.getLong("SettlementId");
            long pos=row.getLong("Pos");
            if(sid>0)out.put(sid,pos);
        }
        return out;
    }
}
