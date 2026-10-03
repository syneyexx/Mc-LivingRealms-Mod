package dev.livingrealms.sim.ecology;

import java.util.*;

/** Strict validation for species datasets before they are admitted to the simulation. */
public final class SpeciesCatalogValidator {
    private SpeciesCatalogValidator() {}

    public record Report(List<String> errors, List<String> warnings) {
        public Report {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
        public boolean valid() { return errors.isEmpty(); }
        public void throwIfInvalid() {
            if (!valid()) throw new IllegalArgumentException("Invalid species catalog: " + String.join("; ", errors));
        }
    }

    public static Report validate(Map<String, SpeciesDefinition> catalog) {
        Objects.requireNonNull(catalog, "catalog");
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (var e : catalog.entrySet()) {
            String key = e.getKey();
            SpeciesDefinition sp = e.getValue();
            if (sp == null) {
                errors.add("Null species at key " + key);
                continue;
            }
            if (!key.equals(sp.id())) errors.add("Catalog key '" + key + "' != species id '" + sp.id() + "'");
            if (sp.minGroup() <= 0 || sp.maxGroup() < sp.minGroup()) errors.add(sp.id() + ": invalid group size");
            if (sp.aggression() < 0 || sp.aggression() > 1) errors.add(sp.id() + ": aggression must be 0..1");
            if (sp.fearfulness() < 0 || sp.fearfulness() > 1) errors.add(sp.id() + ": fearfulness must be 0..1");
            if (sp.huntSkill() < 0 || sp.huntSkill() > 1) errors.add(sp.id() + ": huntSkill must be 0..1");
            if (sp.defense() < 0 || sp.defense() > 1) errors.add(sp.id() + ": defense must be 0..1");
            if (sp.maturityDays() < 0 || sp.maturityDays() > sp.lifespanDays()) errors.add(sp.id() + ": maturity outside lifespan");
            if (sp.gestationDays() < 0 || sp.offspringPerBirth() < 0 || sp.birthsPerYear() < 0) errors.add(sp.id() + ": invalid reproduction values");
            if (sp.climates().isEmpty()) warnings.add(sp.id() + ": no climate bands configured");
            if (sp.habitatTags().isEmpty()) warnings.add(sp.id() + ": no habitat tags configured");

            for (String prey : sp.preySpecies()) {
                if (prey.equals(sp.id())) warnings.add(sp.id() + ": self-predation is configured");
                else if (!catalog.containsKey(prey)) errors.add(sp.id() + ": unknown prey species '" + prey + "'");
            }
            for (String predator : sp.predatorSpecies()) {
                if (!catalog.containsKey(predator)) errors.add(sp.id() + ": unknown predator species '" + predator + "'");
            }
        }

        // Food-web asymmetry is allowed but reported. It often reveals a typo in large generated catalogs.
        for (SpeciesDefinition predator : catalog.values()) {
            for (String preyId : predator.preySpecies()) {
                SpeciesDefinition prey = catalog.get(preyId);
                if (prey != null && !prey.predatorSpecies().contains(predator.id())) {
                    warnings.add("Food web asymmetry: " + predator.id() + " hunts " + preyId + " but prey does not list predator");
                }
            }
        }
        return new Report(errors, warnings);
    }
}
