package dev.livingrealms.sim.ui;

import dev.livingrealms.sim.config.SimulationPreset;
import dev.livingrealms.sim.faction.DevelopmentPriority;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.military.MilitaryObjectiveType;
import dev.livingrealms.sim.player.FactionRank;
import dev.livingrealms.sim.player.PlayerAgencyActions;
import dev.livingrealms.sim.player.PlayerInfluenceActions;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
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
            default -> null;
        };
        if(preset!=null){state.setConfig(preset.config());return new Result(true,true,"config_"+preset.name().toLowerCase());}
        if(command.action()==DashboardActionCommand.Action.FACTION_LEAVE){
            var standing=state.findPlayerStanding(actorKey).orElse(null);
            if(standing==null||!standing.isMember())return new Result(false,false,"not_member");
            if(standing.rank()==FactionRank.RULER)return new Result(false,false,"ruler_cannot_leave");
            boolean left=state.leaveFaction(actorKey);
            return new Result(left,left,left?"faction_left":"leave_failed");
        }
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
        if(command.action()==DashboardActionCommand.Action.FOUND_SETTLEMENT){
            // Ensure standing exists so founding / influence never fail with opaque no_standing.
            state.playerStanding(actorKey);
            String playerName=actorKey.contains(":")?actorKey.substring(actorKey.indexOf(':')+1):actorKey;
            if(playerName.length()>24)playerName=playerName.substring(0,24);
            String settlementName=command.argument();
            if(settlementName==null||settlementName.isBlank()){
                settlementName=playerName.length()>=2?playerName+"stead":"Newstead";
            }
            var r=PlayerSettlementFounder.found(state,actorKey,playerName,settlementName,position);
            return new Result(r.success(),r.success(),r.success()?"founded_"+r.settlementName():r.reason());
        }
        if(command.action()==DashboardActionCommand.Action.MARKET_BUY||command.action()==DashboardActionCommand.Action.MARKET_SELL)return new Result(false,false,"runtime_inventory_required");
        if(command.action()==DashboardActionCommand.Action.ABDICATE){
            var r=PlayerAgencyActions.abdicate(state,actorKey);
            return new Result(r.success(),r.dirty(),r.reason());
        }
        if(command.action()==DashboardActionCommand.Action.PETITION_PEACE){
            var r=PlayerAgencyActions.petitionPeace(state,actorKey,command.targetId());
            return new Result(r.success(),r.dirty(),r.reason());
        }
        if(command.action()==DashboardActionCommand.Action.PROPOSE_TRADE_PACT){
            var r=PlayerAgencyActions.proposeTradePact(state,actorKey,command.targetId());
            return new Result(r.success(),r.dirty(),r.reason());
        }
        if(command.action()==DashboardActionCommand.Action.ARMY_DEFEND_HOME){
            var r=PlayerAgencyActions.armyOrder(state,actorKey,command.targetId(),MilitaryObjectiveType.DEFEND);
            return new Result(r.success(),r.dirty(),r.reason());
        }
        if(command.action()==DashboardActionCommand.Action.ARMY_STAND_DOWN){
            var r=PlayerAgencyActions.armyOrder(state,actorKey,command.targetId(),MilitaryObjectiveType.RETREAT);
            return new Result(r.success(),r.dirty(),r.reason());
        }
        if(command.action()==DashboardActionCommand.Action.ARMY_RALLY){
            var r=PlayerAgencyActions.armyOrder(state,actorKey,command.targetId(),MilitaryObjectiveType.PATROL_BORDER);
            return new Result(r.success(),r.dirty(),r.reason());
        }
        if(command.action()==DashboardActionCommand.Action.SURRENDER||command.action()==DashboardActionCommand.Action.PAY_FINE){
            long factionId=command.targetId();
            if(command.action()==DashboardActionCommand.Action.SURRENDER){
                double bounty=state.captureCriminal(actorKey,factionId);
                return new Result(true,true,"surrendered_bounty_"+Math.round(Math.max(0,bounty)));
            }
            double offer=25;
            if(!command.argument().isBlank()){
                try{offer=Math.max(1,Double.parseDouble(command.argument()));}catch(NumberFormatException ignored){offer=25;}
            }
            double paid=state.payFine(actorKey,factionId,offer);
            return new Result(paid>0,paid>0,paid>0?"fine_paid_"+Math.round(paid):"fine_failed");
        }
        if(isInfluenceAction(command.action())){
            if(command.action()==DashboardActionCommand.Action.REQUEST_MILITARY_SUPPORT){
                var r=PlayerAgencyActions.requestMilitarySupportMission(state,actorKey,command.targetId());
                return new Result(r.success(),r.dirty(),r.reason());
            }
            var unlock=switch(command.action()){
                case REQUEST_AUDIENCE -> PlayerInfluenceActions.Unlock.REQUEST_AUDIENCE;
                case PROPOSE_PROJECT -> PlayerInfluenceActions.Unlock.PROPOSE_PROJECT;
                case REQUEST_MILITARY_SUPPORT -> PlayerInfluenceActions.Unlock.REQUEST_MILITARY_SUPPORT;
                case PETITION_TRADE -> PlayerInfluenceActions.Unlock.PETITION_TRADE;
                case PETITION_CLERGY -> PlayerInfluenceActions.Unlock.PETITION_CLERGY;
                default -> throw new IllegalStateException("influence action");
            };
            var r=PlayerInfluenceActions.apply(state,actorKey,command.targetId(),unlock);
            return new Result(r.success(),r.dirty(),r.reason());
        }
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
            case CONFIG_PERFORMANCE,CONFIG_BALANCED,CONFIG_IMMERSIVE,CONFIG_CINEMATIC,FACTION_JOIN_LOCAL,FACTION_LEAVE,TAX_LOWER,TAX_RAISE,SETTLEMENT_BALANCED,SETTLEMENT_FOOD,SETTLEMENT_HOUSING,SETTLEMENT_INDUSTRY,SETTLEMENT_DEFENSE,MARKET_BUY,MARKET_SELL,REQUEST_AUDIENCE,PROPOSE_PROJECT,REQUEST_MILITARY_SUPPORT,PETITION_TRADE,PETITION_CLERGY,FOUND_SETTLEMENT,ABDICATE,PETITION_PEACE,PROPOSE_TRADE_PACT,ARMY_DEFEND_HOME,ARMY_RALLY,ARMY_STAND_DOWN,SURRENDER,PAY_FINE -> new Result(false,false,"action_unreachable");
        };
    }

    private static boolean isInfluenceAction(DashboardActionCommand.Action action){
        return switch(action){
            case REQUEST_AUDIENCE,PROPOSE_PROJECT,REQUEST_MILITARY_SUPPORT,PETITION_TRADE,PETITION_CLERGY -> true;
            default -> false;
        };
    }

    private static boolean isSettlementPolicy(DashboardActionCommand.Action action){return switch(action){case SETTLEMENT_BALANCED,SETTLEMENT_FOOD,SETTLEMENT_HOUSING,SETTLEMENT_INDUSTRY,SETTLEMENT_DEFENSE->true;default->false;};}

    public record Result(boolean success,boolean dirty,String reason){
        public Result{reason=reason==null?"":reason;}
    }
}
