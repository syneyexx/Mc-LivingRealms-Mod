package dev.livingrealms.api;
public interface NpcAdapter {
    boolean isAdoptable(String entityTypeId);
    String inferRoleKey(String entityTypeId, String displayName);
}
