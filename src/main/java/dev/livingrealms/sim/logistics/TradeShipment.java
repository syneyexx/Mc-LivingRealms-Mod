package dev.livingrealms.sim.logistics;

import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Canonical off-screen shipment. Physical caravans are temporary projections of this state. */
public final class TradeShipment {
    private final long id;
    private final long sellerFactionId;
    private final long buyerFactionId;
    private final ResourceType resource;
    private final double amount;
    private final double value;
    private final SimPosition origin;
    private final SimPosition destination;
    private double progress;

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
    public void advanceDistance(double blocks){if(blocks<0||!Double.isFinite(blocks))throw new IllegalArgumentException("blocks");double d=distance();progress=d<1e-9?1.0:Math.min(1.0,progress+blocks/d);}
    public void restoreProgress(double value){if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("progress");progress=value;}
}
