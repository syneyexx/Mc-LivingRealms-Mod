package dev.livingrealms.sim.civilization;

import java.util.List;

public final class CivilizationNaming {
    private static final List<String> CULTURE_SUFFIX=List.of("Marcher","Riverborn","Highland","Lowland","Forest","Coastal","Ironland","Sunfield","Stoneward","Crownland","Freehold","Deepwood");
    private static final List<String> FAITH=List.of("The Hearth Covenant","The Lantern Faith","The Old Stars","The Green Oath","The River Saints","The Forge Rite","The Crown Creed","The Moon Chapel","The Ancestor Path","The Dawn Assembly","The Stone Testament","The Quiet Flame");
    private static final List<String> DIALECT=List.of("court speech","market cant","river tongue","highland speech","old common","frontier common","guild speech","coastal common","valley speech","crown common","forest speech","deep speech");
    private CivilizationNaming(){}
    public static String culture(long factionId,String factionName){return clean(factionName)+" "+CULTURE_SUFFIX.get(Math.floorMod(factionId,CULTURE_SUFFIX.size()));}
    public static String faith(long factionId){return FAITH.get(Math.floorMod(factionId*7+3,FAITH.size()));}
    public static String dialect(long factionId){return DIALECT.get(Math.floorMod(factionId*11+5,DIALECT.size()));}
    private static String clean(String value){if(value==null||value.isBlank())return "Realm";String x=value.replaceAll("[^A-Za-z0-9 ]"," ").replaceAll("\\s+"," ").trim();return x.isBlank()?"Realm":x;}
}
