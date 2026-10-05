package dev.livingrealms.api;
public interface IndustryAdapter {
    String resolveBlockPath(String preferredCreatePath, String vanillaFallbackPath);
    boolean createAvailable();
}
