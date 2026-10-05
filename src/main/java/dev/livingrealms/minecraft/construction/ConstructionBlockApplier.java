package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.LivingRealmsSavedData;
import dev.livingrealms.minecraft.SimulationRuntime;
import dev.livingrealms.sim.civilization.FactionCivilizationState;
import dev.livingrealms.sim.construction.AuthoredBlockLedger;
import dev.livingrealms.sim.construction.AuthoredOwnerType;
import dev.livingrealms.sim.construction.BuildApplyResult;
import dev.livingrealms.sim.construction.BuildOperation;
import dev.livingrealms.sim.construction.BlockPlacement;
import dev.livingrealms.sim.construction.ConstructionIntent;
import dev.livingrealms.sim.construction.ConstructionJob;
import dev.livingrealms.sim.construction.ConstructionQueue;
import dev.livingrealms.sim.construction.ConstructionRetryKey;
import dev.livingrealms.sim.construction.EntranceAccessPlanner;
import dev.livingrealms.sim.construction.HousingCapacity;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.construction.PhysicalDevelopmentReconciler;
import dev.livingrealms.sim.construction.ResolvedBuildSite;
import dev.livingrealms.sim.construction.SettlementParcelPlanner;
import dev.livingrealms.sim.construction.SettlementPlanCache;
import dev.livingrealms.sim.construction.StructureAccessValidator;
import dev.livingrealms.sim.construction.StructureBlueprintFactory;
import dev.livingrealms.sim.construction.StructureGeometryRules;
import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.construction.WizardTreesPlanner;
import dev.livingrealms.sim.compat.ModCompatibilityPolicy;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
import dev.livingrealms.sim.runtime.ProjectionBudget;
import dev.livingrealms.sim.world.WizardTreesSeeder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Projects strategic settlement blueprints into loaded Overworld chunks under a hard per-tick
 * operation budget. Canonical completion remains in the simulation save; the queue is disposable.
 */

import static dev.livingrealms.minecraft.construction.SettlementConstructionMaterializer.isWizardRole;

/**
 * Provenance-aware block application for settlement construction jobs.
 * Queueing, discovery, terrain resolution, and canonical completion remain in the materializer.
 */
final class ConstructionBlockApplier {
    private ConstructionBlockApplier() {}

    static BuildApplyResult apply(ServerLevel level,ConstructionJob job,BuildOperation operation,AuthoredBlockLedger ledger) {
        BlockPos pos=new BlockPos(operation.x(),operation.y(),operation.z());
        if(pos.getY()<=level.getMinBuildHeight() || pos.getY()>=level.getMaxBuildHeight()-1) {
            return StructureGeometryRules.isRequiredGeometry(operation.slot(),operation.phase())
                    ? BuildApplyResult.TERMINALLY_IMPOSSIBLE : BuildApplyResult.SAFELY_IGNORED;
        }
        if(!level.hasChunkAt(pos)) return BuildApplyResult.DEFERRED_UNLOADED;

        AuthoredOwnerType owner=AuthoredOwnerType.forStructureRole(job.intent().role());
        BlockState current=level.getBlockState(pos);
        // Hard stop: unknown block entities / machines / containers are never overwritten.
        WorldMutationGuard.Classification cls=WorldMutationGuard.classify(level,pos,current,ledger,owner);
        if(cls==WorldMutationGuard.Classification.BLOCK_ENTITY || cls==WorldMutationGuard.Classification.AUTHORED_FOREIGN_OWNER) {
            return StructureGeometryRules.isRequiredGeometry(operation.slot(),operation.phase())
                    ? BuildApplyResult.OBSTRUCTED_PROTECTED : BuildApplyResult.SAFELY_IGNORED;
        }

        if(operation.slot()==PaletteSlot.DOOR){
            return applyDoor(level,job,pos,current,ledger,owner);
        }
        if(operation.slot()==PaletteSlot.BED){
            return applyBed(level,job,pos,current,ledger,owner);
        }

        BlockState target=paletteState(level,job,operation.slot());
        if(current.equals(target)) {
            // Physically satisfied — do NOT claim unknown/player-placed identical blocks as LR-authored.
            return BuildApplyResult.ALREADY_CORRECT;
        }

        boolean allowTerrain=operation.slot()==PaletteSlot.FOUNDATION||operation.slot()==PaletteSlot.PATH
                ||operation.slot()==PaletteSlot.FARMLAND||operation.slot()==PaletteSlot.RUNWAY;
        boolean allowTreeLogs=operation.slot()==PaletteSlot.AIR||allowTerrain;
        if(operation.slot()==PaletteSlot.AIR) {
            if(current.isAir()) return BuildApplyResult.ALREADY_CORRECT;
            boolean wizardOk=isWizardRole(job.intent().role())&&safeWizardExcavate(current);
            WorldMutationGuard.Decision clear=WorldMutationGuard.evaluateClear(level,pos,current,ledger,owner,allowTreeLogs);
            if(!clear.mayMutate()&&!wizardOk) {
                return StructureGeometryRules.isRequiredGeometry(operation.slot(),operation.phase())
                        ? BuildApplyResult.OBSTRUCTED_PROTECTED : BuildApplyResult.SAFELY_IGNORED;
            }
        } else {
            WorldMutationGuard.Decision replace=WorldMutationGuard.evaluateReplace(level,pos,current,ledger,owner,allowTerrain,allowTreeLogs);
            // Legacy foreign building-mod materials may still be replaced inside the active LR footprint
            // when they are not player-vanilla palette lookalikes claimed without provenance.
            boolean legacyForeign=insideActiveJobFootprint(job,pos)&&oldForeignConstructionMaterial(current)
                    &&!current.hasBlockEntity()&&ledger.ownerType(pos.getX(),pos.getY(),pos.getZ())==null;
            if(!replace.mayMutate()&&!legacyForeign) {
                return StructureGeometryRules.isRequiredGeometry(operation.slot(),operation.phase())
                        ? BuildApplyResult.OBSTRUCTED_PROTECTED : BuildApplyResult.SAFELY_IGNORED;
            }
        }

        if(!ledger.canRecord(pos.getX(),pos.getY(),pos.getZ())) return BuildApplyResult.RETRYABLE;
        BlockState previous=current;
        int flags=Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
        if(!level.setBlock(pos,target,flags)) return BuildApplyResult.FAILED;
        if(!ledger.record(pos.getX(),pos.getY(),pos.getZ(),owner)) {
            level.setBlock(pos,previous,flags);
            return BuildApplyResult.FAILED;
        }
        return BuildApplyResult.APPLIED;
    }

    private static BlockState paletteState(ServerLevel level,ConstructionJob job,PaletteSlot slot){
        FactionCivilizationState civ=SimulationRuntime.data(level.getServer()).state().findFactionCivilization(job.intent().factionId()).orElse(null);
        if(civ==null)return FactionBlockPalette.state(job.intent().factionId(),slot);
        return FactionBlockPalette.state(job.intent().factionId(),slot,civ.artisticTradition(),civ.agrarianTradition(),civ.martialTradition(),civ.mercantileTradition());
    }

    private static BuildApplyResult applyDoor(ServerLevel level,ConstructionJob job,BlockPos pos,BlockState current,AuthoredBlockLedger ledger,AuthoredOwnerType owner){
        BlockState doorBase=paletteState(level,job,PaletteSlot.DOOR);
        if(!(doorBase.getBlock() instanceof DoorBlock)){
            if(current.isAir())return BuildApplyResult.ALREADY_CORRECT;
            if(!WorldMutationGuard.trySetAuthored(level,pos,Blocks.AIR.defaultBlockState(),ledger,owner,false,true))
                return BuildApplyResult.OBSTRUCTED_PROTECTED;
            return BuildApplyResult.APPLIED;
        }
        Direction facing=doorFacing(job.intent().rotationQuarterTurns());
        BlockState below=level.getBlockState(pos.below());
        boolean upper=below.getBlock() instanceof DoorBlock && below.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER
                && below.getValue(DoorBlock.FACING)==facing;
        BlockState target=doorBase
                .setValue(DoorBlock.FACING,facing)
                .setValue(DoorBlock.HALF,upper?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.OPEN,false)
                .setValue(DoorBlock.POWERED,false);
        if(current.equals(target)){
            // Geometry satisfied without provenance adoption.
            return BuildApplyResult.ALREADY_CORRECT;
        }
        boolean authoredDoor=current.getBlock() instanceof DoorBlock
                && AuthoredOwnerType.allowsOverwrite(ledger.ownerType(pos.getX(),pos.getY(),pos.getZ()),owner);
        WorldMutationGuard.Decision clear=WorldMutationGuard.evaluateClear(level,pos,current,ledger,owner,true);
        if(!current.isAir()&&!current.canBeReplaced()&&!clear.mayMutate()&&!authoredDoor)return BuildApplyResult.OBSTRUCTED_PROTECTED;
        if(!ledger.canRecord(pos.getX(),pos.getY(),pos.getZ()))return BuildApplyResult.RETRYABLE;
        BlockState previous=current;
        boolean ok=level.setBlock(pos,target,Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
        if(!ok)return BuildApplyResult.FAILED;
        if(!ledger.record(pos.getX(),pos.getY(),pos.getZ(),owner)){
            level.setBlock(pos,previous,Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            return BuildApplyResult.FAILED;
        }
        if(!upper){
            BlockPos up=pos.above();
            BlockState upCur=level.getBlockState(up);
            boolean upAuthored=upCur.getBlock() instanceof DoorBlock
                    && AuthoredOwnerType.allowsOverwrite(ledger.ownerType(up.getX(),up.getY(),up.getZ()),owner);
            WorldMutationGuard.Decision upClear=WorldMutationGuard.evaluateClear(level,up,upCur,ledger,owner,true);
            if(upCur.isAir()||upCur.canBeReplaced()||upClear.mayMutate()||upAuthored){
                BlockState upperState=doorBase.setValue(DoorBlock.FACING,facing).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER).setValue(DoorBlock.OPEN,false).setValue(DoorBlock.POWERED,false);
                WorldMutationGuard.trySetAuthored(level,up,upperState,ledger,owner,false,true);
            }
        }
        return BuildApplyResult.APPLIED;
    }

    /** Player approaches the front (-Z local) looking toward +Z before rotation → SOUTH at rot 0. */
    private static Direction doorFacing(int quarterTurns){
        Direction[] order={Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST};
        return order[Math.floorMod(quarterTurns,4)];
    }

    /**
     * Places a complete two-block Minecraft bed. Blueprint emits foot then head along local +Z;
     * after house rotation that becomes {@link #doorFacing}'s axis. Foot placement also writes HEAD.
     */
    private static BuildApplyResult applyBed(ServerLevel level,ConstructionJob job,BlockPos pos,BlockState current,AuthoredBlockLedger ledger,AuthoredOwnerType owner){
        BlockState bedBase=paletteState(level,job,PaletteSlot.BED);
        if(!(bedBase.getBlock() instanceof BedBlock))return BuildApplyResult.SAFELY_IGNORED;
        Direction facing=doorFacing(job.intent().rotationQuarterTurns());
        BlockPos behind=pos.relative(facing.getOpposite());
        BlockState behindState=level.getBlockState(behind);
        boolean weAreHead=behindState.getBlock() instanceof BedBlock && behindState.hasProperty(BedBlock.PART)
                && behindState.getValue(BedBlock.PART)==BedPart.FOOT;
        BlockState target=bedBase.setValue(BedBlock.FACING,facing).setValue(BedBlock.PART,weAreHead?BedPart.HEAD:BedPart.FOOT);
        if(current.equals(target))return BuildApplyResult.ALREADY_CORRECT;
        WorldMutationGuard.Decision replace=WorldMutationGuard.evaluateReplace(level,pos,current,ledger,owner,false,false);
        boolean authoredBed=current.getBlock() instanceof BedBlock
                && AuthoredOwnerType.allowsOverwrite(ledger.ownerType(pos.getX(),pos.getY(),pos.getZ()),owner);
        if(!replace.mayMutate()&&!authoredBed&&!current.isAir()&&!current.canBeReplaced())
            return BuildApplyResult.SAFELY_IGNORED;
        if(!ledger.canRecord(pos.getX(),pos.getY(),pos.getZ()))return BuildApplyResult.RETRYABLE;
        BlockState previous=current;
        if(!level.setBlock(pos,target,Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS))return BuildApplyResult.FAILED;
        if(!ledger.record(pos.getX(),pos.getY(),pos.getZ(),owner)){
            level.setBlock(pos,previous,Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
            return BuildApplyResult.FAILED;
        }
        if(!weAreHead){
            BlockPos headPos=pos.relative(facing);
            BlockState headCur=level.getBlockState(headPos);
            WorldMutationGuard.Decision headClear=WorldMutationGuard.evaluateClear(level,headPos,headCur,ledger,owner,false);
            boolean headOk=headCur.isAir()||headCur.canBeReplaced()||headClear.mayMutate()
                    ||(headCur.getBlock() instanceof BedBlock && AuthoredOwnerType.allowsOverwrite(ledger.ownerType(headPos.getX(),headPos.getY(),headPos.getZ()),owner));
            if(headOk){
                BlockState head=bedBase.setValue(BedBlock.FACING,facing).setValue(BedBlock.PART,BedPart.HEAD);
                WorldMutationGuard.trySetAuthored(level,headPos,head,ledger,owner,false,false);
            }
        }
        return BuildApplyResult.APPLIED;
    }

    private static boolean safeWizardExcavate(BlockState state){Block b=state.getBlock();return state.is(BlockTags.BASE_STONE_OVERWORLD)||b==Blocks.DIRT||b==Blocks.COARSE_DIRT||b==Blocks.ROOTED_DIRT||b==Blocks.GRAVEL||b==Blocks.CLAY||b==Blocks.MUD||b==Blocks.SAND||b==Blocks.RED_SAND;}

    /**
     * Non-vanilla building-mod materials previously placed by Living Realms adapters.
     * Explicitly excludes common vanilla palette lookalikes so player stone-brick/plank builds are never free real estate.
     */
    private static boolean oldForeignConstructionMaterial(BlockState state){
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if(id==null||id.getNamespace().equals("minecraft")||id.getNamespace().equals("create"))return false;
        return ModCompatibilityPolicy.find(id.getNamespace()).map(e->e.usableByLivingWorld()&&(e.category()==ModCompatibilityPolicy.Category.BUILDING||e.category()==ModCompatibilityPolicy.Category.CONTENT)).orElse(false);
    }

    private static boolean insideActiveJobFootprint(ConstructionJob job,BlockPos pos){
        ConstructionIntent intent=job.intent();
        int turns=Math.floorMod(intent.rotationQuarterTurns(),4);
        int w=(turns&1)==0?intent.width():intent.depth();
        int d=(turns&1)==0?intent.depth():intent.width();
        int cx=(int)Math.round(intent.center().x()),cz=(int)Math.round(intent.center().z());
        int margin=2;
        return Math.abs(pos.getX()-cx)<=w/2+margin && Math.abs(pos.getZ()-cz)<=d/2+margin;
    }
}
