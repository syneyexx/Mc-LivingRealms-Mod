package dev.livingrealms.sim.law;
public record CrimeResult(boolean registered,double bountyAdded,double notorietyAdded,WantedLevel wantedLevel,String reason) {}
