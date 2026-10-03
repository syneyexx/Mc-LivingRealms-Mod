package dev.livingrealms.sim.faction;

import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Settlement {
    public enum Tier { CAMP, HAMLET, VILLAGE, TOWN, CITY, METROPOLIS }
    private final long id;
    private String name;
    private final SimPosition position;
    private int population;
    private int housing;
    private double infrastructure;
    private Tier tier;
    private final Set<String> completedConstruction=new LinkedHashSet<>();
    private double prosperity=.5;
    private double unrest=.08;
    private double foodSecurity=.7;
    private double publicOrder=.65;
    private double employment=.75;
    private DevelopmentPriority developmentPriority=DevelopmentPriority.BALANCED;

    public Settlement(long id,String name,SimPosition pos,int pop,int housing){
        if(id<=0)throw new IllegalArgumentException("id");
        if(name==null||name.isBlank())throw new IllegalArgumentException("name");
        if(pos==null)throw new IllegalArgumentException("position");
        this.id=id;this.name=name;this.position=pos;this.population=Math.max(0,pop);this.housing=Math.max(0,housing);recalc();
    }
    public long id(){return id;} public String name(){return name;} public SimPosition position(){return position;} public int population(){return population;} public int housing(){return housing;} public double infrastructure(){return infrastructure;} public Tier tier(){return tier;}
    public double prosperity(){return prosperity;} public double unrest(){return unrest;} public double foodSecurity(){return foodSecurity;} public double publicOrder(){return publicOrder;} public double employment(){return employment;} public DevelopmentPriority developmentPriority(){return developmentPriority;}
    public void rename(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("name");name=value;} public void addPopulation(int n){population=Math.max(0,population+n);recalc();} public void addHousing(int n){housing=Math.max(0,housing+n);recalc();} public void improveInfrastructure(double v){infrastructure=Math.max(0,infrastructure+v);}
    public int housingShortage(){return Math.max(0,population-housing);}
    public Set<String> completedConstruction(){return Collections.unmodifiableSet(completedConstruction);}
    public boolean isConstructionCompleted(String key){return completedConstruction.contains(key);}
    public boolean markConstructionCompleted(String key){if(key==null||key.isBlank())throw new IllegalArgumentException("key");return completedConstruction.add(key);}
    /** One-shot content migration hook used when an authored blueprint system changes incompatibly. */
    public int resetConstructionCompletion(){int count=completedConstruction.size();completedConstruction.clear();return count;}
    public void adjustProsperity(double v){prosperity=Mathx.clamp(prosperity+v,0,1);} public void adjustUnrest(double v){unrest=Mathx.clamp(unrest+v,0,1);}
    public void setFoodSecurity(double v){foodSecurity=Mathx.clamp(v,0,1);} public void setPublicOrder(double v){publicOrder=Mathx.clamp(v,0,1);} public void setEmployment(double v){employment=Mathx.clamp(v,0,1);}
    public void restoreSociety(double prosperity,double unrest,double foodSecurity,double publicOrder,double employment){this.prosperity=Mathx.clamp(prosperity,0,1);this.unrest=Mathx.clamp(unrest,0,1);this.foodSecurity=Mathx.clamp(foodSecurity,0,1);this.publicOrder=Mathx.clamp(publicOrder,0,1);this.employment=Mathx.clamp(employment,0,1);}
    public void setDevelopmentPriority(DevelopmentPriority value){developmentPriority=java.util.Objects.requireNonNull(value);}
    private void recalc(){tier=population<25?Tier.CAMP:population<100?Tier.HAMLET:population<500?Tier.VILLAGE:population<2000?Tier.TOWN:population<10000?Tier.CITY:Tier.METROPOLIS;}
}
