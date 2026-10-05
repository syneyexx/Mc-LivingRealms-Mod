package dev.livingrealms.minecraft;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.livingrealms.minecraft.entity.LivingRealmsAnimalEntity;
import dev.livingrealms.minecraft.entity.RegionalImpostorEntity;
import dev.livingrealms.minecraft.entity.RegionalImpostorIndex;
import dev.livingrealms.minecraft.entity.WildlifeProjectionIndex;
import dev.livingrealms.minecraft.entity.TradeCaravanEntity;
import dev.livingrealms.minecraft.entity.TradeCaravanIndex;
import dev.livingrealms.minecraft.entity.FactionCitizenEntity;
import dev.livingrealms.minecraft.entity.FactionCitizenIndex;
import dev.livingrealms.minecraft.entity.CitizenConversationRuntime;
import dev.livingrealms.minecraft.entity.MilitaryUnitEntity;
import dev.livingrealms.minecraft.entity.MilitaryUnitIndex;
import dev.livingrealms.minecraft.entity.MobileCivilizationEntity;
import dev.livingrealms.minecraft.entity.MobileCivilizationIndex;
import dev.livingrealms.minecraft.entity.MobileCivilizationKind;
import dev.livingrealms.minecraft.entity.LivingRealmsAircraftEntity;
import dev.livingrealms.minecraft.entity.AircraftProjectionIndex;
import dev.livingrealms.minecraft.entity.LivingRealmsShipEntity;
import dev.livingrealms.minecraft.entity.ShipProjectionIndex;
import dev.livingrealms.minecraft.entity.BountyHunterEntity;
import dev.livingrealms.minecraft.entity.BountyHunterIndex;
import dev.livingrealms.minecraft.entity.SiegeEquipmentEntity;
import dev.livingrealms.minecraft.entity.SiegeEquipmentIndex;
import dev.livingrealms.minecraft.construction.PlayerStructureRevalidationRuntime;
import dev.livingrealms.minecraft.presentation.CivicChoreographyRuntime;
import dev.livingrealms.minecraft.presentation.SeasonalFarmPresentationRuntime;
import dev.livingrealms.minecraft.runtime.LivingRealmsRuntimePerf;
import dev.livingrealms.minecraft.runtime.LivingRealmsRuntimeScheduler;
import dev.livingrealms.sim.player.PlayerSettlementFounder;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.minecraft.law.CrimeRuntime;
import dev.livingrealms.minecraft.law.FactionContainerTheftRuntime;
import dev.livingrealms.minecraft.civilization.HiddenCacheRuntime;
import dev.livingrealms.minecraft.civilization.HistoricalSiteRuntime;
import dev.livingrealms.minecraft.civilization.PirateHideoutRuntime;
import dev.livingrealms.minecraft.civilization.PlayerAssistanceRuntime;
import dev.livingrealms.minecraft.construction.CivicFestivalMaterializer;
import dev.livingrealms.minecraft.network.DashboardRequestLimiter;
import dev.livingrealms.minecraft.network.DashboardActionLimiter;
import dev.livingrealms.minecraft.network.DialogueRequestLimiter;
import dev.livingrealms.minecraft.network.DialogueSessionRuntime;
import dev.livingrealms.sim.law.CrimeType;
import dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer;
import dev.livingrealms.minecraft.construction.SettlementGeographyDiscoveryRuntime;
import dev.livingrealms.minecraft.ForeignStructureDiscoveryRuntime;
import dev.livingrealms.minecraft.construction.HistoricalSiteMaterializer;
import dev.livingrealms.minecraft.construction.RoadsideSiteMaterializer;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.civilian.CitizenRole;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.server.level.ServerLevel;

/**
 * /livingrealms command surface extracted from LivingRealmsEvents.
 * All mutations still flow through SimulationState/LivingRealmsSavedData on the integrated server.
 */
final class LivingRealmsCommands {
    private LivingRealmsCommands() {}

    static void register(RegisterCommandsEvent event, LivingRealmsRuntimeScheduler runtimeScheduler) {
        event.getDispatcher().register(Commands.literal("livingrealms")
                .then(Commands.literal("wanted").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var state=SimulationRuntime.data(ctx.getSource().getServer()).state();
                    var profile=state.crimeLedger().findProfile(CrimeRuntime.actorKey(player));
                    if(profile.isEmpty()||profile.get().jurisdictions().isEmpty()){ctx.getSource().sendSuccess(()->Component.literal("No active criminal record."),false);return 1;}
                    ctx.getSource().sendSuccess(()->Component.literal("Global infamy: "+Math.round(profile.get().globalInfamy())),false);
                    profile.get().jurisdictions().values().forEach(w->{String realm=state.findFaction(w.factionId()).map(f->f.name()).orElse("Faction "+w.factionId());ctx.getSource().sendSuccess(()->Component.literal(realm+" • "+w.wantedLevel()+" • bounty "+Math.round(w.bounty())+" • heat "+Math.round(w.heat())),false);});return 1;
                }))
                .then(Commands.literal("faction").then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var state=SimulationRuntime.data(ctx.getSource().getServer()).state();
                    String actor=CrimeRuntime.actorKey(player);
                    var standing=state.findPlayerStanding(actor);
                    if(standing.isEmpty()){ctx.getSource().sendSuccess(()->Component.literal("No faction standing recorded yet."),false);return 1;}
                    var s=standing.get();
                    String membership=s.isMember()?state.findFaction(s.memberFactionId()).map(f->f.name()+" • rank "+s.rank()+" • service "+Math.round(s.servicePoints())).orElse("Unknown faction "+s.memberFactionId()):"No active faction membership";
                    ctx.getSource().sendSuccess(()->Component.literal(membership),false);
                    if(s.reputations().isEmpty()){ctx.getSource().sendSuccess(()->Component.literal("No faction reputation yet."),false);return 1;}
                    s.reputations().forEach((id,value)->{String name=state.findFaction(id).map(f->f.name()).orElse("Faction "+id);ctx.getSource().sendSuccess(()->Component.literal(name+" • reputation "+Math.round(value)),false);});
                    return 1;
                })).then(Commands.literal("join_here").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var data=SimulationRuntime.data(ctx.getSource().getServer());
                    var state=data.state();
                    var pos=new dev.livingrealms.sim.world.SimPosition(player.getX(),player.getZ());
                    var jurisdiction=dev.livingrealms.sim.territory.TerritoryEngine.resolve(state.factions(),pos,state.config().borderDisputeThreshold());
                    if(jurisdiction.primaryFactionId()<=0||jurisdiction.contested()){ctx.getSource().sendFailure(Component.literal("You are not inside an uncontested faction jurisdiction."));return 0;}
                    var result=state.joinFaction(CrimeRuntime.actorKey(player),jurisdiction.primaryFactionId());
                    String faction=state.findFaction(jurisdiction.primaryFactionId()).map(f->f.name()).orElse("Faction "+jurisdiction.primaryFactionId());
                    if(!result.success()){ctx.getSource().sendFailure(Component.literal("Could not join "+faction+": "+result.reason()));return 0;}
                    data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("Joined "+faction+" as "+result.rank()+"."),false);return 1;
                })).then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();
                    var data=SimulationRuntime.data(ctx.getSource().getServer());
                    if(!data.state().leaveFaction(CrimeRuntime.actorKey(player))){ctx.getSource().sendFailure(Component.literal("You are not a faction member."));return 0;}
                    data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("You left your faction."),false);return 1;
                })))
                .then(Commands.literal("industry").executes(ctx -> {
                    ServerPlayer player=ctx.getSource().getPlayerOrException();var state=SimulationRuntime.data(ctx.getSource().getServer()).state();
                    var pos=new dev.livingrealms.sim.world.SimPosition(player.getX(),player.getZ());var jurisdiction=dev.livingrealms.sim.territory.TerritoryEngine.resolve(state.factions(),pos,state.config().borderDisputeThreshold());
                    long factionId=jurisdiction.primaryFactionId();if(factionId<=0||jurisdiction.contested()){var standing=state.findPlayerStanding(CrimeRuntime.actorKey(player)).orElse(null);factionId=standing!=null&&standing.isMember()?standing.memberFactionId():0;}
                    if(factionId<=0){ctx.getSource().sendFailure(Component.literal("No local or member faction industry to inspect."));return 0;}
                    final long id=factionId;var faction=state.findFaction(id).orElse(null);if(faction==null)return 0;var sites=state.industrialSites().stream().filter(site->site.factionId()==id).toList();
                    long active=sites.stream().filter(site->site.status()==IndustrialSiteStatus.ACTIVE).count(),starved=sites.stream().filter(site->site.status()==IndustrialSiteStatus.STARVED).count(),damaged=sites.stream().filter(site->site.status()==IndustrialSiteStatus.DAMAGED||site.status()==IndustrialSiteStatus.REPAIRING).count(),offline=sites.stream().filter(site->site.status()==IndustrialSiteStatus.OFFLINE).count();
                    ctx.getSource().sendSuccess(()->Component.literal(faction.name()+" industry • sites "+sites.size()+" • active "+active+" • starved "+starved+" • damaged/repair "+damaged+" • offline "+offline),false);
                    ctx.getSource().sendSuccess(()->Component.literal("Fuel "+Math.round(faction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.FUEL))+" • machinery "+Math.round(faction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.MACHINERY))+" • tools "+Math.round(faction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.TOOLS))+" • ammunition "+Math.round(faction.stockpile().get(dev.livingrealms.sim.faction.ResourceType.AMMUNITION))),false);
                    sites.stream().sorted(java.util.Comparator.comparingLong(IndustrialSite::id)).limit(8).forEach(site->{String settlement=state.findSettlement(site.settlementId()).map(x->x.name()).orElse("Settlement "+site.settlementId());ctx.getSource().sendSuccess(()->Component.literal("#"+site.id()+" "+site.kind()+" @ "+settlement+" • L"+site.level()+" • "+site.status()+" • condition "+Math.round(site.condition()*100)+"% • utilization "+Math.round(site.lastUtilization()*100)+"%"),false);});
                    return 1;
                }))
                .then(Commands.literal("bounty")
                    .then(Commands.literal("board").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();var state=SimulationRuntime.data(ctx.getSource().getServer()).state();String actor=CrimeRuntime.actorKey(player);
                        var jurisdiction=dev.livingrealms.sim.territory.TerritoryEngine.resolve(state.factions(),new dev.livingrealms.sim.world.SimPosition(player.getX(),player.getZ()),state.config().borderDisputeThreshold());
                        if(jurisdiction.primaryFactionId()<=0||jurisdiction.contested()){ctx.getSource().sendFailure(Component.literal("No usable bounty board in wilderness/contested territory."));return 0;}
                        long factionId=jurisdiction.primaryFactionId();var contracts=state.bounties().stream().filter(b->b.issuerFactionId()==factionId).filter(b->b.status()==dev.livingrealms.sim.law.BountyContract.Status.OPEN||b.hunterKey().equals(actor)).sorted(java.util.Comparator.comparingDouble(dev.livingrealms.sim.law.BountyContract::reward).reversed()).toList();
                        String realm=state.findFaction(factionId).map(f->f.name()).orElse("Faction "+factionId);ctx.getSource().sendSuccess(()->Component.literal(realm+" bounty board • "+contracts.size()+" active"),false);
                        for(var b:contracts)ctx.getSource().sendSuccess(()->Component.literal("#"+b.id()+" • target "+b.actorKey()+" • reward "+Math.round(b.reward())+" • "+b.status()),false);return 1;
                    }))
                    .then(Commands.literal("accept").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();var data=SimulationRuntime.data(ctx.getSource().getServer());var state=data.state();String actor=CrimeRuntime.actorKey(player);
                        var jurisdiction=dev.livingrealms.sim.territory.TerritoryEngine.resolve(state.factions(),new dev.livingrealms.sim.world.SimPosition(player.getX(),player.getZ()),state.config().borderDisputeThreshold());
                        if(jurisdiction.primaryFactionId()<=0||jurisdiction.contested()){ctx.getSource().sendFailure(Component.literal("Stand inside an uncontested realm to accept its bounties."));return 0;}
                        var contract=state.bounties().stream().filter(b->b.issuerFactionId()==jurisdiction.primaryFactionId()&&b.status()==dev.livingrealms.sim.law.BountyContract.Status.OPEN&&!b.actorKey().equals(actor)).max(java.util.Comparator.comparingDouble(dev.livingrealms.sim.law.BountyContract::reward)).orElse(null);
                        if(contract==null){ctx.getSource().sendFailure(Component.literal("No open bounty is available here."));return 0;}var result=state.acceptBounty(contract.id(),actor);if(!result.success()){ctx.getSource().sendFailure(Component.literal("Could not accept bounty: "+result.reason()));return 0;}data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("Accepted bounty #"+contract.id()+" on "+contract.actorKey()+" for "+Math.round(contract.reward())+"."),false);return 1;
                    }))
                    .then(Commands.literal("active").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();var state=SimulationRuntime.data(ctx.getSource().getServer()).state();String actor=CrimeRuntime.actorKey(player);var contract=state.bounties().stream().filter(b->b.status()==dev.livingrealms.sim.law.BountyContract.Status.ASSIGNED&&b.hunterKey().equals(actor)).findFirst().orElse(null);if(contract==null){ctx.getSource().sendSuccess(()->Component.literal("No active bounty contract."),false);return 1;}ctx.getSource().sendSuccess(()->Component.literal("Active bounty #"+contract.id()+" • target "+contract.actorKey()+" • reward "+Math.round(contract.reward())),false);return 1;
                    }))
                    .then(Commands.literal("abandon").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();var data=SimulationRuntime.data(ctx.getSource().getServer());var state=data.state();String actor=CrimeRuntime.actorKey(player);var contract=state.bounties().stream().filter(b->b.status()==dev.livingrealms.sim.law.BountyContract.Status.ASSIGNED&&b.hunterKey().equals(actor)).findFirst().orElse(null);if(contract==null){ctx.getSource().sendFailure(Component.literal("You have no assigned bounty."));return 0;}var result=state.abandonBounty(contract.id(),actor);if(!result.success()){ctx.getSource().sendFailure(Component.literal("Could not abandon bounty: "+result.reason()));return 0;}data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("Bounty #"+contract.id()+" returned to the board."),false);return 1;
                    })))
                .then(Commands.literal("assist")
                    .then(Commands.literal("board").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();
                        var state=SimulationRuntime.data(ctx.getSource().getServer()).state();
                        String board=PlayerAssistanceRuntime.describeNearby(state,new SimPosition(player.getX(),player.getZ()));
                        ctx.getSource().sendSuccess(()->Component.literal(board),false);
                        return 1;
                    }))
                    .then(Commands.literal("deliver").executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        var result=PlayerAssistanceRuntime.contributeNearest(data.state(),CrimeRuntime.actorKey(player),player,new SimPosition(player.getX(),player.getZ()));
                        if(!result.success()){ctx.getSource().sendFailure(Component.literal(result.message()));return 0;}
                        if(result.dirty())data.setDirty();
                        ctx.getSource().sendSuccess(()->Component.literal(result.message()),false);
                        return 1;
                    }))
                    .then(Commands.argument("taskId", LongArgumentType.longArg(1L)).executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        long taskId=LongArgumentType.getLong(ctx,"taskId");
                        var result=PlayerAssistanceRuntime.contribute(data.state(),CrimeRuntime.actorKey(player),player,new SimPosition(player.getX(),player.getZ()),taskId);
                        if(!result.success()){ctx.getSource().sendFailure(Component.literal(result.message()));return 0;}
                        if(result.dirty())data.setDirty();
                        ctx.getSource().sendSuccess(()->Component.literal(result.message()),false);
                        return 1;
                    })))
                .then(Commands.literal("found")
                    .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                        ServerPlayer player=ctx.getSource().getPlayerOrException();
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        String actor=CrimeRuntime.actorKey(player);
                        String name=StringArgumentType.getString(ctx,"name");
                        var result=PlayerSettlementFounder.found(data.state(),actor,player.getGameProfile().getName(),name,new SimPosition(player.getX(),player.getZ()));
                        if(!result.success()){ctx.getSource().sendFailure(Component.literal("Could not found settlement: "+result.reason()));return 0;}
                        data.setDirty();
                        ctx.getSource().sendSuccess(()->Component.literal("Founded "+result.settlementName()+" • "+result.realmName()+". Citizens can now immigrate, build and enter diplomacy/war like any other realm."),true);
                        return 1;
                    })))
                .then(Commands.literal("locate")
                    .then(Commands.literal("mine").executes(ctx -> locateByQuery(ctx.getSource(), "mine")))
                    .then(Commands.literal("home").executes(ctx -> locateOwnSettlement(ctx.getSource())))
                    .then(Commands.literal("own").executes(ctx -> locateOwnSettlement(ctx.getSource())))
                    .then(Commands.literal("settlement").executes(ctx -> locateNearest(ctx.getSource(), settlement -> true, "settlement")))
                    .then(Commands.literal("city").executes(ctx -> locateByQuery(ctx.getSource(), "city")))
                    .then(Commands.literal("town").executes(ctx -> locateNearest(ctx.getSource(), settlement -> settlement.tier() == Settlement.Tier.TOWN, "town")))
                    .then(Commands.literal("village").executes(ctx -> locateNearest(ctx.getSource(), settlement -> settlement.tier() == Settlement.Tier.VILLAGE, "village")))
                    .then(Commands.literal("hamlet").executes(ctx -> locateNearest(ctx.getSource(), settlement -> settlement.tier() == Settlement.Tier.HAMLET, "hamlet")))
                    .then(Commands.literal("kingdom").executes(ctx -> locateByQuery(ctx.getSource(), "kingdom")))
                    .then(Commands.literal("market").executes(ctx -> locateByQuery(ctx.getSource(), "market")))
                    .then(Commands.literal("port").executes(ctx -> locateByQuery(ctx.getSource(), "port")))
                    .then(Commands.literal("wizardtrees").executes(ctx -> locateByQuery(ctx.getSource(), "wizardtrees")))
                    .then(Commands.literal("ruin").executes(ctx -> locateByQuery(ctx.getSource(), "ruin"))))
                .then(Commands.literal("status").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    var state = SimulationRuntime.data(ctx.getSource().getServer()).state();
                    ctx.getSource().sendSuccess(() -> Component.literal(state.summary()), false);
                    return 1;
                }))
                .then(Commands.literal("perf").requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            LivingRealmsRuntimePerf.print(ctx.getSource(), runtimeScheduler);
                            return 1;
                        })
                        .then(Commands.literal("reset").executes(ctx -> {
                            LivingRealmsRuntimePerf.reset(runtimeScheduler);
                            ctx.getSource().sendSuccess(() -> Component.literal("Runtime perf peaks/deferred counters reset."), false);
                            return 1;
                        })))
                .then(Commands.literal("setday").requires(src -> src.hasPermission(2))
                    .then(Commands.argument("day", LongArgumentType.longArg(0L)).executes(ctx -> {
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        long target=LongArgumentType.getLong(ctx,"day"),before=data.state().clock().day();
                        try {
                            long queued=data.dayAdvanceScheduler().enqueueAbsolute(data.state(),target);
                            long drained=data.dayAdvanceScheduler().drainTick(data.state());
                            if(drained>0){
                                data.setDirty();
                                SettlementConstructionMaterializer.requestCatchup(drained);
                            }
                            long remaining=data.dayAdvanceScheduler().pendingDays();
                            long now=data.state().clock().day();
                            ctx.getSource().sendSuccess(()->Component.literal(
                                    "Living Realms progressed "+drained+" day(s): "+before+" -> "+now
                                            +(remaining>0?" • "+remaining+" day(s) remaining (max "
                                            +dev.livingrealms.sim.world.ManualDayAdvanceScheduler.MAX_DAYS_PER_TICK
                                            +" per tick)":"")
                                            +" • "+data.state().summary()),true);
                            return queued>0||drained>0?1:1;
                        } catch(IllegalArgumentException ex) {
                            ctx.getSource().sendFailure(Component.literal(ex.getMessage()));
                            return 0;
                        }
                    })))
                .then(Commands.literal("advance").requires(src -> src.hasPermission(2))
                    .then(Commands.argument("days", LongArgumentType.longArg(1L, dev.livingrealms.sim.world.SimulationState.MAX_MANUAL_DAY_JUMP)).executes(ctx -> {
                        var data=SimulationRuntime.data(ctx.getSource().getServer());
                        long days=LongArgumentType.getLong(ctx,"days"),before=data.state().clock().day();
                        try {
                            data.dayAdvanceScheduler().enqueueRelative(data.state(),days);
                            long drained=data.dayAdvanceScheduler().drainTick(data.state());
                            if(drained>0){
                                data.setDirty();
                                SettlementConstructionMaterializer.requestCatchup(drained);
                            }
                            long remaining=data.dayAdvanceScheduler().pendingDays();
                            ctx.getSource().sendSuccess(()->Component.literal(
                                    "Living Realms progressed "+drained+" day(s): "+before+" -> "+data.state().clock().day()
                                            +(remaining>0?" • "+remaining+" day(s) remaining (max "
                                            +dev.livingrealms.sim.world.ManualDayAdvanceScheduler.MAX_DAYS_PER_TICK
                                            +" per tick)":"")
                                            +" • "+data.state().summary()),true);
                            return 1;
                        } catch(IllegalArgumentException ex) {
                            ctx.getSource().sendFailure(Component.literal(ex.getMessage()));
                            return 0;
                        }
                    })))
                .then(Commands.literal("advance_day").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    var data = SimulationRuntime.data(ctx.getSource().getServer());
                    data.state().advanceDays(1);
                    data.setDirty();dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer.requestCatchup(1);
                    ctx.getSource().sendSuccess(() -> Component.literal("Advanced: " + data.state().summary()), true);
                    return 1;
                }))
                .then(Commands.literal("integrity").requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        var state = SimulationRuntime.data(ctx.getSource().getServer()).state();
                        var report = dev.livingrealms.sim.validation.CanonicalIntegrityService.inspect(state);
                        ctx.getSource().sendSuccess(() -> Component.literal(report.summary()), false);
                        report.allMessages().stream().limit(24).forEach(msg ->
                                ctx.getSource().sendSuccess(() -> Component.literal(msg), false));
                        if (report.allMessages().size() > 24)
                            ctx.getSource().sendSuccess(() -> Component.literal("… " + (report.allMessages().size() - 24) + " more"), false);
                        return report.hasFatal() ? 0 : 1;
                    })
                    .then(Commands.literal("repair").executes(ctx -> {
                        var data = SimulationRuntime.data(ctx.getSource().getServer());
                        var report = dev.livingrealms.sim.validation.CanonicalIntegrityService.repair(data.state());
                        if (!report.repaired().isEmpty()) data.setDirty();
                        ctx.getSource().sendSuccess(() -> Component.literal(report.summary()), true);
                        report.allMessages().stream().limit(24).forEach(msg ->
                                ctx.getSource().sendSuccess(() -> Component.literal(msg), false));
                        if (report.allMessages().size() > 24)
                            ctx.getSource().sendSuccess(() -> Component.literal("… " + (report.allMessages().size() - 24) + " more"), false);
                        return report.hasFatal() ? 0 : 1;
                    })))
        );
    }

    private static int locateByQuery(net.minecraft.commands.CommandSourceStack source,String kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        var data=SimulationRuntime.data(source.getServer());
        var state=data.state();
        SimPosition from=new SimPosition(player.getX(),player.getZ());
        java.util.Optional<dev.livingrealms.sim.world.LocateQuery.Hit> hit=switch(kind){
            case "city" -> dev.livingrealms.sim.world.LocateQuery.nearestCity(state,from);
            case "mine" -> dev.livingrealms.sim.world.LocateQuery.nearestMine(state,from);
            case "kingdom" -> dev.livingrealms.sim.world.LocateQuery.nearestKingdom(state,from);
            case "market" -> dev.livingrealms.sim.world.LocateQuery.nearestMarket(state,from);
            case "port" -> {
                var port=dev.livingrealms.sim.world.LocateQuery.nearestPort(state,from);
                if(port.isPresent())yield port;
                // Fall back to nearest coastal/ship-suitable settlement so far ports still resolve.
                yield dev.livingrealms.sim.world.LocateQuery.nearestSettlement(state,from,s->s.geography().shipSuitable(),"port");
            }
            case "wizardtrees" -> dev.livingrealms.sim.world.LocateQuery.nearestWizardTrees(state,from);
            case "ruin" -> dev.livingrealms.sim.world.LocateQuery.nearestRuin(state,from);
            default -> java.util.Optional.empty();
        };
        if(hit.isEmpty()){source.sendFailure(Component.literal("No Living Realms "+kind+" exists in the canonical world state yet. Explore farther or wait for discovery — locate searches the whole world with no distance limit."));return 0;}
        var found=hit.get();
        final int x=(int)Math.round(found.x()),z=(int)Math.round(found.z());
        final long distance=Math.round(found.distance());
        source.sendSuccess(()->Component.literal("Nearest "+found.label()+": "+found.name()+" • "+found.type()+" • "+found.factionName()+" • X "+x+" Z "+z+" • "+distance+" blocks"+(distance>2_000?" (far — travel or Waystone)":"")),false);
        return 1;
    }

    private static int locateOwnSettlement(net.minecraft.commands.CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();var state=SimulationRuntime.data(source.getServer()).state();
        var standing=state.findPlayerStanding(CrimeRuntime.actorKey(player)).orElse(null);
        if(standing==null||!standing.isMember()){source.sendFailure(Component.literal("You do not currently belong to a Living Realms faction."));return 0;}
        Faction faction=state.findFaction(standing.memberFactionId()).orElse(null);
        if(faction==null||faction.settlements().isEmpty()){source.sendFailure(Component.literal("Your faction has no settlement."));return 0;}
        Settlement nearest=faction.settlements().stream().min(java.util.Comparator.comparingDouble(s->s.position().distanceTo(new SimPosition(player.getX(),player.getZ())))).orElseThrow();
        final int x=(int)Math.round(nearest.position().x()),z=(int)Math.round(nearest.position().z());
        source.sendSuccess(()->Component.literal("Your nearest settlement: "+nearest.name()+" • "+faction.name()+" • X "+x+" Z "+z),false);return 1;
    }

    private static int locateNearest(net.minecraft.commands.CommandSourceStack source, java.util.function.Predicate<Settlement> filter, String label) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        var data=SimulationRuntime.data(source.getServer());
        var state=data.state();
        SimPosition from=new SimPosition(player.getX(),player.getZ());
        var hit=dev.livingrealms.sim.world.LocateQuery.nearestSettlement(state,from,filter,label);
        if(hit.isEmpty()){source.sendFailure(Component.literal("No Living Realms "+label+" exists in the canonical world state yet. Locate searches the entire world — if it exists anywhere, it will be found."));return 0;}
        var found=hit.get();
        final long distance=Math.round(found.distance());
        final int x=(int)Math.round(found.x()),z=(int)Math.round(found.z());
        source.sendSuccess(()->Component.literal("Nearest "+label+": "+found.name()+" • "+found.type()+" • "+found.factionName()+" • X "+x+" Z "+z+" • "+distance+" blocks"+(distance>2_000?" (far — travel or Waystone)":"")),false);
        return 1;
    }
}
