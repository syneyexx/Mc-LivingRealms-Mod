package dev.livingrealms.sim.logistics;

import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Canonical off-screen shipment. Physical caravans are temporary projections of this state. */
public final class TradeShipment {
    public enum LossState { NONE,PARTIAL,TOTAL }

    private final long id;
    private long sellerFactionId;
    private long buyerFactionId;
    private final ResourceType resource;
    private final double amount;
    private final double value;
    private final SimPosition origin;
    private final SimPosition destination;
    private double progress;
    private long originSettlementId,destinationSettlementId,routeId;
    private int transportModeOrdinal=-1;
    private long departureDay,expectedArrivalDay;
    private double risk,escortStrength;
    private LossState lossState=LossState.NONE;
    private int delayDays;

    public TradeShipment(long id,long sellerFactionId,long buyerFactionId,ResourceType resource,double amount,double value,SimPosition origin,SimPosition destination) {
        if(id<=0||sellerFactionId<=0||buyerFactionId<=0||sellerFactionId==buyerFactionId) throw new IllegalArgumentException("ids");
        if(amount<=0||value<0||!Double.isFinite(amount)||!Double.isFinite(value)) throw new IllegalArgumentException("amount/value");
        this.id=id;this.sellerFactionId=sellerFactionId;this.buyerFactionId=buyerFactionId;
        this.resource=Objects.requireNonNull(resource,"resource");this.amount=amount;this.value=value;
        this.origin=Objects.requireNonNull(origin,"origin");this.destination=Objects.requireNonNull(destination,"destination");
    }
    public long id(){return id;} public long sellerFactionId(){return sellerFactionId;} public long buyerFactionId(){return buyerFactionId;}
    public ResourceType resource(){return resource;} public double amount(){return amount;} public double value(){return value;}
    public SimPosition origin(){return origin;} public SimPosition destination(){return destination;} public double progress(){return progress;}
    public double distance(){return origin.distanceTo(destination);} public boolean arrived(){return progress>=1.0-1e-12;}
    public SimPosition position(){return origin.lerp(destination,progress);}
    public long originSettlementId(){return originSettlementId;} public long destinationSettlementId(){return destinationSettlementId;}
    public long routeId(){return routeId;} public int transportModeOrdinal(){return transportModeOrdinal;}
    public long departureDay(){return departureDay;} public long expectedArrivalDay(){return expectedArrivalDay;}
    public double risk(){return risk;} public double escortStrength(){return escortStrength;} public LossState lossState(){return lossState;} public int delayDays(){return delayDays;}

    public void advanceDistance(double blocks){if(blocks<0||!Double.isFinite(blocks))throw new IllegalArgumentException("blocks");double d=distance();progress=d<1e-9?1.0:Math.min(1.0,progress+blocks/d);}
    public void restoreProgress(double value){if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("progress");progress=value;}
    public void setEscortStrength(double value){if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("escortStrength");escortStrength=value;}
    public void transferSellerFaction(long factionId){if(factionId<=0)throw new IllegalArgumentException("seller faction");sellerFactionId=factionId;}
    public void transferBuyerFaction(long factionId){if(factionId<=0)throw new IllegalArgumentException("buyer faction");buyerFactionId=factionId;}

    public void restoreLogistics(long originSettlementId,long destinationSettlementId,long routeId,int transportModeOrdinal,long departureDay,long expectedArrivalDay,double risk,double escortStrength,LossState lossState,int delayDays){
        if(originSettlementId<0||destinationSettlementId<0||routeId<0||transportModeOrdinal<-1||departureDay<0||expectedArrivalDay<0||delayDays<0)throw new IllegalArgumentException("logistics ids");
        if(!Double.isFinite(risk)||risk<0||risk>1||!Double.isFinite(escortStrength)||escortStrength<0)throw new IllegalArgumentException("logistics risk");
        this.originSettlementId=originSettlementId;this.destinationSettlementId=destinationSettlementId;this.routeId=routeId;this.transportModeOrdinal=transportModeOrdinal;
        this.departureDay=departureDay;this.expectedArrivalDay=expectedArrivalDay;this.risk=Mathx.clamp(risk,0,1);this.escortStrength=escortStrength;
        this.lossState=Objects.requireNonNullElse(lossState,LossState.NONE);this.delayDays=delayDays;
    }
}
