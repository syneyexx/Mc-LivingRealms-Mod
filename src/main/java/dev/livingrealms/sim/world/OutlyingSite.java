package dev.livingrealms.sim.world;

import java.util.Locale;
import java.util.Objects;

/**
 * Non-settlement site attached to a host settlement. Used for foreign hamlets inside the
 * 2000-block spacing belt, farms, camps, and similar outlying places that must not count as
 * canonical settlements for spacing rules.
 */
public final class OutlyingSite {
    public enum Type {
        FOREIGN_HAMLET, FARMSTEAD, MINING_CAMP, ROAD_STATION, MILITARY_POST, REFUGEE_CAMP
    }

    private final long id;
    private final long settlementId;
    private final long factionId;
    private Type type;
    private SimPosition position;
    private String name;
    private int representedPopulation;
    private double housingCredit;
    private boolean foreign;
    private boolean active;

    public OutlyingSite(long id, long settlementId, long factionId, Type type, SimPosition position,
                        String name, int representedPopulation, double housingCredit, boolean foreign) {
        if (id <= 0) throw new IllegalArgumentException("id");
        if (settlementId <= 0) throw new IllegalArgumentException("settlementId");
        if (factionId <= 0) throw new IllegalArgumentException("factionId");
        this.id = id;
        this.settlementId = settlementId;
        this.factionId = factionId;
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.name = clean(name);
        this.representedPopulation = Math.max(0, representedPopulation);
        this.housingCredit = Math.max(0, housingCredit);
        this.foreign = foreign;
        this.active = true;
    }

    public long id() { return id; }
    public long settlementId() { return settlementId; }
    public long factionId() { return factionId; }
    public Type type() { return type; }
    public SimPosition position() { return position; }
    public String name() { return name; }
    public int representedPopulation() { return representedPopulation; }
    public double housingCredit() { return housingCredit; }
    public boolean foreign() { return foreign; }
    public boolean active() { return active; }

    public void setType(Type type) { this.type = Objects.requireNonNull(type, "type"); }
    public void setPosition(SimPosition position) { this.position = Objects.requireNonNull(position, "position"); }
    public void rename(String name) { this.name = clean(name); }
    public void setRepresentedPopulation(int value) { representedPopulation = Math.max(0, value); }
    public void setHousingCredit(double value) { housingCredit = Math.max(0, value); }
    public void deactivate() { active = false; }
    public void restoreActive(boolean value) { active = value; }

    private static String clean(String name) {
        String n = name == null ? "Outlying Site" : name.strip();
        if (n.isBlank()) n = "Outlying Site";
        if (n.length() > 48) n = n.substring(0, 48).strip();
        return n;
    }

    public static String defaultName(Type type, SimPosition pos) {
        String label = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1)
                + " @" + Math.round(pos.x()) + "," + Math.round(pos.z());
    }
}
