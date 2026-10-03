package dev.livingrealms.sim.world;
import java.util.*;
public final class WorldHistory {
    private final ArrayDeque<WorldEvent> events=new ArrayDeque<>(); private final int maxEvents;
    public WorldHistory(int maxEvents){this.maxEvents=Math.max(100,maxEvents);} public void add(WorldEvent e){events.addLast(e);while(events.size()>maxEvents)events.removeFirst();}
    public List<WorldEvent> recent(int count){List<WorldEvent> all=new ArrayList<>(events);int start=Math.max(0,all.size()-count);return List.copyOf(all.subList(start,all.size()));}
    public List<WorldEvent> all(){return List.copyOf(events);}
}
