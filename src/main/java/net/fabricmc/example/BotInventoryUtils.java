package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Static helpers around the local player's inventory – counting stacks, finding slots, …
 */
public final class BotInventoryUtils {

    private BotInventoryUtils() {}

    /** Returns total number of items across all stacks whose display-name equals (ignoring case) {@code target}. */
    public static int countItemsByDisplayName(String target) {
        if (target == null || target.isEmpty()) return 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Text nameText = stack.getName();
            if (nameText == null) continue;
            if (target.equalsIgnoreCase(nameText.getString())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ---------------------------------------------------------------------
    // Finding slots for future click-support (not yet used)
    // ---------------------------------------------------------------------

    /**
     * Tries to find the slot index (in current screen handler) which contains an
     * item-stack whose display-name equals {@code target}. Returns -1 if not found.
     */
    public static int findSlotByDisplayName(String target) {
        if (target == null || target.isEmpty()) return -1;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null) return -1;
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (!slot.hasStack()) continue;
            ItemStack st = slot.getStack();
            if (target.equalsIgnoreCase(st.getName().getString())) {
                return slot.id;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------------
    // Slot clicking & stack transfer helpers
    // ---------------------------------------------------------------------

    /**
     * Performs a raw slot click on the container the local player currently has open.
     *
     * @param slotId      The index inside the current ScreenHandler (see {@link net.minecraft.screen.ScreenHandler#slots}).
     * @param mouseButton Typically 0 for left-click, 1 for right-click.
     * @param actionType  The {@link net.minecraft.screen.slot.SlotActionType} to use (e.g. PICKUP, QUICK_MOVE, SWAP …).
     * @return {@code true} when the click was executed – {@code false} otherwise (e.g. no screen open).
     */
    public static boolean clickSlotRaw(int slotId, int mouseButton, net.minecraft.screen.slot.SlotActionType actionType) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null || mc.interactionManager == null) return false;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                slotId, mouseButton, actionType, mc.player);
        return true;
    }

    /** Convenience helper that left-clicks the slot (same as manually clicking with the mouse). */
    public static boolean leftClickSlot(int slotId) {
        return clickSlotRaw(slotId, 0, net.minecraft.screen.slot.SlotActionType.PICKUP);
    }

    /**
     * Transfers the entire stack in {@code slotId} to the opposite inventory section (main ↔ hotbar) by
     * emulating the player pressing Shift-Click.
     */
    public static boolean quickMoveStack(int slotId) {
        return clickSlotRaw(slotId, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE);
    }

    /**
     * Swaps the stack in {@code slotId} with the hotbar slot {@code hotbarIndex} (0-8).
     * Internally this replicates the behaviour of pressing the corresponding number-key while hovering a slot.
     */
    public static boolean swapWithHotbar(int slotId, int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex > 8) return false;
        return clickSlotRaw(slotId, hotbarIndex, net.minecraft.screen.slot.SlotActionType.SWAP);
    }

    // ---------------------------------------------------------------------
    // High-level transfer helpers used by bots/scripts
    // ---------------------------------------------------------------------

    /**
     * Shift-clicks the given inventory slot so the stack ends up in the hotbar (if free space exists).
     */
    public static boolean moveInventoryToHotbar(int inventorySlotId) {
        return quickMoveStack(inventorySlotId);
    }

    /**
     * Shift-clicks the given hotbar index so the stack ends up back in the main inventory.
     */
    public static boolean moveHotbarToInventory(int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex > 8) return false;
        // Hotbar indices 0-8 usually map to slotIds 36-44 inside the player inventory screen handler
        return quickMoveStack(36 + hotbarIndex);
    }

    /**
     * Finds the first slot whose stack display-name matches {@code target} (case-insensitive)
     * and performs a shift-click (quick-move) on it.
     */
    public static boolean quickMoveByDisplayName(String target) {
        int slotId = findSlotByDisplayName(target);
        if (slotId == -1) return false;
        return quickMoveStack(slotId);
    }

    /**
     * Scans only the player's hotbar for a stack whose display-name matches {@code target}
     * and shift-clicks it so it moves into the main inventory.
     */
    public static boolean quickMoveHotbarByDisplayName(String target) {
        if (target == null || target.isEmpty()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (!slot.hasStack()) continue;
            // Only consider stacks that belong to the player's inventory and reside in the hotbar (index < 9)
            if (slot.inventory == mc.player.getInventory() && slot.getIndex() < 9) {
                if (target.equalsIgnoreCase(slot.getStack().getName().getString())) {
                    return quickMoveStack(slot.id);
                }
            }
        }
        return false;
    }

    /**
     * Finds the first stack whose display-name matches {@code target} (case-insensitive)
     * and performs a normal left-click (PICKUP) on it – useful to pick the item up on the cursor.
     */
    public static boolean leftClickByDisplayName(String target) {
        int slotId = findSlotByDisplayName(target);
        if (slotId == -1) return false;
        return leftClickSlot(slotId);
    }
} 