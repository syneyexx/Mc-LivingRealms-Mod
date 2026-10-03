package dev.livingrealms.sim.player;

public record FactionJoinResult(boolean success,String reason,FactionRank rank) {
    public FactionJoinResult {
        if(reason==null||reason.isBlank()) throw new IllegalArgumentException("reason");
        if(rank==null) throw new IllegalArgumentException("rank");
    }
}
