package dev.livingrealms.sim.faction;
import dev.livingrealms.sim.util.Mathx;
public final class DiplomaticRelation {
    private double opinion; // -100..100
    private RelationStatus status=RelationStatus.NEUTRAL;
    private boolean tradeAgreement;
    public double opinion(){return opinion;} public RelationStatus status(){return status;} public boolean tradeAgreement(){return tradeAgreement;}
    public void adjust(double delta){opinion=Mathx.clamp(opinion+delta,-100,100); recalc();}
    public void declareWar(){status=RelationStatus.WAR; opinion=Math.min(opinion,-70); tradeAgreement=false;}
    public void makePeace(){status=RelationStatus.HOSTILE; opinion=Math.max(opinion,-60);}
    public void setTradeAgreement(boolean v){tradeAgreement=v && status!=RelationStatus.WAR;}
    public void restore(double opinion, RelationStatus status, boolean tradeAgreement){this.opinion=Mathx.clamp(opinion,-100,100);this.status=status;this.tradeAgreement=tradeAgreement && status!=RelationStatus.WAR;}
    private void recalc(){ if(status==RelationStatus.WAR)return; status = opinion>=70?RelationStatus.ALLIED: opinion>=30?RelationStatus.FRIENDLY: opinion>-25?RelationStatus.NEUTRAL: opinion>-60?RelationStatus.RIVAL:RelationStatus.HOSTILE; }
}
