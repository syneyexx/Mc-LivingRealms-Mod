package dev.livingrealms.sim.underworld;

import java.util.Objects;

/** One lot of stolen goods held on the server ledger until sold through the black market. */
public final class StolenGoodsEntry {
    private final long id;
    private final String actorKey;
    private final String goodKey;
    private final double value;
    private final int quantity;
    private final long sourceCrimeId;
    private final long acquiredDay;
    private boolean sold;
    private long soldDay = -1;
    private double salePrice;

    public StolenGoodsEntry(long id, String actorKey, String goodKey, double value, int quantity,
                            long sourceCrimeId, long acquiredDay) {
        if (id <= 0) throw new IllegalArgumentException("id");
        if (actorKey == null || actorKey.isBlank()) throw new IllegalArgumentException("actorKey");
        if (goodKey == null || goodKey.isBlank()) throw new IllegalArgumentException("goodKey");
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("value");
        if (quantity <= 0) throw new IllegalArgumentException("quantity");
        if (sourceCrimeId < 0) throw new IllegalArgumentException("sourceCrimeId");
        if (acquiredDay < 0) throw new IllegalArgumentException("acquiredDay");
        this.id = id;
        this.actorKey = actorKey;
        this.goodKey = goodKey;
        this.value = value;
        this.quantity = quantity;
        this.sourceCrimeId = sourceCrimeId;
        this.acquiredDay = acquiredDay;
    }

    public long id() { return id; }
    public String actorKey() { return actorKey; }
    public String goodKey() { return goodKey; }
    public double value() { return value; }
    public int quantity() { return quantity; }
    public long sourceCrimeId() { return sourceCrimeId; }
    public long acquiredDay() { return acquiredDay; }
    public boolean sold() { return sold; }
    public long soldDay() { return soldDay; }
    public double salePrice() { return salePrice; }

    public boolean sell(long day, double price) {
        if (sold) return false;
        if (!Double.isFinite(price) || price < 0) throw new IllegalArgumentException("price");
        sold = true;
        soldDay = Math.max(0, day);
        salePrice = price;
        return true;
    }

    public void restore(boolean sold, long soldDay, double salePrice) {
        this.sold = sold;
        this.soldDay = soldDay;
        this.salePrice = Double.isFinite(salePrice) ? Math.max(0, salePrice) : 0;
    }

    public static String goodKeyForCrime(dev.livingrealms.sim.law.CrimeType type, String evidence) {
        Objects.requireNonNull(type, "type");
        String base = "stolen:" + type.name().toLowerCase();
        if (evidence == null || evidence.isBlank()) return base;
        String trimmed = evidence.length() > 48 ? evidence.substring(0, 48) : evidence;
        return base + ":" + trimmed.replace(' ', '_');
    }
}
