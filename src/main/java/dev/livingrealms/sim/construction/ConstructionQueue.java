package dev.livingrealms.sim.construction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fair, resumable construction scheduler. The budget counts attempts, not just changed blocks, so
 * already-correct terrain cannot create an unbounded server-tick scan.
 *
 * <p>A job only enters {@link BuildTickResult#completedJobKeys()} when its
 * {@link StructureMaterializationReceipt} reports physically acceptable required geometry.
 * Cursor-finished but obstructed jobs are rejected instead of being marked canonically complete.
 */
public final class ConstructionQueue {
    @FunctionalInterface public interface Executor { BuildApplyResult apply(ConstructionJob job, BuildOperation operation); }

    private final Map<String, ConstructionJob> jobs = new LinkedHashMap<>();

    public boolean enqueue(ConstructionJob job) {
        Objects.requireNonNull(job,"job");
        if(job.complete()) return false;
        return jobs.putIfAbsent(job.key(),job)==null;
    }

    public int size(){return jobs.size();}
    public boolean isEmpty(){return jobs.isEmpty();}
    public void clear(){jobs.clear();}
    public List<ConstructionJob> jobs(){return List.copyOf(jobs.values());}

    public BuildTickResult tick(int maxOperations, Executor executor) {
        if(maxOperations<=0) throw new IllegalArgumentException("maxOperations");
        Objects.requireNonNull(executor,"executor");
        int attempted=0,applied=0,skipped=0,blocked=0;
        List<String> completed=new ArrayList<>();
        List<String> rejected=new ArrayList<>();
        List<ConstructionJob> ordered=new ArrayList<>(jobs.values());
        ordered.sort(Comparator.comparingInt((ConstructionJob j)->j.intent().priority()).reversed().thenComparing(ConstructionJob::key));

        boolean progress=true;
        while(attempted<maxOperations && progress && !ordered.isEmpty()) {
            progress=false;
            for(ConstructionJob job:ordered) {
                if(attempted>=maxOperations) break;
                if(job.complete()) continue;
                attempted++;
                BuildApplyResult result=Objects.requireNonNull(executor.apply(job,job.current()),"executor result");
                boolean beforeComplete=job.complete();
                job.recordResult(result);
                if(result.countsAsAppliedWrite()) applied++;
                else if(result.satisfiesRequired() || result==BuildApplyResult.SAFELY_IGNORED || result==BuildApplyResult.SKIPPED) skipped++;
                else if(result.isDeferral()) blocked++;
                if(result.advancesCursor()) progress=true;
                if(!beforeComplete && job.complete()) {
                    if(job.physicallyComplete()) {
                        if(!completed.contains(job.key())) completed.add(job.key());
                    } else if(!rejected.contains(job.key())) {
                        rejected.add(job.key());
                    }
                }
            }
        }
        for(String key:completed) jobs.remove(key);
        for(String key:rejected) jobs.remove(key);
        return new BuildTickResult(attempted,applied,skipped,blocked,completed,rejected);
    }
}
