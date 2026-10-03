package dev.livingrealms.sim.property;

import java.util.*;

/** Pure inventory-delta assessment used by the Minecraft container bridge. */
public final class ContainerTheftAssessment {
    public record Lot(int count,double unitValue) {
        public Lot {
            if(count<0||!Double.isFinite(unitValue)||unitValue<0)throw new IllegalArgumentException("lot");
        }
    }
    public record Result(int removedItems,double stolenValue,Map<String,Integer> removedByKey) {
        public Result {
            if(removedItems<0||!Double.isFinite(stolenValue)||stolenValue<0)throw new IllegalArgumentException("result");
            removedByKey=Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(removedByKey,"removedByKey")));
        }
        public boolean theftOccurred(){return removedItems>0&&stolenValue>0;}
    }

    private ContainerTheftAssessment(){}

    public static Result assess(Map<String,Lot> before,Map<String,Lot> after){
        Objects.requireNonNull(before,"before");Objects.requireNonNull(after,"after");
        int removed=0;double value=0;Map<String,Integer> delta=new LinkedHashMap<>();
        for(var entry:before.entrySet()){
            String key=entry.getKey();Lot original=entry.getValue();
            if(key==null||key.isBlank()||original==null)throw new IllegalArgumentException("invalid before inventory");
            Lot now=after.get(key);int current=now==null?0:now.count();
            int missing=Math.max(0,original.count()-current);
            if(missing==0)continue;
            removed+=missing;value+=missing*original.unitValue();delta.put(key,missing);
        }
        return new Result(removed,Math.round(value*100.0)/100.0,delta);
    }
}
