package dev.livingrealms;

import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.civilization.projection.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.*;
import java.util.List;

/** Regression gate: traveling humanity/piracy become bounded physical projections without becoming canonical state. */
public final class MobileCivilizationProjectionTest {
    private MobileCivilizationProjectionTest(){}
    public static void main(String[] args){
        SimulationState state=new SimulationState(0x4D4F42494C45L);
        Faction faction=new Faction(state.nextId(),"Wayfarers","Ruler");
        Settlement source=new Settlement(state.nextId(),"Oldford",new SimPosition(0,0),180,200);
        Settlement target=new Settlement(state.nextId(),"Newhaven",new SimPosition(400,0),300,360);
        faction.addSettlement(source);faction.addSettlement(target);state.addFaction(faction);
        MigrationGroup group=new MigrationGroup(state.nextId(),faction.id(),source.id(),target.id(),0,90,MigrationReason.WAR);group.advance(.5);state.addMigrationGroup(group);
        PirateBand band=new PirateBand(state.nextId(),source.id(),0,new SimPosition(220,40),21);state.addPirateBand(band);

        List<MigrationProjection> migrants=MobileCivilizationProjectionPlanner.migrations(state,List.of(new SimPosition(200,0)),500,3);
        check(migrants.size()==3,"migration visuals must obey budget");check(migrants.stream().mapToInt(MigrationProjection::representedPeople).sum()==90,"migration projection must represent group exactly");
        check(migrants.stream().allMatch(p->p.position().distanceTo(new SimPosition(200,0))<12),"migration projections follow canonical progress");
        List<PirateProjection> pirates=MobileCivilizationProjectionPlanner.pirates(state,List.of(new SimPosition(220,40)),500,2);
        check(pirates.size()==2,"pirate visuals must obey budget");check(pirates.stream().mapToInt(PirateProjection::representedPirates).sum()==21,"pirate projection must represent band exactly");
        check(group.people()==90&&band.strength()==21,"planning must never mutate canonical population");

        check(state.recordPhysicalMigrationLoss(group.id(),migrants.getFirst().representedPeople(),"test"),"physical migration loss");
        check(group.people()==60,"physical migration death must reduce canonical group once");
        check(state.recordPhysicalPirateLoss(band.id(),pirates.getFirst().representedPirates(),"test"),"physical pirate loss");
        check(band.strength()==10,"physical pirate death must reduce canonical band once");
        check(!state.recordPhysicalPirateLoss(999999,1,"test"),"unknown physical projection must not create state");
        System.out.println("PASS mobile civilization projection: bounded migration/piracy LOD + canonical physical-loss bridge");
    }
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
