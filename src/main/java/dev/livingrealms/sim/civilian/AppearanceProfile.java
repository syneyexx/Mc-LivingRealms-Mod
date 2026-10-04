package dev.livingrealms.sim.civilian;

/** Deterministic multi-axis appearance; textureIndex() maps to the 0–47 renderer skin slot. */
public record AppearanceProfile(
        int baseBody,int complexion,int hairStyle,int hairColor,int ageCue,
        int clothingCulture,int clothingProfession,int clothingClass,int factionAccent) {
    public AppearanceProfile {
        if(baseBody<0||baseBody>7)throw new IllegalArgumentException("baseBody");
        if(complexion<0||complexion>7)throw new IllegalArgumentException("complexion");
        if(hairStyle<0||hairStyle>15)throw new IllegalArgumentException("hairStyle");
        if(hairColor<0||hairColor>7)throw new IllegalArgumentException("hairColor");
        if(ageCue<0||ageCue>2)throw new IllegalArgumentException("ageCue");
        if(clothingCulture<0||clothingCulture>15)throw new IllegalArgumentException("clothingCulture");
        if(clothingProfession<0||clothingProfession>31)throw new IllegalArgumentException("clothingProfession");
        if(clothingClass<0||clothingClass>5)throw new IllegalArgumentException("clothingClass");
        if(factionAccent<0||factionAccent>15)throw new IllegalArgumentException("factionAccent");
    }

    /** Stable 0–47 composite for the physical renderer skin atlas. */
    public int textureIndex(){
        long z=mix(((long)baseBody<<40)|((long)complexion<<36)|((long)hairStyle<<32)|((long)hairColor<<28)
                |((long)ageCue<<24)|((long)clothingCulture<<20)|((long)clothingProfession<<14)
                |((long)clothingClass<<10)|factionAccent);
        return Math.floorMod((int)(z^(z>>>17)^(hairStyle*31L)^(clothingProfession*17L)^(ageCue*101L)),48);
    }

    public long pack(){
        long v=0;
        v|=(long)baseBody&0x7L;v|=((long)complexion&0x7L)<<3;v|=((long)hairStyle&0xFL)<<6;v|=((long)hairColor&0x7L)<<10;
        v|=((long)ageCue&0x3L)<<13;v|=((long)clothingCulture&0xFL)<<15;v|=((long)clothingProfession&0x1FL)<<19;
        v|=((long)clothingClass&0x7L)<<24;v|=((long)factionAccent&0xFL)<<27;
        return v;
    }

    public static AppearanceProfile unpack(long packed){
        int baseBody=(int)(packed&0x7L);int complexion=(int)((packed>>>3)&0x7L);int hairStyle=(int)((packed>>>6)&0xFL);
        int hairColor=(int)((packed>>>10)&0x7L);int ageCue=(int)((packed>>>13)&0x3L);int clothingCulture=(int)((packed>>>15)&0xFL);
        int clothingProfession=(int)((packed>>>19)&0x1FL);int clothingClass=(int)((packed>>>24)&0x7L);int factionAccent=(int)((packed>>>27)&0xFL);
        if(ageCue>2)ageCue=2;if(clothingClass>5)clothingClass=5;
        return new AppearanceProfile(baseBody,complexion,hairStyle,hairColor,ageCue,clothingCulture,clothingProfession,clothingClass,factionAccent);
    }

    public static AppearanceProfile forCitizen(long seed,long id,CitizenRole role,int ageYears,long factionId){
        if(id<=0||role==null||factionId<=0)throw new IllegalArgumentException("appearance citizen");
        long z=mix(seed^id*0xA0761D6478BD642FL^factionId*0x9E3779B97F4A7C15L^role.ordinal()*0x632BE59BD9B4E019L^(long)ageYears*0x94D049BB133111EBL);
        int ageCue=ageYears<16?0:ageYears>=55?2:1;
        int clothingClass=switch(role){
            case OFFICIAL,SCHOLAR,PRIEST -> 3+Math.floorMod((int)(z>>>8),3);
            case TRADER,ARTISAN,TEACHER,HEALER -> 2+Math.floorMod((int)(z>>>10),2);
            case GUARD,BUILDER,MINER,CARPENTER -> 1+Math.floorMod((int)(z>>>12),2);
            default -> Math.floorMod((int)(z>>>14),3);
        };
        return new AppearanceProfile(
                Math.floorMod((int)z,8),Math.floorMod((int)(z>>>3),8),Math.floorMod((int)(z>>>6),16),Math.floorMod((int)(z>>>10),8),
                ageCue,Math.floorMod((int)(factionId^(z>>>16)),16),Math.floorMod(role.ordinal()*3+(int)(z>>>20),32),
                Math.min(5,clothingClass),Math.floorMod((int)(factionId*17L^(z>>>24)),16));
    }

    private static long mix(long z){z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);}
}
