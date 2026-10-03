package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Persistent historical trace of a settlement that lost its resident population. */
public final class RuinSite {
    private final long id,originalSettlementId,originalFactionId,createdDay;
    private final SimPosition position;
    private final String originalName,cause;
    private double preservation=.9;
    private boolean looted,reclaimed;
    public RuinSite(long id,long originalSettlementId,long originalFactionId,long createdDay,SimPosition position,String originalName,String cause){
        if(id<=0||originalSettlementId<=0||originalFactionId<=0||createdDay<0||position==null||originalName==null||originalName.isBlank())throw new IllegalArgumentException("ruin site");
        this.id=id;this.originalSettlementId=originalSettlementId;this.originalFactionId=originalFactionId;this.createdDay=createdDay;this.position=position;this.originalName=originalName;this.cause=Objects.requireNonNullElse(cause,"abandonment");
    }
    public long id(){return id;} public long originalSettlementId(){return originalSettlementId;} public long originalFactionId(){return originalFactionId;} public long createdDay(){return createdDay;} public SimPosition position(){return position;} public String originalName(){return originalName;} public String cause(){return cause;} public double preservation(){return preservation;} public boolean looted(){return looted;} public boolean reclaimed(){return reclaimed;} public boolean active(){return !reclaimed;}
    public void weather(double amount){if(Double.isFinite(amount)&&amount>0)preservation=Mathx.clamp(preservation-amount,0,1);} public void markLooted(){looted=true;} public void reclaim(){reclaimed=true;} public void restore(double preservation,boolean looted,boolean reclaimed){this.preservation=Mathx.clamp(preservation,0,1);this.looted=looted;this.reclaimed=reclaimed;}
}
