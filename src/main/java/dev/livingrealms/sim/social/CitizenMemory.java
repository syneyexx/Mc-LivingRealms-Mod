package dev.livingrealms.sim.social;

import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Bounded personal memory/knowledge item. */
public record CitizenMemory(long day,MemoryType type,String subjectKey,String sourceKey,String summary,SimPosition position,double importance,double confidence) {
    public CitizenMemory {
        if(day<0||type==null||position==null)throw new IllegalArgumentException("memory");
        subjectKey=Objects.requireNonNullElse(subjectKey,"");sourceKey=Objects.requireNonNullElse(sourceKey,"");summary=Objects.requireNonNullElse(summary,"");
        if(summary.length()>512)summary=summary.substring(0,512);
        if(!Double.isFinite(importance)||!Double.isFinite(confidence))throw new IllegalArgumentException("memory score");
        importance=Mathx.clamp(importance,0,1);confidence=Mathx.clamp(confidence,0,1);
    }
}
