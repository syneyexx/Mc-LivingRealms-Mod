package dev.livingrealms.sim.faction;

import dev.livingrealms.sim.government.*;
import java.util.*;

public final class Faction {
    private final long id;
    private String name;
    private String rulerName;
    private final Stockpile stockpile=new Stockpile();
    private final List<Settlement> settlements=new ArrayList<>();
    private final List<Army> armies=new ArrayList<>();
    private final Map<Long,DiplomaticRelation> relations=new HashMap<>();
    private double treasury=1000;
    private double technology=.1;
    private GovernmentState government;

    public Faction(long id,String name,String rulerName){
        if(id<=0)throw new IllegalArgumentException("id");
        if(name==null||name.isBlank())throw new IllegalArgumentException("name");
        if(rulerName==null||rulerName.isBlank())throw new IllegalArgumentException("rulerName");
        this.id=id;this.name=name;this.rulerName=rulerName;
        long rulerId=id==Long.MAX_VALUE?id:id+1;
        this.government=new GovernmentState(GovernmentType.FEUDAL_MONARCHY,SuccessionLaw.HEREDITARY,
                new RulerProfile(rulerId,rulerName,36,.95,.55,.55,.55,.72));
    }
    public long id(){return id;} public String name(){return name;} public String rulerName(){return rulerName;} public Stockpile stockpile(){return stockpile;} public List<Settlement> settlements(){return Collections.unmodifiableList(settlements);} public List<Army> armies(){return Collections.unmodifiableList(armies);} public double treasury(){return treasury;} public double technology(){return technology;} public GovernmentState government(){return government;}
    public int population(){return settlements.stream().mapToInt(Settlement::population).sum();}
    public void addSettlement(Settlement s){settlements.add(Objects.requireNonNull(s));}
    public Settlement removeSettlement(long settlementId){for(var it=settlements.iterator();it.hasNext();){Settlement s=it.next();if(s.id()==settlementId){it.remove();return s;}}return null;}
    public void addArmy(Army a){armies.add(Objects.requireNonNull(a));}
    public Army removeArmy(long armyId){for(var it=armies.iterator();it.hasNext();){Army a=it.next();if(a.id()==armyId){it.remove();return a;}}return null;}
    public DiplomaticRelation relationWith(long otherId){if(otherId<=0||otherId==id)throw new IllegalArgumentException("other faction");return relations.computeIfAbsent(otherId,k->new DiplomaticRelation());} public Map<Long,DiplomaticRelation> relations(){return Collections.unmodifiableMap(relations);}
    public void addTreasury(double q){if(!Double.isFinite(q))throw new IllegalArgumentException("treasury delta");treasury=Math.max(0,treasury+q);} public void advanceTechnology(double q){if(!Double.isFinite(q))throw new IllegalArgumentException("technology delta");technology=Math.max(0,technology+q);} public void setRulerName(String n){if(n==null||n.isBlank())throw new IllegalArgumentException("rulerName");rulerName=n;}
    public void restoreTreasury(double v){treasury=Math.max(0,v);} public void restoreTechnology(double v){technology=Math.max(0,v);} public void restoreGovernment(GovernmentState v){government=Objects.requireNonNull(v);rulerName=v.ruler().name();}
}
