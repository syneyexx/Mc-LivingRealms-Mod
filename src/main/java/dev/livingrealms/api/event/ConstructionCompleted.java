package dev.livingrealms.api.event;
public record ConstructionCompleted(long day, long settlementId, String constructionKey, String origin) implements LifecycleEvent {
    public ConstructionCompleted { constructionKey = constructionKey == null ? "" : constructionKey; origin = origin == null ? "" : origin; }
}
