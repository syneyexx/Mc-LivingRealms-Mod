package dev.livingrealms.minecraft.network;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.minecraft.law.CrimeRuntime;
import dev.livingrealms.minecraft.economy.PlayerMarketRuntime;
import dev.livingrealms.sim.ui.DashboardActionCommand;
import dev.livingrealms.sim.ui.DashboardActionService;
import dev.livingrealms.sim.ui.RealmDashboardBuilder;
import dev.livingrealms.sim.ui.RealmDashboardCodec;
import dev.livingrealms.sim.world.SimPosition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Custom payload registration for bounded strategic UI synchronization. */
public final class LivingRealmsNetwork {
    public static final String NETWORK_VERSION = "16";

    private LivingRealmsNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(LivingRealmsNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(DashboardRequestPayload.TYPE, DashboardRequestPayload.STREAM_CODEC, LivingRealmsNetwork::handleDashboardRequest);
        registrar.playToServer(DashboardActionPayload.TYPE, DashboardActionPayload.STREAM_CODEC, LivingRealmsNetwork::handleDashboardAction);
        registrar.playToServer(CreativeSpawnItemPayload.TYPE, CreativeSpawnItemPayload.STREAM_CODEC, LivingRealmsNetwork::handleCreativeSpawnItem);
        registrar.playToServer(DialogueMessagePayload.TYPE, DialogueMessagePayload.STREAM_CODEC, LivingRealmsNetwork::handleDialogueMessage);
        registrar.playToClient(DashboardSnapshotPayload.TYPE, DashboardSnapshotPayload.STREAM_CODEC, LivingRealmsNetwork::handleDashboardSnapshot);
        registrar.playToClient(DialogueOpenPayload.TYPE, DialogueOpenPayload.STREAM_CODEC, LivingRealmsNetwork::handleDialogueOpen);
        registrar.playToClient(DialogueResponsePayload.TYPE, DialogueResponsePayload.STREAM_CODEC, LivingRealmsNetwork::handleDialogueResponse);
        registrar.playToClient(WaypointPayload.TYPE, WaypointPayload.STREAM_CODEC, LivingRealmsNetwork::handleWaypoint);
    }

    private static void handleDashboardRequest(DashboardRequestPayload ignored, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!DashboardRequestLimiter.allow(player.getUUID(), player.tickCount)) return;
        sendDashboard(player,level);
    }


    private static void handleDashboardAction(DashboardActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (!DashboardActionLimiter.allow(player.getUUID(),player.tickCount)) return;
        try {
            DashboardActionCommand command=DashboardActionCommand.parse(payload.command());
            var data=SimulationRuntime.data(level.getServer());String actor=CrimeRuntime.actorKey(player);SimPosition position=new SimPosition(player.getX(),player.getZ());
            if(command.action()==DashboardActionCommand.Action.MARKET_BUY||command.action()==DashboardActionCommand.Action.MARKET_SELL){
                var trade=PlayerMarketRuntime.trade(data.state(),actor,player,position,command.action()==DashboardActionCommand.Action.MARKET_BUY,command.targetId());
                if(!trade.success()){player.sendSystemMessage(Component.literal("Living Realms market: "+trade.message()));return;}
                if(trade.dirty())data.setDirty();player.sendSystemMessage(Component.literal(trade.message()));sendDashboard(player,level);return;
            }
            if(command.action()==DashboardActionCommand.Action.REGISTER_BUILDING){
                var survey=dev.livingrealms.minecraft.construction.PlayerStructureSurvey.surveyAndRegister(
                        level,player,actor,data.state(),command.targetId(),command.argument());
                if(!survey.success()){player.sendSystemMessage(Component.literal(survey.message()));return;}
                data.setDirty();
                player.sendSystemMessage(Component.literal(survey.message()));
                sendDashboard(player,level);
                return;
            }
            var result=DashboardActionService.apply(data.state(),actor,position,command);
            if(!result.success()){player.sendSystemMessage(Component.literal(humanActionFailure(command.action(),result.reason())));return;}
            if(result.dirty())data.setDirty();
            if("war_petitioned".equals(result.reason())){
                player.sendSystemMessage(Component.literal("War petition filed with the court — a ruler must still declare."));
            }else{
                sendActionFeedback(player,command.action());
            }
            sendDashboard(player,level);
        } catch(RuntimeException ex){
            LivingRealms.LOGGER.warn("Rejected dashboard action from {}",player.getGameProfile().getName(),ex);
            player.sendSystemMessage(Component.literal("Living Realms rejected that dashboard action."));
        }
    }

    private static void sendActionFeedback(ServerPlayer player, DashboardActionCommand.Action action) {
        String key = actionMessageKey(action);
        if (key.startsWith("message.")) player.sendSystemMessage(Component.translatable(key));
        else player.sendSystemMessage(Component.literal(key));
    }

    private static String humanActionFailure(DashboardActionCommand.Action action, String reason) {
        String r = reason == null ? "" : reason;
        return switch (r) {
            case "insufficient_reputation" -> "Cannot join: need reputation ≥ 10 with this realm.";
            case "wanted" -> "Cannot join: bounty must be ≤ 25 (pay fine / serve custody first).";
            case "in_custody" -> "Cannot join while in custody.";
            case "already_member", "already_member_elsewhere" -> "You already belong to a realm. Leave first.";
            case "ruler_cannot_leave" -> "Rulers cannot leave. Use succession/abdication to step down.";
            case "not_member" -> "You are not a member of any realm.";
            case "petition_only" -> "Only the ruler may declare war unilaterally. File a court petition instead.";
            case "defense_not_offensive" -> "Defense is not a valid offensive war goal.";
            case "escort_target_invalid" -> "Escort targets must be your shipments or friendly settlements — not the enemy.";
            case "wrong_local_faction" -> "That realm is not the local jurisdiction here.";
            case "no_usable_board" -> "No usable jurisdiction here (wilderness or contested).";
            default -> "Living Realms action failed: " + (r.isBlank() ? action.name() : r);
        };
    }

    private static String actionMessageKey(DashboardActionCommand.Action action){
        return switch(action){
            case BOUNTY_ACCEPT -> "Bounty accepted.";
            case BOUNTY_ABANDON -> "Bounty returned to the board.";
            case CONFIG_PERFORMANCE -> "Living Realms profile: Performance.";
            case CONFIG_BALANCED -> "Living Realms profile: Balanced.";
            case CONFIG_IMMERSIVE -> "Living Realms profile: Immersive.";
            case CONFIG_CINEMATIC -> "Living Realms profile: Cinematic.";
            case FACTION_JOIN_LOCAL -> "Joined local realm.";
            case FACTION_LEAVE -> "Left your realm.";
            case TAX_LOWER -> "Realm tax rate lowered.";
            case TAX_RAISE -> "Realm tax rate raised.";
            case SETTLEMENT_BALANCED -> "Settlement priority: Balanced.";
            case SETTLEMENT_FOOD -> "Settlement priority: Food.";
            case SETTLEMENT_HOUSING -> "Settlement priority: Housing.";
            case SETTLEMENT_INDUSTRY -> "Settlement priority: Industry.";
            case SETTLEMENT_DEFENSE -> "Settlement priority: Defense.";
            case MARKET_BUY -> "Market purchase completed.";
            case MARKET_SELL -> "Market sale completed.";
            case REQUEST_AUDIENCE -> "message.livingrealms.action.audience";
            case PROPOSE_PROJECT -> "message.livingrealms.action.propose_project";
            case REQUEST_MILITARY_SUPPORT -> "message.livingrealms.action.military_support";
            case PETITION_TRADE -> "message.livingrealms.action.petition_trade";
            case PETITION_CLERGY -> "message.livingrealms.action.petition_clergy";
            case FOUND_SETTLEMENT -> "Settlement founded. Your realm can now grow, trade and enter diplomacy.";
            case ABDICATE -> "You abdicated. A court successor now holds the realm.";
            case PETITION_PEACE -> "Peace petition accepted — war ended with a treaty.";
            case PROPOSE_TRADE_PACT -> "Trade pact signed.";
            case DECLARE_WAR -> "War declared.";
            case ARMY_DEFEND_HOME -> "Army ordered to defend home.";
            case ARMY_RALLY -> "Army ordered to rally / patrol the border.";
            case ARMY_STAND_DOWN -> "Army ordered to stand down and resupply.";
            case ARMY_CAPTURE -> "Army ordered to capture the target settlement.";
            case ARMY_SIEGE -> "Army ordered to siege the target settlement.";
            case ARMY_RAID -> "Army ordered to raid the target settlement.";
            case ARMY_ESCORT -> "Army ordered to escort the target.";
            case ARMY_PATROL -> "Army ordered to patrol.";
            case SURRENDER -> "You surrendered to local authorities.";
            case PAY_FINE -> "Fine paid toward your bounty.";
            case SET_DEVELOPMENT_MODE -> "Settlement development mode updated.";
            case REGISTER_BUILDING -> "Building registered.";
            case UNDERWORLD_ACCEPT -> "Underworld contract accepted.";
            case UNDERWORLD_BRIBE -> "Officials bribed.";
            case BLACK_MARKET_SELL -> "Black-market sale completed.";
        };
    }

    private static void sendDashboard(ServerPlayer player,ServerLevel level){
        try {
            var state = SimulationRuntime.data(level.getServer()).state();
            var snapshot = RealmDashboardBuilder.build(state,CrimeRuntime.actorKey(player),new SimPosition(player.getX(),player.getZ()));
            String json = RealmDashboardCodec.encode(snapshot);
            PacketDistributor.sendToPlayer(player,new DashboardSnapshotPayload(json));
        } catch (RuntimeException ex) {
            LivingRealms.LOGGER.error("Could not build dashboard snapshot for {}", player.getGameProfile().getName(), ex);
            player.sendSystemMessage(Component.translatable("message.livingrealms.dashboard_server_error"));
        }
    }

    private static void handleCreativeSpawnItem(CreativeSpawnItemPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.getAbilities().instabuild) return;
        ResourceLocation id;
        try { id = ResourceLocation.parse(payload.itemId()); } catch (RuntimeException ex) { return; }
        if (!BuiltInRegistries.ITEM.containsKey(id)) return;
        var item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return;
        ItemStack stack = new ItemStack(item, Math.min(payload.count(), item.getDefaultMaxStackSize()));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }


    private static void handleDialogueMessage(DialogueMessagePayload payload,IPayloadContext context){
        if(!(context.player() instanceof ServerPlayer player))return;if(!DialogueRequestLimiter.allow(player.getUUID(),player.tickCount))return;
        try{DialogueSessionRuntime.reply(player,payload);}catch(RuntimeException ex){LivingRealms.LOGGER.warn("Rejected NPC dialogue message from {}",player.getGameProfile().getName(),ex);}
    }

    private static void handleDialogueOpen(DialogueOpenPayload payload,IPayloadContext context){try{DialogueClientBridge.open(payload);}catch(RuntimeException ex){LivingRealms.LOGGER.error("Rejected dialogue-open payload",ex);}}
    private static void handleDialogueResponse(DialogueResponsePayload payload,IPayloadContext context){try{DialogueClientBridge.response(payload);}catch(RuntimeException ex){LivingRealms.LOGGER.error("Rejected dialogue-response payload",ex);}}
    private static void handleWaypoint(WaypointPayload payload,IPayloadContext context){try{WaypointClientBridge.receive(payload);}catch(RuntimeException ex){LivingRealms.LOGGER.error("Rejected waypoint payload",ex);}}

    private static void handleDashboardSnapshot(DashboardSnapshotPayload payload, IPayloadContext context) {
        try {
            DashboardClientBridge.receive(payload.json());
        } catch (RuntimeException ex) {
            LivingRealms.LOGGER.error("Rejected Living Realms dashboard payload", ex);
        }
    }
}
