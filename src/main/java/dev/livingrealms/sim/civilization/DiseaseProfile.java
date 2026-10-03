package dev.livingrealms.sim.civilization;

import java.util.*;

/** Data-like disease definition used by the bounded epidemic simulator. */
public record DiseaseProfile(String key,double transmission,double mortality,double waterSensitivity,double crowdingSensitivity,double tradeSensitivity,double medicineSensitivity){
    public DiseaseProfile{
        if(key==null||key.isBlank())throw new IllegalArgumentException("disease key");
        for(double v:new double[]{transmission,mortality,waterSensitivity,crowdingSensitivity,tradeSensitivity,medicineSensitivity})if(!Double.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("disease profile");
    }
    private static final Map<String,DiseaseProfile> BUILTIN;
    static{
        LinkedHashMap<String,DiseaseProfile> m=new LinkedHashMap<>();
        register(m,new DiseaseProfile("waterborne_fever",.48,.34,.92,.30,.22,.72));
        register(m,new DiseaseProfile("crowding_flux",.62,.22,.30,.94,.38,.58));
        register(m,new DiseaseProfile("trade_pox",.74,.18,.18,.54,.96,.64));
        register(m,new DiseaseProfile("winter_lung",.56,.26,.20,.66,.44,.70));
        BUILTIN=Map.copyOf(m);
    }
    private static void register(Map<String,DiseaseProfile> map,DiseaseProfile profile){map.put(profile.key(),profile);}
    public static DiseaseProfile of(String key){return BUILTIN.getOrDefault(key,BUILTIN.get("trade_pox"));}
    public static Collection<DiseaseProfile> builtins(){return BUILTIN.values();}
}
