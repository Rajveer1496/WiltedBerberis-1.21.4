package net.fabricmc.example;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Advanced variant of BushBot that first clears all currently-visible bushes in the player's
 * farmland cluster and, once the cluster is empty, switches to a high-speed harvesting mode:
 * it waits for a full spawn cycle (3 s without particles) and then mines the newly-spawned
 * bushes in reverse spawn order (latest → earliest) with no pickup delays.
 *
 * Activate with /fastbushbot.
 */
public final class FastBushBot {

    private enum State { IDLE, GOTO_SENT, MINING_SENT, PICKUP_WAIT }

    // -------------------------------------------------------------------------
    private static boolean enabled = false;
    private static State state = State.IDLE;
    private static BlockPos currentTarget = null;
    private static long stateTimestamp = 0L;

    // avoid retargeting freshly mined bushes
    private static final Set<BlockPos> RECENT_MINED = new HashSet<>();
    private static final long RECENT_TIMEOUT_MS = 10_000;

    // idle cluster-scan helpers
    private static Vec3d lastPlayerPos = null;
    private static long lastMoveTime = 0L;
    private static boolean scanTriggeredDuringIdle = false;

    // fast-mode data
    private static final List<BlockPos> pendingBatch = new ArrayList<>();
    private static long lastSpawnTimestamp = 0L;
    private static final long BATCH_DELAY_MS = 3_000;

    // Snapshot of bushes present during previous tick (batch phase only)
    private static final Set<BlockPos> prevBushBlocks = new HashSet<>();

    // False until the cluster has been cleared for the first time
    private static boolean hasDoneInitialClear = false;

    // Inventory monitoring
    private static int lastInvHash = 0;
    private static long lastInvChange = 0L;
    private static final long NO_PICKUP_TIMEOUT_MS = 30_000; // 30 seconds

    // ADD field
    private static boolean watchdogActive = true;

    private FastBushBot() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick());
    }

    public static boolean toggle() {
        enabled = !enabled;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled) {
            reset();
            net.fabricmc.example.RiftWatchdog.stop();
        }
        if (mc.player != null) {
            net.fabricmc.example.DebugManager.send("[FastBushBot] " + (enabled ? "enabled" : "disabled"), false);
        }
        if (enabled) {
            net.fabricmc.example.RiftWatchdog.start();
            lastPlayerPos = mc.player.getPos();
            lastMoveTime = System.currentTimeMillis();
            scanTriggeredDuringIdle = false;
            hasDoneInitialClear = false;
            lastInvHash = computeInventoryHash(mc);
            lastInvChange = System.currentTimeMillis();
            primeState();
            mc.player.networkHandler.sendChatMessage("#set autoTool false");
            mc.player.networkHandler.sendChatMessage("#set freeLook false");
        } else {
            mc.player.networkHandler.sendChatMessage("#set autoTool true");
            mc.player.networkHandler.sendChatMessage("#set freeLook true");
        }
        return enabled;
    }

    /** Returns whether the fast bot is currently active. */
    public static boolean isEnabled() {
        return enabled;
    }

    // ---------------------------------------------------------------------
    private static void onTick() {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();

        // Track movement to trigger farmland rescan when idle
        Vec3d curPos = mc.player.getPos();
        if (lastPlayerPos == null || curPos.squaredDistanceTo(lastPlayerPos) > 0.01) {
            lastPlayerPos = curPos;
            lastMoveTime = now;
            scanTriggeredDuringIdle = false;
        }
        if (!scanTriggeredDuringIdle && now - lastMoveTime > 20_000) {
            net.fabricmc.example.FarmOverlay.scanNow();
            scanTriggeredDuringIdle = true;
        }

        // cleanup mined cache
        RECENT_MINED.removeIf(pos -> now - pos.asLong() > RECENT_TIMEOUT_MS);

        // update spawn tracking
        updateSpawnTracking(mc, now);

        // ---- Inventory watchdog ----
        if (watchdogActive) {
            // We intentionally sit idle after the initial clear while waiting for the next
            // spawn cycle. During that phase the inventory will not change for a long time.
            // Skip the watchdog check in that situation so we don't falsely trigger a restart.
            boolean waitingForSpawn = hasDoneInitialClear && pendingBatch.isEmpty() && state == State.IDLE;

            // Only monitor when the bot is actually in its idle state (not while walking/mining)
            boolean monitorNow = state == State.IDLE;

            int curHash = computeInventoryHash(mc);
            if (curHash != lastInvHash) {
                lastInvHash = curHash;
                lastInvChange = now;
            }

            if (!monitorNow) {
                // We're moving or mining; do not count this period towards the timeout
                lastInvChange = now;
            } else if (waitingForSpawn) {
                // Reset the timer so the 30-second window starts only after the first bush
                // of the next batch is mined (i.e. when inventory will change again).
                lastInvChange = now;
            } else if (now - lastInvChange > NO_PICKUP_TIMEOUT_MS) {
                long delta = now - lastInvChange;
                net.fabricmc.example.WiltedBerberisOverlayMod.LOGGER.warn("FastBushBot watchdog reset – no inventory change for {} ms (state={}, waitingSpawn={})", delta, state, waitingForSpawn);
                // stop bot and hard reset
                FastBushBot.toggle();
                BotTaskScheduler.clearAll();
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().getConnection().disconnect(Text.literal("Restarting"));
                }
                return;
            }
        }

        switch (state) {
            case IDLE -> selectNextBush(mc);
            case GOTO_SENT -> checkArrival(mc);
            case MINING_SENT -> checkMiningDone(mc, now);
            case PICKUP_WAIT -> checkPickupComplete(mc, now);
        }
    }

    // ---------------------------------------------------------------------
    private static void updateSpawnTracking(MinecraftClient mc, long now) {
        if (!hasDoneInitialClear) return; // ignore until first clear complete

        net.fabricmc.example.FarmOverlay.Cluster cluster = net.fabricmc.example.FarmOverlay.getClusterContaining(mc.player.getBlockPos());
        if (cluster == null) return;

        ClientWorld world = mc.world;
        Set<BlockPos> currentBushes = new HashSet<>();

        // For each farmland block in the cluster, check block above for dead bush
        for (BlockPos farmland : cluster.blocks) {
            BlockPos bushPos = farmland.up();
            if (world.getBlockState(bushPos).isOf(Blocks.DEAD_BUSH)) {
                currentBushes.add(bushPos);
            }
        }

        // Detect newly spawned bushes
        for (BlockPos pos : currentBushes) {
            if (!prevBushBlocks.contains(pos)) {
                pendingBatch.add(pos);
                lastSpawnTimestamp = now;
            }
        }

        // Remove bushes that got mined from prev set
        prevBushBlocks.clear();
        prevBushBlocks.addAll(currentBushes);
    }

    private static void selectNextBush(MinecraftClient mc) {
        net.fabricmc.example.FarmOverlay.Cluster cluster = net.fabricmc.example.FarmOverlay.getClusterContaining(mc.player.getBlockPos());
        ClientWorld world = mc.world;

        // Build current bush set based on real blocks (used for emptiness detection and phase-2)
        Set<BlockPos> blockBushes = new HashSet<>();
        if (cluster != null) {
            for (BlockPos farmland : cluster.blocks) {
                BlockPos candidate = farmland.up();
                if (world.getBlockState(candidate).isOf(Blocks.DEAD_BUSH)) {
                    blockBushes.add(candidate);
                }
            }
        }

        // ---- Phase transition check (cluster empty?) ----
        if (!hasDoneInitialClear && blockBushes.isEmpty()) {
            hasDoneInitialClear = true;
            net.fabricmc.example.DebugManager.send("[FastBushBot] Initial clear done – batch mode", false);
        }

        // --------- Phase-2: batch logic ---------
        if (hasDoneInitialClear) {
            if (!pendingBatch.isEmpty() && System.currentTimeMillis() - lastSpawnTimestamp > BATCH_DELAY_MS) {
                BlockPos target = pendingBatch.remove(0);
                if (!RECENT_MINED.contains(target)) {
                    gotoTarget(mc, target);
                }
            }
            return; // either executed a target or waiting for batch completion/spawn
        }

        // --------- Phase-1: Mine bushes using particle highlights ---------
        Set<BlockPos> highlights = net.fabricmc.example.WiltedBerberisTracker.getHighlightBlocks();
        Vec3d playerPos = mc.player.getPos();
        double bestDist = Double.MAX_VALUE;
        BlockPos best = null;
        for (BlockPos pos : highlights) {
            if (RECENT_MINED.contains(pos)) continue;
            if (cluster != null && !cluster.containsXZ(pos)) continue;
            double d = pos.getSquaredDistance(playerPos);
            if (d < bestDist) { bestDist = d; best = pos; }
        }
        if (best != null) {
            gotoTarget(mc, best);
        }
    }

    private static void gotoTarget(MinecraftClient mc, BlockPos target) {
        currentTarget = target;
        mc.player.networkHandler.sendChatMessage("#cancel");
        mc.player.networkHandler.sendChatMessage(baritoneGoto(target));
        state = State.GOTO_SENT;
        stateTimestamp = System.currentTimeMillis();
        net.fabricmc.example.DebugManager.send("[FastBushBot] Heading to " + coordString(target), false);
    }

    private static void checkArrival(MinecraftClient mc) {
        if (currentTarget == null) { state = State.IDLE; return; }
        double dx = mc.player.getX() - (currentTarget.getX() + 0.5);
        double dy = mc.player.getY() - (currentTarget.getY() + 0.5);
        double dz = mc.player.getZ() - (currentTarget.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz <= 2.25) {
            mc.player.networkHandler.sendChatMessage(baritoneMine(currentTarget));
            state = State.MINING_SENT;
            stateTimestamp = System.currentTimeMillis();
        }
    }

    private static void checkMiningDone(MinecraftClient mc, long now) {
        if (currentTarget == null) { state = State.IDLE; return; }
        if (!mc.world.getBlockState(currentTarget).isOf(Blocks.DEAD_BUSH)) {
            // Start pickup wait – duration depends on phase
            state = State.PICKUP_WAIT;
            stateTimestamp = now;
        } else if (now - stateTimestamp > 5_000) {
            state = State.IDLE; // timeout safeguard
        }
    }

    private static void checkPickupComplete(MinecraftClient mc, long now) {
        long waitMs = hasDoneInitialClear ? 300 : 1_500;
        if (now - stateTimestamp > waitMs) {
            RECENT_MINED.add(currentTarget);
            currentTarget = null;
            state = State.IDLE;
        }
    }

    // ---------------------------------------------------------------------
    private static String baritoneGoto(BlockPos p) {
        return "#goto " + p.getX() + " " + p.getY() + " " + p.getZ();
    }
    private static String baritoneMine(BlockPos p) {
        return "#minespecefic " + p.getX() + " " + p.getY() + " " + p.getZ();
    }
    private static String coordString(BlockPos p) { return String.format("(%d, %d, %d)", p.getX(), p.getY(), p.getZ()); }

    private static void primeState() {
        pendingBatch.clear();
        prevBushBlocks.clear();
    }

    private static void reset() {
        state = State.IDLE;
        currentTarget = null;
        lastPlayerPos = null;
        pendingBatch.clear();
        RECENT_MINED.clear();
        hasDoneInitialClear = false;
        prevBushBlocks.clear();
    }

    private static int computeInventoryHash(MinecraftClient mc) {
        if (mc.player == null) return 0;
        int h = 1;
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (st.isEmpty()) continue;
            h = 31 * h + Item.getRawId(st.getItem());
            h = 31 * h + st.getCount();
        }
        return h;
    }

    // ADD method after toggle()
    public static void setWatchdogActive(boolean active) {
        watchdogActive = active;
    }
} 