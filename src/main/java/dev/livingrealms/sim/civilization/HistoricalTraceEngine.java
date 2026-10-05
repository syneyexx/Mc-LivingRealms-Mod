package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Wave 16 — causal historical traces from real war/raid/battle/death events. */
public final class HistoricalTraceEngine {
    private HistoricalTraceEngine() {}

    public static void materializeFromHistory(SimulationState state) {
        long day = state.clock().day();
        Set<String> legendKeys = new HashSet<>();
        for (LegendRecord l : state.legends()) legendKeys.add(l.subjectKey());
        Set<String> ruinCauses = new HashSet<>();
        for (RuinSite r : state.ruinSites()) if (r.active()) ruinCauses.add(r.cause());
        for (WorldEvent e : state.history().recent(240)) {
            String type = e.type() == null ? "" : e.type().toLowerCase(Locale.ROOT);
            if (type.equals("battle") || type.equals("naval_battle") || type.equals("raid_repelled")) {
                ensureBattleTrace(state, e, legendKeys, day);
            } else if (type.equals("raid_success")) {
                ensureAbandonedRaidSite(state, e, ruinCauses, legendKeys, day);
            } else if (type.contains("siege_won") || type.equals("settlement_captured")) {
                ensureCaptureMemorial(state, e, legendKeys, day);
            } else if (type.contains("citizen_death") || type.contains("named_epidemic_death") || type.equals("epidemic_deaths")) {
                ensureGraveTrace(state, e, legendKeys, day);
            }
        }
        for (LegendRecord legend : state.legends()) {
            if (legend.monumented() || legend.settlementId() <= 0) continue;
            Settlement s = state.findSettlement(legend.settlementId()).orElse(null);
            if (s == null) continue;
            if (s.completedConstruction().stream().noneMatch(k -> k.startsWith("monument:"))) continue;
            legend.markMonumented();
            state.history().add(new WorldEvent(day, "legend_memorialized",
                    "legend=" + legend.id() + ", settlement=" + s.id() + ", subject=" + legend.subjectKey()));
        }
    }

    public static String wartimeCause(SimulationState state, Faction faction, Settlement settlement) {
        WarState war = state.wars().stream().filter(WarState::active)
                .filter(w -> w.involves(faction.id()) && w.targetSettlementId() == settlement.id()).findFirst().orElse(null);
        if (war != null) return "war:warId=" + war.id() + ",settlement=" + settlement.id();
        WarState any = state.wars().stream().filter(WarState::active).filter(w -> w.involves(faction.id())).findFirst().orElse(null);
        if (any != null) return "war:warId=" + any.id() + ",settlement=" + settlement.id();
        return "abandonment:settlement=" + settlement.id();
    }

    private static void ensureBattleTrace(SimulationState state, WorldEvent e, Set<String> legendKeys, long day) {
        long warId = extract(e.message(), "war=");
        long raidId = extract(e.message(), "raid=");
        long armyA = extract(e.message(), "army=");
        long armyB = extractAfterVs(e.message());
        long settlementId = extract(e.message(), "settlement=");
        long factionId = extract(e.message(), "faction=");
        if (factionId <= 0 && armyA > 0) {
            factionId = state.factions().stream().flatMap(f -> f.armies().stream().filter(a -> a.id() == armyA).map(a -> f.id())).findFirst().orElse(0L);
        }
        if (factionId <= 0 && warId > 0) {
            factionId = state.wars().stream().filter(w -> w.id() == warId).map(WarState::attackerFactionId).findFirst().orElse(0L);
        }
        if (factionId <= 0) return;
        String subject = warId > 0 ? "battle_marker:war=" + warId + (armyA > 0 ? ",army=" + armyA : "")
                : raidId > 0 ? "battle_marker:raid=" + raidId
                : "battle_marker:event=" + Integer.toUnsignedString(e.message().hashCode());
        if (legendKeys.contains(subject)) return;
        Settlement settlement = settlementId > 0 ? state.findSettlement(settlementId).orElse(null) : null;
        if (settlement == null && armyA > 0) {
            Army a = findArmy(state, armyA);
            if (a != null) settlement = nearestSettlement(state, a.position(), factionId);
        }
        if (settlement == null) {
            settlement = state.findFaction(factionId).map(f -> f.settlements().isEmpty() ? null : f.settlements().getFirst()).orElse(null);
        }
        if (settlement == null) return;
        String title = warId > 0 ? "Battle Marker" : raidId > 0 ? "Raid Marker" : "Field Marker";
        String desc = e.message() + (armyB > 0 ? ", armyB=" + armyB : "");
        state.addLegend(new LegendRecord(state.nextId(), e.day(), factionId, settlement.id(), subject, title, desc, .62));
        legendKeys.add(subject);
        state.history().add(new WorldEvent(day, "historical_trace_placed",
                "kind=battle_marker, legendSubject=" + subject + ", settlement=" + settlement.id()
                        + (warId > 0 ? ", war=" + warId : "") + (raidId > 0 ? ", raid=" + raidId : "")));
    }

    private static void ensureAbandonedRaidSite(SimulationState state, WorldEvent e, Set<String> ruinCauses, Set<String> legendKeys, long day) {
        long raidId = extract(e.message(), "raid=");
        long targetId = extract(e.message(), "target=");
        if (raidId <= 0 || targetId <= 0) return;
        Settlement target = state.findSettlement(targetId).orElse(null);
        Faction owner = state.findSettlementOwner(targetId).orElse(null);
        if (target == null || owner == null) return;
        String cause = "abandoned_raid:raidId=" + raidId + ",settlement=" + targetId;
        if (!ruinCauses.contains(cause) && target.population() < 40) {
            SimPosition offset = new SimPosition(target.position().x() + 18, target.position().z() - 12);
            RuinSite site = new RuinSite(state.nextId(), target.id(), owner.id(), e.day(), offset, target.name() + " Outskirts", cause);
            site.weather(.15);
            state.addRuinSite(site);
            ruinCauses.add(cause);
            state.history().add(new WorldEvent(day, "historical_trace_placed",
                    "kind=abandoned_site, ruin=" + site.id() + ", raid=" + raidId + ", settlement=" + targetId));
        }
        String subject = "raid_scar:raid=" + raidId;
        if (!legendKeys.contains(subject)) {
            state.addLegend(new LegendRecord(state.nextId(), e.day(), owner.id(), target.id(), subject, "Raid Scar", e.message(), .58));
            legendKeys.add(subject);
        }
    }

    private static void ensureCaptureMemorial(SimulationState state, WorldEvent e, Set<String> legendKeys, long day) {
        long settlementId = extract(e.message(), "settlement=");
        long warId = extract(e.message(), "war=");
        long toFaction = extract(e.message(), "toFaction=");
        if (toFaction <= 0) toFaction = extract(e.message(), "faction=");
        Settlement settlement = settlementId > 0 ? state.findSettlement(settlementId).orElse(null) : null;
        if (settlement == null) {
            for (Faction f : state.factions()) {
                for (Settlement s : f.settlements()) {
                    if (e.message() != null && e.message().contains(s.name())) {
                        settlement = s;
                        if (toFaction <= 0) toFaction = f.id();
                        break;
                    }
                }
                if (settlement != null) break;
            }
        }
        if (settlement == null || toFaction <= 0) return;
        String subject = warId > 0 ? "capture_memorial:war=" + warId + ",settlement=" + settlement.id()
                : "capture_memorial:settlement=" + settlement.id() + ",day=" + e.day();
        if (legendKeys.contains(subject)) return;
        state.addLegend(new LegendRecord(state.nextId(), e.day(), toFaction, settlement.id(), subject, "Capture Memorial", e.message(), .78));
        legendKeys.add(subject);
    }

    private static void ensureGraveTrace(SimulationState state, WorldEvent e, Set<String> legendKeys, long day) {
        long citizenId = extract(e.message(), "citizen=");
        long settlementId = extract(e.message(), "settlement=");
        long epidemicId = extract(e.message(), "epidemic=");
        if (settlementId <= 0) return;
        Settlement settlement = state.findSettlement(settlementId).orElse(null);
        Faction owner = state.findSettlementOwner(settlementId).orElse(null);
        if (settlement == null || owner == null) return;
        String subject = citizenId > 0 ? "grave:citizen=" + citizenId
                : epidemicId > 0 ? "grave:epidemic=" + epidemicId + ",settlement=" + settlementId
                : "grave:settlement=" + settlementId + ",day=" + e.day();
        if (legendKeys.contains(subject)) return;
        state.addLegend(new LegendRecord(state.nextId(), e.day(), owner.id(), settlement.id(), subject,
                citizenId > 0 ? "Named Grave" : "Mass Grave", e.message(), .5));
        legendKeys.add(subject);
        state.history().add(new WorldEvent(day, "historical_trace_placed",
                "kind=grave, subject=" + subject + ", settlement=" + settlementId));
    }

    private static Army findArmy(SimulationState state, long armyId) {
        for (Faction f : state.factions()) for (Army a : f.armies()) if (a.id() == armyId) return a;
        return null;
    }
    private static Settlement nearestSettlement(SimulationState state, SimPosition pos, long preferFaction) {
        Settlement best = null; double bestD = Double.POSITIVE_INFINITY;
        for (Faction f : state.factions()) for (Settlement s : f.settlements()) {
            double d = s.position().distanceTo(pos);
            if (f.id() == preferFaction) d *= 0.85;
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }
    private static long extract(String text, String marker) {
        if (text == null) return 0;
        int p = text.indexOf(marker); if (p < 0) return 0; p += marker.length();
        int end = p; while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
        if (end == p) return 0;
        try { return Long.parseLong(text.substring(p, end)); } catch (NumberFormatException ignored) { return 0; }
    }
    private static long extractAfterVs(String text) {
        if (text == null) return 0;
        int vs = text.indexOf(" vs "); if (vs < 0) return 0;
        int p = vs + 4; while (p < text.length() && !Character.isDigit(text.charAt(p))) p++;
        int end = p; while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
        if (end == p) return 0;
        try { return Long.parseLong(text.substring(p, end)); } catch (NumberFormatException ignored) { return 0; }
    }
}
