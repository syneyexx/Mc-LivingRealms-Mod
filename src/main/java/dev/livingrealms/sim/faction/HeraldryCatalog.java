package dev.livingrealms.sim.faction;

/** Stable heraldry derivation from faction identity — no random rolls at render time. */
public final class HeraldryCatalog {
    private HeraldryCatalog() {}
    public static FactionHeraldry forFaction(long factionId,String name){
        if(factionId<=0)throw new IllegalArgumentException("factionId");
        String label=name==null||name.isBlank()?"realm":name.trim().toLowerCase(java.util.Locale.ROOT);
        long z=mix(factionId*0x9E3779B97F4A7C15L^label.hashCode()*0xD1B54A32D192ED03L);
        int primary=(int)(z&0xFFFFFFL);int secondary=(int)((z>>>24)&0xFFFFFFL);
        int emblem=Math.floorMod((int)(z>>>48),16);
        String key="livingrealms:banner/"+Math.floorMod((int)factionId,64)+"_"+emblem;
        return new FactionHeraldry(primary,secondary,emblem,key);
    }
    private static long mix(long z){z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);}
}
