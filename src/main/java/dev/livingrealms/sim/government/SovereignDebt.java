package dev.livingrealms.sim.government;

import dev.livingrealms.sim.util.Mathx;

/** Treasury borrowing instrument backed by merchant houses, peer factions, or named actors. */
public final class SovereignDebt {
    private final long id,debtorFactionId,createdDay,dueDay;
    private final String creditorKey;
    private final double principal,interestRate;
    private double remaining,risk;
    private boolean defaulted,active=true;

    public SovereignDebt(long id,long debtorFactionId,String creditorKey,double principal,double interestRate,long createdDay,long dueDay,double risk){
        if(id<=0||debtorFactionId<=0||creditorKey==null||creditorKey.isBlank())throw new IllegalArgumentException("debt identity");
        if(principal<=0||!Double.isFinite(principal)||interestRate<0||!Double.isFinite(interestRate))throw new IllegalArgumentException("debt terms");
        if(createdDay<0||dueDay<createdDay||!Double.isFinite(risk)||risk<0||risk>1)throw new IllegalArgumentException("debt schedule");
        this.id=id;this.debtorFactionId=debtorFactionId;this.creditorKey=creditorKey;this.principal=principal;this.interestRate=interestRate;
        this.remaining=principal;this.createdDay=createdDay;this.dueDay=dueDay;this.risk=risk;
    }

    public long id(){return id;} public long debtorFactionId(){return debtorFactionId;} public String creditorKey(){return creditorKey;}
    public double principal(){return principal;} public double interestRate(){return interestRate;} public double remaining(){return remaining;}
    public long createdDay(){return createdDay;} public long dueDay(){return dueDay;} public double risk(){return risk;}
    public boolean defaulted(){return defaulted;} public boolean active(){return active;}

    public double accrueInterest(double fractionOfYear){
        if(!active||defaulted||fractionOfYear<0||!Double.isFinite(fractionOfYear))return 0;
        double interest=remaining*interestRate*fractionOfYear;remaining+=interest;return interest;
    }
    public double service(double payment){
        if(!active||defaulted||payment<0||!Double.isFinite(payment))throw new IllegalArgumentException("debt service");
        double paid=Math.min(remaining,payment);remaining-=paid;if(remaining<=1e-9){remaining=0;active=false;}return paid;
    }
    public void markDefaulted(){defaulted=true;active=false;}
    public void restore(double remaining,double risk,boolean defaulted,boolean active){
        if(!Double.isFinite(remaining)||remaining<0||!Double.isFinite(risk)||risk<0||risk>1)throw new IllegalArgumentException("debt restore");
        this.remaining=remaining;this.risk=Mathx.clamp(risk,0,1);this.defaulted=defaulted;this.active=active&&!defaulted&&remaining>0;
    }
}
