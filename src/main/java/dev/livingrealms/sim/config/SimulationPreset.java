package dev.livingrealms.sim.config;

/** Curated singleplayer performance/immersion profiles. All values remain deterministic and save-persistent. */
public enum SimulationPreset {
    PERFORMANCE(new SimulationConfig(256,1536,96,16,64,20,100,1,1.25,.20,.78,12.0)),
    BALANCED(SimulationConfig.defaults()),
    IMMERSIVE(new SimulationConfig(384,2560,220,32,128,48,220,1,1.25,.20,.78,12.0)),
    CINEMATIC(new SimulationConfig(480,3072,320,48,180,64,280,1,1.25,.20,.78,12.0));

    private final SimulationConfig config;
    SimulationPreset(SimulationConfig config){this.config=config;}
    public SimulationConfig config(){return config;}

    public static String labelFor(SimulationConfig config){
        for(SimulationPreset preset:values())if(preset.config.equals(config))return preset.name();
        return "CUSTOM";
    }
}
