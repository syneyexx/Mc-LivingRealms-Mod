package dev.livingrealms.sim.social;

import dev.livingrealms.sim.util.Mathx;

/** Satisfaction values where 1 is fully satisfied and 0 is urgent need. */
public final class CitizenNeeds {
    private double hunger=.8,safety=.8,social=.7,status=.5,comfort=.65;
    public double hunger(){return hunger;} public double safety(){return safety;} public double social(){return social;} public double status(){return status;} public double comfort(){return comfort;}
    public double overall(){return (hunger*1.35+safety*1.25+social+status*.65+comfort*.9)/5.15;}
    public void restore(double hunger,double safety,double social,double status,double comfort){this.hunger=b(hunger);this.safety=b(safety);this.social=b(social);this.status=b(status);this.comfort=b(comfort);}
    public void approach(double hunger,double safety,double social,double status,double comfort,double rate){double r=Mathx.clamp(rate,0,1);this.hunger=move(this.hunger,b(hunger),r);this.safety=move(this.safety,b(safety),r);this.social=move(this.social,b(social),r);this.status=move(this.status,b(status),r);this.comfort=move(this.comfort,b(comfort),r);}
    private static double move(double a,double b,double r){return Mathx.clamp(a+(b-a)*r,0,1);} private static double b(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("need");return Mathx.clamp(v,0,1);}
}
