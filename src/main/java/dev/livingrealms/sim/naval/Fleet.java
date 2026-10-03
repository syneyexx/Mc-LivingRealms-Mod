package dev.livingrealms.sim.naval;

import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/** Canonical naval task force. One Fleet may materialize into a bounded number of ship entities near players. */
public final class Fleet {
    private final long id;
    private final long factionId;
    private final EnumMap<ShipClass,Integer> ships=new EnumMap<>(ShipClass.class);
    private SimPosition position;
    private long homePortId;
    private SimPosition targetPosition;
    private NavalMission mission=NavalMission.IDLE;
    private double fuel=1;
    private double readiness=.8;
    private double experience=.2;
    private double supply=1;
    private int embarkedPersonnel;

    public Fleet(long id,long factionId,long homePortId,SimPosition position,ShipClass initialClass,int count){
        if(id<=0||factionId<=0||homePortId<=0||position==null||initialClass==null||count<1)throw new IllegalArgumentException("fleet");
        this.id=id;this.factionId=factionId;this.homePortId=homePortId;this.position=position;this.targetPosition=position;
        for(ShipClass c:ShipClass.values())ships.put(c,0);ships.put(initialClass,count);
    }
    public long id(){return id;} public long factionId(){return factionId;} public long homePortId(){return homePortId;} public SimPosition position(){return position;} public SimPosition targetPosition(){return targetPosition;} public NavalMission mission(){return mission;} public double fuel(){return fuel;} public double readiness(){return readiness;} public double experience(){return experience;} public double supply(){return supply;} public int embarkedPersonnel(){return embarkedPersonnel;}
    public Map<ShipClass,Integer> ships(){return Collections.unmodifiableMap(new EnumMap<>(ships));}
    public int count(ShipClass c){return ships.getOrDefault(c,0);} public int totalShips(){return ships.values().stream().mapToInt(Integer::intValue).sum();} public boolean destroyed(){return totalShips()<=0;}
    public int troopCapacity(){return ships.entrySet().stream().mapToInt(e->e.getKey().cargoCapacity()*e.getValue()).sum();}
    public double combatPower(){double p=0;for(var e:ships.entrySet())p+=e.getValue()*(e.getKey().attack()+e.getKey().defense()*.4);return p*(.55+.45*readiness)*(.7+.6*experience)*(.65+.35*supply);}
    public double speed(){double result=Double.POSITIVE_INFINITY;for(var e:ships.entrySet())if(e.getValue()>0)result=Math.min(result,e.getKey().speed());return Double.isFinite(result)?result:0;}
    public double range(){double result=Double.POSITIVE_INFINITY;for(var e:ships.entrySet())if(e.getValue()>0)result=Math.min(result,e.getKey().range());return Double.isFinite(result)?result:0;}
    public double fuelDemand(){double demand=0;for(var e:ships.entrySet())demand+=e.getValue()*e.getKey().fuelCapacity();return demand*.01;}
    public void addShips(ShipClass c,int count){if(count<0)throw new IllegalArgumentException("count");ships.put(c,ships.getOrDefault(c,0)+count);}
    public void assign(NavalMission value,SimPosition target){mission=Objects.requireNonNull(value);targetPosition=target==null?position:target;}
    public void setHomePortId(long value){if(value<=0)throw new IllegalArgumentException("homePortId");homePortId=value;}
    public void embark(int personnel){if(personnel<0||embarkedPersonnel+personnel>troopCapacity())throw new IllegalArgumentException("personnel");embarkedPersonnel+=personnel;}
    public int disembarkAll(){int v=embarkedPersonnel;embarkedPersonnel=0;return v;}
    public void refuel(double amount){fuel=Mathx.clamp(fuel+Math.max(0,amount),0,1);} public void resupply(double amount){supply=Mathx.clamp(supply+Math.max(0,amount),0,1);} public void adjustReadiness(double amount){readiness=Mathx.clamp(readiness+amount,0,1);} public void gainExperience(double amount){experience=Mathx.clamp(experience+amount,0,1);}
    public void moveDay(){if(destroyed())return;double maxDistance=speed()*Math.max(.1,fuel)*(.6+.4*supply);double d=position.distanceTo(targetPosition);if(d<=maxDistance)position=targetPosition;else if(d>0)position=position.lerp(targetPosition,maxDistance/d);double range=Math.max(1,range());fuel=Mathx.clamp(fuel-Math.min(1,d/range)*.15,0,1);supply=Mathx.clamp(supply-.012*totalShips(),0,1);if(fuel<.12||supply<.12)mission=NavalMission.RETURN_TO_PORT;}
    public void loseShips(ShipClass c,int count){if(c==null||count<0)throw new IllegalArgumentException("ship loss");ships.put(c,Math.max(0,this.count(c)-count));if(count>0)readiness=Mathx.clamp(readiness-count*.025,0,1);embarkedPersonnel=Math.min(embarkedPersonnel,troopCapacity());}
    public void loseFraction(double fraction){fraction=Mathx.clamp(fraction,0,1);for(ShipClass c:ShipClass.values()){int before=count(c);int after=(int)Math.floor(before*(1-fraction));ships.put(c,Math.max(0,after));}readiness=Mathx.clamp(readiness-fraction*.55,0,1);embarkedPersonnel=Math.min(embarkedPersonnel,troopCapacity());}
    public void restore(Map<ShipClass,Integer> restoredShips,SimPosition restoredPosition,long restoredHomePortId,SimPosition restoredTarget,NavalMission restoredMission,double restoredFuel,double restoredReadiness,double restoredExperience,double restoredSupply,int restoredEmbarked){
        Objects.requireNonNull(restoredShips);for(ShipClass c:ShipClass.values())ships.put(c,Math.max(0,restoredShips.getOrDefault(c,0)));position=Objects.requireNonNull(restoredPosition);homePortId=restoredHomePortId;targetPosition=Objects.requireNonNull(restoredTarget);mission=Objects.requireNonNull(restoredMission);fuel=Mathx.clamp(restoredFuel,0,1);readiness=Mathx.clamp(restoredReadiness,0,1);experience=Mathx.clamp(restoredExperience,0,1);supply=Mathx.clamp(restoredSupply,0,1);embarkedPersonnel=Math.max(0,Math.min(restoredEmbarked,troopCapacity()));
    }
}
