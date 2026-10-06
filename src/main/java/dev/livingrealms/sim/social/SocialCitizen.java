package dev.livingrealms.sim.social;

import dev.livingrealms.sim.civilian.AppearanceProfile;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.util.Mathx;
import java.util.*;

/**
 * Persistent named person behind a physical citizen projection. Aggregate settlement population
 * remains authoritative; these bounded agents carry identity, memory, needs and relationships.
 */
public final class SocialCitizen {
    public static final int MAX_MEMORIES=48, MAX_RELATIONSHIPS=32, MAX_SKIN_VARIANTS=48;
    private final long id; private long factionId,settlementId; private int projectionSlot; private final int skinVariant; private final String name;
    private CitizenRole role; private final long birthDay; private double health=.9,money=8,professionSkill=.22; private boolean alive=true; private long householdId;
    private final CitizenNeeds needs=new CitizenNeeds(); private final CitizenPersonality personality;
    private final ArrayDeque<CitizenMemory> memories=new ArrayDeque<>(); private final LinkedHashMap<String,CitizenRelationship> relationships=new LinkedHashMap<>();
    private String cultureKey="",faithKey="",workplaceKey="",professionHistoryKey="";
    private SocialClass socialClass=SocialClass.WORKING; private SocialClass wealthClass=SocialClass.WORKING;
    private EmploymentStatus employmentStatus=EmploymentStatus.EMPLOYED;
    private double education=.15,personalInfluence=.05; private long appearancePacked;

    public SocialCitizen(long id,long factionId,long settlementId,int projectionSlot,String name,int skinVariant,CitizenRole role,long birthDay,CitizenPersonality personality){
        if(id<=0||factionId<=0||settlementId<=0||projectionSlot<0||name==null||name.isBlank()||skinVariant<0||skinVariant>=MAX_SKIN_VARIANTS||role==null||personality==null)throw new IllegalArgumentException("social citizen");
        this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.projectionSlot=projectionSlot;this.name=name;this.skinVariant=skinVariant;this.role=role;this.birthDay=birthDay;this.personality=personality;
        this.appearancePacked=AppearanceProfile.forCitizen(id^factionId,id,role,20,factionId).pack();
        this.socialClass=classForRole(role);this.wealthClass=socialClass;
    }
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public int projectionSlot(){return projectionSlot;} public String name(){return name;} public int skinVariant(){return skinVariant;} public CitizenRole role(){return role;} public long birthDay(){return birthDay;} public double health(){return health;} public double money(){return money;} public double professionSkill(){return professionSkill;} public boolean alive(){return alive;} public long householdId(){return householdId;} public CitizenNeeds needs(){return needs;} public CitizenPersonality personality(){return personality;}
    public String cultureKey(){return cultureKey;} public String faithKey(){return faithKey;} public SocialClass socialClass(){return socialClass;} public SocialClass wealthClass(){return wealthClass;}
    public double education(){return education;} public String workplaceKey(){return workplaceKey;} public double personalInfluence(){return personalInfluence;}
    public long appearancePacked(){return appearancePacked;} public AppearanceProfile appearanceProfile(){return AppearanceProfile.unpack(appearancePacked);}
    public String professionHistoryKey(){return professionHistoryKey;} public EmploymentStatus employmentStatus(){return employmentStatus;}
    public List<CitizenMemory> memories(){return List.copyOf(memories);} public Map<String,CitizenRelationship> relationships(){return Collections.unmodifiableMap(relationships);}
    public int ageYears(long currentDay){return Math.max(0,(int)((currentDay-birthDay)/365L));}
    public double socialMobilityScore(){return Mathx.clamp(professionSkill*.35+education*.25+Math.min(1,money/80.0)*.2+personalInfluence*.2,0,1);}
    public void setRole(CitizenRole value){CitizenRole next=Objects.requireNonNull(value);if(role!=next){if(!professionHistoryKey.isBlank())professionHistoryKey=professionHistoryKey+">";professionHistoryKey=(professionHistoryKey+role.name()).length()>96?role.name():professionHistoryKey+role.name();role=next;professionSkill=Math.max(.12,professionSkill*.72);socialClass=classForRole(next);}}
    public void practiceProfession(double amount){if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("profession practice");professionSkill=Mathx.clamp(professionSkill+amount*(1-professionSkill),0,1);if(professionSkill>=.72&&employmentStatus==EmploymentStatus.EMPLOYED)employmentStatus=EmploymentStatus.MASTER;if(professionSkill>=.35&&employmentStatus==EmploymentStatus.APPRENTICE)employmentStatus=EmploymentStatus.EMPLOYED;}
    public void migrateTo(long factionId,long settlementId){if(factionId<=0||settlementId<=0)throw new IllegalArgumentException("citizen destination");this.factionId=factionId;this.settlementId=settlementId;}
    public void rebindProjectionSlot(int slot){if(slot<0)throw new IllegalArgumentException("projectionSlot");projectionSlot=slot;}
    public void setHouseholdId(long value){if(value<0)throw new IllegalArgumentException("householdId");householdId=value;}
    public void addMoney(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("money");money=Math.max(0,money+delta);refreshWealthClass();}
    public void adjustHealth(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("health");health=Mathx.clamp(health+delta,0,1);if(health<=0)alive=false;}
    public void markDead(){alive=false;health=0;}
    public void remember(CitizenMemory memory){Objects.requireNonNull(memory);memories.addLast(memory);while(memories.size()>MAX_MEMORIES)memories.removeFirst();}
    /** Wave 28 — replace memory deque after retention compaction / summarization. */
    public void replaceMemories(java.util.Collection<CitizenMemory> next){
        memories.clear();
        if(next==null)return;
        for(CitizenMemory memory:next){
            if(memory==null)continue;
            memories.addLast(memory);
            if(memories.size()>=MAX_MEMORIES)break;
        }
    }
    /** Retention-only replacement preserving deterministic insertion order and the hard relationship cap. */
    public void replaceRelationships(java.util.Collection<CitizenRelationship> next){
        relationships.clear();
        if(next==null)return;
        for(CitizenRelationship relationship:next){
            if(relationship==null)continue;
            relationships.putIfAbsent(relationship.targetKey(),relationship);
            if(relationships.size()>=MAX_RELATIONSHIPS)break;
        }
    }
    public Optional<CitizenMemory> latestMemory(java.util.function.Predicate<CitizenMemory> predicate){Iterator<CitizenMemory> it=memories.descendingIterator();while(it.hasNext()){CitizenMemory m=it.next();if(predicate.test(m))return Optional.of(m);}return Optional.empty();}
    public CitizenRelationship relationship(String targetKey){CitizenRelationship existing=relationships.get(targetKey);if(existing!=null)return existing;while(relationships.size()>=MAX_RELATIONSHIPS){String first=relationships.keySet().iterator().next();relationships.remove(first);}CitizenRelationship created=new CitizenRelationship(targetKey);relationships.put(targetKey,created);return created;}
    public void restoreCore(CitizenRole role,double health,double money,boolean alive){this.role=Objects.requireNonNull(role);this.health=Mathx.clamp(health,0,1);this.money=Math.max(0,money);this.alive=alive&&this.health>0;refreshWealthClass();}
    public void restoreHumanity(double professionSkill,long householdId){if(!Double.isFinite(professionSkill)||professionSkill<0||professionSkill>1||householdId<0)throw new IllegalArgumentException("citizen humanity");this.professionSkill=professionSkill;this.householdId=householdId;}
    public void restoreAppearance(long packed){this.appearancePacked=AppearanceProfile.unpack(packed).pack();}
    public void restoreSocialExtensions(String cultureKey,String faithKey,SocialClass socialClass,double education,String workplaceKey,SocialClass wealthClass,double personalInfluence,long appearancePacked,String professionHistoryKey,EmploymentStatus employmentStatus){
        restoreAppearance(appearancePacked);
        restoreIdentityProfile(cultureKey,faithKey,socialClass,education,workplaceKey,wealthClass,personalInfluence,professionHistoryKey,employmentStatus);
    }
    public void restoreIdentityProfile(String cultureKey,String faithKey,SocialClass socialClass,double education,String workplaceKey,SocialClass wealthClass,double personalInfluence,String professionHistoryKey,EmploymentStatus employmentStatus){
        this.cultureKey=Objects.requireNonNullElse(cultureKey,"");this.faithKey=Objects.requireNonNullElse(faithKey,"");
        this.socialClass=Objects.requireNonNull(socialClass);this.education=Mathx.clamp(education,0,1);
        this.workplaceKey=Objects.requireNonNullElse(workplaceKey,"");this.wealthClass=Objects.requireNonNull(wealthClass);
        this.personalInfluence=Mathx.clamp(personalInfluence,0,1);this.professionHistoryKey=Objects.requireNonNullElse(professionHistoryKey,"");
        this.employmentStatus=Objects.requireNonNull(employmentStatus);
    }
    public void setCultureKey(String culture){cultureKey=Objects.requireNonNullElse(culture,"");}
    public void setFaithKey(String faith){faithKey=Objects.requireNonNullElse(faith,"");}
    public void setCultureFaith(String culture,String faith){setCultureKey(culture);setFaithKey(faith);}
    public void setWorkplace(String key){workplaceKey=Objects.requireNonNullElse(key,"");}
    public void setEmploymentStatus(EmploymentStatus status){employmentStatus=Objects.requireNonNull(status);}
    public void adjustEducation(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("education");education=Mathx.clamp(education+delta,0,1);}
    public void adjustPersonalInfluence(double delta){if(!Double.isFinite(delta))throw new IllegalArgumentException("influence");personalInfluence=Mathx.clamp(personalInfluence+delta,0,1);}
    public void setPersonalInfluence(double value){if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("influence");personalInfluence=value;}
    public void setSocialClass(SocialClass value){socialClass=Objects.requireNonNull(value);}
    public void restoreMemory(CitizenMemory memory){remember(memory);} public void restoreRelationship(CitizenRelationship rel){if(relationships.size()<MAX_RELATIONSHIPS)relationships.put(rel.targetKey(),rel);}
    private void refreshWealthClass(){wealthClass=money<5?SocialClass.POOR:money<20?SocialClass.WORKING:money<45?SocialClass.ARTISAN:money<90?SocialClass.MERCHANT:money<180?SocialClass.PROFESSIONAL:SocialClass.ELITE;}
    private static SocialClass classForRole(CitizenRole role){
        return switch(role){
            case OFFICIAL,PRIEST -> SocialClass.ELITE;
            case SCHOLAR,TEACHER,HEALER -> SocialClass.PROFESSIONAL;
            case TRADER -> SocialClass.MERCHANT;
            case ARTISAN,CARPENTER,BUTCHER,BUILDER -> SocialClass.ARTISAN;
            case GUARD,SAILOR,DOCKWORKER,MINER,LUMBERJACK,FISHER,HUNTER,FARMER -> SocialClass.WORKING;
            default -> SocialClass.WORKING;
        };
    }
}
