package dev.livingrealms.sim.economy;

import dev.livingrealms.sim.civilization.ResourceClaim;
import dev.livingrealms.sim.civilization.ResourceClaimType;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.industry.*;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimulationState;
import java.util.*;

/**
 * Derived world-market concentration. There is deliberately no mutable monopoly flag: dominance
 * follows real stock, resource access and operating industry and disappears when those advantages do.
 *
 * <p>Per-day leverage is memoized so trade dispatch does not recompute O(factions × routes) scores
 * for every seller/buyer/resource pair.</p>
 */
public final class ResourceDominanceEngine {
    private static long cacheDay = Long.MIN_VALUE;
    private static int cacheIdentity;
    private static final EnumMap<ResourceType, ResourceDominance> dominanceCache = new EnumMap<>(ResourceType.class);
    private static final EnumMap<ResourceType, Map<Long, Double>> scoreCache = new EnumMap<>(ResourceType.class);

    private ResourceDominanceEngine() {}

    public static ResourceDominance analyze(SimulationState state, ResourceType resource) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(resource);
        ensureDayCache(state);
        ResourceDominance cached = dominanceCache.get(resource);
        if (cached != null) return cached;
        Map<Long, Double> scoresByFaction = scoreCache.computeIfAbsent(resource, r -> new LinkedHashMap<>());
        List<Share> scores = new ArrayList<>();
        double total = 0;
        for (Faction faction : state.factions()) {
            double score = capacityScore(state, faction, resource);
            scoresByFaction.put(faction.id(), score);
            scores.add(new Share(faction.id(), score));
            total += score;
        }
        scores.sort(Comparator.comparingDouble(Share::score).reversed().thenComparingLong(Share::factionId));
        ResourceDominance result;
        if (scores.isEmpty() || total <= 0) {
            result = new ResourceDominance(resource, 0, 0, 0, 0, false);
        } else {
            Share leader = scores.getFirst();
            Share runner = scores.size() > 1 ? scores.get(1) : new Share(0, 0);
            double leaderShare = leader.score() / total;
            double runnerShare = runner.score() / total;
            boolean monopoly = leaderShare >= .62 && leaderShare >= runnerShare * 1.75;
            result = new ResourceDominance(resource, leader.factionId(), leaderShare, runner.factionId(), runnerShare, monopoly);
        }
        dominanceCache.put(resource, result);
        return result;
    }

    public static double share(SimulationState state, long factionId, ResourceType resource) {
        if (factionId <= 0) return 0;
        ensureDayCache(state);
        analyze(state, resource);
        Map<Long, Double> scores = scoreCache.getOrDefault(resource, Map.of());
        double total = 0;
        for (double score : scores.values()) total += score;
        if (total <= 0) return 0;
        return scores.getOrDefault(factionId, 0.0) / total;
    }

    /** Market leverage is intentionally capped so one realm cannot create runaway prices. */
    public static double sellerLeverageMultiplier(SimulationState state, long sellerFactionId, ResourceType resource) {
        double share = share(state, sellerFactionId, resource);
        if (share <= .40) return 1.0;
        return 1.0 + Math.min(.22, (share - .40) * .42);
    }

    private static void ensureDayCache(SimulationState state) {
        long day = state.clock().day();
        int identity = System.identityHashCode(state);
        if (cacheDay == day && cacheIdentity == identity) return;
        cacheDay = day;
        cacheIdentity = identity;
        dominanceCache.clear();
        scoreCache.clear();
    }

    private static double capacityScore(SimulationState state, Faction faction, ResourceType resource) {
        double stock = Math.log1p(Math.max(0, faction.stockpile().get(resource))) * 1.15;
        double claims = 0;
        for (ResourceClaim claim : state.resourceClaims()) {
            if (claim.active() && claim.factionId() == faction.id()) {
                claims += claimContribution(claim.type(), resource) * (.35 + .65 * claim.strength());
            }
        }
        double industry = 0;
        for (IndustrialSite site : state.industrialSites()) {
            if (site.factionId() == faction.id() && site.operational()) {
                industry += industryContribution(site.kind(), resource) * site.level() * (.4 + .6 * site.condition());
            }
        }
        double logistics = 0;
        for (var route : state.routes()) {
            if (route.operational() && route.ownerFactionId() == faction.id()) {
                logistics += .03 + route.capacityPerDay() / 8000.0;
            }
        }
        return Math.max(.0001, stock + claims * 3.5 + industry * 2.8 + Math.min(2.5, logistics));
    }

    private static double claimContribution(ResourceClaimType claim, ResourceType resource) {
        return switch (claim) {
            case FARMLAND -> resource == ResourceType.FOOD ? 1.0 : resource == ResourceType.TEXTILES ? .18 : 0;
            case FISHERY -> resource == ResourceType.FOOD ? .72 : 0;
            case TIMBER -> resource == ResourceType.WOOD ? 1.0 : 0;
            case MINE -> switch (resource) {
                case STONE -> .72;
                case IRON -> 1.0;
                case COAL -> .75;
                case COPPER -> .72;
                case GOLD -> .42;
                default -> 0;
            };
            case WATER -> resource == ResourceType.FOOD ? .18 : 0;
        };
    }

    private static double industryContribution(IndustryKind kind, ResourceType resource) {
        return switch (kind) {
            case SAWMILL -> resource == ResourceType.WOOD ? 1.0 : 0;
            case STONEWORKS -> resource == ResourceType.STONE ? 1.0 : 0;
            case METALWORKS -> switch (resource) {
                case IRON -> 1.0;
                case COPPER -> .72;
                case GOLD -> .28;
                default -> 0;
            };
            case COKEWORKS -> resource == ResourceType.COAL ? 1.0 : 0;
            case TOOLWORKS -> resource == ResourceType.TOOLS ? 1.0 : 0;
            case MACHINERY_WORKS -> resource == ResourceType.MACHINERY ? 1.0 : 0;
            case FUEL_REFINERY -> resource == ResourceType.FUEL ? 1.0 : 0;
            case MUNITIONS -> resource == ResourceType.AMMUNITION ? 1.0 : 0;
            case TEXTILE_MILL -> resource == ResourceType.TEXTILES ? 1.0 : 0;
        };
    }

    private record Share(long factionId, double score) {}

    public record ResourceDominance(
            ResourceType resource,
            long leaderFactionId,
            double leaderShare,
            long runnerUpFactionId,
            double runnerUpShare,
            boolean monopoly
    ) {
        public ResourceDominance {
            Objects.requireNonNull(resource);
            leaderShare = Mathx.clamp(leaderShare, 0, 1);
            runnerUpShare = Mathx.clamp(runnerUpShare, 0, 1);
            if (leaderFactionId < 0 || runnerUpFactionId < 0) throw new IllegalArgumentException("faction");
        }
    }
}
