package dev.livingrealms.sim.world;

import java.util.Locale;
import java.util.Objects;

/**
 * Sparse wilderness/road feature that is explicitly not a canonical settlement and does not
 * participate in ordinary living-settlement role-pair spacing rules.
 */
public final class RoadsideSite {
    public enum Type {
        WAYSTATION, SHRINE, MILESTONE, TRAVELER_CAMP, HUNTER_CAMP, SHEPHERD_CAMP,
        LOGGING_SITE, TOLL_POST, ABANDONED_CART, BATTLEFIELD_MEMORIAL, GALLOWS
    }

    public enum Lifecycle { ACTIVE, ABANDONED, RUINED, REPAIRED }

    private final long id;
    private Type type;
    private SimPosition position;
    private String name;
    private long relatedSettlementId;
    private long relatedRouteId;
    private final long createdDay;
    private Lifecycle lifecycle;
    private boolean active;

    public RoadsideSite(long id, Type type, SimPosition position, String name,
                        long relatedSettlementId, long relatedRouteId, long createdDay) {
        if (id <= 0) throw new IllegalArgumentException("id");
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.name = clean(name);
        this.relatedSettlementId = Math.max(0, relatedSettlementId);
        this.relatedRouteId = Math.max(0, relatedRouteId);
        this.createdDay = createdDay;
        this.lifecycle = Lifecycle.ACTIVE;
        this.active = true;
    }

    public long id() { return id; }
    public Type type() { return type; }
    public SimPosition position() { return position; }
    public String name() { return name; }
    public long relatedSettlementId() { return relatedSettlementId; }
    public long relatedRouteId() { return relatedRouteId; }
    public long createdDay() { return createdDay; }
    public Lifecycle lifecycle() { return lifecycle; }
    public boolean active() { return active; }

    public void abandon() { lifecycle = Lifecycle.ABANDONED; }
    public void ruin() { lifecycle = Lifecycle.RUINED; active = false; }
    public void repair() { lifecycle = Lifecycle.REPAIRED; active = true; }
    public void deactivate() { active = false; }

    /** Main-thread deterministic alignment hook for starter corridor/worldgen reconciliation. */
    public void relocate(SimPosition position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    public void restore(Lifecycle lifecycle, boolean active) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.active = active;
    }

    private static String clean(String name) {
        String n = name == null ? "Roadside" : name.strip();
        if (n.isBlank()) n = "Roadside";
        if (n.length() > 48) n = n.substring(0, 48).strip();
        return n;
    }

    public static String defaultName(Type type, SimPosition pos) {
        String label = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1)
                + " @" + Math.round(pos.x()) + "," + Math.round(pos.z());
    }
}
