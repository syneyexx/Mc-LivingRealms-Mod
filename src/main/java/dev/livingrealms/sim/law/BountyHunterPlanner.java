package dev.livingrealms.sim.law;

import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/** Pure deterministic planner for physical NPC bounty-hunter projections. */
public final class BountyHunterPlanner {
    public record OnlineTarget(String actorKey,SimPosition position,boolean alive){public OnlineTarget{Objects.requireNonNull(actorKey);Objects.requireNonNull(position);}}
    public record Existing(long contractId,String hunterKey){public Existing{hunterKey=hunterKey==null?"":hunterKey;}}
    public record Desired(long contractId,long issuerFactionId,String targetActorKey,String hunterKey,SimPosition targetPosition){public Desired{Objects.requireNonNull(targetActorKey);Objects.requireNonNull(hunterKey);Objects.requireNonNull(targetPosition);}}
    public record Plan(List<Desired> spawns,Set<Long> keepContractIds,Set<Long> despawnContractIds){public Plan{spawns=List.copyOf(spawns);keepContractIds=Set.copyOf(keepContractIds);despawnContractIds=Set.copyOf(despawnContractIds);}}

    private BountyHunterPlanner(){}

    public static Plan reconcile(Collection<BountyContract> contracts,Collection<OnlineTarget> onlineTargets,Collection<Existing> existing,int maxHunters){
        if(maxHunters<0)throw new IllegalArgumentException("maxHunters");
        Map<String,OnlineTarget> targets=new HashMap<>();for(OnlineTarget t:onlineTargets)if(t.alive())targets.put(t.actorKey(),t);
        Map<Long,List<Existing>> byContract=new HashMap<>();for(Existing e:existing)byContract.computeIfAbsent(e.contractId(),k->new ArrayList<>()).add(e);
        List<BountyContract> eligible=contracts.stream().filter(c->c.status()==BountyContract.Status.OPEN||isNpcAssignment(c)).filter(c->targets.containsKey(c.actorKey())).sorted(Comparator.comparingDouble(BountyContract::reward).reversed().thenComparingLong(BountyContract::id)).toList();
        Set<Long> keep=new LinkedHashSet<>(),despawn=new LinkedHashSet<>();List<Desired> spawns=new ArrayList<>();int used=0;
        for(BountyContract c:eligible){if(used>=maxHunters)break;List<Existing> current=byContract.getOrDefault(c.id(),List.of());if(!current.isEmpty()){keep.add(c.id());used++;for(int i=1;i<current.size();i++)despawn.add(c.id());continue;}String hunterKey="npc-bounty:"+c.id();spawns.add(new Desired(c.id(),c.issuerFactionId(),c.actorKey(),hunterKey,targets.get(c.actorKey()).position()));keep.add(c.id());used++;}
        Set<Long> eligibleIds=new HashSet<>();for(BountyContract c:eligible)eligibleIds.add(c.id());for(Existing e:existing)if(!keep.contains(e.contractId())||!eligibleIds.contains(e.contractId()))despawn.add(e.contractId());
        return new Plan(spawns,keep,despawn);
    }
    public static boolean isNpcAssignment(BountyContract c){return c.status()==BountyContract.Status.ASSIGNED&&c.hunterKey().startsWith("npc-bounty:");}
}
