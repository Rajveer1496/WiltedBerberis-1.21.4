package net.fabricmc.example;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Provides very small-footprint, client-side helpers for simulating human-like
 * input. Currently supported:
 *   • Smoothly rotating the player view to a target yaw/pitch over a number of ticks
 *   • Pressing & releasing vanilla KeyBindings for a specified number of ticks
 *
 * The implementation purposefully injects tiny random variation every tick so that
 * the resulting motion/press timings do not look like a perfect linear robot.
 */
public final class HumanInputSimulator {

    private HumanInputSimulator() {}

    // ------------------------------------------------------------
    // Rotation handling
    // ------------------------------------------------------------
    private static boolean rotating = false;
    private static float targetYaw;
    private static float targetPitch;
    private static int ticksRemaining;
    private static int totalTicks;
    private static float startYaw;
    private static float startPitch;

    // ------------------------------------------------------------
    // Key press handling
    // ------------------------------------------------------------
    private static final Map<KeyBinding, Integer> activeKeyPresses = new HashMap<>();

    // ------------------------------------------------------------
    // Deferred inventory opening
    // ------------------------------------------------------------
    private static boolean openInventoryNextTick = false;

    private static final Random RNG = new Random();

    /** Must be called once from mod init. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick(client));
    }

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Smoothly rotates the local player toward the supplied yaw/pitch over {@code durationTicks} ticks.
     * Any already running rotation will be overwritten.
     */
    public static void lookAt(float yaw, float pitch, int durationTicks) {
        if (durationTicks <= 0) {
            // Instant
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
            }
            rotating = false;
            return;
        }
        // Doubled random offsets for more natural inaccuracy
        float randYawOffset = jitter(1.0f);
        float randPitchOffset = jitter(0.5f);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            startYaw = wrapDegrees(mc.player.getYaw());
            startPitch = mc.player.getPitch();
        } else {
            startYaw = wrapDegrees(yaw);
            startPitch = pitch;
        }

        targetYaw = wrapDegrees(yaw + randYawOffset);
        targetPitch = MathHelper.clamp(pitch + randPitchOffset, -90f, 90f);

        ticksRemaining = durationTicks;
        totalTicks = durationTicks;
        rotating = true;
    }

    /**
     * Schedules a vanilla key to be considered pressed for {@code durationTicks} ticks.
     * Multiple keys can be active concurrently.
     */
    public static void pressKey(KeyBinding key, int durationTicks) {
        if (durationTicks <= 0) return;
        activeKeyPresses.put(key, durationTicks);
    }

    /** Convenience wrapper that virtually presses the inventory-key for a couple of ticks. */
    public static void openInventoryHumanLike() {
        // Schedule the inventory screen to open on the next client tick to avoid race conditions with
        // other screens (e.g. the chat screen closing right after sending a command).
        openInventoryNextTick = true;
    }

    // ========================================================================
    // Tick handler
    // ========================================================================
    private static void onTick(MinecraftClient mc) {
        handleRotation(mc);
        handleKeys();

        if (openInventoryNextTick) {
            openInventoryNextTick = false;
            if (mc.currentScreen == null && mc.player != null) {
                mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player));
            }
        }
    }

    private static void handleRotation(MinecraftClient mc) {
        if (!rotating || mc.player == null) return;

        float progress = 1.0f - (ticksRemaining / (float) totalTicks);
        float eased = easeInOutCubic(progress);

        float diffYaw = wrapDegrees(targetYaw - startYaw);
        float diffPitch = targetPitch - startPitch;

        // Halved micro-jitter per tick (smoother, less shaky)
        float newYaw = wrapDegrees(startYaw + diffYaw * eased + jitter(0.02f));
        float newPitch = MathHelper.clamp(startPitch + diffPitch * eased + jitter(0.01f), -90f, 90f);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        ticksRemaining--;
        if (ticksRemaining <= 0) {
            mc.player.setYaw(targetYaw);
            mc.player.setPitch(targetPitch);
            rotating = false;
        }
    }

    private static void handleKeys() {
        if (activeKeyPresses.isEmpty()) return;
        // We iterate over entry-set to allow removal while iterating
        activeKeyPresses.entrySet().removeIf(entry -> {
            KeyBinding key = entry.getKey();
            int remaining = entry.getValue();
            // Ensure key state
            key.setPressed(true);
            // Decrement counter
            remaining--;
            if (remaining <= 0) {
                key.setPressed(false);
                return true; // remove
            } else {
                entry.setValue(remaining);
                return false;
            }
        });
    }

    // ========================================================================
    // Helpers
    // ========================================================================
    private static float wrapDegrees(float deg) {
        return MathHelper.wrapDegrees(deg);
    }

    private static float jitter(float maxAbs) {
        return (RNG.nextFloat() * 2f - 1f) * maxAbs;
    }

    // Cubic ease-in-out for smoother acceleration/deceleration
    private static float easeInOutCubic(float x) {
        if (x < 0.5f) {
            return 4f * x * x * x;
        } else {
            float f = -2f * x + 2f;
            return 1f - (f * f * f) / 2f;
        }
    }
} 