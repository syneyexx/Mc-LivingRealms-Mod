package dev.livingrealms.sim.world;

import java.util.Locale;
import java.util.Objects;

/**
 * Persistent canonical traveler on the wilderness road network. Distinct from trade caravans,
 * refugee migrations, and military armies.
 */
public final class CitizenJourney {
    public enum Purpose {
        COURIER, PEDDLER, PILGRIM, PATROL, TAX_COLLECTOR, SHEPHERD, HUNTER, SEASONAL_WORKER, DIPLOMAT, EXPLORER
    }

    public enum Status { ACTIVE, ARRIVED, ABORTED }

    private final long id;
    private final long citizenId;
    private final long factionId;
    private final long originSettlementId;
    private final long targetSettlementId;
    private long routeId;
    private Purpose purpose;
    private final long createdDay;
    private double progress;
    private Status status;
    private String payload;

    public CitizenJourney(long id, long citizenId, long factionId, long originSettlementId,
                          long targetSettlementId, long routeId, Purpose purpose, long createdDay) {
        if (id <= 0) throw new IllegalArgumentException("id");
        if (factionId <= 0) throw new IllegalArgumentException("factionId");
        if (originSettlementId <= 0 || targetSettlementId <= 0) throw new IllegalArgumentException("settlements");
        this.id = id;
        this.citizenId = citizenId;
        this.factionId = factionId;
        this.originSettlementId = originSettlementId;
        this.targetSettlementId = targetSettlementId;
        this.routeId = Math.max(0, routeId);
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.createdDay = createdDay;
        this.progress = 0;
        this.status = Status.ACTIVE;
        this.payload = "";
    }

    public long id() { return id; }
    public long citizenId() { return citizenId; }
    public long factionId() { return factionId; }
    public long originSettlementId() { return originSettlementId; }
    public long targetSettlementId() { return targetSettlementId; }
    public long routeId() { return routeId; }
    public Purpose purpose() { return purpose; }
    public long createdDay() { return createdDay; }
    public double progress() { return progress; }
    public Status status() { return status; }
    public String payload() { return payload; }
    public boolean active() { return status == Status.ACTIVE; }

    public void setRouteId(long routeId) { this.routeId = Math.max(0, routeId); }
    public void setPayload(String payload) {
        String p = payload == null ? "" : payload.strip();
        if (p.length() > 96) p = p.substring(0, 96);
        this.payload = p;
    }

    /** Advances journey progress by {@code delta} in [0,1]. Returns true if newly arrived. */
    public boolean advance(double delta) {
        if (status != Status.ACTIVE) return false;
        progress = Math.min(1.0, progress + Math.max(0, delta));
        if (progress >= 1.0) {
            status = Status.ARRIVED;
            return true;
        }
        return false;
    }

    public void abort() {
        if (status == Status.ACTIVE) status = Status.ABORTED;
    }

    public void restore(double progress, Status status, String payload, long routeId) {
        this.progress = Math.max(0, Math.min(1, progress));
        this.status = Objects.requireNonNull(status, "status");
        setPayload(payload);
        setRouteId(routeId);
    }

    public String purposeLabel() {
        return purpose.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
