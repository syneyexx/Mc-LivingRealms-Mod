package dev.livingrealms.sim.worldgen;

import dev.livingrealms.sim.faction.SettlementRole;
import dev.livingrealms.sim.world.SettlementDensitySeeder;
import dev.livingrealms.sim.world.StarterCivilizationLayoutPlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Seed-stable strategic manifest for starter civilization worldgen.
 *
 * <p>The manifest is deliberately cheap: it validates identities, realm hierarchy and regional
 * connectivity without sampling terrain or generating chunks. Worldgen may stay chunk-lazy, but
 * the complete civilization graph must already be known and internally coherent before the first
 * Living Realms block is authored.</p>
 */
public record StarterCivilizationManifest(
        long worldSeed,
        int realmCount,
        int settlementCount,
        int capitalCount,
        int townCount,
        int villageCount,
        int hamletCount,
        int routeCount,
        Map<String, RealmSummary> realms
) {
    public record RealmSummary(
            String realmId,
            long factionId,
            int settlements,
            int capitals,
            int towns,
            int villages,
            int hamlets,
            int routes
    ) {}

    public StarterCivilizationManifest {
        if (realmCount <= 0 || settlementCount <= 0 || capitalCount <= 0) {
            throw new IllegalArgumentException("empty starter civilization manifest");
        }
        realms = Map.copyOf(Objects.requireNonNull(realms, "realms"));
    }

    public static StarterCivilizationManifest build(
            StarterCivilizationLayoutPlanner.Layout layout,
            List<StarterRegionalRoutePlanner.RoutePlan> routes) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(routes, "routes");

        Set<Long> settlementIds = new HashSet<>();
        Set<String> settlementKeys = new HashSet<>();
        Set<Long> factionIds = new HashSet<>();
        Map<Long, String> realmBySettlement = new HashMap<>();
        Map<Long, Long> factionBySettlement = new HashMap<>();
        Map<String, MutableRealm> mutableRealms = new LinkedHashMap<>();

        int capitals = 0;
        int towns = 0;
        int villages = 0;
        int hamlets = 0;

        for (StarterCivilizationLayoutPlanner.RealmPlan realm : layout.realms()) {
            String realmId = realm.definition().id();
            if (mutableRealms.containsKey(realmId)) {
                throw new IllegalStateException("duplicate starter realm id: " + realmId);
            }
            if (!factionIds.add(realm.factionId())) {
                throw new IllegalStateException("duplicate starter faction id: " + realm.factionId());
            }

            MutableRealm summary = new MutableRealm(realmId, realm.factionId());
            mutableRealms.put(realmId, summary);

            for (StarterCivilizationLayoutPlanner.SettlementPlan settlement : realm.settlements()) {
                if (!settlementIds.add(settlement.id())) {
                    throw new IllegalStateException("duplicate starter settlement id: " + settlement.id());
                }
                if (!settlementKeys.add(settlement.stableKey())) {
                    throw new IllegalStateException("duplicate starter settlement key: " + settlement.stableKey());
                }
                if (!realmId.equals(settlement.realmId())) {
                    throw new IllegalStateException("starter settlement realm mismatch: " + settlement.stableKey());
                }
                if (!settlement.role().ordinarySurfaceSettlement()) {
                    throw new IllegalStateException("non-surface role in surface starter manifest: "
                            + settlement.stableKey() + " role=" + settlement.role());
                }

                realmBySettlement.put(settlement.id(), realmId);
                factionBySettlement.put(settlement.id(), realm.factionId());
                summary.settlements++;
                switch (settlement.role()) {
                    case CAPITAL -> { summary.capitals++; capitals++; }
                    case TOWN, CITY -> { summary.towns++; towns++; }
                    case VILLAGE -> { summary.villages++; villages++; }
                    case HAMLET -> { summary.hamlets++; hamlets++; }
                    case SPECIAL -> throw new IllegalStateException(
                            "special settlement in surface starter manifest: " + settlement.stableKey());
                }
            }

            if (summary.capitals != 1) {
                throw new IllegalStateException(realmId + " requires exactly one capital, got " + summary.capitals);
            }
            if (summary.towns < SettlementDensitySeeder.MIN_TOWNS_PER_REALM
                    || summary.towns > SettlementDensitySeeder.MAX_TOWNS_PER_REALM) {
                throw new IllegalStateException(realmId + " town count outside authored range: " + summary.towns);
            }
            if (summary.villages < SettlementDensitySeeder.MIN_VILLAGES_PER_REALM
                    || summary.villages > SettlementDensitySeeder.MAX_VILLAGES_PER_REALM) {
                throw new IllegalStateException(realmId + " village count outside authored range: " + summary.villages);
            }
            if (summary.hamlets < SettlementDensitySeeder.MIN_RURAL_HAMLETS_PER_REALM
                    || summary.hamlets > SettlementDensitySeeder.MAX_RURAL_HAMLETS_PER_REALM) {
                throw new IllegalStateException(realmId + " hamlet count outside authored range: " + summary.hamlets);
            }
        }

        Set<Long> routeIds = new HashSet<>();
        Set<String> routeKeys = new HashSet<>();
        Set<Long> incidentSettlements = new HashSet<>();
        for (StarterRegionalRoutePlanner.RoutePlan route : routes) {
            if (!routeIds.add(route.stableRouteId())) {
                throw new IllegalStateException("duplicate starter route id: " + route.stableRouteId());
            }
            if (!routeKeys.add(route.stableKey())) {
                throw new IllegalStateException("duplicate starter route key: " + route.stableKey());
            }
            String fromRealm = realmBySettlement.get(route.fromSettlementId());
            String toRealm = realmBySettlement.get(route.toSettlementId());
            if (fromRealm == null || toRealm == null) {
                throw new IllegalStateException("starter route references unknown settlement: " + route.stableKey());
            }
            if (!fromRealm.equals(toRealm)) {
                throw new IllegalStateException("starter regional route crosses realm boundary: " + route.stableKey());
            }
            long expectedFaction = factionBySettlement.get(route.fromSettlementId());
            if (route.factionId() != expectedFaction
                    || factionBySettlement.get(route.toSettlementId()) != expectedFaction) {
                throw new IllegalStateException("starter route faction mismatch: " + route.stableKey());
            }
            incidentSettlements.add(route.fromSettlementId());
            incidentSettlements.add(route.toSettlementId());
            mutableRealms.get(fromRealm).routes++;
        }

        List<Long> isolated = new ArrayList<>();
        for (long settlementId : settlementIds) {
            if (!incidentSettlements.contains(settlementId)) isolated.add(settlementId);
        }
        if (!isolated.isEmpty()) {
            throw new IllegalStateException("isolated starter settlements without regional route: " + isolated);
        }

        Map<String, RealmSummary> frozen = new LinkedHashMap<>();
        for (MutableRealm realm : mutableRealms.values()) {
            frozen.put(realm.realmId, new RealmSummary(
                    realm.realmId, realm.factionId, realm.settlements, realm.capitals,
                    realm.towns, realm.villages, realm.hamlets, realm.routes));
        }

        return new StarterCivilizationManifest(
                layout.worldSeed(),
                layout.realms().size(),
                settlementIds.size(),
                capitals,
                towns,
                villages,
                hamlets,
                routes.size(),
                frozen);
    }

    private static final class MutableRealm {
        final String realmId;
        final long factionId;
        int settlements;
        int capitals;
        int towns;
        int villages;
        int hamlets;
        int routes;

        MutableRealm(String realmId, long factionId) {
            this.realmId = realmId;
            this.factionId = factionId;
        }
    }
}
