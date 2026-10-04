package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.compat.CompatibleContentRuntime;
import dev.livingrealms.sim.construction.PaletteSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps semantic construction materials to robust vanilla blocks. The palette is deterministic per
 * faction and intentionally avoids block states that need placement context. Create machinery is
 * integrated separately, after the vanilla shell is safe to build.
 */
public final class FactionBlockPalette {
    private FactionBlockPalette() {}

    public static BlockState state(long factionId, PaletteSlot slot) {
        return state(factionId, slot, cultureStyle(factionId, 0, 0, 0));
    }

    /**
     * Culture-aware palette: artistic/agrarian/martial tradition nudges the deterministic wood/stone
     * family so realms read differently without a second city system.
     */
    public static BlockState state(long factionId, PaletteSlot slot, double artistic, double agrarian, double martial) {
        return state(factionId, slot, cultureStyle(factionId, artistic, agrarian, martial));
    }

    private static int cultureStyle(long factionId, double artistic, double agrarian, double martial) {
        int base = Math.floorMod(Long.hashCode(factionId), 4);
        if (artistic <= 0 && agrarian <= 0 && martial <= 0) return base;
        if (martial >= artistic && martial >= agrarian) return 1; // deepslate/spruce martial look
        if (agrarian >= artistic) return 2; // sandstone/birch agrarian look
        if (artistic > .55) return 3; // dark oak / tuff artistic look
        return base;
    }

    private static BlockState state(long factionId, PaletteSlot slot, int style) {
        // Foreign registry blocks are intentionally restricted to decorative/non-structural slots.
        // Earlier RC4 builds allowed arbitrary mod blocks to become walls/beams/roofs, which could
        // produce visually broken towers or context-sensitive blocks repeated hundreds of times.
        if(slot==PaletteSlot.GLASS||slot==PaletteSlot.FENCE||slot==PaletteSlot.PATH||slot==PaletteSlot.LIGHT||slot==PaletteSlot.DOOR){
            var compatible=CompatibleContentRuntime.decorativeBlock(factionId,slot);
            if(compatible.isPresent())return compatible.get();
        }
        style=Math.floorMod(style,4);
        return switch(slot) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case FOUNDATION -> switch(style) {
                case 1 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                case 2 -> Blocks.SANDSTONE.defaultBlockState();
                case 3 -> Blocks.TUFF_BRICKS.defaultBlockState();
                default -> Blocks.STONE_BRICKS.defaultBlockState();
            };
            case FLOOR -> switch(style) {
                case 1 -> Blocks.SPRUCE_PLANKS.defaultBlockState();
                case 2 -> Blocks.BIRCH_PLANKS.defaultBlockState();
                case 3 -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
                default -> Blocks.OAK_PLANKS.defaultBlockState();
            };
            case WALL -> switch(style) {
                case 1 -> Blocks.SPRUCE_PLANKS.defaultBlockState();
                case 2 -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                case 3 -> Blocks.TUFF_BRICKS.defaultBlockState();
                default -> Blocks.BRICKS.defaultBlockState();
            };
            case BEAM -> switch(style) {
                case 1 -> Blocks.SPRUCE_LOG.defaultBlockState();
                case 2 -> Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
                case 3 -> Blocks.DARK_OAK_LOG.defaultBlockState();
                default -> Blocks.OAK_LOG.defaultBlockState();
            };
            case ROOF -> switch(style) {
                case 1 -> Blocks.DEEPSLATE_TILES.defaultBlockState();
                case 2 -> Blocks.CUT_SANDSTONE.defaultBlockState();
                case 3 -> Blocks.DARK_PRISMARINE.defaultBlockState();
                default -> Blocks.BRICKS.defaultBlockState();
            };
            case GLASS -> Blocks.GLASS.defaultBlockState();
            // Real door blocks; facing/half are finalized in SettlementConstructionMaterializer.apply.
            case DOOR -> switch(style) {
                case 1 -> Blocks.SPRUCE_DOOR.defaultBlockState();
                case 2 -> Blocks.BIRCH_DOOR.defaultBlockState();
                case 3 -> Blocks.DARK_OAK_DOOR.defaultBlockState();
                default -> Blocks.OAK_DOOR.defaultBlockState();
            };
            case FENCE -> switch(style) {
                case 1 -> Blocks.SPRUCE_FENCE.defaultBlockState();
                case 2 -> Blocks.BIRCH_FENCE.defaultBlockState();
                case 3 -> Blocks.DARK_OAK_FENCE.defaultBlockState();
                default -> Blocks.OAK_FENCE.defaultBlockState();
            };
            case PATH -> Blocks.DIRT_PATH.defaultBlockState();
            case FARMLAND -> Blocks.FARMLAND.defaultBlockState();
            case CROP -> Blocks.WHEAT.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case LIGHT -> Blocks.GLOWSTONE.defaultBlockState();
            case METAL -> CreateBlockLookup.orElse("copper_casing",Blocks.IRON_BLOCK).defaultBlockState();
            case MACHINE -> CreateBlockLookup.orElse("andesite_casing",Blocks.COPPER_BLOCK).defaultBlockState();
            case STORAGE -> Blocks.BARREL.defaultBlockState();
            case RUNWAY -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case REDSTONE_LIGHT -> Blocks.REDSTONE_TORCH.defaultBlockState();
        };
    }
}
