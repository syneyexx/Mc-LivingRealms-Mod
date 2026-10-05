package dev.livingrealms.sim.world;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.GovernmentType;

/** Seeds the hidden mountain/underground magic faction as an additive canonical realm. */
public final class WizardTreesSeeder {
    public static final String FACTION_NAME="Wizard Trees";
    private WizardTreesSeeder(){}

    public static int ensure(SimulationState state){
        for(Faction faction:state.factions())if(isWizardTrees(faction))return 0;
        Faction faction=new Faction(state.nextId(),FACTION_NAME,"Root-Seer Sylvara");
        faction.government().setType(GovernmentType.THEOCRACY);
        faction.government().setTaxRate(.07);faction.government().setLawEnforcement(.52);
        faction.advanceTechnology(.62);faction.addTreasury(6_800);
        faction.stockpile().add(ResourceType.FOOD,2_400);faction.stockpile().add(ResourceType.WOOD,1_100);faction.stockpile().add(ResourceType.STONE,2_800);faction.stockpile().add(ResourceType.IRON,420);faction.stockpile().add(ResourceType.TOOLS,180);
        // Keep Wizard Trees colonies on the same spacing floor as surface realms.
        faction.addSettlement(new Settlement(state.nextId(),"Rootvault",new SimPosition(4_900,-6_700),760,860,
                SettlementOrigin.WIZARD_TREES,true,DevelopmentMode.AUTO));
        faction.addSettlement(new Settlement(state.nextId(),"Emberroot Hollow",new SimPosition(7_100,-7_200),340,390,
                SettlementOrigin.WIZARD_TREES,true,DevelopmentMode.AUTO));
        faction.addSettlement(new Settlement(state.nextId(),"The Mycelium Deep",new SimPosition(2_700,-8_100),210,250,
                SettlementOrigin.WIZARD_TREES,true,DevelopmentMode.AUTO));
        Army wardens=new Army(state.nextId(),faction.id(),faction.settlements().getFirst().position(),74);faction.addArmy(wardens);
        for(Faction other:state.factions()){
            faction.relationWith(other.id()).adjust(-4);other.relationWith(faction.id()).adjust(-4);
        }
        state.addFaction(faction);
        state.history().add(new WorldEvent(state.clock().day(),"wizard_trees_emerge","Rumors spread of the Wizard Trees, a hidden magical society beneath the mountains."));
        return 1;
    }
    public static boolean isWizardTrees(Faction faction){return faction!=null&&FACTION_NAME.equals(faction.name());}
}
