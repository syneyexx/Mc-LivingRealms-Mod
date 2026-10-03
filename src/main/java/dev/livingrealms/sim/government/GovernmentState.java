package dev.livingrealms.sim.government;

import dev.livingrealms.sim.util.Mathx;
import java.util.Objects;

public final class GovernmentState {
    private GovernmentType type;
    private SuccessionLaw successionLaw;
    private RulerProfile ruler;
    private double stability = .72;
    private double legitimacy = .70;
    private double corruption = .12;
    private double taxRate = .12;
    private double lawEnforcement = .45;
    private long yearsInPower;

    public GovernmentState(GovernmentType type, SuccessionLaw successionLaw, RulerProfile ruler) {
        this.type=Objects.requireNonNull(type); this.successionLaw=Objects.requireNonNull(successionLaw); this.ruler=Objects.requireNonNull(ruler);
    }
    public GovernmentType type(){return type;} public SuccessionLaw successionLaw(){return successionLaw;} public RulerProfile ruler(){return ruler;}
    public double stability(){return stability;} public double legitimacy(){return legitimacy;} public double corruption(){return corruption;} public double taxRate(){return taxRate;} public double lawEnforcement(){return lawEnforcement;} public long yearsInPower(){return yearsInPower;}
    public void setType(GovernmentType v){type=Objects.requireNonNull(v);} public void setSuccessionLaw(SuccessionLaw v){successionLaw=Objects.requireNonNull(v);} public void setRuler(RulerProfile v){ruler=Objects.requireNonNull(v);yearsInPower=0;}
    public void adjustStability(double v){stability=Mathx.clamp(stability+v,0,1);} public void adjustLegitimacy(double v){legitimacy=Mathx.clamp(legitimacy+v,0,1);} public void adjustCorruption(double v){corruption=Mathx.clamp(corruption+v,0,1);} public void setTaxRate(double v){taxRate=Mathx.clamp(v,0,.65);} public void setLawEnforcement(double v){lawEnforcement=Mathx.clamp(v,0,1);} public void advanceYear(){yearsInPower++;ruler.ageYear();}
    public void restore(double stability,double legitimacy,double corruption,double taxRate,double lawEnforcement,long yearsInPower){this.stability=Mathx.clamp(stability,0,1);this.legitimacy=Mathx.clamp(legitimacy,0,1);this.corruption=Mathx.clamp(corruption,0,1);this.taxRate=Mathx.clamp(taxRate,0,.65);this.lawEnforcement=Mathx.clamp(lawEnforcement,0,1);this.yearsInPower=Math.max(0,yearsInPower);}
}
