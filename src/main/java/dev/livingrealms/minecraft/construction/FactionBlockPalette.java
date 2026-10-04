package dev.livingrealms.minecraft.construction;

import dev.livingrealms.minecraft.compat.CompatibleContentRuntime;
import dev.livingrealms.sim.construction.PaletteSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps semantic construction materials to robust vanilla blocks. The palette is deterministic per
 * faction and intentionally avoids block states that need placement context. Create machinery is
 * integrated separately, after the vanilla shell is safe to build.
 *
 * Eight culture families: stone-oak default, martial deepslate/spruce, agrarian sandstone/birch,
 * artistic tuff/dark-oak, mercantile polished-andesite/jungle, coastal prismarine/mangrove,
 * highland cobble/acacia, and scholarly quartz/cherry.
 */
public final class FactionBlockPalette {
    private FactionBlockPalette() {}

    public static BlockState state(long factionId, PaletteSlot slot) {
        return state(factionId, slot, cultureStyle(factionId, 0, 0, 0, 0));
    }

    /**
     * Culture-aware palette: artistic/agrarian/martial/mercantile tradition nudges the deterministic
     * material family so realms read differently without a second city system.
     */
    public static BlockState state(long factionId, PaletteSlot slot, double artistic, double agrarian, double martial) {
        return state(factionId, slot, cultureStyle(factionId, artistic, agrarian, martial, 0));
    }

    public static BlockState state(long factionId, PaletteSlot slot, double artistic, double agrarian, double martial, double mercantile) {
        return state(factionId, slot, cultureStyle(factionId, artistic, agrarian, martial, mercantile));
    }

    /** Exposed for tests and planners that need the resolved family index. */
    public static int cultureStyle(long factionId, double artistic, double agrarian, double martial, double mercantile) {
        int base = Math.floorMod(Long.hashCode(factionId), 8);
        if (artistic <= 0 && agrarian <= 0 && martial <= 0 && mercantile <= 0) return base;
        double max = Math.max(Math.max(martial, agrarian), Math.max(artistic, mercantile));
        if (max <= 0) return base;
        if (martial >= max && martial >= .45) return 1;
        if (agrarian >= max && agrarian >= .45) return 2;
        if (artistic >= max && artistic >= .45) return 3;
        if (mercantile >= max && mercantile >= .45) return 4;
        // Mild trait tilt toward coastal/highland/scholarly families for mid-trait realms.
        if (artistic > .35 && mercantile > .35) return 7;
        if (agrarian > .4 && martial < .35) return 5;
        if (martial > .35 && agrarian < .35) return 6;
        return base;
    }

    private static BlockState state(long factionId, PaletteSlot slot, int style) {
        // Foreign registry blocks are intentionally restricted to decorative/non-structural slots.
        if(slot==PaletteSlot.GLASS||slot==PaletteSlot.FENCE||slot==PaletteSlot.PATH||slot==PaletteSlot.LIGHT||slot==PaletteSlot.DOOR){
            var compatible = CompatibleContentRuntime.decorativeBlock(factionId, slot);
            if (compatible.isPresent()) return compatible.get();
        }
        style = Math.floorMod(style, 8);
        return switch (slot) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case FOUNDATION -> switch (style) {
                case 1 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                case 2 -> Blocks.SANDSTONE.defaultBlockState();
                case 3 -> Blocks.TUFF_BRICKS.defaultBlockState();
                case 4 -> Blocks.POLISHED_ANDESITE.defaultBlockState();
                case 5 -> Blocks.PRISMARINE.defaultBlockState();
                case 6 -> Blocks.COBBLESTONE.defaultBlockState();
                case 7 -> Blocks.QUARTZ_BRICKS.defaultBlockState();
                default -> Blocks.STONE_BRICKS.defaultBlockState();
            };
            case FLOOR -> switch (style) {
                case 1 -> Blocks.SPRUCE_PLANKS.defaultBlockState();
                case 2 -> Blocks.BIRCH_PLANKS.defaultBlockState();
                case 3 -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
                case 4 -> Blocks.JUNGLE_PLANKS.defaultBlockState();
                case 5 -> Blocks.MANGROVE_PLANKS.defaultBlockState();
                case 6 -> Blocks.ACACIA_PLANKS.defaultBlockState();
                case 7 -> Blocks.CHERRY_PLANKS.defaultBlockState();
                default -> Blocks.OAK_PLANKS.defaultBlockState();
            };
            case WALL -> switch (style) {
                case 1 -> Blocks.SPRUCE_PLANKS.defaultBlockState();
                case 2 -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                case 3 -> Blocks.TUFF_BRICKS.defaultBlockState();
                case 4 -> Blocks.POLISHED_ANDESITE.defaultBlockState();
                case 5 -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
                case 6 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                case 7 -> Blocks.SMOOTH_QUARTZ.defaultBlockState();
                default -> Blocks.BRICKS.defaultBlockState();
            };
            case BEAM -> switch (style) {
                case 1 -> Blocks.SPRUCE_LOG.defaultBlockState();
                case 2 -> Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
                case 3 -> Blocks.DARK_OAK_LOG.defaultBlockState();
                case 4 -> Blocks.JUNGLE_LOG.defaultBlockState();
                case 5 -> Blocks.MANGROVE_LOG.defaultBlockState();
                case 6 -> Blocks.ACACIA_LOG.defaultBlockState();
                case 7 -> Blocks.CHERRY_LOG.defaultBlockState();
                default -> Blocks.OAK_LOG.defaultBlockState();
            };
            case ROOF -> switch (style) {
                case 1 -> Blocks.DEEPSLATE_TILES.defaultBlockState();
                case 2 -> Blocks.CUT_SANDSTONE.defaultBlockState();
                case 3 -> Blocks.DARK_PRISMARINE.defaultBlockState();
                case 4 -> Blocks.OXIDIZED_CUT_COPPER.defaultBlockState();
                case 5 -> Blocks.DARK_PRISMARINE.defaultBlockState();
                case 6 -> Blocks.STONE_BRICKS.defaultBlockState();
                case 7 -> Blocks.QUARTZ_BLOCK.defaultBlockState();
                default -> Blocks.BRICKS.defaultBlockState();
            };
            case GLASS -> Blocks.GLASS.defaultBlockState();
            case DOOR -> switch (style) {
                case 1 -> Blocks.SPRUCE_DOOR.defaultBlockState();
                case 2 -> Blocks.BIRCH_DOOR.defaultBlockState();
                case 3 -> Blocks.DARK_OAK_DOOR.defaultBlockState();
                case 4 -> Blocks.JUNGLE_DOOR.defaultBlockState();
                case 5 -> Blocks.MANGROVE_DOOR.defaultBlockState();
                case 6 -> Blocks.ACACIA_DOOR.defaultBlockState();
                case 7 -> Blocks.CHERRY_DOOR.defaultBlockState();
                default -> Blocks.OAK_DOOR.defaultBlockState();
            };
            case FENCE -> switch (style) {
                case 1 -> Blocks.SPRUCE_FENCE.defaultBlockState();
                case 2 -> Blocks.BIRCH_FENCE.defaultBlockState();
                case 3 -> Blocks.DARK_OAK_FENCE.defaultBlockState();
                case 4 -> Blocks.JUNGLE_FENCE.defaultBlockState();
                case 5 -> Blocks.MANGROVE_FENCE.defaultBlockState();
                case 6 -> Blocks.ACACIA_FENCE.defaultBlockState();
                case 7 -> Blocks.CHERRY_FENCE.defaultBlockState();
                default -> Blocks.OAK_FENCE.defaultBlockState();
            };
            case PATH -> Blocks.DIRT_PATH.defaultBlockState();
            case FARMLAND -> Blocks.FARMLAND.defaultBlockState();
            case CROP -> Blocks.WHEAT.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case LIGHT -> switch (style) {
                case 5 -> Blocks.SEA_LANTERN.defaultBlockState();
                default -> Blocks.GLOWSTONE.defaultBlockState();
            };
            case METAL -> CreateBlockLookup.orElse("copper_casing", Blocks.IRON_BLOCK).defaultBlockState();
            case MACHINE -> CreateBlockLookup.orElse("andesite_casing", Blocks.COPPER_BLOCK).defaultBlockState();
            case STORAGE -> Blocks.BARREL.defaultBlockState();
            case RUNWAY -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case REDSTONE_LIGHT -> Blocks.REDSTONE_TORCH.defaultBlockState();
            case BED -> switch (style) {
                case 1 -> Blocks.BLUE_BED.defaultBlockState();
                case 2 -> Blocks.YELLOW_BED.defaultBlockState();
                case 3 -> Blocks.PURPLE_BED.defaultBlockState();
                case 4 -> Blocks.CYAN_BED.defaultBlockState();
                case 5 -> Blocks.LIGHT_BLUE_BED.defaultBlockState();
                case 6 -> Blocks.ORANGE_BED.defaultBlockState();
                case 7 -> Blocks.WHITE_BED.defaultBlockState();
                default -> Blocks.RED_BED.defaultBlockState();
            };
            case TABLE -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case DECORATION -> switch (style) {
                case 2 -> Blocks.POTTED_OXEYE_DAISY.defaultBlockState();
                case 3 -> Blocks.POTTED_AZALEA.defaultBlockState();
                case 5 -> Blocks.POTTED_CORNFLOWER.defaultBlockState();
                case 7 -> Blocks.BOOKSHELF.defaultBlockState();
                default -> Blocks.FLOWER_POT.defaultBlockState();
            };
        };
    }
}
