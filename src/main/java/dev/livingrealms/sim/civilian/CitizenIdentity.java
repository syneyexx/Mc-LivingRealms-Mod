package dev.livingrealms.sim.civilian;

/** Deterministic physical identity for projected citizens. */
public record CitizenIdentity(String name,int skinVariant) {
    private static final String[] FIRST={
            "Alden","Mira","Tomas","Elise","Rowan","Lena","Corin","Nora","Darian","Maeve","Jonas","Talia",
            "Bram","Iris","Cedric","Anya","Lucan","Freya","Marek","Selene","Garrick","Lyra","Owen","Petra",
            "Silas","Vera","Edric","Maren","Nolan","Astrid","Ronan","Clara","Leif","Elara","Hugo","Sofia",
            "Theo","Yara","Finn","Amara","Joren","Nadia","Kellan","Rhea","Milo","Evelyn","Arlen","Celia"
    };
    private static final String[] LAST={
            "Ashford","Briar","Crowley","Dunwell","Evermere","Farrow","Grey","Hart","Iverson","Jensen","Kestrel","Lark",
            "Marsh","North","Oakley","Pryce","Quill","Rook","Stone","Thorne","Vale","West","Yarrow","Alder",
            "Bell","Cairn","Drake","Ember","Frost","Grove","Hale","Ironwood","Keene","Lowell","Morrow","Reed",
            "Sable","Tanner","Underhill","Voss","Whitlock","Young","Arden","Bennet","Cross","Dale","Ellis","Ford"
    };
    public CitizenIdentity {
        if(name==null||name.isBlank())throw new IllegalArgumentException("name");
        if(skinVariant<0||skinVariant>=48)throw new IllegalArgumentException("skinVariant");
    }
    public static CitizenIdentity forAgent(long worldSeed,long agentId,long factionId,long settlementId,int slot,CitizenRole role){
        if(agentId<=0||factionId<=0||settlementId<=0||slot<0||role==null)throw new IllegalArgumentException("agent");
        long z=mix(worldSeed ^ agentId*0xA0761D6478BD642FL ^ factionId*0x9E3779B97F4A7C15L ^ settlementId*0xD1B54A32D192ED03L ^ (long)slot*0x94D049BB133111EBL ^ role.ordinal()*0x632BE59BD9B4E019L);
        String first=FIRST[Math.floorMod((int)z,FIRST.length)];String last=LAST[Math.floorMod((int)(z>>>32),LAST.length)];
        int skin=AppearanceProfile.forCitizen(worldSeed,agentId,role,20,factionId).textureIndex();
        return new CitizenIdentity(first+" "+last,skin);
    }
    public static CitizenIdentity forProjection(long factionId,long settlementId,int slot,CitizenRole role){
        if(factionId<=0||settlementId<=0||slot<0||role==null)throw new IllegalArgumentException("projection");
        long z=mix(factionId*0x9E3779B97F4A7C15L ^ settlementId*0xD1B54A32D192ED03L ^ (long)slot*0x94D049BB133111EBL ^ role.ordinal()*0x632BE59BD9B4E019L);
        String first=FIRST[Math.floorMod((int)z,FIRST.length)];
        String last=LAST[Math.floorMod((int)(z>>>32),LAST.length)];
        int skin=AppearanceProfile.forCitizen(factionId^settlementId,(factionId<<20)^settlementId^(slot+1L),role,20,factionId).textureIndex();
        return new CitizenIdentity(first+" "+last,skin);
    }
    private static long mix(long z){z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);}
}
