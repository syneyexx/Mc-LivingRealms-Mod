package dev.livingrealms.sim.faction;

import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class Settlement {
    public enum Tier { CAMP, HAMLET, VILLAGE, TOWN, CITY, METROPOLIS }
    private final long id;
    private String name;
    private SimPosition position;
    private int population;
    private int housing;
    private double infrastructure;
    private Tier tier;
    private SettlementRole role;
    private final Set<String> completedConstruction=new LinkedHashSet<>();
    private final Map<String,ConstructionOrigin> constructionOrigins=new LinkedHashMap<>();
    private double prosperity=.5;
    private double unrest=.08;
    private double foodSecurity=.7;
    private double publicOrder=.65;
    private double employment=.75;
    private DevelopmentPriority developmentPriority=DevelopmentPriority.BALANCED;
    private final Stockpile stockpile=new Stockpile();
    private double barnCapacity=400;
    private double granaryCapacity=600;
    /** Geography is sidecar/runtime state; name heuristic bootstraps until world discovery authors it. */
    private SettlementGeographyProfile geography=SettlementGeographyProfile.unknown();
    private SettlementOrigin origin=SettlementOrigin.AUTHORED_SEED;
    private boolean physicallyAnchored;
    private DevelopmentMode developmentMode=DevelopmentMode.AUTO;
    /** Ephemeral: intent key of the active physical construction job near players (not saved). */
    private String activeConstructionKey="";
    /** Ephemeral priority landmark keys (grand projects / hero monuments) forcing planner backlog. */
    private final Set<String> priorityLandmarks=new LinkedHashSet<>();

    private long lastGrainBookDay = Long.MIN_VALUE;

    public Settlement(long id,String name,SimPosition pos,int pop,int housing){
        this(id,name,pos,pop,housing,SettlementOrigin.AUTHORED_SEED,false,DevelopmentMode.AUTO,null);
    }

    public Settlement(long id,String name,SimPosition pos,int pop,int housing,
                      SettlementOrigin origin,boolean physicallyAnchored,DevelopmentMode developmentMode){
        this(id,name,pos,pop,housing,origin,physicallyAnchored,developmentMode,null);
    }

    public Settlement(long id,String name,SimPosition pos,int pop,int housing,
                      SettlementOrigin origin,boolean physicallyAnchored,DevelopmentMode developmentMode,
                      SettlementRole role){
        if(id<=0)throw new IllegalArgumentException("id");
        if(name==null||name.isBlank())throw new IllegalArgumentException("name");
        if(pos==null)throw new IllegalArgumentException("position");
        this.id=id;this.name=name;this.position=pos;this.population=Math.max(0,pop);this.housing=Math.max(0,housing);
        this.origin=java.util.Objects.requireNonNull(origin,"origin");
        this.physicallyAnchored=physicallyAnchored||alwaysAnchored(origin);
        this.developmentMode=java.util.Objects.requireNonNull(developmentMode,"developmentMode");
        this.geography=SettlementGeographyProfile.fromNameHeuristic(name);
        recalc();
        this.role=role==null?SettlementRole.fromTier(this.tier):java.util.Objects.requireNonNull(role,"role");
        refreshStorageCapacity();seedStarterStores();
    }
    public long id(){return id;} public String name(){return name;} public SimPosition position(){return position;} public int population(){return population;} public int housing(){return housing;} public double infrastructure(){return infrastructure;} public Tier tier(){return tier;} public SettlementRole role(){return role;}
    public SettlementOrigin origin(){return origin;}
    public boolean physicallyAnchored(){return physicallyAnchored;}
    public DevelopmentMode developmentMode(){return developmentMode;}
    public void setDevelopmentMode(DevelopmentMode mode){developmentMode=java.util.Objects.requireNonNull(mode,"mode");}
    /** Codec/migration hook. Role is stable and intentionally independent from population tier. */
    public void restoreRole(SettlementRole value){role=java.util.Objects.requireNonNull(value,"role");}
    public void setOrigin(SettlementOrigin value){
        origin=java.util.Objects.requireNonNull(value,"origin");
        if(alwaysAnchored(origin))physicallyAnchored=true;
    }
    /** Marks the settlement as corresponding to real Minecraft geometry; irreversible. */
    public void markPhysicallyAnchored(){physicallyAnchored=true;}
    public void restoreProvenance(SettlementOrigin origin,boolean physicallyAnchored,DevelopmentMode mode){
        this.origin=java.util.Objects.requireNonNull(origin,"origin");
        this.physicallyAnchored=physicallyAnchored||alwaysAnchored(this.origin);
        this.developmentMode=java.util.Objects.requireNonNull(mode,"mode");
    }
    /**
     * Restricted relocate for explicit pre-materialization migration/admin paths only.
     * Anchored settlements refuse relocation — ordinary simulation must never teleport a real town.
     */
    public void relocate(SimPosition value){
        if(physicallyAnchored)throw new IllegalStateException("anchored settlement cannot relocate: "+name+" ("+id+")");
        position=java.util.Objects.requireNonNull(value,"position");
    }
    /**
     * Fresh-world bootstrap hook: terrain-resolved starter worldgen may align an AUTHORED_SEED
     * position after WORLDGEN receipts were predeclared, but never after runtime materialization.
     */
    public void alignStarterWorldgenPosition(SimPosition value){
        if(origin!=SettlementOrigin.AUTHORED_SEED)
            throw new IllegalStateException("non-starter settlement cannot worldgen-align: "+name+" ("+id+")");
        if(constructionOrigins.values().stream().anyMatch(v->v!=ConstructionOrigin.WORLDGEN))
            throw new IllegalStateException("materialized settlement cannot worldgen-align: "+name+" ("+id+")");
        position=java.util.Objects.requireNonNull(value,"position");
    }
    private static boolean alwaysAnchored(SettlementOrigin origin){
        return origin==SettlementOrigin.PLAYER_FOUNDED
                ||origin==SettlementOrigin.FOREIGN_ADOPTED
                ||origin==SettlementOrigin.WIZARD_TREES
                ||origin==SettlementOrigin.LEGACY;
    }
    public double prosperity(){return prosperity;} public double unrest(){return unrest;} public double foodSecurity(){return foodSecurity;} public double publicOrder(){return publicOrder;} public double employment(){return employment;} public DevelopmentPriority developmentPriority(){return developmentPriority;}
    public Stockpile stockpile(){return stockpile;} public double barnCapacity(){return barnCapacity;} public double granaryCapacity(){return granaryCapacity;}
    public SettlementGeographyProfile geography(){return geography;}
    public void setGeography(SettlementGeographyProfile value){geography=java.util.Objects.requireNonNull(value,"geography");}
    public void markGrainBooked(long day){lastGrainBookDay=day;}
    public boolean grainBookedForDay(long day){return lastGrainBookDay==day;}
    /** Derived specialization label for planners/UI; not a separate economic authority. */
    public SettlementSpecialization specialization(boolean capital,double technology){
        return SettlementSpecialization.derive(this,capital,technology,
                countPrefix("temple:")>0,countPrefix("school:")>0,countPrefix("barracks:")>0,
                countPrefix("factory:")>0,countPrefix("dock:")>0);
    }
    public double developmentScore(double education,double tradeConnectivity,double administration,double publicServices){
        return SettlementDevelopment.score(this,education,tradeConnectivity,administration,publicServices);
    }
    public Tier effectiveTier(double developmentScore){return SettlementDevelopment.effectiveTier(this,developmentScore);}
    public void rename(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("name");name=value;if(!geography.worldDiscovered())geography=SettlementGeographyProfile.fromNameHeuristic(name);}
    public void addPopulation(int n){Tier before=tier;population=Math.max(0,population+n);recalc();refreshStorageCapacity();if(before!=tier)emitTierChanged(before,tier);}
    public void addHousing(int n){Tier before=tier;housing=Math.max(0,housing+n);recalc();refreshStorageCapacity();if(before!=tier)emitTierChanged(before,tier);}
    /** Authoritative housing set when verified player/foreign capacity exceeds the canonical field. */
    public void setHousing(int value){Tier before=tier;housing=Math.max(0,value);recalc();refreshStorageCapacity();if(before!=tier)emitTierChanged(before,tier);}
    private void emitTierChanged(Tier previous,Tier next){if(previous==null||next==null||previous==next)return;dev.livingrealms.api.LivingRealmsApi.publish(new dev.livingrealms.api.event.SettlementTierChanged(0L,id,previous.name(),next.name(),population));}
    public void improveInfrastructure(double v){infrastructure=Math.max(0,infrastructure+v);refreshStorageCapacity();}
    public int housingShortage(){return Math.max(0,population-housing);}
    public Set<String> completedConstruction(){return Collections.unmodifiableSet(completedConstruction);}
    public Map<String,ConstructionOrigin> constructionOrigins(){return Collections.unmodifiableMap(constructionOrigins);}
    public boolean isConstructionCompleted(String key){return completedConstruction.contains(key);}
    public ConstructionOrigin constructionOrigin(String key){return constructionOrigins.getOrDefault(key,ConstructionOrigin.MATERIALIZED);}
    /** Headless/test receipt-equivalent: marks a Living Realms production-eligible completion. */
    public boolean markConstructionCompleted(String key){
        boolean changed=markConstruction(key,ConstructionOrigin.MATERIALIZED);
        if(changed){
            priorityLandmarks.remove(key);
            markPhysicallyAnchored();
            dev.livingrealms.api.LivingRealmsApi.publish(new dev.livingrealms.api.event.ConstructionCompleted(0L,id,key,ConstructionOrigin.MATERIALIZED.name()));
        }
        return changed;
    }
    /** Codec restore path — does not fire extension API events. */
    public void restoreConstructionCompleted(String key){
        if(key==null||key.isBlank())return;
        completedConstruction.add(key);
        constructionOrigins.putIfAbsent(key,ConstructionOrigin.MATERIALIZED);
        refreshStorageCapacity();
    }
    /** Foreign village footprint credit — never counted as Living Realms farm/mine production. */
    public boolean markForeignAdopted(String key){return markConstruction(key,ConstructionOrigin.FOREIGN_ADOPTED);}
    public boolean markConstruction(String key,ConstructionOrigin origin){
        if(key==null||key.isBlank())throw new IllegalArgumentException("key");
        ConstructionOrigin next=origin==null?ConstructionOrigin.MATERIALIZED:origin;
        boolean added=completedConstruction.add(key);
        ConstructionOrigin prev=constructionOrigins.put(key,next);
        if(added||prev!=next)refreshStorageCapacity();
        return added||prev!=next;
    }
    public void restoreConstructionOrigin(String key,ConstructionOrigin origin){
        if(key==null||key.isBlank()||!completedConstruction.contains(key))return;
        constructionOrigins.put(key,origin==null?ConstructionOrigin.MATERIALIZED:origin);
    }
    public String activeConstructionKey(){return activeConstructionKey;}
    public void setActiveConstructionKey(String key){activeConstructionKey=key==null?"":key;}
    public Set<String> priorityLandmarks(){return Collections.unmodifiableSet(priorityLandmarks);}
    public void requestLandmark(String key){if(key==null||key.isBlank())throw new IllegalArgumentException("key");if(!completedConstruction.contains(key))priorityLandmarks.add(key);}
    /**
     * Removes one LivingRealms-authored structure matching a prefix (e.g. wall:/gate:/keep:).
     * Used for siege damage against simulation construction keys — never player/foreign builds.
     */
    public String damageAuthoredStructure(String prefix){
        if(prefix==null||prefix.isBlank())throw new IllegalArgumentException("prefix");
        String hit=completedConstruction.stream().filter(k->k.startsWith(prefix)).findFirst().orElse(null);
        if(hit==null)return null;
        completedConstruction.remove(hit);constructionOrigins.remove(hit);refreshStorageCapacity();improveInfrastructure(-.015);adjustUnrest(.02);adjustProsperity(-.01);
        return hit;
    }
    /** Removes every LivingRealms-authored key with the given prefix (siege breach thresholds). */
    public int damageAllAuthoredStructures(String prefix){
        if(prefix==null||prefix.isBlank())throw new IllegalArgumentException("prefix");
        var hits=completedConstruction.stream().filter(k->k.startsWith(prefix)).toList();
        for(String hit:hits){completedConstruction.remove(hit);constructionOrigins.remove(hit);}
        if(!hits.isEmpty()){refreshStorageCapacity();improveInfrastructure(-.015*hits.size());adjustUnrest(.02*Math.min(3,hits.size()));adjustProsperity(-.01*Math.min(3,hits.size()));}
        return hits.size();
    }
    /** One-shot content migration hook used when an authored blueprint system changes incompatibly. */
    public int resetConstructionCompletion(){int count=completedConstruction.size();completedConstruction.clear();constructionOrigins.clear();refreshStorageCapacity();return count;}
    public void adjustProsperity(double v){prosperity=Mathx.clamp(prosperity+v,0,1);} public void adjustUnrest(double v){unrest=Mathx.clamp(unrest+v,0,1);}
    public void setFoodSecurity(double v){foodSecurity=Mathx.clamp(v,0,1);} public void setPublicOrder(double v){publicOrder=Mathx.clamp(v,0,1);} public void setEmployment(double v){employment=Mathx.clamp(v,0,1);}
    public void restoreSociety(double prosperity,double unrest,double foodSecurity,double publicOrder,double employment){this.prosperity=Mathx.clamp(prosperity,0,1);this.unrest=Mathx.clamp(unrest,0,1);this.foodSecurity=Mathx.clamp(foodSecurity,0,1);this.publicOrder=Mathx.clamp(publicOrder,0,1);this.employment=Mathx.clamp(employment,0,1);}
    public void setDevelopmentPriority(DevelopmentPriority value){developmentPriority=java.util.Objects.requireNonNull(value);}
    public void restoreEconomy(double barnCapacity,double granaryCapacity,java.util.Map<ResourceType,Double> stores){
        this.barnCapacity=Math.max(50,barnCapacity);this.granaryCapacity=Math.max(80,granaryCapacity);
        if(stores!=null){
            for(ResourceType type:ResourceType.values())stockpile().set(type,0);
            for(var e:stores.entrySet())stockpile().set(e.getKey(),Math.max(0,e.getValue()));
        }
        enforceStorageCaps();
    }
    public void refreshStorageCapacity(){
        int warehouses=countPrefix("warehouse:");
        int farms=countPrefix("farm:");
        int markets=countPrefix("market:");
        // Population-scaled barns/granaries: enough for a season+ of local stores, not a tiny chest.
        barnCapacity=Math.max(400,population*8.0+farms*120+warehouses*280+infrastructure*60);
        granaryCapacity=Math.max(600,population*45.0+farms*180+markets*100+warehouses*220+infrastructure*80);
    }
    public void enforceStorageCaps(){
        double edible=edibleStock();
        if(edible>granaryCapacity){
            double scale=granaryCapacity/Math.max(1,edible);
            for(ResourceType type:new ResourceType[]{ResourceType.FOOD,ResourceType.GRAIN,ResourceType.FLOUR,ResourceType.BREAD,ResourceType.MEAT,ResourceType.ALE})
                stockpile.set(type,stockpile.get(type)*scale);
        }
        double bulk=stockpile.get(ResourceType.WOOD)+stockpile.get(ResourceType.STONE)+stockpile.get(ResourceType.TEXTILES)+stockpile.get(ResourceType.WOOL);
        if(bulk>barnCapacity){
            double scale=barnCapacity/Math.max(1,bulk);
            stockpile.set(ResourceType.WOOD,stockpile.get(ResourceType.WOOD)*scale);
            stockpile.set(ResourceType.STONE,stockpile.get(ResourceType.STONE)*scale);
            stockpile.set(ResourceType.TEXTILES,stockpile.get(ResourceType.TEXTILES)*scale);
            stockpile.set(ResourceType.WOOL,stockpile.get(ResourceType.WOOL)*scale);
        }
    }
    public double edibleStock(){
        double total=0;
        for(ResourceType type:new ResourceType[]{ResourceType.FOOD,ResourceType.GRAIN,ResourceType.FLOUR,ResourceType.BREAD,ResourceType.MEAT,ResourceType.ALE})
            total+=stockpile.get(type)*ResourceType.foodValue(type);
        return total;
    }
    private void seedStarterStores(){
        if(stockpile.get(ResourceType.FOOD)>0||stockpile.get(ResourceType.GRAIN)>0||stockpile.get(ResourceType.BREAD)>0)return;
        // ~90 winter days of buffer so worlds seeded mid-cycle do not instantly starve.
        double buffer=Math.max(80,population*18.0);
        stockpile.add(ResourceType.GRAIN,buffer*0.55);
        stockpile.add(ResourceType.BREAD,buffer*0.45);
        stockpile.add(ResourceType.WOOD,Math.max(30,population*1.2));
        stockpile.add(ResourceType.STONE,Math.max(15,population*.5));
        stockpile.add(ResourceType.WOOL,Math.max(5,population*.08));
        stockpile.add(ResourceType.TEXTILES,Math.max(10,population*.25));
        enforceStorageCaps();
    }
    /** Production-eligible keys only — FOREIGN_ADOPTED footprints do not mint Living Realms yield. */
    public int countProductionPrefix(String prefix){
        return (int)completedConstruction.stream()
                .filter(k->k.startsWith(prefix))
                .filter(k->constructionOrigin(k)!=ConstructionOrigin.FOREIGN_ADOPTED)
                .count();
    }
    private int countPrefix(String prefix){return (int)completedConstruction.stream().filter(k->k.startsWith(prefix)).count();}
    private void recalc(){tier=population<25?Tier.CAMP:population<100?Tier.HAMLET:population<500?Tier.VILLAGE:population<2000?Tier.TOWN:population<10000?Tier.CITY:Tier.METROPOLIS;}
}
