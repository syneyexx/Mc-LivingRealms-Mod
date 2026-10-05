package dev.livingrealms.minecraft.civilization;

import dev.livingrealms.sim.civilization.AssistanceContributionEngine;
import dev.livingrealms.sim.civilization.AssistanceTask;
import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.Comparator;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Physical bridge: player inventory → verified assistance contribution.
 * Dialogue/commands may advertise tasks; this class never invents completion without inventory drain.
 */
public final class PlayerAssistanceRuntime {
    private PlayerAssistanceRuntime() {}

    public record Result(boolean success, boolean dirty, String message) {
        public Result { message = message == null ? "" : message; }
    }

    public static Result contributeNearest(SimulationState state, String actorKey, ServerPlayer player, SimPosition position) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(player);
        Objects.requireNonNull(position);
        AssistanceTask task = state.assistanceTasks().stream()
                .filter(AssistanceTask::active)
                .filter(t -> state.findSettlement(t.settlementId()).map(s -> s.position().distanceTo(position) <= AssistanceContributionEngine.CONTRIBUTION_RADIUS).orElse(false))
                .max(Comparator.comparingDouble(AssistanceTask::remainingPressure).thenComparingLong(AssistanceTask::id))
                .orElse(null);
        if (task == null) return new Result(false, false, "No open assistance task nearby.");
        return contribute(state, actorKey, player, position, task.id());
    }

    public static Result contribute(SimulationState state, String actorKey, ServerPlayer player, SimPosition position, long taskId) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(player);
        Objects.requireNonNull(position);
        AssistanceTask task = state.assistanceTasks().stream().filter(t -> t.id() == taskId).findFirst().orElse(null);
        if (task == null || !task.active()) return new Result(false, false, "Task is not open.");
        ResourceType resource = AssistanceContributionEngine.requiredResource(task.type());
        Item item = item(resource);
        if (item == null) return new Result(false, false, "Unsupported aid commodity.");
        int units = (int) Math.round(AssistanceContributionEngine.PACKAGE_UNITS);
        Inventory inventory = player.getInventory();
        if (count(inventory, item) < units) {
            return new Result(false, false, "Need " + units + " " + itemName(item) + " for " + task.type().name().toLowerCase() + ".");
        }
        if (remove(inventory, item, units) != units) return new Result(false, false, "Inventory changed.");
        var committed = AssistanceContributionEngine.contributeVerified(state, actorKey, position, taskId, units);
        if (!committed.success()) {
            add(inventory, item, units);
            return new Result(false, false, "Could not apply aid: " + committed.reason());
        }
        String status = committed.status().name().toLowerCase();
        return new Result(true, true, "Delivered " + units + " " + itemName(item) + " toward task #" + taskId
                + " (" + task.type().name().toLowerCase() + "). Status: " + status + ".");
    }

    public static String describeNearby(SimulationState state, SimPosition position) {
        return state.assistanceTasks().stream()
                .filter(AssistanceTask::active)
                .filter(t -> state.findSettlement(t.settlementId()).map(s -> s.position().distanceTo(position) <= AssistanceContributionEngine.CONTRIBUTION_RADIUS).orElse(false))
                .sorted(Comparator.comparingDouble(AssistanceTask::remainingPressure).reversed())
                .limit(6)
                .map(t -> {
                    String settlement = state.findSettlement(t.settlementId()).map(s -> s.name()).orElse("Settlement");
                    ResourceType r = AssistanceContributionEngine.requiredResource(t.type());
                    return "#" + t.id() + " " + t.type().name().toLowerCase() + " @ " + settlement
                            + " • bring " + (int) Math.round(AssistanceContributionEngine.PACKAGE_UNITS) + " "
                            + r.name().toLowerCase() + " • pressure " + Math.round(t.remainingPressure() * 100) + "%";
                })
                .reduce((a, b) -> a + "\n" + b)
                .orElse("No open assistance tasks nearby.");
    }

    private static Item item(ResourceType r) {
        return switch (r) {
            case FOOD, BREAD -> Items.BREAD;
            case GRAIN, FLOUR -> Items.WHEAT;
            case MEAT -> Items.BEEF;
            case ALE -> Items.HONEY_BOTTLE;
            case WOOL, TEXTILES -> Items.WHITE_WOOL;
            case WOOD -> Items.OAK_LOG;
            case STONE -> Items.COBBLESTONE;
            case IRON -> Items.IRON_INGOT;
            case COAL -> Items.COAL;
            case COPPER -> Items.COPPER_INGOT;
            case GOLD -> Items.GOLD_INGOT;
            case FUEL -> Items.CHARCOAL;
            case AMMUNITION -> Items.ARROW;
            case TOOLS -> Items.IRON_PICKAXE;
            case MACHINERY -> Items.IRON_BLOCK;
        };
    }

    private static String itemName(Item item) {
        return item.getDescription().getString();
    }

    private static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) n += s.getCount();
        }
        return n;
    }

    private static int remove(Inventory inv, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.getItem() != item) continue;
            int n = Math.min(remaining, s.getCount());
            s.shrink(n);
            remaining -= n;
        }
        return count - remaining;
    }

    private static void add(Inventory inv, Item item, int count) {
        inv.add(new ItemStack(item, count));
    }
}
