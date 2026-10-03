package dev.livingrealms.sim.ecology;

import dev.livingrealms.sim.biome.ClimateBand;
import java.util.*;

/**
 * Starter catalog. The engine is intentionally data-driven so the complete terrestrial/aquatic
 * catalog can be generated from JSON without changing simulation code.
 */
public final class SpeciesCatalog {
    private SpeciesCatalog() {}
    private static Set<ClimateBand> c(ClimateBand... v) { return Set.of(v); }
    private static Set<String> s(String... v) { return Set.of(v); }
    private static SpeciesDefinition sp(String id, String name, Diet diet, ActivityCycle cycle, SocialPattern social,
            double mass, double life, double maturity, double gestation, double litter, double births,
            double food, double water, double move, double aggression, double fear, double hunt, double defense,
            double minGroup, double maxGroup, Set<ClimateBand> climates, Set<String> habitat,
            Set<String> prey, Set<String> predators, boolean attacksHumans) {
        return new SpeciesDefinition(id,name,diet,cycle,social,mass,life,maturity,gestation,litter,births,food,water,move,
                aggression,fear,hunt,defense,minGroup,maxGroup,climates,habitat,prey,predators,attacksHumans);
    }
    public static Map<String, SpeciesDefinition> starter() {
        List<SpeciesDefinition> list = List.of(
            sp("rabbit","European Rabbit",Diet.HERBIVORE,ActivityCycle.CREPUSCULAR,SocialPattern.COLONY,1.8,3285,180,30,5,6,0.25,0.12,3,.05,.95,.0,.10,2,20,c(ClimateBand.TEMPERATE),s("grassland","forest"),s(),s("red_fox","gray_wolf","golden_eagle"),false),
            sp("red_deer","Red Deer",Diet.HERBIVORE,ActivityCycle.CREPUSCULAR,SocialPattern.HERD,180,5475,600,235,1,1,5,8,8,.10,.8,0,.55,2,30,c(ClimateBand.TEMPERATE,ClimateBand.BOREAL),s("forest","grassland"),s(),s("gray_wolf"),false),
            sp("bison","American Bison",Diet.HERBIVORE,ActivityCycle.DIURNAL,SocialPattern.HERD,700,7300,730,285,1,1,12,30,10,.25,.55,0,.9,4,100,c(ClimateBand.TEMPERATE),s("grassland"),s(),s("gray_wolf"),true),
            sp("zebra","Plains Zebra",Diet.HERBIVORE,ActivityCycle.CATHEMERAL,SocialPattern.HERD,350,7300,1100,375,1,.8,8,20,12,.12,.8,0,.55,4,30,c(ClimateBand.TROPICAL),s("grassland","warm"),s(),s("lion"),false),
            sp("wildebeest","Blue Wildebeest",Diet.HERBIVORE,ActivityCycle.DIURNAL,SocialPattern.HERD,230,7300,900,250,1,1,7,18,20,.08,.9,0,.45,8,200,c(ClimateBand.TROPICAL),s("grassland","warm"),s(),s("lion"),false),
            sp("moose","Moose",Diet.HERBIVORE,ActivityCycle.CREPUSCULAR,SocialPattern.SOLITARY,450,6570,730,235,1.5,1,10,25,10,.3,.55,0,.8,1,3,c(ClimateBand.BOREAL,ClimateBand.TEMPERATE),s("forest","wetland","cold"),s(),s("gray_wolf"),true),
            sp("gray_wolf","Gray Wolf",Diet.CARNIVORE,ActivityCycle.CATHEMERAL,SocialPattern.PACK,45,4745,670,63,5,1,2.5,2,30,.7,.35,.78,.5,3,12,c(ClimateBand.BOREAL,ClimateBand.TEMPERATE,ClimateBand.POLAR),s("forest","grassland","cold"),s("rabbit","red_deer","moose","bison"),s(),true),
            sp("red_fox","Red Fox",Diet.OMNIVORE,ActivityCycle.CREPUSCULAR,SocialPattern.PAIR,6,4380,300,52,4,1,0.5,.5,12,.35,.55,.65,.2,1,4,c(ClimateBand.TEMPERATE,ClimateBand.BOREAL),s("forest","grassland"),s("rabbit"),s("gray_wolf"),false),
            sp("lion","Lion",Diet.CARNIVORE,ActivityCycle.CATHEMERAL,SocialPattern.PACK,190,5475,1100,110,3,0.7,6,5,18,.85,.18,.88,.65,2,15,c(ClimateBand.TROPICAL),s("grassland","warm"),s("zebra","wildebeest"),s(),true),
            sp("brown_bear","Brown Bear",Diet.OMNIVORE,ActivityCycle.CATHEMERAL,SocialPattern.SOLITARY,280,9125,1460,220,2,.5,7,10,15,.72,.3,.62,.85,1,2,c(ClimateBand.BOREAL,ClimateBand.TEMPERATE),s("forest","river","cold"),s("salmon","red_deer"),s(),true),
            sp("polar_bear","Polar Bear",Diet.CARNIVORE,ActivityCycle.CATHEMERAL,SocialPattern.SOLITARY,450,9125,1460,240,2,.5,8,8,20,.88,.12,.86,.9,1,2,c(ClimateBand.POLAR),s("cold","coast"),s("seal"),s(),true),
            sp("golden_eagle","Golden Eagle",Diet.CARNIVORE,ActivityCycle.DIURNAL,SocialPattern.PAIR,5,9125,1460,45,1.5,1,0.3,.1,60,.35,.5,.7,.2,1,2,c(ClimateBand.TEMPERATE,ClimateBand.ALPINE),s("mountain","grassland","flying"),s("rabbit"),s(),false),
            sp("wild_boar","Wild Boar",Diet.OMNIVORE,ActivityCycle.NOCTURNAL,SocialPattern.HERD,90,5475,365,115,5,1.5,3,4,8,.55,.5,.25,.6,2,15,c(ClimateBand.TEMPERATE,ClimateBand.SUBTROPICAL),s("forest","wetland"),s(),s("gray_wolf"),true),
            sp("saltwater_crocodile","Saltwater Crocodile",Diet.CARNIVORE,ActivityCycle.CATHEMERAL,SocialPattern.SOLITARY,500,25550,3650,90,35,.5,5,3,8,.95,.05,.92,.98,1,3,c(ClimateBand.TROPICAL),s("wetland","river","coast","warm","amphibious"),s("wild_boar"),s(),true),
            sp("salmon","Atlantic Salmon",Diet.OMNIVORE,ActivityCycle.CATHEMERAL,SocialPattern.SCHOOL,5,2920,730,30,1000,.5,.08,0,15,.02,.9,.2,.08,20,1000,c(ClimateBand.AQUATIC,ClimateBand.BOREAL,ClimateBand.TEMPERATE),s("river","freshwater","ocean","aquatic"),s(),s("brown_bear"),false),
            sp("seal","Harbor Seal",Diet.PISCIVORE,ActivityCycle.CATHEMERAL,SocialPattern.COLONY,90,10950,1460,300,1,1,5,0,25,.12,.75,.5,.35,2,40,c(ClimateBand.POLAR,ClimateBand.AQUATIC,ClimateBand.TEMPERATE),s("coast","ocean","cold","amphibious"),s("salmon"),s("polar_bear"),false),
            sp("orca","Orca",Diet.CARNIVORE,ActivityCycle.CATHEMERAL,SocialPattern.PACK,4000,18250,3650,510,1,.2,100,0,80,.72,.1,.95,.98,3,25,c(ClimateBand.AQUATIC,ClimateBand.POLAR,ClimateBand.TEMPERATE),s("ocean","coast","aquatic"),s("seal","salmon"),s(),false),
            sp("great_white_shark","Great White Shark",Diet.CARNIVORE,ActivityCycle.CATHEMERAL,SocialPattern.SOLITARY,900,14600,2920,365,8,.2,30,0,70,.85,.1,.93,.86,1,2,c(ClimateBand.AQUATIC,ClimateBand.TEMPERATE,ClimateBand.SUBTROPICAL),s("ocean","coast","aquatic"),s("seal","salmon"),s("orca"),true),
            sp("elephant","African Bush Elephant",Diet.HERBIVORE,ActivityCycle.CATHEMERAL,SocialPattern.HERD,5000,23725,3650,660,1,.2,150,120,20,.4,.25,0,1.0,4,40,c(ClimateBand.TROPICAL),s("grassland","warm"),s(),s(),true),
            sp("hippopotamus","Hippopotamus",Diet.HERBIVORE,ActivityCycle.NOCTURNAL,SocialPattern.HERD,1500,14600,2200,240,1,.5,40,50,8,.92,.15,0,.98,4,30,c(ClimateBand.TROPICAL),s("river","wetland","warm","amphibious"),s(),s(),true)
        );
        Map<String,SpeciesDefinition> out = new LinkedHashMap<>();
        for (SpeciesDefinition sp : list) {
            if (out.put(sp.id(), sp) != null) throw new IllegalStateException("Duplicate species: " + sp.id());
        }
        return Collections.unmodifiableMap(out);
    }
}
