package dev.livingrealms.api.event;
public record TradeCompleted(long day, long shipmentId, long sellerFactionId, long buyerFactionId, String resource, double amount, double value) implements LifecycleEvent {
    public TradeCompleted { resource = resource == null ? "" : resource; }
}
