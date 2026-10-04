package dev.livingrealms.sim.world;

import dev.livingrealms.sim.civilization.AssistanceTask;
import dev.livingrealms.sim.civilization.ResourceClaim;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.industry.IndustrialSite;
import dev.livingrealms.sim.logistics.TradeShipment;
import dev.livingrealms.sim.military.MilitaryCommandEngine;
import dev.livingrealms.sim.naval.PortState;
import dev.livingrealms.sim.social.HouseholdState;
import dev.livingrealms.sim.social.SocialCitizen;
import java.util.Comparator;
import java.util.Objects;

/** Canonical settlement ownership change: faction lists plus attached social, economic and military state. */
public final class SettlementTransfer {
    private SettlementTransfer() {}

    public static Settlement transfer(SimulationState state, Settlement settlement, Faction from, Faction to) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.id() == to.id()) throw new IllegalArgumentException("transfer factions must differ");
        long settlementId = settlement.id();
        Settlement moved = from.removeSettlement(settlementId);
        if (moved == null) return null;
        to.addSettlement(moved);
        long toFactionId = to.id();
        long fromFactionId = from.id();

        for (SocialCitizen citizen : state.socialCitizens()) {
            if (citizen.alive() && citizen.settlementId() == settlementId) citizen.migrateTo(toFactionId, settlementId);
        }
        for (HouseholdState household : state.households()) {
            if (household.settlementId() == settlementId) household.migrate(toFactionId, settlementId);
        }
        for (ResourceClaim claim : state.resourceClaims()) {
            if (claim.active() && claim.settlementId() == settlementId && claim.factionId() == fromFactionId) claim.setFactionId(toFactionId);
        }
        for (IndustrialSite site : state.industrialSites()) {
            if (site.settlementId() == settlementId) site.transferTo(toFactionId);
        }
        for (PortState port : state.ports()) {
            if (port.settlementId() == settlementId) port.setFactionId(toFactionId);
        }
        for (AssistanceTask task : state.assistanceTasks()) {
            if (task.settlementId() == settlementId && task.factionId() == fromFactionId) task.setFactionId(toFactionId);
        }
        for (TradeShipment shipment : state.shipments()) {
            if (shipment.originSettlementId() == settlementId && shipment.sellerFactionId() == fromFactionId) shipment.transferSellerFaction(toFactionId);
            if (shipment.destinationSettlementId() == settlementId && shipment.buyerFactionId() == fromFactionId) shipment.transferBuyerFaction(toFactionId);
        }
        MilitaryCommandEngine.reevaluateObjectives(state);
        return moved;
    }

    /** Capital = highest tier, then population, then oldest (lowest) settlement id. */
    public static Settlement capitalTarget(Faction faction) {
        return faction.settlements().stream().max(capitalComparator()).orElse(null);
    }

    public static Comparator<Settlement> capitalComparator() {
        return Comparator.comparingInt((Settlement s) -> s.tier().ordinal())
                .thenComparingInt(Settlement::population)
                .thenComparingLong(s -> -s.id());
    }
}
