package dev.livingrealms.sim.data;

import dev.livingrealms.sim.biome.ClimateBand;
import dev.livingrealms.sim.ecology.*;
import java.util.*;

/** Strict JSON codec for species data. Unknown/missing required fields fail fast. */
public final class SpeciesJsonCodec {
    private SpeciesJsonCodec() {}

    public static SpeciesDefinition decode(String json) {
        Object root=MiniJson.parse(json);
        if(!(root instanceof Map<?,?> raw))throw new IllegalArgumentException("Species root must be an object");
        Map<String,Object> m=stringMap(raw);
        Set<String> allowed=Set.of("id","commonName","diet","activityCycle","socialPattern","adultMassKg","lifespanDays","maturityDays","gestationDays","offspringPerBirth","birthsPerYear","dailyFoodKg","dailyWaterLitres","movementKmPerDay","aggression","fearfulness","huntSkill","defense","minGroup","maxGroup","climates","habitatTags","preySpecies","predatorSpecies","attacksHumans","morphology","locomotion","swimSpeedFactor","flightSpeedFactor","visualFamily");
        for(String k:m.keySet())if(!allowed.contains(k))throw new IllegalArgumentException("Unknown species field: "+k);
        SpeciesDefinition base=new SpeciesDefinition(
                str(m,"id"),str(m,"commonName"),enumValue(Diet.class,str(m,"diet")),enumValue(ActivityCycle.class,str(m,"activityCycle")),enumValue(SocialPattern.class,str(m,"socialPattern")),
                num(m,"adultMassKg"),num(m,"lifespanDays"),num(m,"maturityDays"),num(m,"gestationDays"),num(m,"offspringPerBirth"),num(m,"birthsPerYear"),num(m,"dailyFoodKg"),num(m,"dailyWaterLitres"),num(m,"movementKmPerDay"),
                num(m,"aggression"),num(m,"fearfulness"),num(m,"huntSkill"),num(m,"defense"),num(m,"minGroup"),num(m,"maxGroup"),
                enumSet(ClimateBand.class,m.get("climates"),"climates"),strSet(m.get("habitatTags"),"habitatTags"),strSet(m.get("preySpecies"),"preySpecies"),strSet(m.get("predatorSpecies"),"predatorSpecies"),bool(m,"attacksHumans")
        );
        MorphologyFamily morphology=optionalEnum(m,"morphology",MorphologyFamily.class,base.morphology());
        LocomotionMode locomotion=optionalEnum(m,"locomotion",LocomotionMode.class,base.locomotion());
        double swim=optionalNum(m,"swimSpeedFactor",base.swimSpeedFactor());
        double flight=optionalNum(m,"flightSpeedFactor",base.flightSpeedFactor());
        SpeciesVisualFamily visual=optionalEnum(m,"visualFamily",SpeciesVisualFamily.class,base.visualFamily());
        if(morphology==base.morphology()&&locomotion==base.locomotion()&&Double.compare(swim,base.swimSpeedFactor())==0&&Double.compare(flight,base.flightSpeedFactor())==0&&visual==base.visualFamily())return base;
        return new SpeciesDefinition(base.id(),base.commonName(),base.diet(),base.activityCycle(),base.socialPattern(),base.adultMassKg(),base.lifespanDays(),base.maturityDays(),base.gestationDays(),base.offspringPerBirth(),base.birthsPerYear(),base.dailyFoodKg(),base.dailyWaterLitres(),base.movementKmPerDay(),base.aggression(),base.fearfulness(),base.huntSkill(),base.defense(),base.minGroup(),base.maxGroup(),base.climates(),base.habitatTags(),base.preySpecies(),base.predatorSpecies(),base.attacksHumans(),morphology,locomotion,swim,flight,visual);
    }

    public static String encode(SpeciesDefinition sp) {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("id",sp.id());m.put("commonName",sp.commonName());m.put("diet",sp.diet().name());m.put("activityCycle",sp.activityCycle().name());m.put("socialPattern",sp.socialPattern().name());
        m.put("adultMassKg",sp.adultMassKg());m.put("lifespanDays",sp.lifespanDays());m.put("maturityDays",sp.maturityDays());m.put("gestationDays",sp.gestationDays());m.put("offspringPerBirth",sp.offspringPerBirth());m.put("birthsPerYear",sp.birthsPerYear());m.put("dailyFoodKg",sp.dailyFoodKg());m.put("dailyWaterLitres",sp.dailyWaterLitres());m.put("movementKmPerDay",sp.movementKmPerDay());
        m.put("aggression",sp.aggression());m.put("fearfulness",sp.fearfulness());m.put("huntSkill",sp.huntSkill());m.put("defense",sp.defense());m.put("minGroup",sp.minGroup());m.put("maxGroup",sp.maxGroup());
        m.put("climates",sp.climates().stream().map(Enum::name).sorted().toList());m.put("habitatTags",sp.habitatTags().stream().sorted().toList());m.put("preySpecies",sp.preySpecies().stream().sorted().toList());m.put("predatorSpecies",sp.predatorSpecies().stream().sorted().toList());m.put("attacksHumans",sp.attacksHumans());m.put("morphology",sp.morphology().name());m.put("locomotion",sp.locomotion().name());m.put("swimSpeedFactor",sp.swimSpeedFactor());m.put("flightSpeedFactor",sp.flightSpeedFactor());if(sp.visualFamily()!=null)m.put("visualFamily",sp.visualFamily().name());
        return MiniJson.stringify(m);
    }

    private static Map<String,Object> stringMap(Map<?,?> raw){Map<String,Object> out=new LinkedHashMap<>();for(var e:raw.entrySet()){if(!(e.getKey() instanceof String k))throw new IllegalArgumentException("Non-string key");out.put(k,e.getValue());}return out;}
    private static String str(Map<String,Object> m,String k){Object v=require(m,k);if(!(v instanceof String s)||s.isBlank())throw new IllegalArgumentException(k+" must be non-empty string");return s;}
    private static double num(Map<String,Object> m,String k){Object v=require(m,k);if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException(k+" must be finite number");return n.doubleValue();}
    private static double optionalNum(Map<String,Object> m,String k,double fallback){Object v=m.get(k);if(v==null)return fallback;if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException(k+" must be finite number");return n.doubleValue();}
    private static <E extends Enum<E>> E optionalEnum(Map<String,Object> m,String k,Class<E> type,E fallback){Object v=m.get(k);if(v==null)return fallback;if(!(v instanceof String s)||s.isBlank())throw new IllegalArgumentException(k+" must be string");return enumValue(type,s);}
    private static boolean bool(Map<String,Object> m,String k){Object v=require(m,k);if(!(v instanceof Boolean b))throw new IllegalArgumentException(k+" must be boolean");return b;}
    private static Object require(Map<String,Object> m,String k){if(!m.containsKey(k))throw new IllegalArgumentException("Missing species field: "+k);return m.get(k);}
    private static Set<String> strSet(Object v,String field){if(!(v instanceof List<?> list))throw new IllegalArgumentException(field+" must be array");LinkedHashSet<String> out=new LinkedHashSet<>();for(Object x:list){if(!(x instanceof String s)||s.isBlank())throw new IllegalArgumentException(field+" entries must be strings");out.add(s);}return Set.copyOf(out);}
    private static <E extends Enum<E>> E enumValue(Class<E> type,String value){try{return Enum.valueOf(type,value.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid "+type.getSimpleName()+": "+value,e);}}
    private static <E extends Enum<E>> Set<E> enumSet(Class<E> type,Object v,String field){if(!(v instanceof List<?> list))throw new IllegalArgumentException(field+" must be array");LinkedHashSet<E> out=new LinkedHashSet<>();for(Object x:list){if(!(x instanceof String s))throw new IllegalArgumentException(field+" entries must be strings");out.add(enumValue(type,s));}return Set.copyOf(out);}
}
