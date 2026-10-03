package dev.livingrealms.sim.transport;

import dev.livingrealms.sim.util.Mathx;

/** Canonical infrastructure route between two settlements. */
public final class TransportRoute {
    private final long id;
    private final long ownerFactionId;
    private final long fromSettlementId;
    private final long toSettlementId;
    private final TransportMode mode;
    private final double distanceBlocks;
    private double quality;
    private double security;
    private double capacityPerDay;
    private boolean operational=true;
    public TransportRoute(long id,long ownerFactionId,long fromSettlementId,long toSettlementId,TransportMode mode,double distanceBlocks,double quality,double security,double capacityPerDay){if(id<=0||ownerFactionId<=0||fromSettlementId<=0||toSettlementId<=0||fromSettlementId==toSettlementId)throw new IllegalArgumentException("route identity");if(mode==null)throw new IllegalArgumentException("mode");if(!(distanceBlocks>0)||!(capacityPerDay>0))throw new IllegalArgumentException("route numeric");this.id=id;this.ownerFactionId=ownerFactionId;this.fromSettlementId=fromSettlementId;this.toSettlementId=toSettlementId;this.mode=mode;this.distanceBlocks=distanceBlocks;this.quality=Mathx.clamp(quality,0,1);this.security=Mathx.clamp(security,0,1);this.capacityPerDay=capacityPerDay;}
    public long id(){return id;} public long ownerFactionId(){return ownerFactionId;} public long fromSettlementId(){return fromSettlementId;} public long toSettlementId(){return toSettlementId;} public TransportMode mode(){return mode;} public double distanceBlocks(){return distanceBlocks;} public double quality(){return quality;} public double security(){return security;} public double capacityPerDay(){return capacityPerDay;} public boolean operational(){return operational;}
    public double speedBlocksPerDay(){return baseSpeed(mode)*(0.55+quality*.65);}
    public void improve(double amount){quality=Mathx.clamp(quality+amount,0,1);} public void adjustSecurity(double amount){security=Mathx.clamp(security+amount,0,1);} public void setOperational(boolean v){operational=v;} public void restore(double q,double s,double c,boolean op){quality=Mathx.clamp(q,0,1);security=Mathx.clamp(s,0,1);capacityPerDay=Math.max(.01,c);operational=op;}
    public static double baseSpeed(TransportMode mode){return switch(mode){case FOOT->85;case CARAVAN->180;case ROAD->280;case RAIL->950;case RIVER->260;case SHIP->520;case AIR->2400;};}
}
