package dev.livingrealms.minecraft.entity;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.sim.social.CitizenConversationService;
import dev.livingrealms.sim.social.SocialCitizen;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/**
 * Bounded physical projection of the canonical person-to-person information network.
 * Conversation text is ephemeral; transferred memories and relationship changes are canonical.
 */
public final class CitizenConversationRuntime {
    private static final int MAX_EXCHANGES_PER_PULSE=12;
    private static final double TALK_RADIUS_SQR=8.0D*8.0D;
    private static final long PAIR_COOLDOWN_TICKS=20L*35L;
    private static final Map<String,Long> COOLDOWN_UNTIL=new HashMap<>();
    private CitizenConversationRuntime(){}

    public static void tick(MinecraftServer server,LivingRealmsSavedData data,long runtimeTick){
        if(runtimeTick%100L!=0)return;
        var state=data.state();
        List<FactionCitizenEntity> loaded=FactionCitizenIndex.loaded().stream()
                .filter(FactionCitizenEntity::isAlive)
                .filter(e->e.citizenId()>0&&!e.isSpeaking()&&e.getTarget()==null)
                .sorted(Comparator.comparingLong(FactionCitizenEntity::settlementId).thenComparingLong(FactionCitizenEntity::citizenId))
                .toList();
        if(loaded.size()<2)return;
        prune(runtimeTick);
        Set<UUID> used=new HashSet<>();int exchanges=0;boolean dirty=false;
        for(FactionCitizenEntity first:loaded){
            if(exchanges>=MAX_EXCHANGES_PER_PULSE)break;
            if(used.contains(first.getUUID()))continue;
            SocialCitizen a=state.findSocialCitizen(first.citizenId()).orElse(null);if(a==null||!a.alive()||inCustody(state,a.id()))continue;
            FactionCitizenEntity second=loaded.stream()
                    .filter(e->e!=first&&!used.contains(e.getUUID())&&e.settlementId()==first.settlementId()&&e.factionId()==first.factionId())
                    .filter(e->first.distanceToSqr(e)<=TALK_RADIUS_SQR)
                    .filter(e->{SocialCitizen b=state.findSocialCitizen(e.citizenId()).orElse(null);return b!=null&&b.alive()&&!inCustody(state,b.id());})
                    .min(Comparator.<FactionCitizenEntity>comparingDouble(e -> first.distanceToSqr(e)).thenComparingLong(FactionCitizenEntity::citizenId)).orElse(null);
            if(second==null)continue;
            String pair=pairKey(first.citizenId(),second.citizenId());if(COOLDOWN_UNTIL.getOrDefault(pair,0L)>runtimeTick)continue;
            SocialCitizen b=state.findSocialCitizen(second.citizenId()).orElse(null);if(b==null)continue;
            CitizenConversationService.Exchange exchange=CitizenConversationService.converse(state,a,b);
            first.getNavigation().stop();second.getNavigation().stop();first.getLookControl().setLookAt(second,30F,30F);second.getLookControl().setLookAt(first,30F,30F);
            first.speak(exchange.firstLine(),80);second.speak(exchange.secondLine(),80);
            COOLDOWN_UNTIL.put(pair,runtimeTick+PAIR_COOLDOWN_TICKS);used.add(first.getUUID());used.add(second.getUUID());exchanges++;dirty=true;
        }
        if(dirty)data.setDirty();
    }

    private static boolean inCustody(dev.livingrealms.sim.world.SimulationState state,long citizenId){String key="citizen:"+citizenId;return state.custody().stream().anyMatch(c->c.active()&&c.actorKey().equals(key));}
    private static String pairKey(long a,long b){return a<b?a+":"+b:b+":"+a;}
    private static void prune(long tick){if(COOLDOWN_UNTIL.size()<512)return;COOLDOWN_UNTIL.entrySet().removeIf(e->e.getValue()<=tick);}
    public static void clear(){COOLDOWN_UNTIL.clear();}
}
