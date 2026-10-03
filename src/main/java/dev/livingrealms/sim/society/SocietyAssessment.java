package dev.livingrealms.sim.society;

import dev.livingrealms.sim.util.Mathx;

/** Immutable diagnosis of a settlement's current civilian living conditions. */
public record SocietyAssessment(SettlementNeeds needs, double satisfaction, SocietyPressure primaryPressure, double pressureSeverity) {
    public SocietyAssessment {
        if (needs == null) throw new IllegalArgumentException("needs");
        if (primaryPressure == null) throw new IllegalArgumentException("primaryPressure");
        satisfaction = Mathx.clamp(satisfaction, 0, 1);
        pressureSeverity = Mathx.clamp(pressureSeverity, 0, 1);
    }
}
