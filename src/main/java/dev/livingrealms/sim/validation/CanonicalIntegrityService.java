package dev.livingrealms.sim.validation;

import dev.livingrealms.sim.aviation.AirWing;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.construction.RegisteredPlayerStructure;
import dev.livingrealms.sim.diplomacy.Treaty;
import dev.livingrealms.sim.diplomacy.WarState;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.government.GrandProject;
import dev.livingrealms.sim.industry.IndustrialSite;
import dev.livingrealms.sim.law.BountyContract;
import dev.livingrealms.sim.law.CustodyRecord;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.military.MilitaryObjective;
import dev.livingrealms.sim.military.SiegeState;
import dev.livingrealms.sim.naval.Fleet;
import dev.livingrealms.sim.naval.PortState;
import dev.livingrealms.sim.player.PlayerStanding;
import dev.livingrealms.sim.social.HouseholdState;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.social.SocialPopulationEngine;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimulationState;
import java.util.*;

/**
 * Cross-reference integrity checker distinguishing fatal canonical damage from rebuildable projection indexes.
 * Safe repair never invents missing factions, kings, or settlements.
 */
public final class CanonicalIntegrityService {
    private CanonicalIntegrityService() {}

    public static IntegrityReport inspect(SimulationState state) {
        return analyze(state, false);
    }

    public static IntegrityReport repair(SimulationState state) {
        return analyze(state, true);
    }

    private static IntegrityReport analyze(SimulationState state, boolean applyRepair) {
        Objects.requireNonNull(state, "state");
        List<String> fatal = new ArrayList<>();
        List<String> rebuildable = new ArrayList<>();
        List<String> repaired = new ArrayList<>();

        Set<Long> factionIds = new HashSet<>();
        Set<Long> settlementIds = new HashSet<>();
        Set<Long> armyIds = new HashSet<>();
        for (Faction f : state.factions()) {
            if (!factionIds.add(f.id())) fatal.add("duplicate faction " + f.id());
            for (Settlement s : f.settlements()) {
                if (!settlementIds.add(s.id())) fatal.add("settlement owned more than once " + s.id());
            }
            for (Army a : f.armies()) {
                if (!armyIds.add(a.id())) fatal.add("army owned more than once " + a.id());
                if (a.factionId() != f.id()) fatal.add("army " + a.id() + " owner mismatch");
            }
        }

        Set<Long> citizenIds = new HashSet<>();
        for (SocialCitizen c : state.socialCitizens()) {
            citizenIds.add(c.id());
            if (!factionIds.contains(c.factionId())) fatal.add("citizen " + c.id() + " missing faction " + c.factionId());
            if (!settlementIds.contains(c.settlementId())) fatal.add("citizen " + c.id() + " missing settlement " + c.settlementId());
        }

        for (TradeShipment s : state.shipments()) {
            if (!factionIds.contains(s.sellerFactionId()) || !factionIds.contains(s.buyerFactionId()) || s.sellerFactionId() == s.buyerFactionId())
                fatal.add("shipment " + s.id() + " invalid factions");
            if (s.originSettlementId() > 0 && !settlementIds.contains(s.originSettlementId()))
                fatal.add("shipment " + s.id() + " missing origin settlement " + s.originSettlementId());
            if (s.destinationSettlementId() > 0 && !settlementIds.contains(s.destinationSettlementId()))
                fatal.add("shipment " + s.id() + " missing destination settlement " + s.destinationSettlementId());
            if (s.routeId() > 0 && state.routes().stream().noneMatch(r -> r.id() == s.routeId()))
                fatal.add("shipment " + s.id() + " missing route " + s.routeId());
        }
        for (TransportRoute r : state.routes()) {
            if (!factionIds.contains(r.ownerFactionId()) || !settlementIds.contains(r.fromSettlementId()) || !settlementIds.contains(r.toSettlementId()) || r.fromSettlementId() == r.toSettlementId())
                fatal.add("route " + r.id() + " invalid endpoints");
        }
        for (Treaty t : state.treaties()) {
            if (!pair(factionIds, t.factionA(), t.factionB())) fatal.add("treaty " + t.id() + " invalid factions");
        }
        for (WarState w : state.wars()) {
            if (!pair(factionIds, w.attackerFactionId(), w.defenderFactionId())) fatal.add("war " + w.id() + " invalid factions");
            if (w.targetSettlementId() > 0 && !settlementIds.contains(w.targetSettlementId()))
                fatal.add("war " + w.id() + " missing target settlement");
        }
        for (MilitaryObjective o : state.objectives()) {
            if (!armyIds.contains(o.armyId()) || !factionIds.contains(o.ownerFactionId()))
                fatal.add("objective " + o.id() + " invalid owner/army");
            if (o.targetFactionId() > 0 && !factionIds.contains(o.targetFactionId()))
                fatal.add("objective " + o.id() + " missing target faction");
            if (o.targetSettlementId() > 0 && !settlementIds.contains(o.targetSettlementId()))
                fatal.add("objective " + o.id() + " missing target settlement");
        }
        for (SiegeState s : state.sieges()) {
            if (!pair(factionIds, s.attackerFactionId(), s.defenderFactionId()) || !settlementIds.contains(s.settlementId()))
                fatal.add("siege " + s.id() + " invalid references");
        }
        for (AirWing w : state.airWings()) {
            if (!factionIds.contains(w.factionId())) fatal.add("airwing " + w.id() + " missing faction");
        }
        Set<Long> portIds = new HashSet<>();
        for (PortState p : state.ports()) {
            portIds.add(p.id());
            if (!factionIds.contains(p.factionId()) || !settlementIds.contains(p.settlementId()))
                fatal.add("port " + p.id() + " invalid owner/settlement");
        }
        for (Fleet f : state.fleets()) {
            if (!factionIds.contains(f.factionId()) || !portIds.contains(f.homePortId()))
                fatal.add("fleet " + f.id() + " invalid faction/home port");
        }
        for (IndustrialSite site : state.industrialSites()) {
            if (!factionIds.contains(site.factionId()) || !settlementIds.contains(site.settlementId()))
                fatal.add("industrial site " + site.id() + " invalid owner/settlement");
        }
        for (BountyContract b : state.bounties()) {
            if (!factionIds.contains(b.issuerFactionId())) fatal.add("bounty " + b.id() + " missing issuer");
        }
        for (CustodyRecord c : state.custody()) {
            if (!factionIds.contains(c.factionId())) fatal.add("custody " + c.id() + " missing faction");
        }
        for (RegisteredPlayerStructure s : state.registeredPlayerStructures()) {
            if (!settlementIds.contains(s.settlementId()))
                fatal.add("registered structure " + s.id() + " missing settlement " + s.settlementId());
        }
        for (GrandProject p : state.grandProjects()) {
            if (!factionIds.contains(p.sponsorFactionId()) || !settlementIds.contains(p.settlementId()))
                fatal.add("grand project " + p.id() + " invalid sponsor/settlement");
        }
        for (DynastyState d : state.dynasties().values()) {
            if (!factionIds.contains(d.factionId())) {
                String msg = "orphan dynasty index for missing faction " + d.factionId();
                rebuildable.add(msg);
                if (applyRepair && state.removeDynasty(d.factionId())) repaired.add(msg);
            } else {
                if (d.rulerCitizenId() > 0 && !citizenIds.contains(d.rulerCitizenId()))
                    fatal.add("dynasty " + d.factionId() + " missing ruler citizen " + d.rulerCitizenId());
                if (d.regentCitizenId() > 0 && !citizenIds.contains(d.regentCitizenId()))
                    fatal.add("dynasty " + d.factionId() + " missing regent citizen " + d.regentCitizenId());
            }
        }

        // Rebuildable projection indexes: civilization profiles keyed by entity id.
        for (long settlementId : new ArrayList<>(state.settlementCivilizations().keySet())) {
            if (!settlementIds.contains(settlementId)) {
                String msg = "stale settlement civilization index " + settlementId;
                rebuildable.add(msg);
                if (applyRepair && state.removeSettlementCivilization(settlementId)) repaired.add(msg);
            } else {
                SettlementCivilizationState c = state.settlementCivilizations().get(settlementId);
                if (c != null && !factionIds.contains(c.heritageFactionId()))
                    fatal.add("settlement civilization " + settlementId + " missing heritage faction " + c.heritageFactionId());
            }
        }
        for (long factionId : new ArrayList<>(state.factionCivilizations().keySet())) {
            if (!factionIds.contains(factionId)) {
                String msg = "stale faction civilization index " + factionId;
                rebuildable.add(msg);
                if (applyRepair && state.removeFactionCivilization(factionId)) repaired.add(msg);
            }
        }

        // Duplicate living projection slots — rebind extras deterministically.
        Map<String, List<SocialCitizen>> slots = new HashMap<>();
        for (SocialCitizen c : state.socialCitizens()) {
            if (!c.alive()) continue;
            slots.computeIfAbsent(c.settlementId() + ":" + c.projectionSlot(), k -> new ArrayList<>()).add(c);
        }
        for (var e : slots.entrySet()) {
            if (e.getValue().size() <= 1) continue;
            List<SocialCitizen> dupes = new ArrayList<>(e.getValue());
            dupes.sort(Comparator.comparingLong(SocialCitizen::id));
            String msg = "duplicate living projection slot " + e.getKey() + " count=" + dupes.size();
            rebuildable.add(msg);
            if (applyRepair) {
                // Keep lowest id; rebind the rest into free virtual slots.
                for (int i = 1; i < dupes.size(); i++) {
                    SocialCitizen c = dupes.get(i);
                    try {
                        int next = SocialPopulationEngine.allocateVirtualProjectionSlot(state, c.settlementId(), c.id());
                        c.rebindProjectionSlot(next);
                        repaired.add("rebound citizen " + c.id() + " projection slot -> " + next);
                    } catch (RuntimeException ex) {
                        fatal.add("could not rebind duplicate projection for citizen " + c.id() + ": " + ex.getMessage());
                    }
                }
            }
        }

        // Optional household backlinks — clear/set only when unambiguous.
        Set<Long> householdIds = new HashSet<>();
        for (HouseholdState h : state.households()) householdIds.add(h.id());
        Map<Long, List<Long>> householdsListingCitizen = new HashMap<>();
        for (HouseholdState h : state.households()) {
            if (!factionIds.contains(h.factionId()) || !settlementIds.contains(h.settlementId()))
                fatal.add("household " + h.id() + " invalid faction/settlement");
            for (long memberId : h.memberIds()) {
                SocialCitizen member = state.findSocialCitizen(memberId).orElse(null);
                if (member == null) {
                    fatal.add("household " + h.id() + " missing member " + memberId);
                    continue;
                }
                householdsListingCitizen.computeIfAbsent(memberId, k -> new ArrayList<>()).add(h.id());
            }
        }
        for (HouseholdState h : state.households()) {
            for (long memberId : h.memberIds()) {
                SocialCitizen member = state.findSocialCitizen(memberId).orElse(null);
                if (member == null) continue;
                long backlink = member.householdId();
                boolean missingOrStale = backlink == 0 || !householdIds.contains(backlink);
                if (missingOrStale) {
                    List<Long> listings = householdsListingCitizen.getOrDefault(memberId, List.of());
                    if (listings.size() > 1) {
                        fatal.add("ambiguous household backlink for citizen " + memberId + " (listed in multiple households, backlink=" + backlink + ")");
                        continue;
                    }
                    String msg = backlink == 0
                            ? "household " + h.id() + " member " + memberId + " missing backlink"
                            : "household " + h.id() + " member " + memberId + " stale backlink " + backlink;
                    rebuildable.add(msg);
                    if (applyRepair) {
                        member.setHouseholdId(h.id());
                        repaired.add(msg);
                    }
                } else if (backlink != h.id()) {
                    // Competing membership across two real households — never invent which wins.
                    fatal.add("ambiguous household backlink for citizen " + memberId + " (listed in " + h.id() + ", points to " + backlink + ")");
                }
            }
        }
        for (SocialCitizen c : state.socialCitizens()) {
            if (c.householdId() > 0 && !householdIds.contains(c.householdId())) {
                String msg = "citizen " + c.id() + " stale household backlink " + c.householdId();
                rebuildable.add(msg);
                if (applyRepair) {
                    c.setHouseholdId(0);
                    repaired.add(msg);
                }
            }
        }

        // Player standing projection rows for missing factions.
        for (PlayerStanding ps : state.playerStandings().values()) {
            for (long factionId : new ArrayList<>(ps.reputations().keySet())) {
                if (!factionIds.contains(factionId)) {
                    String msg = "player " + ps.actorKey() + " stale reputation faction " + factionId;
                    rebuildable.add(msg);
                    if (applyRepair && ps.clearReputation(factionId)) repaired.add(msg);
                }
            }
            for (long factionId : new ArrayList<>(ps.influences().keySet())) {
                if (!factionIds.contains(factionId)) {
                    String msg = "player " + ps.actorKey() + " stale influence faction " + factionId;
                    rebuildable.add(msg);
                    if (applyRepair && ps.clearInfluence(factionId)) repaired.add(msg);
                }
            }
            if (ps.isMember() && !factionIds.contains(ps.memberFactionId()))
                fatal.add("player " + ps.actorKey() + " member of missing faction " + ps.memberFactionId());
        }

        if (applyRepair) state.repairNextIdWatermark();

        // After repair, drop rebuildable entries that were fixed from the outstanding list.
        List<String> outstandingRebuildable = new ArrayList<>();
        if (applyRepair) {
            Set<String> fixed = new HashSet<>(repaired);
            for (String r : rebuildable) if (!fixed.contains(r) && repaired.stream().noneMatch(x -> x.equals(r) || x.startsWith("rebound citizen"))) outstandingRebuildable.add(r);
            // Re-check duplicate slot messages: if we rebound, original message stays in rebuildable but was addressed.
            List<String> filtered = new ArrayList<>();
            for (String r : rebuildable) {
                if (r.startsWith("duplicate living projection slot")) {
                    if (repaired.stream().anyMatch(x -> x.startsWith("rebound citizen"))) continue;
                }
                if (fixed.contains(r)) continue;
                filtered.add(r);
            }
            outstandingRebuildable = filtered;
        } else {
            outstandingRebuildable = rebuildable;
        }

        return new IntegrityReport(fatal, outstandingRebuildable, repaired, applyRepair);
    }

    private static boolean pair(Set<Long> factions, long a, long b) {
        return a != b && factions.contains(a) && factions.contains(b);
    }
}
