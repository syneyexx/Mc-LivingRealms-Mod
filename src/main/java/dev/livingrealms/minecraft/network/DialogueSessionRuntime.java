package dev.livingrealms.minecraft.network;

import dev.livingrealms.minecraft.*;
import dev.livingrealms.minecraft.economy.PlayerMarketRuntime;
import dev.livingrealms.minecraft.entity.*;
import dev.livingrealms.minecraft.law.CrimeRuntime;
import dev.livingrealms.sim.dialogue.*;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side conversation sessions. Short context is transient; NPC memory/relationships are canonical and persisted. */
public final class DialogueSessionRuntime {
    private static final NaturalLanguageDialogueEngine ENGINE=new NaturalLanguageDialogueEngine();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private DialogueSessionRuntime(){}
    public static void open(ServerPlayer player,FactionCitizenEntity entity){
        if(player==null||entity==null||!entity.isAlive()||player.distanceToSqr(entity)>64.0D)return;var data=SimulationRuntime.data(player.serverLevel().getServer());var state=data.state();int before=state.socialCitizens().size();SocialCitizen citizen=entity.citizenId()>0?state.findSocialCitizen(entity.citizenId()).orElse(null):null;if(citizen==null)citizen=state.ensureSocialCitizen(entity.factionId(),entity.settlementId(),entity.projectionSlot(),entity.role());if(state.socialCitizens().size()!=before)data.setDirty();
        DialogueContext context=new DialogueContext();String actor=CrimeRuntime.actorKey(player);DialogueResult greeting=ENGINE.respond(state,citizen,actor,"hello",context);data.setDirty();SESSIONS.put(player.getUUID(),new Session(citizen.id(),context,entity.getUUID()));PacketDistributor.sendToPlayer(player,new DialogueOpenPayload(citizen.id(),citizen.name(),citizen.role().name(),greeting.response()));
    }
    public static void open(ServerPlayer player,AbstractVillager villager){
        if(player==null||villager==null||!villager.isAlive()||player.distanceToSqr(villager)>64.0D)return;
        openAdopted(player,villager,villagerRole(villager));
    }

    /** Stable adoption path for allowlisted foreign civilians that do not extend AbstractVillager. */
    public static void openAdopted(ServerPlayer player,Entity entity,CitizenRole role){
        if(player==null||entity==null||!entity.isAlive()||player.distanceToSqr(entity)>64.0D)return;
        var data=SimulationRuntime.data(player.serverLevel().getServer());var state=data.state();
        Faction owner=null;Settlement settlement=null;double best=Double.POSITIVE_INFINITY;
        for(Faction f:state.factions())for(Settlement st:f.settlements()){double dx=entity.getX()-st.position().x(),dz=entity.getZ()-st.position().z(),d=dx*dx+dz*dz;if(d<best){best=d;owner=f;settlement=st;}}
        if(owner==null||settlement==null)return;
        // Same UUID → same projection slot forever (reload-safe identity).
        int slot=1_000_000+Math.floorMod(entity.getUUID().hashCode(),900_000_000);
        CitizenRole resolved=role==null?CitizenRole.TRADER:role;
        int before=state.socialCitizens().size();SocialCitizen citizen=state.ensureSocialCitizen(owner.id(),settlement.id(),slot,resolved);if(state.socialCitizens().size()!=before)data.setDirty();
        DialogueContext context=new DialogueContext();String actor=CrimeRuntime.actorKey(player);DialogueResult greeting=ENGINE.respond(state,citizen,actor,"hello",context);data.setDirty();SESSIONS.put(player.getUUID(),new Session(citizen.id(),context,entity.getUUID()));PacketDistributor.sendToPlayer(player,new DialogueOpenPayload(citizen.id(),citizen.name(),citizen.role().name(),greeting.response()));
    }

    private static CitizenRole villagerRole(AbstractVillager villager){
        if(!(villager instanceof Villager v))return CitizenRole.TRADER;String profession=v.getVillagerData().getProfession().toString().toLowerCase(Locale.ROOT);
        if(profession.contains("farmer"))return CitizenRole.FARMER;if(profession.contains("fisher"))return CitizenRole.FISHER;if(profession.contains("butcher"))return CitizenRole.BUTCHER;
        if(profession.contains("cleric"))return CitizenRole.PRIEST;if(profession.contains("librarian")||profession.contains("cartographer"))return CitizenRole.SCHOLAR;
        if(profession.contains("mason"))return CitizenRole.BUILDER;if(profession.contains("armorer")||profession.contains("weapon")||profession.contains("toolsmith")||profession.contains("leather"))return CitizenRole.ARTISAN;
        return CitizenRole.TRADER;
    }

    public static void reply(ServerPlayer player,DialogueMessagePayload payload){
        if(player==null||payload==null)return;Session session=SESSIONS.get(player.getUUID());if(session==null||session.citizenId()!=payload.citizenId())return;
        Entity physical=player.serverLevel().getEntity(session.physicalEntityId());if(physical==null||!physical.isAlive()||player.distanceToSqr(physical)>100.0D){SESSIONS.remove(player.getUUID());return;}
        var data=SimulationRuntime.data(player.serverLevel().getServer());var state=data.state();SocialCitizen citizen=state.findSocialCitizen(payload.citizenId()).orElse(null);if(citizen==null||!citizen.alive())return;String actor=CrimeRuntime.actorKey(player);DialogueResult result=ENGINE.respond(state,citizen,actor,payload.message(),session.context());String response=applyActions(player,citizen,result,responseText(result));data.setDirty();PacketDistributor.sendToPlayer(player,new DialogueResponsePayload(citizen.id(),response));
    }
    private static String responseText(DialogueResult result){return result.response();}
    private static String applyActions(ServerPlayer player,SocialCitizen citizen,DialogueResult result,String response){StringBuilder extra=new StringBuilder();for(DialogueAction action:result.actions()){switch(action.type()){
        case ACCEPT_GIFT -> {ItemStack held=player.getMainHandItem();if(held.isEmpty()){extra.append("\n[You are not holding a gift.]");break;}String item=held.getHoverName().getString();held.shrink(1);double rep=SocialInteractionService.applyGift(SimulationRuntime.data(player.serverLevel().getServer()).state(),citizen,CrimeRuntime.actorKey(player),item,4);extra.append("\n[Gift accepted: ").append(item).append(" • faction reputation ").append(Math.round(rep)).append(']');}
        case MARK_LOCATION -> {
            if(action.location()!=null){
                extra.append("\n[Location: ").append(action.subject()).append(" @ ").append(Math.round(action.location().x())).append(", ").append(Math.round(action.location().z())).append(']');
                PacketDistributor.sendToPlayer(player,new WaypointPayload(action.subject(),action.location().x(),action.location().z(),900));
            }
        }
        case OPEN_TRADE -> extra.append(tradeBridge(player,citizen));
        case ALERT_GUARDS -> {for(FactionCitizenEntity guard:FactionCitizenIndex.loaded())if(guard.factionId()==citizen.factionId()&&guard.role()==dev.livingrealms.sim.civilian.CitizenRole.GUARD&&guard.distanceToSqr(player)<48.0D*48.0D)guard.setTarget(player);extra.append("\n[Nearby guards have been alerted to you as a threat.]");}
        case NOTIFY_GUARDS -> {var data=SimulationRuntime.data(player.serverLevel().getServer());var state=data.state();String actor=CrimeRuntime.actorKey(player);int notified=0;for(FactionCitizenEntity guard:FactionCitizenIndex.loaded())if(guard.factionId()==citizen.factionId()&&guard.role()==dev.livingrealms.sim.civilian.CitizenRole.GUARD&&guard.distanceToSqr(player)<48.0D*48.0D){SocialCitizen social=state.findSocialCitizen(guard.citizenId()).orElse(null);if(social!=null){var pos=action.location()!=null?action.location():state.findSettlement(citizen.settlementId()).orElseThrow().position();social.remember(new CitizenMemory(state.clock().day(),MemoryType.RUMOR,action.subject(),actor,"A player reported "+action.subject().replace('-',' ')+" nearby.",pos,.38,action.magnitude()));notified++;}}if(notified>0)data.setDirty();extra.append("\n[Report passed to ").append(notified).append(" nearby guard").append(notified==1?"":"s").append(" without marking you hostile.]");}
        case OFFER_TASK -> extra.append("\n[Potential help: ").append(action.subject()).append(']');
        case REPUTATION_CHANGED,NONE -> {}
    }}return response+extra;}
    public static void close(UUID player){if(player!=null)SESSIONS.remove(player);} public static void clear(){SESSIONS.clear();}

    /** Server-authoritative trade bridge: attempt a small FOOD emerald package, else quote. */
    private static String tradeBridge(ServerPlayer player,SocialCitizen citizen){
        var data=SimulationRuntime.data(player.serverLevel().getServer());
        var state=data.state();
        var attempt=PlayerMarketRuntime.trade(state,CrimeRuntime.actorKey(player),player,
                new SimPosition(player.getX(),player.getZ()),true,ResourceType.FOOD.ordinal()+1L);
        if(attempt.success()){
            if(attempt.dirty())data.setDirty();
            return "\n[Trade completed: "+attempt.message()+']';
        }
        return "\n["+DialogueTradeBridge.quoteSummary(state,citizen)
                +(attempt.message().isBlank()?"":"; note: "+attempt.message())
                +" Open F12 Economy if dialogue trade is unavailable here.]";
    }

    private record Session(long citizenId,DialogueContext context,UUID physicalEntityId){}
}
