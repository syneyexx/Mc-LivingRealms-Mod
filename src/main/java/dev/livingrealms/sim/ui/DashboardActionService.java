package dev.livingrealms.sim.ui;

import dev.livingrealms.sim.config.SimulationPreset;
import dev.livingrealms.sim.faction.DevelopmentPriority;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;

import dev.livingrealms.sim.territory.TerritoryEngine;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Objects;

/** Canonical authorization and mutation rules for dashboard actions. Minecraft only transports the intent. */
public final class DashboardActionService {
    private DashboardActionService(){}

    public static Result apply(SimulationState state,String actorKey,SimPosition position,DashboardActionCommand command){
        Objects.requireNonNull(state,"state");Objects.requireNonNull(position,"position");Objects.requireNonNull(command,"command");
        if(actorKey==null||actorKey.isBlank())return new Result(false,false,"invalid_actor");
        SimulationPreset preset=switch(command.action()){
            case CONFIG_PERFORMANCE -> SimulationPreset.PERFORMANCE;
            case CONFIG_BALANCED -> SimulationPreset.BALANCED;
            case CONFIG_IMMERSIVE -> SimulationPreset.IMMERSIVE;
            case CONFIG_CINEMATIC -> SimulationPreset.CINEMATIC;
            case CONFIG_SHOWCASE -> SimulationPreset.SHOWCASE;
            default -> null;
        };
        if(preset!=null){state.setConfig(preset.config());return new Result(true,true,"config_"+preset.name().toLowerCase());}
        if(command.action()==DashboardActionCommand.Action.FACTION_LEAVE){boolean left=state.leaveFaction(actorKey);return new Result(left,left,left?"faction_left":"not_member");}
        if(command.action()==DashboardActionCommand.Action.TAX_LOWER||command.action()==DashboardActionCommand.Action.TAX_RAISE||isSettlementPolicy(command.action())){
            var standing=state.findPlayerStanding(actorKey).orElse(null);if(standing==null||!standing.isMember())return new Result(false,false,"not_member");
            Faction faction=state.findFaction(standing.memberFactionId()).orElse(null);if(faction==null)return new Result(false,false,"member_faction_missing");
            if(command.action()==DashboardActionCommand.Action.TAX_LOWER||command.action()==DashboardActionCommand.Action.TAX_RAISE){
                if(command.targetId()!=faction.id())return new Result(false,false,"wrong_faction");
                double before=faction.government().taxRate();double delta=command.action()==DashboardActionCommand.Action.TAX_RAISE?.01:-.01;faction.government().setTaxRate(before+delta);
                boolean changed=Math.abs(faction.government().taxRate()-before)>1e-12;return new Result(changed,changed,changed?"tax_updated":"tax_limit");
            }
            Settlement settlement=faction.settlements().stream().filter(s->s.id()==command.targetId()).findFirst().orElse(null);if(settlement==null)return new Result(false,false,"settlement_not_owned");
            DevelopmentPriority priority=switch(command.action()){case SETTLEMENT_BALANCED->DevelopmentPriority.BALANCED;case SETTLEMENT_FOOD->DevelopmentPriority.FOOD;case SETTLEMENT_HOUSING->DevelopmentPriority.HOUSING;case SETTLEMENT_INDUSTRY->DevelopmentPriority.INDUSTRY;case SETTLEMENT_DEFENSE->DevelopmentPriority.DEFENSE;default->throw new IllegalStateException("policy action");};
            if(settlement.developmentPriority()==priority)return new Result(false,false,"policy_unchanged");settlement.setDevelopmentPriority(priority);return new Result(true,true,"policy_"+priority.name().toLowerCase());
        }
        if(command.action()==DashboardActionCommand.Action.MARKET_BUY||command.action()==DashboardActionCommand.Action.MARKET_SELL)return new Result(false,false,"runtime_inventory_required");
        var jurisdiction=TerritoryEngine.resolve(state.factions(),position,state.config().borderDisputeThreshold());
        if(!jurisdiction.claimed()||jurisdiction.contested())return new Result(false,false,"no_usable_board");
        if(command.action()==DashboardActionCommand.Action.FACTION_JOIN_LOCAL){
            if(command.targetId()!=jurisdiction.primaryFactionId())return new Result(false,false,"wrong_local_faction");
            var joined=state.joinFaction(actorKey,jurisdiction.primaryFactionId());return new Result(joined.success(),joined.success(),joined.reason());
        }
        var contract=state.bounties().stream().filter(b->b.id()==command.targetId()).findFirst().orElse(null);
        if(contract==null||contract.issuerFactionId()!=jurisdiction.primaryFactionId())return new Result(false,false,"wrong_board");
        return switch(command.action()){
            case BOUNTY_ACCEPT -> {var r=state.acceptBounty(contract.id(),actorKey);yield new Result(r.success(),r.success(),r.reason());}
            case BOUNTY_ABANDON -> {var r=state.abandonBounty(contract.id(),actorKey);yield new Result(r.success(),r.success(),r.reason());}
            case CONFIG_PERFORMANCE,CONFIG_BALANCED,CONFIG_IMMERSIVE,CONFIG_CINEMATIC,CONFIG_SHOWCASE,FACTION_JOIN_LOCAL,FACTION_LEAVE,TAX_LOWER,TAX_RAISE,SETTLEMENT_BALANCED,SETTLEMENT_FOOD,SETTLEMENT_HOUSING,SETTLEMENT_INDUSTRY,SETTLEMENT_DEFENSE,MARKET_BUY,MARKET_SELL -> new Result(false,false,"action_unreachable");
        };
    }

    private static boolean isSettlementPolicy(DashboardActionCommand.Action action){return switch(action){case SETTLEMENT_BALANCED,SETTLEMENT_FOOD,SETTLEMENT_HOUSING,SETTLEMENT_INDUSTRY,SETTLEMENT_DEFENSE->true;default->false;};}

    public record Result(boolean success,boolean dirty,String reason){
        public Result{reason=reason==null?"":reason;}
    }
}
