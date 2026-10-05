package dev.livingrealms.sim.construction;

import java.util.Objects;

/**
 * Persistent recognition of a player-built structure contributing capacity to a settlement.
 * Registration is recognition — Living Realms must not rebuild/replace the player's blocks.
 */
public final class RegisteredPlayerStructure {
    public enum Role {
        HOUSE, TOWN_HALL, WAREHOUSE, WORKSHOP, MARKET, TAVERN, TEMPLE, BARRACKS, FARM, PASTURE, CLINIC, SCHOOL
    }

    private final long id;
    private final long settlementId;
    private final String ownerActorKey;
    private Role role;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final int doorX, doorY, doorZ;
    private int capacity;
    private final long registrationDay;
    private long fingerprint;
    private boolean valid;
    private long lastValidatedDay;

    public RegisteredPlayerStructure(long id, long settlementId, String ownerActorKey, Role role,
                                     int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                     int doorX, int doorY, int doorZ, int capacity, long registrationDay,
                                     long fingerprint) {
        if (id <= 0 || settlementId <= 0) throw new IllegalArgumentException("ids");
        this.id = id;
        this.settlementId = settlementId;
        this.ownerActorKey = Objects.requireNonNullElse(ownerActorKey, "");
        this.role = Objects.requireNonNull(role, "role");
        this.minX = Math.min(minX, maxX); this.maxX = Math.max(minX, maxX);
        this.minY = Math.min(minY, maxY); this.maxY = Math.max(minY, maxY);
        this.minZ = Math.min(minZ, maxZ); this.maxZ = Math.max(minZ, maxZ);
        this.doorX = doorX; this.doorY = doorY; this.doorZ = doorZ;
        this.capacity = Math.max(0, capacity);
        this.registrationDay = registrationDay;
        this.fingerprint = fingerprint;
        this.valid = true;
        this.lastValidatedDay = registrationDay;
    }

    public long id() { return id; }
    public long settlementId() { return settlementId; }
    public String ownerActorKey() { return ownerActorKey; }
    public Role role() { return role; }
    public int minX() { return minX; } public int minY() { return minY; } public int minZ() { return minZ; }
    public int maxX() { return maxX; } public int maxY() { return maxY; } public int maxZ() { return maxZ; }
    public int doorX() { return doorX; } public int doorY() { return doorY; } public int doorZ() { return doorZ; }
    public int capacity() { return capacity; }
    public long registrationDay() { return registrationDay; }
    public long fingerprint() { return fingerprint; }
    public boolean valid() { return valid; }
    public long lastValidatedDay() { return lastValidatedDay; }

    public void setRole(Role role) { this.role = Objects.requireNonNull(role, "role"); }
    public void setCapacity(int capacity) { this.capacity = Math.max(0, capacity); }
    public void markValid(long day, long fingerprint) {
        this.valid = true;
        this.lastValidatedDay = day;
        this.fingerprint = fingerprint;
    }
    public void markInvalid(long day) {
        this.valid = false;
        this.lastValidatedDay = day;
        this.capacity = 0;
    }
    public void restore(boolean valid, int capacity, long fingerprint, long lastValidatedDay, Role role) {
        this.valid = valid;
        this.capacity = Math.max(0, capacity);
        this.fingerprint = fingerprint;
        this.lastValidatedDay = lastValidatedDay;
        this.role = Objects.requireNonNull(role, "role");
    }

    public int housingCredit() {
        if (!valid || role != Role.HOUSE) return 0;
        return capacity;
    }
}
