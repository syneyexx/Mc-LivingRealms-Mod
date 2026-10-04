package dev.livingrealms.sim.faction;

import java.util.Locale;
import java.util.Objects;

/** Deterministic faction heraldry for banners, guards, UI and court presentation. */
public record FactionHeraldry(int primaryRgb,int secondaryRgb,int emblemId,String bannerTextureKey) {
    public FactionHeraldry {
        if(emblemId<0||emblemId>15)throw new IllegalArgumentException("emblemId");
        if(bannerTextureKey==null||bannerTextureKey.isBlank())throw new IllegalArgumentException("bannerTextureKey");
    }

    public static FactionHeraldry forFaction(long factionId,String name){
        if(factionId<=0)throw new IllegalArgumentException("factionId");
        long z=mix(factionId*0x9E3779B97F4A7C15L^(name==null?0:name.hashCode()*0xD1B54A32D192ED03L));
        int emblem=Math.floorMod((int)z,16);
        int primary=rgb((int)(z>>>8),(int)(z>>>16),(int)(z>>>24));
        int secondary=rgb((int)(z>>>12)^0x5A,(int)(z>>>20)^0x3C,(int)(z>>>28)^0x21);
        String key="textures/heraldry/faction_banner_"+emblem+".png";
        return new FactionHeraldry(primary,secondary,emblem,key);
    }

    public String wireEmblem(){return "emblem_"+emblemId;}
    public String displayName(String factionName){return Objects.requireNonNullElse(factionName,"Realm")+" Banner";}
    public String resourcePath(){return bannerTextureKey.toLowerCase(Locale.ROOT);}

    private static int rgb(int r,int g,int b){
        int rr=80+(Math.floorMod(r,140));int gg=70+(Math.floorMod(g,150));int bb=60+(Math.floorMod(b,160));
        return (rr<<16)|(gg<<8)|bb;
    }
    private static long mix(long z){z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);}
}
