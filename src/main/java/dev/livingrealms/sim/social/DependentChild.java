package dev.livingrealms.sim.social;

import java.util.*;

/**
 * Bounded pre-adult person record. The child has a stable canonical id from birth so lineage,
 * adoption and later materialisation into SocialCitizen do not invent a new identity at age 18.
 */
public final class DependentChild {
    private final long id;
    private final long birthDay;
    private final long parentAId;
    private final long parentBId;
    private long adoptiveParentAId;
    private long adoptiveParentBId;

    public DependentChild(long id,long birthDay,long parentAId,long parentBId){
        if(id<=0||birthDay<0||parentAId<0||parentBId<0||parentAId==id||parentBId==id)throw new IllegalArgumentException("dependent child");
        this.id=id;this.birthDay=birthDay;this.parentAId=parentAId;this.parentBId=parentBId;
    }
    public long id(){return id;} public long birthDay(){return birthDay;} public long parentAId(){return parentAId;} public long parentBId(){return parentBId;} public long adoptiveParentAId(){return adoptiveParentAId;} public long adoptiveParentBId(){return adoptiveParentBId;}
    public boolean adopted(){return adoptiveParentAId>0||adoptiveParentBId>0;}
    public List<Long> biologicalParents(){ArrayList<Long> out=new ArrayList<>(2);if(parentAId>0)out.add(parentAId);if(parentBId>0&&parentBId!=parentAId)out.add(parentBId);return List.copyOf(out);}
    public List<Long> adoptiveParents(){ArrayList<Long> out=new ArrayList<>(2);if(adoptiveParentAId>0)out.add(adoptiveParentAId);if(adoptiveParentBId>0&&adoptiveParentBId!=adoptiveParentAId)out.add(adoptiveParentBId);return List.copyOf(out);}
    public void adoptBy(Collection<Long> guardians){
        adoptiveParentAId=0;adoptiveParentBId=0;if(guardians==null)return;
        for(long id:guardians){if(id<=0||id==parentAId||id==parentBId)continue;if(adoptiveParentAId==0)adoptiveParentAId=id;else if(adoptiveParentBId==0&&id!=adoptiveParentAId){adoptiveParentBId=id;break;}}
    }
    public void restoreAdoptiveParents(long a,long b){if(a<0||b<0)throw new IllegalArgumentException("adoptive parents");adoptiveParentAId=a;adoptiveParentBId=b;}
}
