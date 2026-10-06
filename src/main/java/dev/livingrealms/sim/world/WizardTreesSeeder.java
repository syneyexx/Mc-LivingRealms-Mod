package dev.livingrealms.sim.world;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.GovernmentType;

/** Seeds the hidden mountain/underground magic faction as an additive canonical realm. */
public final class WizardTreesSeeder {
    public static final String FACTION_NAME="Wizard Trees";

    public record StarterSettlementSpec(
            String stableKey, String name, SimPosition position, int population, int housing) {}

    private static final java.util.List<StarterSettlementSpec> STARTER_SETTLEMENTS = java.util.List.of(
            new StarterSettlementSpec("rootvault", "Rootvault",
                    new SimPosition(-12_400,-14_200), 760, 860),
            new StarterSettlementSpec("emberroot_hollow", "Emberroot Hollow",
                    new SimPosition(-11_500,-14_650), 340, 390),
            new StarterSettlementSpec("mycelium_deep", "The Mycelium Deep",
                    new SimPosition(-12_900,-14_900), 210, 250));

    private WizardTreesSeeder(){}

    public static java.util.List<StarterSettlementSpec> starterSettlements() {
        return STARTER_SETTLEMENTS;
    }

    public static int ensure(SimulationState state){
        for(Faction faction:state.factions())if(isWizardTrees(faction))return 0;
        Faction faction=new Faction(state.nextId(),FACTION_NAME,"Root-Seer Sylvara");
        faction.government().setType(GovernmentType.THEOCRACY);
        faction.government().setTaxRate(.07);faction.government().setLawEnforcement(.52);
        faction.advanceTechnology(.62);faction.addTreasury(6_800);
        faction.stockpile().add(ResourceType.FOOD,2_400);faction.stockpile().add(ResourceType.WOOD,1_100);faction.stockpile().add(ResourceType.STONE,2_800);faction.stockpile().add(ResourceType.IRON,420);faction.stockpile().add(ResourceType.TOOLS,180);
        // Separate underground civilization layer: compact 600–1500 block colony spacing.
        // SPECIAL role keeps these colonies out of the ordinary surface settlement/road matrix.
        for (StarterSettlementSpec spec : STARTER_SETTLEMENTS) {
            faction.addSettlement(new Settlement(
                    state.nextId(), spec.name(), spec.position(), spec.population(), spec.housing(),
                    SettlementOrigin.WIZARD_TREES, true, DevelopmentMode.AUTO, SettlementRole.SPECIAL));
        }
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
