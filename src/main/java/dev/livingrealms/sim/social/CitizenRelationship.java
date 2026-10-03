package dev.livingrealms.sim.social;

import dev.livingrealms.sim.util.Mathx;
import java.util.Objects;

/** Persistent directed relationship from one person to another actor/citizen. */
public final class CitizenRelationship {
    private final String targetKey; private double friendship,hostility,romance,rivalry,trust=.5; private FamilyBond familyBond=FamilyBond.NONE;
    public CitizenRelationship(String targetKey){if(targetKey==null||targetKey.isBlank())throw new IllegalArgumentException("targetKey");this.targetKey=targetKey;}
    public String targetKey(){return targetKey;} public double friendship(){return friendship;} public double hostility(){return hostility;} public double romance(){return romance;} public double rivalry(){return rivalry;} public double trust(){return trust;} public FamilyBond familyBond(){return familyBond;}
    public void adjust(double friendship,double hostility,double romance,double rivalry,double trust){this.friendship=b(this.friendship+friendship);this.hostility=b(this.hostility+hostility);this.romance=b(this.romance+romance);this.rivalry=b(this.rivalry+rivalry);this.trust=b(this.trust+trust);}
    public void setFamilyBond(FamilyBond value){familyBond=Objects.requireNonNull(value);}
    public void restore(double friendship,double hostility,double romance,double rivalry,double trust,FamilyBond bond){this.friendship=b(friendship);this.hostility=b(hostility);this.romance=b(romance);this.rivalry=b(rivalry);this.trust=b(trust);this.familyBond=Objects.requireNonNull(bond);}
    public double sentiment(){return Mathx.clamp(friendship+trust*.45-hostility-rivalry*.45,-1,1);}
    private static double b(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("relationship");return Mathx.clamp(v,0,1);}
}
