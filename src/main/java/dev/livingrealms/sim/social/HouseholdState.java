package dev.livingrealms.sim.social;

import dev.livingrealms.sim.util.Mathx;
import java.util.*;

/** Persistent bounded household grouping named citizens with lineage-aware dependent children and shared wealth. */
public final class HouseholdState {
    public static final int MAX_NAMED_MEMBERS=12, MAX_CHILDREN=16;
    private final long id;
    private long factionId;
    private long settlementId;
    private final long foundedDay;
    private final LinkedHashSet<Long> memberIds=new LinkedHashSet<>();
    private final ArrayList<DependentChild> children=new ArrayList<>();
    private double sharedWealth;
    private String homeKey="";
    private boolean active=true;

    public HouseholdState(long id,long factionId,long settlementId,long foundedDay){
        if(id<=0||factionId<=0||settlementId<=0||foundedDay<0)throw new IllegalArgumentException("household");
        this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.foundedDay=foundedDay;
    }
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public long foundedDay(){return foundedDay;}
    public Set<Long> memberIds(){return Collections.unmodifiableSet(memberIds);} public List<DependentChild> children(){return Collections.unmodifiableList(children);} public int unnamedChildren(){return children.size();} public double sharedWealth(){return sharedWealth;} public String homeKey(){return homeKey;} public boolean active(){return active;}
    public int representedPeople(){return memberIds.size()+children.size();}
    public boolean addMember(long citizenId){if(citizenId<=0||memberIds.size()>=MAX_NAMED_MEMBERS)return false;return memberIds.add(citizenId);}
    public boolean removeMember(long citizenId){return memberIds.remove(citizenId);}
    public boolean addChild(DependentChild child){if(child==null||children.size()>=MAX_CHILDREN||children.stream().anyMatch(c->c.id()==child.id()))return false;children.add(child);children.sort(Comparator.comparingLong(DependentChild::birthDay).thenComparingLong(DependentChild::id));return true;}
    public Optional<DependentChild> findChild(long childId){return children.stream().filter(c->c.id()==childId).findFirst();}
    public Optional<DependentChild> removeChild(long childId){for(Iterator<DependentChild> it=children.iterator();it.hasNext();){DependentChild child=it.next();if(child.id()==childId){it.remove();return Optional.of(child);}}return Optional.empty();}
    /** Removes and returns dependants that reached adulthood. */
    public List<DependentChild> takeAdultChildren(long currentDay){List<DependentChild> out=new ArrayList<>();for(Iterator<DependentChild> it=children.iterator();it.hasNext();){DependentChild child=it.next();if(currentDay-child.birthDay()>=18L*365L){out.add(child);it.remove();}}return out;}
    public void loseYoungestChildren(int count){for(int i=0;i<count&&!children.isEmpty();i++)children.remove(children.size()-1);} public void adjustWealth(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("wealth");sharedWealth=Math.max(0,sharedWealth+delta);} public void setHomeKey(String value){homeKey=Objects.requireNonNullElse(value,"");} public void deactivate(){active=false;}
    public void migrate(long factionId,long settlementId){if(factionId<=0||settlementId<=0)throw new IllegalArgumentException("household destination");this.factionId=factionId;this.settlementId=settlementId;}
    public void restore(Collection<Long> members,Collection<DependentChild> restoredChildren,double wealth,String homeKey,boolean active){memberIds.clear();if(members!=null)for(long id:members)if(id>0&&memberIds.size()<MAX_NAMED_MEMBERS)memberIds.add(id);children.clear();if(restoredChildren!=null)for(DependentChild child:restoredChildren)if(child!=null&&children.size()<MAX_CHILDREN&&!children.stream().anyMatch(c->c.id()==child.id()))children.add(child);children.sort(Comparator.comparingLong(DependentChild::birthDay).thenComparingLong(DependentChild::id));sharedWealth=Math.max(0,Double.isFinite(wealth)?wealth:0);this.homeKey=Objects.requireNonNullElse(homeKey,"");this.active=active;}
    public double resilience(){return Mathx.clamp(.25+Math.min(.35,representedPeople()*.035)+Math.min(.4,sharedWealth/200.0),0,1);}
}
