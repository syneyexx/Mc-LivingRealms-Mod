package dev.livingrealms.sim.social;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.util.Mathx;
import java.util.*;

/**
 * Persistent named person behind a physical citizen projection. Aggregate settlement population
 * remains authoritative; these bounded agents carry identity, memory, needs and relationships.
 */
public final class SocialCitizen {
    public static final int MAX_MEMORIES=48, MAX_RELATIONSHIPS=32;
    private final long id; private long factionId,settlementId; private final int projectionSlot,skinVariant; private final String name;
    private CitizenRole role; private final long birthDay; private double health=.9,money=8,professionSkill=.22; private boolean alive=true; private long householdId;
    private final CitizenNeeds needs=new CitizenNeeds(); private final CitizenPersonality personality;
    private final ArrayDeque<CitizenMemory> memories=new ArrayDeque<>(); private final LinkedHashMap<String,CitizenRelationship> relationships=new LinkedHashMap<>();
    public SocialCitizen(long id,long factionId,long settlementId,int projectionSlot,String name,int skinVariant,CitizenRole role,long birthDay,CitizenPersonality personality){
        if(id<=0||factionId<=0||settlementId<=0||projectionSlot<0||name==null||name.isBlank()||skinVariant<0||skinVariant>=12||role==null||personality==null)throw new IllegalArgumentException("social citizen");
        this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.projectionSlot=projectionSlot;this.name=name;this.skinVariant=skinVariant;this.role=role;this.birthDay=birthDay;this.personality=personality;
    }
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public int projectionSlot(){return projectionSlot;} public String name(){return name;} public int skinVariant(){return skinVariant;} public CitizenRole role(){return role;} public long birthDay(){return birthDay;} public double health(){return health;} public double money(){return money;} public double professionSkill(){return professionSkill;} public boolean alive(){return alive;} public long householdId(){return householdId;} public CitizenNeeds needs(){return needs;} public CitizenPersonality personality(){return personality;}
    public List<CitizenMemory> memories(){return List.copyOf(memories);} public Map<String,CitizenRelationship> relationships(){return Collections.unmodifiableMap(relationships);}
    public int ageYears(long currentDay){return Math.max(0,(int)((currentDay-birthDay)/365L));}
    public void setRole(CitizenRole value){CitizenRole next=Objects.requireNonNull(value);if(role!=next){role=next;professionSkill=Math.max(.12,professionSkill*.72);}} public void practiceProfession(double amount){if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("profession practice");professionSkill=Mathx.clamp(professionSkill+amount*(1-professionSkill),0,1);} public void migrateTo(long factionId,long settlementId){if(factionId<=0||settlementId<=0)throw new IllegalArgumentException("citizen destination");this.factionId=factionId;this.settlementId=settlementId;} public void setHouseholdId(long value){if(value<0)throw new IllegalArgumentException("householdId");householdId=value;} public void addMoney(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("money");money=Math.max(0,money+delta);} public void adjustHealth(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("health");health=Mathx.clamp(health+delta,0,1);if(health<=0)alive=false;} public void markDead(){alive=false;health=0;}
    public void remember(CitizenMemory memory){Objects.requireNonNull(memory);memories.addLast(memory);while(memories.size()>MAX_MEMORIES)memories.removeFirst();}
    public Optional<CitizenMemory> latestMemory(java.util.function.Predicate<CitizenMemory> predicate){Iterator<CitizenMemory> it=memories.descendingIterator();while(it.hasNext()){CitizenMemory m=it.next();if(predicate.test(m))return Optional.of(m);}return Optional.empty();}
    public CitizenRelationship relationship(String targetKey){CitizenRelationship existing=relationships.get(targetKey);if(existing!=null)return existing;while(relationships.size()>=MAX_RELATIONSHIPS){String first=relationships.keySet().iterator().next();relationships.remove(first);}CitizenRelationship created=new CitizenRelationship(targetKey);relationships.put(targetKey,created);return created;}
    public void restoreCore(CitizenRole role,double health,double money,boolean alive){this.role=Objects.requireNonNull(role);this.health=Mathx.clamp(health,0,1);this.money=Math.max(0,money);this.alive=alive&&this.health>0;} public void restoreHumanity(double professionSkill,long householdId){if(!Double.isFinite(professionSkill)||professionSkill<0||professionSkill>1||householdId<0)throw new IllegalArgumentException("citizen humanity");this.professionSkill=professionSkill;this.householdId=householdId;}
    public void restoreMemory(CitizenMemory memory){remember(memory);} public void restoreRelationship(CitizenRelationship rel){if(relationships.size()<MAX_RELATIONSHIPS)relationships.put(rel.targetKey(),rel);}
}
