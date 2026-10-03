package dev.livingrealms.sim.military;

import dev.livingrealms.sim.world.SimPosition;

/** One representative tactical entity for an aggregate Army. */
public record MilitaryProjection(long armyId,long factionId,int slot,MilitaryUnitClass unitClass,int representedPersonnel,SimPosition armyPosition) {
    public MilitaryProjection {if(armyId<=0||factionId<=0||slot<0||unitClass==null||representedPersonnel<=0||armyPosition==null)throw new IllegalArgumentException("military projection");}
    public String projectionKey(){return armyId+":"+slot;}
}
