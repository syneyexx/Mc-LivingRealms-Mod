package dev.livingrealms;

import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.government.GovernmentType;
import dev.livingrealms.sim.world.*;

/** Product gate for the hidden Wizard Trees faction and its redstone-lit underground plan. */
public final class WizardTreesTest {
    private WizardTreesTest(){}
    public static void main(String[] args){
        SimulationState state=new SimulationState(0x715A7DL);DemoSeeder.seed(state);
        var faction=state.factions().stream().filter(WizardTreesSeeder::isWizardTrees).findFirst().orElseThrow();
        check(faction.government().type()==GovernmentType.THEOCRACY,"Wizard Trees government");
        check(faction.settlements().size()>=3,"Wizard Trees colonies");
        var settlement=faction.settlements().getFirst();var plan=WizardTreesPlanner.plan(faction,settlement);
        check(plan.stream().anyMatch(i->i.role()==StructureRole.WIZARD_HALL),"missing wizard hall");
        check(plan.stream().anyMatch(i->i.role()==StructureRole.WIZARD_GROVE),"missing grow chamber");
        check(plan.stream().anyMatch(i->i.role()==StructureRole.WIZARD_TUNNEL),"missing tunnels");
        long redstone=plan.stream().map(StructureBlueprintFactory::create).flatMap(b->b.placements().stream()).filter(p->p.slot()==PaletteSlot.REDSTONE_LIGHT).count();
        check(redstone>=4,"grow complex must contain redstone-light semantics");
        check(WizardTreesSeeder.ensure(state)==0,"Wizard Trees seeding must be idempotent");
        System.out.println("PASS Wizard Trees: hidden theocracy + three colonies + underground halls/homes/tunnels + redstone-lit grow chamber");
    }
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
