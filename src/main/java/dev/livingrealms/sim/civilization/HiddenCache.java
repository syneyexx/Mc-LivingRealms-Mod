package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/** Wealth/goods hidden during severe threats. Knowledge is intentionally scoped to the owner or the faction that compromised it. */
public final class HiddenCache {
    private final long id,factionId,settlementId,createdDay;
    private final SimPosition position;
    private final EnumMap<ResourceType,Double> goods=new EnumMap<>(ResourceType.class);
    private long discoveredByFactionId;
    private boolean recovered;
    public HiddenCache(long id,long factionId,long settlementId,long createdDay,SimPosition position,Map<ResourceType,Double> goods){if(id<=0||factionId<=0||settlementId<=0||createdDay<0||position==null)throw new IllegalArgumentException("hidden cache");this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.createdDay=createdDay;this.position=position;for(ResourceType r:ResourceType.values())this.goods.put(r,Math.max(0,goods==null?0:goods.getOrDefault(r,0.0)));}
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public long createdDay(){return createdDay;} public SimPosition position(){return position;} public Map<ResourceType,Double> goods(){return Collections.unmodifiableMap(goods);} public boolean discovered(){return discoveredByFactionId>0;} public long discoveredByFactionId(){return discoveredByFactionId;} public boolean recovered(){return recovered;} public double value(){return goods.values().stream().mapToDouble(Double::doubleValue).sum();}
    public boolean knownToFaction(long viewerFactionId){return viewerFactionId>0&&(viewerFactionId==factionId||viewerFactionId==discoveredByFactionId);}
    public void discover(long finderFactionId){if(finderFactionId<=0||finderFactionId==factionId)return;if(discoveredByFactionId==0)discoveredByFactionId=finderFactionId;}
    public void recover(){recovered=true;}
    public void restore(long discoveredByFactionId,boolean recovered){if(discoveredByFactionId<0)throw new IllegalArgumentException("hidden cache discovery");this.discoveredByFactionId=discoveredByFactionId;this.recovered=recovered;}
}
