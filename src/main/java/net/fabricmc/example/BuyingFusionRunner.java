package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the multi-step GUI interaction required to purchase Grand Experience Bottles from the Bazaar.
 * All timings are client-side (~2 seconds between clicks).
 */
public final class BuyingFusionRunner {

    private static final Logger LOG = LoggerFactory.getLogger("BuyingFusionRunner");

    private static final int STEP_DELAY = 40; // 2 seconds @20 TPS

    private BuyingFusionRunner() {}

    public static void start(Runnable onDone) {
        LOG.info("BuyingFusion: starting sequence – pausing all other automation");

        // 1) Cancel any scheduled tasks from previous workflows
        BotTaskScheduler.clearAll();

        // 2) Stop FastBushBot if running
        if (FastBushBot.isEnabled()) {
            FastBushBot.toggle();
        }

        // 3) Cancel any active Baritone pathing
        sendChat("#cancel");

        // Step 1 — open bazaar
        BotTaskScheduler.schedule(0, () -> sendChat("/bz"));
        // Step 2 — Oddities
        BotTaskScheduler.schedule(STEP_DELAY, () -> click("Oddities"));
        // Step 3 — EXP Bottles
        BotTaskScheduler.schedule(STEP_DELAY * 2, () -> click("EXP Bottles"));
        // Step 4 — Grand Experience Bottle
        BotTaskScheduler.schedule(STEP_DELAY * 3, () -> click("Grand Experience Bottle"));
        // Step 5 — Buy Instantly
        BotTaskScheduler.schedule(STEP_DELAY * 4, () -> click("Buy Instantly"));
        // Step 6 — Fill my inventory!
        BotTaskScheduler.schedule(STEP_DELAY * 5, () -> click("Fill my inventory!"));
        // Step 7 — close inventory & restart full script
        BotTaskScheduler.schedule(STEP_DELAY * 6, () -> {
            closeScreen();
            LOG.info("BuyingFusion: finished – restarting full automation");

            // Issue /skyblock to restart the main pipeline
            sendChat("/skyblock");

            // After 10 seconds (200 ticks) warp to Rift, then wait 30 seconds (600 ticks) before restarting the farm workflow
            BotTaskScheduler.schedule(200, () -> {
                sendChat("/warp rift");
                // Wait 30 seconds (600 ticks) after warping before starting the farm workflow
                BotTaskScheduler.schedule(600, net.fabricmc.example.RiftHarvestWorkflow::start);
            });
        });
    }

    private static void sendChat(String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            LOG.info("BuyingFusion: sending {}", cmd);
            mc.player.networkHandler.sendChatMessage(cmd);
        }
    }

    private static void click(String name) {
        boolean ok = BotInventoryUtils.leftClickByDisplayName(name);
        LOG.info("BuyingFusion: click '{}' -> {}", name, ok ? "OK" : "not found");
    }

    private static void closeScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.closeHandledScreen();
        } else if (mc.currentScreen != null) {
            mc.setScreen(null);
        }
    }
} 