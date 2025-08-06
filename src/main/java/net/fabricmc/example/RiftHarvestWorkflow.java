package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.example.FastBushBot;
import net.minecraft.client.option.KeyBinding;

public final class RiftHarvestWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger("RiftHarvestWorkflow");

    private static final BlockPos FIRST_GOAL = new BlockPos(-50, 104, 72);
    private static BlockPos secondGoal = new BlockPos(-63, 71, -182);
    /**
     * Allows changing the mining-area destination at runtime.
     */
    public static void setSecondGoal(BlockPos pos) {
        if (pos != null) secondGoal = pos;
    }
    public static void setSecondGoal(int x, int y, int z) {
        setSecondGoal(new BlockPos(x, y, z));
    }

    private static final int DELAY_SHORT = 40; // 2 seconds
    private static final int DELAY_LONG = 100; // 5 seconds

    private RiftHarvestWorkflow() {}

    public static void start() {
        LOG.info("RHW: starting harvest workflow");
        // Step A: path to first location using Baritone
        sendChat("#set freeLook false");
        sendChat("#set autoTool false");
        sendChat("#goto -50 104 72");
        // Periodically check distance
        BotTaskScheduler.schedule(DELAY_SHORT, RiftHarvestWorkflow::checkFirstGotoFinished);
    }

    // =====================================================
    private static void checkFirstGotoFinished() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            BotTaskScheduler.schedule(DELAY_SHORT, RiftHarvestWorkflow::checkFirstGotoFinished);
            return;
        }
        if (isNear(mc.player.getPos(), FIRST_GOAL, 1.0)) {
            LOG.info("RHW: reached first goal – initiating zombie interaction");
            engageZombie();
        } else {
            // keep polling
            BotTaskScheduler.schedule(DELAY_SHORT, RiftHarvestWorkflow::checkFirstGotoFinished);
        }
    }

    private static boolean isNear(Vec3d pos, BlockPos target, double radius) {
        double dx = pos.x - target.getX();
        double dy = pos.y - target.getY();
        double dz = pos.z - target.getZ();
        return (dx*dx + dy*dy + dz*dz) <= radius*radius;
    }

    // =====================================================
    /** Starts the process of locating the nearest zombie, rotating toward it via the Baritone
     *  "/bot look" command and then repeatedly left-clicking every 2 s until the GUI opens. */
    private static void engageZombie() {
        Entity zombie = findNearestZombie();
        if (zombie == null) {
            LOG.info("RHW: no zombie found nearby – scanning again in 2 s");
            BotTaskScheduler.schedule(DELAY_SHORT, RiftHarvestWorkflow::engageZombie);
            return;
        }

        // Rotate view smoothly using our helper (same effect as /bot look)
        float[] rot = calculateYawPitch(zombie);
        HumanInputSimulator.lookAt(rot[0], rot[1], 40);
        // After initial rotation ends, begin aim-and-click loop
        BotTaskScheduler.schedule(DELAY_SHORT, RiftHarvestWorkflow::attackUntilGuiOpens);
    }

    /** Presses the attack key every 2 s until a screen is opened by the server. */
    private static void attackUntilGuiOpens() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) {
            LOG.info("RHW: Baba Yaga GUI opened – stopping attack loop");
            clickBabaYaga();
            return;
        }

        Entity target = findNearestZombie();
        if (target != null) {
            float[] rot = calculateYawPitch(target);
            HumanInputSimulator.lookAt(rot[0], rot[1], 4); // quick 0.2s adjust
            if (mc.interactionManager != null) {
                mc.interactionManager.attackEntity(mc.player, target);
            }
        }

        // Swing hand regardless to ensure animation / extra hits
        if (mc.player != null) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        // Schedule next aim & click in 2 s
        BotTaskScheduler.schedule(DELAY_SHORT, RiftHarvestWorkflow::attackUntilGuiOpens);
    }

    /** Returns yaw/pitch (in degrees) required to look from player to the supplied entity. */
    private static float[] calculateYawPitch(Entity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return new float[] {0f, 0f};

        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double dy;
        if (target instanceof net.minecraft.entity.LivingEntity && mc.player instanceof net.minecraft.entity.LivingEntity) {
            dy = ((net.minecraft.entity.LivingEntity) target).getEyeY() - ((net.minecraft.entity.LivingEntity) mc.player).getEyeY();
        } else {
            dy = (target.getY() + target.getHeight() * 0.5) - (mc.player.getY() + mc.player.getHeight() * 0.5);
        }

        double distXZ = Math.sqrt(dx*dx + dz*dz);

        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, distXZ)));

        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(pitch, -90f, 90f);

        return new float[] { yaw, pitch };
    }

    private static Entity findNearestZombie() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return null;
        double best = Double.MAX_VALUE;
        Entity bestE = null;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ZombieEntity)) continue;
            double dist = e.squaredDistanceTo(mc.player);
            if (dist < best) {
                best = dist;
                bestE = e;
            }
        }
        return bestE;
    }

    // =====================================================
    private static void clickBabaYaga() {
        BotTaskScheduler.schedule(0, () -> {
            boolean ok = BotInventoryUtils.leftClickByDisplayName("The Baba Yaga");
            LOG.info("RHW: click The Baba Yaga -> {}", ok);
        });

        // 3s after clicking, equip hot-bar slot 2 (index 1)
        BotTaskScheduler.schedule(60, RiftHarvestWorkflow::equipSlot2);

        // 5-second wait before scan command and movement
        BotTaskScheduler.schedule(DELAY_LONG, () -> {
            FarmOverlay.scanNow();
            LOG.info("RHW: triggered farm overlay scan");
            // path to mining area
            sendChat("#goto " + secondGoal.getX() + " " + secondGoal.getY() + " " + secondGoal.getZ());
            LOG.info("RHW: pathing to mining area");
            // Wait until we are close enough, then start the BushBot
            BotTaskScheduler.schedule(DELAY_SHORT, RiftHarvestWorkflow::checkSecondGotoFinished);
        });
    }

    private static void sendChat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.networkHandler.sendChatMessage(msg);
        }
    }

    // --------------------------------------------------
    /** Virtually presses the "2" hotbar key so the player holds slot #2 (index 1). */
    private static void equipSlot2() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (mc.options.hotbarKeys.length < 2) return;
        KeyBinding kb = mc.options.hotbarKeys[1];
        HumanInputSimulator.pressKey(kb, 4); // hold for ~0.2 s
        mc.player.getInventory().selectedSlot = 1; // local visual update; the key-press will trigger vanilla packet
        LOG.info("RHW: equipped hotbar slot 2");
    }

    // =====================================================
    /** Polls until the player is inside a 1-block radius of {@link #secondGoal} before enabling BushBot. */
    private static void checkSecondGotoFinished() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            BotTaskScheduler.schedule(DELAY_SHORT, RiftHarvestWorkflow::checkSecondGotoFinished);
            return;
        }
        if (isNear(mc.player.getPos(), secondGoal, 1.5)) {
            resumeAtMiningArea();
        } else {
            BotTaskScheduler.schedule(DELAY_SHORT, RiftHarvestWorkflow::checkSecondGotoFinished);
        }
    }

    /** Public entry so external workflows can hand control back to RHW right before activating BushBot. */
    public static void resumeAtMiningArea() {
        // Trigger a fresh farmland scan so FastBushBot immediately knows the current cluster
        // and can mine without waiting for the idle-scan timer. Without this scan the bot may
        // stay idle for >30s, causing its internal watchdog to disconnect and restart the
        // whole automation loop.
        FarmOverlay.scanNow();

        FastBushBot.setWatchdogActive(true);
        FastBushBot.toggle();
        LOG.info("RHW: arrived at mining area – FastBushBot toggled on (via resumeAtMiningArea)");
    }
} 