package dev.livingrealms.sim.animal;

import dev.livingrealms.sim.util.Mathx;

/** Normalized sensory/context inputs from either Minecraft or a headless simulation test. */
public record AnimalStimulus(
        double hunger,
        double thirst,
        double fear,
        double health,
        double reproductionDrive,
        boolean preyVisible,
        boolean predatorVisible,
        boolean humanVisible,
        boolean waterReachable,
        boolean foodReachable,
        boolean groupTooFar,
        boolean habitatUnsuitable,
        boolean activeTimeOfDay
) {
    public AnimalStimulus {
        hunger = Mathx.clamp(hunger, 0, 1);
        thirst = Mathx.clamp(thirst, 0, 1);
        fear = Mathx.clamp(fear, 0, 1);
        health = Mathx.clamp(health, 0, 1);
        reproductionDrive = Mathx.clamp(reproductionDrive, 0, 1);
    }
}
