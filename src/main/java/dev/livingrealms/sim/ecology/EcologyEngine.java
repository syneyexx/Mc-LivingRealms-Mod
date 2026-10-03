package dev.livingrealms.sim.ecology;

import dev.livingrealms.sim.util.*;
import java.util.*;

/** Aggregate ecological simulation. One day-step can represent thousands of off-screen animals cheaply. */
public final class EcologyEngine {
    private static final double YEAR_DAYS = 365.0;
    private final Map<String, SpeciesDefinition> species;
    public EcologyEngine(Map<String, SpeciesDefinition> species) { this.species=Map.copyOf(species); }

    public void simulate(EcosystemRegion region, double days, DeterministicRng rng) {
        if (days <= 0) return;
        region.regrowPlants(days);
        List<PopulationGroup> groups = new ArrayList<>(region.populations());
        Map<String,List<PopulationGroup>> bySpecies = new HashMap<>();
        groups.forEach(g -> bySpecies.computeIfAbsent(g.speciesId(), k -> new ArrayList<>()).add(g));

        // 1) Plants are a shared carrying-capacity resource. Allocate proportionally so ordering is irrelevant.
        Map<PopulationGroup, Double> plantNeeds = new IdentityHashMap<>();
        double totalPlantNeed = 0.0;
        for (PopulationGroup g : groups) {
            SpeciesDefinition sp = species.get(g.speciesId());
            if (sp == null || g.extinct()) continue;
            if (sp.diet()==Diet.HERBIVORE || sp.diet()==Diet.OMNIVORE || sp.diet()==Diet.DETRITIVORE) {
                double share = sp.diet()==Diet.HERBIVORE ? 1.0 : sp.diet()==Diet.OMNIVORE ? .45 : .25;
                double need = g.population() * sp.dailyFoodKg() * days * share;
                plantNeeds.put(g, need); totalPlantNeed += need;
            }
        }
        double plantEaten = region.consumePlants(totalPlantNeed);
        double plantSatisfaction = totalPlantNeed <= 0 ? 1.0 : Mathx.clamp(plantEaten / totalPlantNeed, 0.0, 1.0);
        for (PopulationGroup g : plantNeeds.keySet()) g.setHunger(g.hunger() + (1.0-plantSatisfaction)*.30 - plantSatisfaction*.12);

        // 2) Water pressure affects all species that need water. Aquatic/wet habitats largely remove this pressure.
        double availableWater=Mathx.clamp(region.biome().waterAvailability(),0,1);
        for(PopulationGroup g:groups){
            SpeciesDefinition sp=species.get(g.speciesId()); if(sp==null||g.extinct())continue;
            if(sp.dailyWaterLitres()<=0){g.setThirst(0);continue;}
            double demandScale=Mathx.clamp(sp.dailyWaterLitres()/Math.max(.1,sp.adultMassKg()*.08),.2,3.0);
            g.setThirst(g.thirst()+(1-availableWater)*.12*demandScale*days-availableWater*.09*days);
        }

        // 3) Predation. Omnivores only hunt for the carnivorous share of their diet.
        for (PopulationGroup predator : groups) {
            SpeciesDefinition p = species.get(predator.speciesId());
            if (p == null || predator.extinct() || p.preySpecies().isEmpty()) continue;
            double meatShare = p.diet()==Diet.OMNIVORE ? .55 : 1.0;
            double need = predator.population()*p.dailyFoodKg()*days*meatShare;
            double consumedKg = 0;
            List<String> preyIds = new ArrayList<>(p.preySpecies()); preyIds.sort(String::compareTo);
            for (String preyId : preyIds) {
                List<PopulationGroup> targets=bySpecies.getOrDefault(preyId,List.of()); SpeciesDefinition preyDef=species.get(preyId); if (preyDef==null) continue;
                for (PopulationGroup prey : targets) {
                    if (prey.extinct() || consumedKg>=need) continue;
                    double encounter = Mathx.clamp(.08 + p.huntSkill()*.25 + prey.hunger()*.08 - preyDef.defense()*.10, .01, .45);
                    double maxKills = predator.population()*encounter*days;
                    double desiredKills=(need-consumedKg)/Math.max(0.1,preyDef.adultMassKg()*.65);
                    // Functional response + refuge prevents a successful predator cohort from deterministically erasing
                    // every prey cohort in a few daily steps. Predators begin starving before the local prey seed is gone.
                    double viableReserve=Math.max(4.0,preyDef.minGroup()*2.0);
                    double harvestable=Math.max(0.0,prey.population()-viableReserve);
                    double dailyTake=Math.min(prey.population()*.045*days,harvestable*.18);
                    double kills=Math.min(dailyTake, Math.min(maxKills, desiredKills))*rng.between(.75,1.25);
                    kills=Math.min(kills,harvestable); prey.addPopulation(-kills); consumedKg += kills*preyDef.adultMassKg()*.65;
                    double nonFatalEncounters=Math.min(prey.population(),kills*rng.between(.15,.55));
                    prey.setInjuryPressure(prey.injuryPressure()+Mathx.safeDiv(nonFatalEncounters,Math.max(1,prey.population()))*.20);
                }
            }
            double sat=Mathx.clamp(Mathx.safeDiv(consumedKg,Math.max(.001,need)),0,1);
            predator.setHunger(predator.hunger()+(.35*(1-sat))-(.25*sat));
        }

        // 4) Disease is density- and health-dependent. It rises in crowded/stressed populations and recedes otherwise.
        for(PopulationGroup g:groups){
            SpeciesDefinition sp=species.get(g.speciesId()); if(sp==null||g.extinct())continue;
            double roughCapacity=Math.max(5,region.areaKm2()*Math.max(2,120.0/Math.sqrt(Math.max(.05,sp.adultMassKg()))));
            double density=Mathx.clamp(g.population()/roughCapacity,0,3);
            double stress=(g.hunger()+g.thirst()+g.injuryPressure())/3.0;
            double transmission=Math.max(0,density-.55)*.018*days+stress*.008*days;
            double recovery=(.012+.02*g.health())*days;
            g.setDiseasePressure(g.diseasePressure()+transmission-recovery);
        }

        // 5) Births, natural deaths, disease, injuries, starvation and habitat pressure.
        for (PopulationGroup g : groups) {
            SpeciesDefinition sp=species.get(g.speciesId()); if (sp==null || g.extinct()) continue;
            double habitat=.35; if (sp.climates().contains(region.biome().climate())) habitat+=.35;
            long tagMatches=sp.habitatTags().stream().filter(region.biome().tags()::contains).count(); habitat += Math.min(.30, tagMatches*.10); habitat=Mathx.clamp(habitat,0.05,1.0);
            double annualBirthRate=(sp.offspringPerBirth()*sp.birthsPerYear())/YEAR_DAYS;
            double stress=Mathx.clamp(g.hunger()*.45+g.thirst()*.35+g.diseasePressure()*.35+g.injuryPressure()*.25,0,1);
            double juvenileSurvival=Mathx.clamp(.68/Math.sqrt(Math.max(1.0,sp.offspringPerBirth())),.018,.58);
            double births=g.population()*g.reproductiveFraction()*annualBirthRate*days*habitat*(1-stress*.85)*juvenileSurvival;
            double ageMortality=days/Math.max(1,sp.lifespanDays());
            double starvation=g.hunger()>.6 ? (g.hunger()-.6)*.08*days : 0;
            double dehydration=g.thirst()>.55 ? (g.thirst()-.55)*.13*days : 0;
            double disease=g.diseasePressure()*.025*days;
            double injuries=g.injuryPressure()*.018*days;
            double poorHabitat=(1-habitat)*.00035*days;
            double deaths=g.population()*(ageMortality+starvation+dehydration+disease+injuries+poorHabitat);
            g.addPopulation(births-deaths);
            g.setHealth(1-Mathx.clamp(g.hunger()*.35+g.thirst()*.28+g.diseasePressure()*.25+g.injuryPressure()*.18+poorHabitat,0,.97));
            g.setMigrationPressure(Mathx.clamp((1-habitat)*.55+g.hunger()*.25+g.thirst()*.35,0,1));
            g.setAverageAgeDays(Math.min(sp.lifespanDays(),g.averageAgeDays()+days*(.45-g.averageAgeDays()/Math.max(1,sp.lifespanDays())*.18)));
            g.setHunger(Math.max(0,g.hunger()-.03*days)); g.setThirst(Math.max(0,g.thirst()-.025*days));
            g.setInjuryPressure(Math.max(0,g.injuryPressure()-.025*days));
        }
        region.purgeExtinct();
    }
}
