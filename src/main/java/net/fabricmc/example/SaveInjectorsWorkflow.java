package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves crafted Berberis Fuel Injectors inside the player's Ender Chest and returns
 * to the farming area afterwards.
 * Flow:
 * 1. /craft → shift-click all injector stacks from chest to inventory.
 * 2. close GUI → /enderchest → click "Next Page".
 * 3. shift-click all injector stacks from inventory to the chest (player slots only).
 * 4. close GUI → #goto <goalPos>.
 * 5. Once within 2.5 blocks, toggle BushBot to resume farming.
 */
public final class SaveInjectorsWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger("SaveInjectorsWF");

    private static final int DELAY_SHORT = 40; // 2 seconds
    private static BlockPos goalPos = new BlockPos(-64, 72, -184);
    /**
     * Allows changing the farming-area destination at runtime.
     */
    public static void setGoalPos(BlockPos pos) {
        if (pos != null) goalPos = pos;
    }
    public static void setGoalPos(int x, int y, int z) {
        setGoalPos(new BlockPos(x, y, z));
    }
    private static final String INJECTOR_NAME = "Berberis Fuel Injector";

    private SaveInjectorsWorkflow() {}

    public static void start() {
        LOG.info("SIW: starting save injectors workflow (watchdog disabled)");
        FastBushBot.setWatchdogActive(false);
        sendChat("/craft");
        BotTaskScheduler.schedule(20, SaveInjectorsWorkflow::waitForCraftGui);
    }

    // ---------------- Craft GUI ----------------
    private static void waitForCraftGui() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) {
            BotTaskScheduler.schedule(20, SaveInjectorsWorkflow::waitForCraftGui);
            return;
        }
        LOG.info("SIW: crafting GUI open – transferring from chest");
        transferFromCraftChest();
    }

    private static void transferFromCraftChest() {
        boolean moved = moveInjectorFromChest();
        if (moved) {
            BotTaskScheduler.schedule(DELAY_SHORT, SaveInjectorsWorkflow::transferFromCraftChest);
        } else {
            LOG.info("SIW: chest empty – closing and opening Ender Chest");
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) mc.player.closeScreen();
            BotTaskScheduler.schedule(DELAY_SHORT, SaveInjectorsWorkflow::openEnderChest);
        }
    }

    // ---------------- Ender Chest ----------------
    private static void openEnderChest() {
        sendChat("/enderchest");
        LOG.info("SIW: opening Ender Chest");
        BotTaskScheduler.schedule(DELAY_SHORT, SaveInjectorsWorkflow::clickNextPage);
    }

    private static void clickNextPage() {
        if (clickByNameContains("Next Page")) {
            LOG.info("SIW: clicked Next Page – moving injectors into Ender Chest");
            BotTaskScheduler.schedule(DELAY_SHORT, SaveInjectorsWorkflow::transferToChest);
        } else {
            BotTaskScheduler.schedule(20, SaveInjectorsWorkflow::clickNextPage);
        }
    }

    private static void transferToChest() {
        boolean moved = moveInjectorFromPlayerInv();
        if (moved) {
            BotTaskScheduler.schedule(DELAY_SHORT, SaveInjectorsWorkflow::transferToChest);
        } else {
            LOG.info("SIW: all injectors stored – closing GUI & returning to farm");
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) mc.player.closeScreen();
            BotTaskScheduler.schedule(DELAY_SHORT, SaveInjectorsWorkflow::gotoFarmArea);
        }
    }

    // ---------------- Path & resume ----------------
    private static void gotoFarmArea() {
        sendChat("#goto " + goalPos.getX() + " " + goalPos.getY() + " " + goalPos.getZ());
        LOG.info("SIW: pathing back to farm area");
        BotTaskScheduler.schedule(DELAY_SHORT, SaveInjectorsWorkflow::checkArrival);
    }

    private static void checkArrival() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            BotTaskScheduler.schedule(DELAY_SHORT, SaveInjectorsWorkflow::checkArrival);
            return;
        }
        if (isNear(mc.player.getPos(), goalPos, 2.5)) {
            RiftHarvestWorkflow.resumeAtMiningArea();
            LOG.info("SIW: arrived – control passed back to RiftHarvestWorkflow");
        } else {
            BotTaskScheduler.schedule(DELAY_SHORT, SaveInjectorsWorkflow::checkArrival);
        }
    }

    // ---------------- Helpers ----------------
    private static boolean isNear(Vec3d pos, BlockPos target, double radius) {
        double dx = pos.x - target.getX();
        double dy = pos.y - target.getY();
        double dz = pos.z - target.getZ();
        return (dx*dx + dy*dy + dz*dz) <= radius*radius;
    }

    private static boolean moveInjectorFromChest() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null || mc.interactionManager == null) return false;
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (!slot.hasStack()) continue;
            if (slot.inventory == mc.player.getInventory()) continue;
            if (INJECTOR_NAME.equalsIgnoreCase(slot.getStack().getName().getString())) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                        slot.id, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }
        return false;
    }

    private static boolean moveInjectorFromPlayerInv() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null || mc.interactionManager == null) return false;
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory != mc.player.getInventory()) continue;
            if (!slot.hasStack()) continue;
            if (INJECTOR_NAME.equalsIgnoreCase(slot.getStack().getName().getString())) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                        slot.id, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }
        return false;
    }

    private static boolean clickByNameContains(String partial) {
        if (partial == null || partial.isEmpty()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null || mc.interactionManager == null) return false;
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (!slot.hasStack()) continue;
            ItemStack st = slot.getStack();
            String name = st.getName().getString();
            if (name != null && name.toLowerCase().contains(partial.toLowerCase())) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                        slot.id, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                return true;
            }
        }
        return false;
    }

    private static void sendChat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.networkHandler.sendChatMessage(msg);
        }
    }
} 