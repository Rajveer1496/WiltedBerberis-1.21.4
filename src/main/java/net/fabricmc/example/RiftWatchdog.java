package net.fabricmc.example;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.client.world.ClientWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors chat for Rift collapse and checks inventory fullness while BushBot is running.
 * On critical conditions it restarts the full automation flow.
 */
public final class RiftWatchdog {

    private static final Logger LOG = LoggerFactory.getLogger("RiftWatchdog");
    private static boolean running = false;

    private RiftWatchdog() {}

    public static void init() {
        // chat listener once – remains but guarded by running flag
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (message == null) return;
            String plain = message.getString();
            String lowered = plain.toLowerCase();
            if (plain.contains("THE RIFT IS COLLAPSING")) {
                LOG.warn("Watchdog: Rift collapsing detected");
                handleCollapse();
            } else if (lowered.contains("limbo")) {
                LOG.warn("Watchdog: Limbo message detected in chat");
                handleLimbo();
            }
        });

        // Connection lost listener (kicks, timeouts etc.)
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOG.warn("Watchdog: disconnected from server – starting auto reconnect");
            BotTaskScheduler.schedule(100, RiftWatchdog::attemptReconnect);
        });

        // World change monitor on each client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTickWorldMonitor(client));
    }

    public static void start() {
        if (running) return;
        running = true;
        scheduleInventoryCheck();
        LOG.info("Watchdog started");
    }

    public static void stop() {
        running = false;
    }

    // -----------------------------------------------------------------------------
    private static void scheduleInventoryCheck() {
        if (!running) return;
        BotTaskScheduler.schedule(40, RiftWatchdog::checkInventoryFull);
    }

    private static void checkInventoryFull() {
        if (!running) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { scheduleInventoryCheck(); return; }

        // Consider only the main inventory + hot-bar (0-35). Armor / off-hand
        // slots are ignored. We deem the inventory full once at least 34
        // of those 36 slots are occupied.
        int filled = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (!st.isEmpty()) filled++;
        }
        if (filled >= 34) {
            LOG.info("Watchdog: inventory full");
            handleInventoryFull();
        } else {
            scheduleInventoryCheck();
        }
    }

    // -----------------------------------------------------------------------------
    private static void handleCollapse() {
        LOG.warn("Watchdog reset reason: Rift collapsing");
        FastBushBot.toggle(); // ensures disabled if it was on
        // Cancel all pending automation tasks (injector save etc.)
        BotTaskScheduler.clearAll();
        stop();
        // Wait 2 s then reconnect sequence
        BotTaskScheduler.schedule(40, () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(Text.literal("Rift collapsing restart"));
            }
            // After 5 s, start auto-reconnect attempts every 5 s until connected
            BotTaskScheduler.schedule(100, RiftWatchdog::attemptReconnect);
        });
    }

    private static void handleInventoryFull() {
        LOG.warn("Watchdog reset reason: inventory full (>=34 slots)");
        FastBushBot.toggle(); // stop bot
        stop();
        // After 2 seconds, start the bazaar selling workflow
        BotTaskScheduler.schedule(40, SaveInjectorsWorkflow::start);
    }

    private static void waitForCraftScreenAndInjectors() {
        BotTaskScheduler.schedule(20, () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen == null) {
                waitForCraftScreenAndInjectors(); return; }
            // Inside crafting table gui (chest-like) – attempt shift-click injector until none
            boolean any;
            do {
                any = BotInventoryUtils.quickMoveByDisplayName("Berberis Fule Injector");
                if (any) {
                    // wait 1 s between clicks
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            } while (any);
            // Close inventory after 2s then TODO start BazarSellWorkflow
            BotTaskScheduler.schedule(40, () -> {
                mc.player.closeScreen();
                BotTaskScheduler.schedule(40, () -> {
                    // TODO: invoke BazarSellWorkflow.start(); placeholder
                    FastBushBot.toggle(); // restart fast bot for now
                    LOG.info("Watchdog: crafting done – restarted FastBushBot (replace with BazarSellWorkflow later)");
                });
            });
        });
    }

    private static void sendChat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.networkHandler.sendChatMessage(msg);
        }
    }

    // --------------------------------------------------
    /** Tries to reconnect to Hypixel. Keeps retrying every 5 seconds until the player is in-game. */
    private static void attemptReconnect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        if (mc.player != null && mc.world != null) {
            LOG.info("Watchdog: reconnect successful");
            return; // already connected
        }

        LOG.info("Watchdog: attempting reconnect to Hypixel …");
        BotSkyblockJoiner.expectJoin(); // ensure post-login flow triggers

        net.minecraft.client.gui.screen.Screen parent = mc.currentScreen;
        net.minecraft.client.network.ServerAddress addr = net.minecraft.client.network.ServerAddress.parse("mc.hypixel.net");
        net.minecraft.client.network.ServerInfo info = new net.minecraft.client.network.ServerInfo("Hypixel", "mc.hypixel.net", net.minecraft.client.network.ServerInfo.ServerType.OTHER);
        net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(parent, mc, addr, info, false, null);

        // schedule next check
        BotTaskScheduler.schedule(100, RiftWatchdog::attemptReconnect);
    }

    // Store last known dimension key; safer than checking the world instance itself because Hypixel
    // sometimes reloads the same dimension which creates a new ClientWorld object.
    private static net.minecraft.util.Identifier lastDim = null;

    private static void onTickWorldMonitor(MinecraftClient mc) {
        if (!running) {
            lastDim = mc.world != null ? mc.world.getRegistryKey().getValue() : null;
            return;
        }
        net.minecraft.util.Identifier curDim = mc.world != null ? mc.world.getRegistryKey().getValue() : null;
        if (curDim != null && !curDim.equals(lastDim)) {
            lastDim = curDim;
            LOG.warn("Watchdog: dimension change detected ({})->{}", lastDim, curDim);
            handleWorldChange();
        }

        // ---- Enforce holding hotbar slot 2 whenever the mining bot is active ----
        if (running && net.fabricmc.example.FastBushBot.isEnabled() && mc.player != null) {
            if (mc.player.getInventory().selectedSlot != 1) {
                // Switch back to slot 2 (index 1)
                if (mc.options.hotbarKeys.length >= 2) {
                    net.minecraft.client.option.KeyBinding kb = mc.options.hotbarKeys[1];
                    net.fabricmc.example.HumanInputSimulator.pressKey(kb, 2);
                }
                mc.player.getInventory().selectedSlot = 1;
            }
        }
    }

    private static void handleWorldChange() {
        LOG.warn("Watchdog reset reason: dimension change");
        FastBushBot.toggle(); // stop bot if active
        stop();
        // Wait 5 s then disconnect and begin reconnect loop
        BotTaskScheduler.schedule(100, () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(Text.literal("World change restart"));
            }
            BotTaskScheduler.schedule(100, RiftWatchdog::attemptReconnect);
        });
    }

    // -----------------------------------------------------------------------------
    /** Treat any appearance of the word "limbo" in chat as being kicked to limbo; fully reset. */
    private static void handleLimbo() {
        LOG.warn("Watchdog reset reason: limbo chat message");
        FastBushBot.toggle();
        BotTaskScheduler.clearAll();
        stop();

        // Wait 2 s then disconnect & start reconnect loop
        BotTaskScheduler.schedule(40, () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(Text.literal("Limbo restart"));
            }
            // Begin reconnect attempts
            BotTaskScheduler.schedule(100, RiftWatchdog::attemptReconnect);
        });
    }
} 