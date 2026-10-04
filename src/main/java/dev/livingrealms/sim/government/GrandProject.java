package dev.livingrealms.sim.government;

import dev.livingrealms.sim.construction.ConstructionPhase;
import dev.livingrealms.sim.util.Mathx;
import java.util.Objects;

/** Multi-year civic megaproject with materials, labor and pause reasons. */
public final class GrandProject {
    private final long id,sponsorFactionId,settlementId,createdDay;
    private final GrandProjectType type;
    private final double cost,foodNeed,timberNeed,stoneNeed,ironNeed;
    private final int laborNeed;
    private double progress,spentTreasury,spentFood,spentTimber,spentStone,spentIron;
    private int laborApplied;
    private ConstructionPhase phase=ConstructionPhase.CLEAR;
    private String pauseReason="";
    private boolean complete,active=true;
    private boolean effectInfrastructure,effectProsperity,effectLegitimacy,effectLegend;

    public GrandProject(long id,long sponsorFactionId,long settlementId,GrandProjectType type,long createdDay){
        if(id<=0||sponsorFactionId<=0||settlementId<=0||type==null||createdDay<0)throw new IllegalArgumentException("grand project");
        this.id=id;this.sponsorFactionId=sponsorFactionId;this.settlementId=settlementId;this.type=type;this.createdDay=createdDay;
        this.cost=type.baseCost();this.foodNeed=type.foodNeed();this.timberNeed=type.timberNeed();this.stoneNeed=type.stoneNeed();this.ironNeed=type.ironNeed();this.laborNeed=type.laborNeed();
    }

    public long id(){return id;} public long sponsorFactionId(){return sponsorFactionId;} public long settlementId(){return settlementId;}
    public GrandProjectType type(){return type;} public long createdDay(){return createdDay;} public double cost(){return cost;}
    public double foodNeed(){return foodNeed;} public double timberNeed(){return timberNeed;} public double stoneNeed(){return stoneNeed;} public double ironNeed(){return ironNeed;}
    public int laborNeed(){return laborNeed;} public double progress(){return progress;} public ConstructionPhase phase(){return phase;}
    public String pauseReason(){return pauseReason;} public boolean complete(){return complete;} public boolean active(){return active;}
    public double spentTreasury(){return spentTreasury;} public double spentFood(){return spentFood;} public double spentTimber(){return spentTimber;} public double spentStone(){return spentStone;} public double spentIron(){return spentIron;} public int laborApplied(){return laborApplied;}
    public boolean effectInfrastructure(){return effectInfrastructure;} public boolean effectProsperity(){return effectProsperity;}
    public boolean effectLegitimacy(){return effectLegitimacy;} public boolean effectLegend(){return effectLegend;}

    public void setPauseReason(String reason){pauseReason=Objects.requireNonNullElse(reason,"");}
    public void advance(double treasurySpend,double food,double timber,double stone,double iron,int labor){
        if(complete||!active)return;
        if(treasurySpend<0||food<0||timber<0||stone<0||iron<0||labor<0)throw new IllegalArgumentException("project spend");
        if(!Double.isFinite(treasurySpend+food+timber+stone+iron))throw new IllegalArgumentException("project spend finite");
        spentTreasury+=treasurySpend;spentFood+=food;spentTimber+=timber;spentStone+=stone;spentIron+=iron;laborApplied+=labor;
        double materialFrac=Math.min(1,spentFood/Math.max(1e-9,foodNeed))*.2
                +Math.min(1,spentTimber/Math.max(1e-9,timberNeed))*.25
                +Math.min(1,spentStone/Math.max(1e-9,stoneNeed))*.25
                +Math.min(1,spentIron/Math.max(1e-9,ironNeed))*.1
                +Math.min(1,spentTreasury/Math.max(1e-9,cost))*.1
                +Math.min(1,(double)laborApplied/Math.max(1,laborNeed))*.1;
        progress=Mathx.clamp(materialFrac,0,1);
        phase=progress<.15?ConstructionPhase.CLEAR:progress<.35?ConstructionPhase.FOUNDATION:progress<.6?ConstructionPhase.FRAME:progress<.85?ConstructionPhase.SHELL:ConstructionPhase.DETAIL;
        pauseReason="";
        if(progress>=1.0-1e-9){complete=true;active=false;effectInfrastructure=true;effectProsperity=true;effectLegitimacy=type==GrandProjectType.PALACE||type==GrandProjectType.CATHEDRAL||type==GrandProjectType.CIVIC_MONUMENT;effectLegend=type==GrandProjectType.PALACE||type==GrandProjectType.CATHEDRAL||type==GrandProjectType.UNIVERSITY||type==GrandProjectType.CIVIC_MONUMENT;}
    }
    public void restore(double progress,ConstructionPhase phase,String pauseReason,boolean complete,boolean active,
                        double spentTreasury,double spentFood,double spentTimber,double spentStone,double spentIron,int laborApplied,
                        boolean effectInfrastructure,boolean effectProsperity,boolean effectLegitimacy,boolean effectLegend){
        if(!Double.isFinite(progress)||progress<0||progress>1||phase==null)throw new IllegalArgumentException("project restore");
        this.progress=progress;this.phase=phase;this.pauseReason=Objects.requireNonNullElse(pauseReason,"");
        this.complete=complete;this.active=active&&!complete;this.spentTreasury=Math.max(0,spentTreasury);this.spentFood=Math.max(0,spentFood);
        this.spentTimber=Math.max(0,spentTimber);this.spentStone=Math.max(0,spentStone);this.spentIron=Math.max(0,spentIron);
        this.laborApplied=Math.max(0,laborApplied);this.effectInfrastructure=effectInfrastructure;this.effectProsperity=effectProsperity;
        this.effectLegitimacy=effectLegitimacy;this.effectLegend=effectLegend;
    }
}
