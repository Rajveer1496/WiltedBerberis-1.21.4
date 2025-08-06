package net.fabricmc.example;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.example.DebugManager;
import net.fabricmc.example.RiftWatchdog;

import java.util.HashSet;
import java.util.Set;

/**
 * Very small state-machine that walks Baritone to each detected Wilted Berberis (dead bush) and mines it
 * using `#goto` and a custom `#minespecefic` command. Activate with /bushbot.
 */
public final class AutoBushBot {

    private enum State { IDLE, GOTO_SENT, MINING_SENT, PICKUP_WAIT }

    private static boolean enabled = false;
    private static State state = State.IDLE;
    private static BlockPos currentTarget = null;
    private static long stateTimestamp = 0L;

    // Stores bushes we recently mined so we don't immediately retarget them if they respawn
    private static final Set<BlockPos> RECENT_MINED = new HashSet<>();
    private static final long RECENT_TIMEOUT_MS = 10_000; // 10s cooldown per position

    // Idle scan support
    private static Vec3d lastPlayerPos = null;
    private static long lastMoveTime = 0L;
    private static boolean scanTriggeredDuringIdle = false;

    private AutoBushBot() {}

    /** Called from mod initialiser. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick());
    }

    public static boolean toggle() {
        enabled = !enabled;
        if (!enabled) {
            reset();
            RiftWatchdog.stop();
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            DebugManager.send("[BushBot] " + (enabled ? "enabled" : "disabled"), false);
        }
        if (enabled) {
            RiftWatchdog.start();
            lastPlayerPos = mc.player.getPos();
            lastMoveTime = System.currentTimeMillis();
            scanTriggeredDuringIdle = false;
            // Disable Baritone's auto-tool so it doesn't switch hotbar slots while BushBot is active
            mc.player.networkHandler.sendChatMessage("#set autoTool false");
            // Make the camera follow Baritone's movements
            mc.player.networkHandler.sendChatMessage("#set freeLook false");
        } else {
            // Re-enable auto-tool when the bot is turned off to restore previous behaviour
            mc.player.networkHandler.sendChatMessage("#set autoTool true");
            // Restore default freeLook behaviour
            mc.player.networkHandler.sendChatMessage("#set freeLook true");
        }
        return enabled;
    }

    private static void onTick() {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();

        // movement tracking
        Vec3d curPos = mc.player.getPos();
        if (lastPlayerPos == null || curPos.squaredDistanceTo(lastPlayerPos) > 0.01) {
            lastPlayerPos = curPos;
            lastMoveTime = now;
            scanTriggeredDuringIdle = false;
        }

        // If no movement for 20s, trigger single farmland rescan
        if (!scanTriggeredDuringIdle && now - lastMoveTime > 20_000) {
            FarmOverlay.scanNow();
            scanTriggeredDuringIdle = true;
        }

        // Clean up recent mined set
        RECENT_MINED.removeIf(pos -> now - pos.asLong() > RECENT_TIMEOUT_MS); // misuse but quick; better store map, but okay

        switch (state) {
            case IDLE -> selectNextBush(mc);
            case GOTO_SENT -> checkArrival(mc);
            case MINING_SENT -> checkMiningDone(mc, now);
            case PICKUP_WAIT -> checkPickupComplete(mc, now);
        }
    }

    private static void selectNextBush(MinecraftClient mc) {
        Vec3d playerPos = mc.player.getPos();
        double bestDist = Double.MAX_VALUE;
        BlockPos best = null;

        // Determine farmland cluster the player is currently in (if any)
        BlockPos playerBlockPos = mc.player.getBlockPos();
        FarmOverlay.Cluster playerCluster = FarmOverlay.getClusterContaining(playerBlockPos);

        for (BlockPos pos : WiltedBerberisTracker.getHighlightBlocks()) {
            if (RECENT_MINED.contains(pos)) continue;

            // If player is inside a farmland cluster, restrict bot targets to bushes within that cluster
            if (playerCluster != null && !playerCluster.containsXZ(pos)) continue;

            double d = pos.getSquaredDistance(playerPos);
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        if (best == null) return; // nothing to do

        currentTarget = best;
        // Send Baritone commands
        mc.player.networkHandler.sendChatMessage("#cancel");
        mc.player.networkHandler.sendChatMessage(baritoneGoto(best));
        state = State.GOTO_SENT;
        stateTimestamp = System.currentTimeMillis();
        DebugManager.send("[BushBot] Heading to " + coordString(best), false);
    }

    private static void checkArrival(MinecraftClient mc) {
        if (currentTarget == null) {
            state = State.IDLE; return;
        }
        double dx = mc.player.getX() - (currentTarget.getX() + 0.5);
        double dy = mc.player.getY() - (currentTarget.getY() + 0.5);
        double dz = mc.player.getZ() - (currentTarget.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz <= 2.25) { // within 1.5 blocks
            mc.player.networkHandler.sendChatMessage(baritoneMine(currentTarget));
            state = State.MINING_SENT;
            stateTimestamp = System.currentTimeMillis();
            DebugManager.send("[BushBot] Mining " + coordString(currentTarget), false);
        }
    }

    private static void checkMiningDone(MinecraftClient mc, long now) {
        if (currentTarget == null) { state = State.IDLE; return; }
        // If block at position is no longer a dead bush, consider mined
        if (!mc.world.getBlockState(currentTarget).isOf(Blocks.DEAD_BUSH)) {
            state = State.PICKUP_WAIT;
            stateTimestamp = now;
            return;
        }
        // Safety timeout 5s
        if (now - stateTimestamp > 5_000) {
            state = State.IDLE; // give up
        }
    }

    private static void checkPickupComplete(MinecraftClient mc, long now) {
        if (now - stateTimestamp > 1_500) {
            // Waited long enough, mark mined and move on
            RECENT_MINED.add(currentTarget);
            currentTarget = null;
            state = State.IDLE;
        }
    }

    private static String baritoneGoto(BlockPos p) {
        return "#goto " + p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String baritoneMine(BlockPos p) {
        return "#minespecefic " + p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String coordString(BlockPos p) {
        return String.format("(%d, %d, %d)", p.getX(), p.getY(), p.getZ());
    }

    private static void reset() {
        currentTarget = null;
        state = State.IDLE;
        lastPlayerPos = null;
    }
} 