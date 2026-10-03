package dev.livingrealms.sim.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resumable physical construction job. */
public final class ConstructionJob {
    private final ConstructionIntent intent;
    private final List<BuildOperation> operations;
    private int cursor;

    public ConstructionJob(ConstructionIntent intent, int groundY) {
        this(intent, resolve(intent, groundY), 0);
    }

    public ConstructionJob(ConstructionIntent intent, List<BuildOperation> operations, int cursor) {
        this.intent = Objects.requireNonNull(intent, "intent");
        this.operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        if (cursor < 0 || cursor > operations.size()) throw new IllegalArgumentException("cursor");
        this.cursor = cursor;
    }

    public String key() { return intent.settlementId()+":"+intent.key(); }
    public ConstructionIntent intent() { return intent; }
    public int cursor() { return cursor; }
    public int operationCount() { return operations.size(); }
    public int remaining() { return operations.size()-cursor; }
    public boolean complete() { return cursor >= operations.size(); }
    public BuildOperation current() { if(complete()) throw new IllegalStateException("job complete"); return operations.get(cursor); }
    public void advance() { if(!complete()) cursor++; }
    public List<BuildOperation> operations() { return operations; }

    private static List<BuildOperation> resolve(ConstructionIntent intent,int groundY) {
        StructureBlueprint blueprint=StructureBlueprintFactory.create(intent);
        int cx=(int)Math.round(intent.center().x());
        int cz=(int)Math.round(intent.center().z());
        int turns=Math.floorMod(intent.rotationQuarterTurns(),4);
        List<BuildOperation> out=new ArrayList<>(blueprint.operationCount());
        for(BlockPlacement p:blueprint.placements()) {
            int rx=p.dx(), rz=p.dz();
            for(int i=0;i<turns;i++){int t=rx;rx=-rz;rz=t;}
            out.add(new BuildOperation(cx+rx,groundY+p.dy(),cz+rz,p.slot(),p.phase()));
        }
        return out;
    }
}
