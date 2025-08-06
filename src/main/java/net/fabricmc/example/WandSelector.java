package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;

/**
 * Utility that picks the hot-bar slot which contains the "Wand of Farming".
 * If it is already selected nothing happens. If the player doesn't have the wand
 * in the hot-bar the call is ignored.
 */
public final class WandSelector {

    private static final String WAND_NAME = "Wand of Farming";

    private WandSelector() {}

    /** Attempts to switch the selected hot-bar slot to the Wand of Farming. */
    public static void select() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // search hot-bar 0-8
        int found = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (!st.isEmpty() && st.getName() != null && WAND_NAME.equalsIgnoreCase(st.getName().getString())) {
                found = i; break;
            }
        }
        if (found == -1) return; // not present

        if (mc.player.getInventory().selectedSlot == found) return; // already holding

        // simulate number-key press for that hot-bar index (0->Key 1 … 8->Key 9)
        KeyBinding kb = mc.options.hotbarKeys[found];
        HumanInputSimulator.pressKey(kb, 2); // quick press (2 ticks)
    }
} 