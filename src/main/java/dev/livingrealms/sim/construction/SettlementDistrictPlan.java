package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic district overlay derived from {@link SettlementPlanner} intents.
 * Preserves completed structures; never relocates existing city geometry.
 */
public final class SettlementDistrictPlan {
    public record DistrictAnchor(SettlementDistrict district, SimPosition center, int structureCount, int priorityBoost) {}

    private final long settlementId;
    private final List<DistrictAnchor> anchors;
    private final Map<String, SettlementDistrict> byIntentKey;

    public SettlementDistrictPlan(long settlementId, List<DistrictAnchor> anchors, Map<String, SettlementDistrict> byIntentKey) {
        this.settlementId = settlementId;
        this.anchors = List.copyOf(Objects.requireNonNull(anchors, "anchors"));
        this.byIntentKey = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(byIntentKey, "byIntentKey")));
    }

    public static SettlementDistrictPlan derive(Faction faction, Settlement settlement) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        List<ConstructionIntent> intents = SettlementPlanner.plan(faction, settlement);
        EnumMap<SettlementDistrict, List<ConstructionIntent>> grouped = new EnumMap<>(SettlementDistrict.class);
        Map<String, SettlementDistrict> byKey = new LinkedHashMap<>();
        for (ConstructionIntent intent : intents) {
            SettlementDistrict district = refine(intent, settlement);
            byKey.put(intent.key(), district);
            grouped.computeIfAbsent(district, d -> new ArrayList<>()).add(intent);
        }
        List<DistrictAnchor> anchors = new ArrayList<>();
        for (var e : grouped.entrySet()) {
            List<ConstructionIntent> list = e.getValue();
            double x = 0, z = 0;
            for (ConstructionIntent intent : list) {
                x += intent.center().x();
                z += intent.center().z();
            }
            int n = Math.max(1, list.size());
            int boost = switch (e.getKey()) {
                case MARKET, HARBOR -> 12;
                case GOVERNMENT, MILITARY -> 10;
                case INDUSTRIAL, CRAFTS -> 8;
                case RELIGIOUS -> 6;
                default -> 0;
            };
            anchors.add(new DistrictAnchor(e.getKey(), new SimPosition(x / n, z / n), list.size(), boost));
        }
        anchors.sort((a, b) -> {
            int c = Integer.compare(b.structureCount(), a.structureCount());
            return c != 0 ? c : a.district().name().compareTo(b.district().name());
        });
        return new SettlementDistrictPlan(settlement.id(), anchors, byKey);
    }

    private static SettlementDistrict refine(ConstructionIntent intent, Settlement settlement) {
        SettlementDistrict base = SettlementDistrict.forRole(intent.role());
        if (intent.role() != StructureRole.HOUSE) return base;
        // Dense apartments near keep = wealthy/old town; outer cottages = workers/rural fringe.
        double dist = intent.center().distanceTo(settlement.position());
        boolean apartment = intent.width() >= 11 || intent.depth() >= 11;
        boolean townhouse = intent.width() >= 9 || intent.depth() >= 9;
        if (apartment && dist < 48) return SettlementDistrict.WEALTHY_QUARTER;
        if (townhouse && dist < 80) return SettlementDistrict.OLD_TOWN;
        if (dist > 140) return SettlementDistrict.RURAL_FRINGE;
        if (dist > 90) return SettlementDistrict.WORKERS_QUARTER;
        return SettlementDistrict.RESIDENTIAL;
    }

    public long settlementId() { return settlementId; }
    public List<DistrictAnchor> anchors() { return anchors; }
    public Map<String, SettlementDistrict> byIntentKey() { return byIntentKey; }
    public SettlementDistrict districtOf(String intentKey) {
        return byIntentKey.getOrDefault(intentKey, SettlementDistrict.RESIDENTIAL);
    }

    public int priorityBoost(ConstructionIntent intent) {
        Objects.requireNonNull(intent, "intent");
        SettlementDistrict district = districtOf(intent.key());
        for (DistrictAnchor anchor : anchors) {
            if (anchor.district() == district) return anchor.priorityBoost();
        }
        return 0;
    }
}
