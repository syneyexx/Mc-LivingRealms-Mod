package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.law.CrimeType;

/** NPC-facing court case layered over the existing CrimeLedger; player crimes keep using Wanted/Custody. */
public final class JusticeCase {
    private final long id,factionId,settlementId,openedDay;
    private final String accusedKey;
    private final CrimeType crimeType;
    private JusticeStatus status=JusticeStatus.INVESTIGATING;
    private SentenceType sentence=SentenceType.WARNING;
    private double fine;
    private long releaseDay;
    public JusticeCase(long id,long factionId,long settlementId,long openedDay,String accusedKey,CrimeType crimeType){if(id<=0||factionId<=0||settlementId<=0||openedDay<0||accusedKey==null||accusedKey.isBlank()||crimeType==null)throw new IllegalArgumentException("justice case");this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.openedDay=openedDay;this.accusedKey=accusedKey;this.crimeType=crimeType;}
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public long openedDay(){return openedDay;} public String accusedKey(){return accusedKey;} public CrimeType crimeType(){return crimeType;} public JusticeStatus status(){return status;} public SentenceType sentence(){return sentence;} public double fine(){return fine;} public long releaseDay(){return releaseDay;} public boolean active(){return status!=JusticeStatus.COMPLETED&&status!=JusticeStatus.DISMISSED;}
    public void charge(){if(status==JusticeStatus.INVESTIGATING)status=JusticeStatus.CHARGED;} public void sentence(SentenceType sentence,double fine,long releaseDay){if(sentence==null||!Double.isFinite(fine)||fine<0||releaseDay<openedDay)throw new IllegalArgumentException("sentence");this.sentence=sentence;this.fine=fine;this.releaseDay=releaseDay;this.status=sentence==SentenceType.IMPRISONMENT?JusticeStatus.SERVING:JusticeStatus.SENTENCED;} public void complete(){status=JusticeStatus.COMPLETED;} public void dismiss(){status=JusticeStatus.DISMISSED;} public void restore(JusticeStatus status,SentenceType sentence,double fine,long releaseDay){if(status==null||sentence==null||!Double.isFinite(fine)||fine<0||releaseDay<0)throw new IllegalArgumentException("justice restore");this.status=status;this.sentence=sentence;this.fine=fine;this.releaseDay=releaseDay;}
}
