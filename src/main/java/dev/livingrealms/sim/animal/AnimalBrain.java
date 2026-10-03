package dev.livingrealms.sim.animal;

import dev.livingrealms.sim.ecology.*;
import dev.livingrealms.sim.util.Mathx;

/**
 * Species-parameterized utility brain. It contains no Minecraft classes and can therefore be
 * regression-tested. Physical entities translate the chosen intent into pathfinding/attacks.
 */
public final class AnimalBrain {
    private AnimalBrain() {}

    public static AnimalDecision decide(SpeciesDefinition sp, AnimalStimulus s) {
        if (sp == null || s == null) throw new IllegalArgumentException("species/stimulus");

        // Immediate survival dominates all lower-priority desires.
        if (s.predatorVisible()) {
            double flee = Mathx.clamp(.35 + sp.fearfulness() * .55 + (1 - s.health()) * .25, 0, 1);
            double defend = Mathx.clamp(sp.aggression() * .55 + sp.defense() * .45 - sp.fearfulness() * .25, 0, 1);
            if (defend > flee && sp.adultMassKg() >= 20) return new AnimalDecision(AnimalIntent.DEFEND, defend, "predator_threat");
            return new AnimalDecision(AnimalIntent.FLEE, flee, "predator_threat");
        }

        // Dangerous species may treat a nearby human as a threat/target. Most animals retreat.
        if (s.humanVisible()) {
            double attack = sp.attacksHumans() ? Mathx.clamp(sp.aggression() * .75 + (1 - sp.fearfulness()) * .25, 0, 1) : 0;
            double flee = Mathx.clamp(sp.fearfulness() * .80 + .15, 0, 1);
            if (attack > flee && attack >= .55) return new AnimalDecision(AnimalIntent.DEFEND, attack, "human_proximity");
            if (flee >= .45) return new AnimalDecision(AnimalIntent.FLEE, flee, "human_proximity");
        }

        if (s.thirst() >= .72 && s.waterReachable()) return new AnimalDecision(AnimalIntent.DRINK, s.thirst(), "thirst");

        boolean predatorDiet = sp.diet() == Diet.CARNIVORE || sp.diet() == Diet.PISCIVORE || sp.diet() == Diet.INSECTIVORE;
        if ((predatorDiet || sp.diet() == Diet.OMNIVORE) && s.hunger() >= .48 && s.preyVisible()) {
            double hunt = Mathx.clamp(.35 + s.hunger() * .45 + sp.huntSkill() * .20, 0, 1);
            return new AnimalDecision(AnimalIntent.HUNT, hunt, "prey_and_hunger");
        }

        if (s.hunger() >= .45 && s.foodReachable()) {
            AnimalIntent foodIntent = sp.diet() == Diet.HERBIVORE ? AnimalIntent.GRAZE : AnimalIntent.FORAGE;
            return new AnimalDecision(foodIntent, Mathx.clamp(.3 + s.hunger() * .65, 0, 1), "hunger");
        }

        if (s.groupTooFar() && sp.socialPattern() != SocialPattern.SOLITARY) {
            return new AnimalDecision(AnimalIntent.FOLLOW_GROUP, .60, "social_cohesion");
        }

        if (s.habitatUnsuitable()) return new AnimalDecision(AnimalIntent.MIGRATE, .58, "habitat_pressure");

        if (s.reproductionDrive() >= .70 && s.health() >= .65 && s.hunger() <= .45) {
            return new AnimalDecision(AnimalIntent.MATE, s.reproductionDrive(), "reproduction");
        }

        if (!s.activeTimeOfDay()) return new AnimalDecision(AnimalIntent.REST, .52, "activity_cycle");
        return new AnimalDecision(AnimalIntent.ROAM, .25, "baseline_exploration");
    }
}
