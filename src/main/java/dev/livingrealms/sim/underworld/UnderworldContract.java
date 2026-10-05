package dev.livingrealms.sim.underworld;

import java.util.Objects;

/** Persistent underworld job — completed only by a matching real crime incident. */
public final class UnderworldContract {
    public enum Status { AVAILABLE, ACCEPTED, COMPLETED, FAILED, EXPIRED, CANCELLED }

    private final long id;
    private final UnderworldContractType type;
    private final long jurisdictionFactionId;
    private final String targetVictimKey;
    private final double minValue;
    private final double reward;
    private final long createdDay;
    private final long expiresDay;
    private Status status = Status.AVAILABLE;
    private String acceptorActorKey = "";
    private long acceptedDay = -1;
    private long closedDay = -1;
    private long matchingCrimeId;

    public UnderworldContract(long id, UnderworldContractType type, long jurisdictionFactionId,
                              String targetVictimKey, double minValue, double reward,
                              long createdDay, long expiresDay) {
        if (id <= 0) throw new IllegalArgumentException("id");
        this.type = Objects.requireNonNull(type, "type");
        if (jurisdictionFactionId <= 0) throw new IllegalArgumentException("jurisdictionFactionId");
        if (!Double.isFinite(minValue) || minValue < 0) throw new IllegalArgumentException("minValue");
        if (!Double.isFinite(reward) || reward < 0) throw new IllegalArgumentException("reward");
        if (createdDay < 0) throw new IllegalArgumentException("createdDay");
        if (expiresDay < createdDay) throw new IllegalArgumentException("expiresDay");
        this.id = id;
        this.jurisdictionFactionId = jurisdictionFactionId;
        this.targetVictimKey = targetVictimKey == null ? "" : targetVictimKey;
        this.minValue = minValue;
        this.reward = reward;
        this.createdDay = createdDay;
        this.expiresDay = expiresDay;
    }

    public long id() { return id; }
    public UnderworldContractType type() { return type; }
    public long jurisdictionFactionId() { return jurisdictionFactionId; }
    public String targetVictimKey() { return targetVictimKey; }
    public double minValue() { return minValue; }
    public double reward() { return reward; }
    public long createdDay() { return createdDay; }
    public long expiresDay() { return expiresDay; }
    public Status status() { return status; }
    public String acceptorActorKey() { return acceptorActorKey; }
    public long acceptedDay() { return acceptedDay; }
    public long closedDay() { return closedDay; }
    public long matchingCrimeId() { return matchingCrimeId; }

    public boolean open() {
        return status == Status.AVAILABLE || status == Status.ACCEPTED;
    }

    public boolean closed() {
        return !open();
    }

    public boolean accept(String actorKey, long day) {
        if (status != Status.AVAILABLE || actorKey == null || actorKey.isBlank()) return false;
        if (day > expiresDay) {
            expire(day);
            return false;
        }
        acceptorActorKey = actorKey;
        acceptedDay = Math.max(0, day);
        status = Status.ACCEPTED;
        return true;
    }

    public boolean matchesCrime(dev.livingrealms.sim.law.CrimeIncident incident) {
        Objects.requireNonNull(incident, "incident");
        if (status != Status.ACCEPTED) return false;
        if (!acceptorActorKey.equals(incident.actorKey())) return false;
        if (jurisdictionFactionId != incident.jurisdictionFactionId()) return false;
        if (type.crimeType() != incident.type()) return false;
        if (!targetVictimKey.isBlank() && !targetVictimKey.equals(incident.victimKey())) return false;
        return incident.stolenOrDamageValue() + 1e-9 >= minValue;
    }

    public boolean complete(long crimeId, long day) {
        if (status != Status.ACCEPTED) return false;
        matchingCrimeId = crimeId;
        closedDay = Math.max(0, day);
        status = Status.COMPLETED;
        return true;
    }

    public void fail(long day) {
        if (!open()) return;
        closedDay = Math.max(0, day);
        status = Status.FAILED;
    }

    public void expire(long day) {
        if (!open()) return;
        closedDay = Math.max(0, day);
        status = Status.EXPIRED;
    }

    public void cancel(long day) {
        if (status == Status.COMPLETED) return;
        closedDay = Math.max(0, day);
        status = Status.CANCELLED;
    }

    public void restore(Status status, String acceptorActorKey, long acceptedDay, long closedDay, long matchingCrimeId) {
        this.status = Objects.requireNonNull(status, "status");
        this.acceptorActorKey = acceptorActorKey == null ? "" : acceptorActorKey;
        this.acceptedDay = acceptedDay;
        this.closedDay = closedDay;
        this.matchingCrimeId = Math.max(0, matchingCrimeId);
    }
}
