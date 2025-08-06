package net.fabricmc.example;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

/**
 * After the script-initiated connection to Hypixel completes, waits ~7 seconds
 * (140 client ticks) and then issues "/skyblock" to enter the SkyBlock gamemode.
 */
public final class BotSkyblockJoiner {

    private BotSkyblockJoiner() {}

    private static boolean pending = false;

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!pending) return;
            pending = false;
            // Schedule after 7 seconds (20 tps → 140 ticks)
            BotTaskScheduler.schedule(140, BotSkyblockJoiner::sendSkyblockCommand);
        });
    }

    /** Called by MenuAutomation right before starting the connect flow. */
    public static void expectJoin() {
        pending = true;
    }

    private static void sendSkyblockCommand() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        mc.player.networkHandler.sendChatMessage("/skyblock");

        // After entering SkyBlock, wait 10 seconds (200 ticks) before warping to Rift
        BotTaskScheduler.schedule(200, () -> {
            MinecraftClient inner = MinecraftClient.getInstance();
            if (inner.player != null) {
                inner.player.networkHandler.sendChatMessage("/warp rift");
                // After warping, wait 30 seconds (600 ticks) then start the Rift workflow
                BotTaskScheduler.schedule(600, net.fabricmc.example.RiftHarvestWorkflow::start);
            }
        });
    }
} 