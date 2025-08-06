package net.fabricmc.example;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.example.DebugManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Tracks firework particles which indicate Wilted Berberis positions and renders an overlay box.
 */
public class WiltedBerberisTracker {

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    private static final List<Entry> ENTRIES = new ArrayList<>();

    // How long to keep an entry alive (in milliseconds)
    private static final long ENTRY_LIFETIME_MS = 2000L; // Increased to 2 seconds

    private static final double MERGE_DISTANCE_SQ = 4.0; // distance^2 within which particles belong to the same plant

    // confirmed blocks to highlight
    private static final Set<net.minecraft.util.math.BlockPos> HIGHLIGHT_BLOCKS = new HashSet<>();
    
    // Track when we last validated blocks to clean up destroyed ones
    private static long lastValidationTime = 0;
    private static final long VALIDATION_INTERVAL_MS = 1000L; // Validate every second

    public static Set<net.minecraft.util.math.BlockPos> getHighlightBlocks() {
        return HIGHLIGHT_BLOCKS;
    }

    /**
     * Called from ClientWorld mixin whenever a particle spawns.
     */
    public static void onParticleSpawn(ParticleEffect effect, double x, double y, double z) {
        if (effect.getType() != ParticleTypes.FIREWORK) return;

        Vec3d pos = new Vec3d(x, y, z);

        // Find nearest existing entry
        Entry nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Entry e : ENTRIES) {
            double distSq = e.position.squaredDistanceTo(pos);
            if (distSq < MERGE_DISTANCE_SQ && distSq < nearestSq) {
                nearest = e;
                nearestSq = distSq;
            }
        }

        long now = System.currentTimeMillis();
        if (nearest == null) {
            ENTRIES.add(new Entry(pos, now));
            // Process the new particle immediately
            processParticleLocation(pos, now);
        } else {
            // Update existing entry position and time
            nearest.position = pos;
            nearest.lastUpdate = now;
            // Reprocess in case the particle moved to a new bush
            processParticleLocation(pos, now);
        }
    }
    
    private static void processParticleLocation(Vec3d pos, long timestamp) {
        if (CLIENT.world == null) return;
        
        // Check multiple potential block positions around the particle
        // Particles might not be exactly centered on the block
        int baseX = (int) Math.floor(pos.x);
        int baseY = (int) Math.floor(pos.y);
        int baseZ = (int) Math.floor(pos.z);
        
        // Only check the block the particle spawned on (1 × 1 × 1 search area)
        net.minecraft.util.math.BlockPos bpos = new net.minecraft.util.math.BlockPos(baseX, baseY, baseZ);

        // Skip if we already have this block highlighted
        if (!HIGHLIGHT_BLOCKS.contains(bpos)) {
            boolean isBush = CLIENT.world.getBlockState(bpos).isOf(net.minecraft.block.Blocks.DEAD_BUSH);

            if (isBush) {
                // If local-cluster mode is enabled, ignore bushes outside the player's cluster
                boolean relevant = true;
                if (FarmOverlay.isLocalClusterMode()) {
                    FarmOverlay.Cluster playerCluster = FarmOverlay.getClusterContaining(CLIENT.player.getBlockPos());
                    if (playerCluster == null || !playerCluster.containsXZ(bpos)) {
                        relevant = false;
                    }
                }

                if (!relevant) return; // ignore bushes outside current cluster

                HIGHLIGHT_BLOCKS.add(bpos);

                // Debug feedback (only if relevant cluster) always send message
                String msg = String.format("[WB] Overlay added at (%d, %d, %d)", baseX, baseY, baseZ);
                DebugManager.send(msg, false);
                // WiltedBerberisOverlayMod.LOGGER.info(msg);

                // Found a bush, we're done for this particle
                return;
            }
        }
        
        // If no bush found, just log for debugging (no chat spam)
        // WiltedBerberisOverlayMod.LOGGER.info(
        //         String.format("[WB] Particle at (%.2f, %.2f, %.2f) -> no dead bush found nearby", pos.x, pos.y, pos.z));
    }

    /**
     * Called every client tick to clear old entries and validate highlighted blocks.
     */
    public static void onClientTick() {
        if (CLIENT.world == null) return;
        
        long now = System.currentTimeMillis();
        
        // Remove old particle entries
        ENTRIES.removeIf(e -> now - e.lastUpdate > ENTRY_LIFETIME_MS);
        
        // Periodically validate highlighted blocks and remove destroyed ones
        if (now - lastValidationTime > VALIDATION_INTERVAL_MS) {
            lastValidationTime = now;
            validateHighlightedBlocks();
        }
    }
    
    /**
     * Validates all highlighted blocks and removes any that are no longer dead bushes.
     */
    private static void validateHighlightedBlocks() {
        if (CLIENT.world == null) return;
        
        Iterator<net.minecraft.util.math.BlockPos> iterator = HIGHLIGHT_BLOCKS.iterator();
        int removedCount = 0;
        
        while (iterator.hasNext()) {
            net.minecraft.util.math.BlockPos pos = iterator.next();
            
            // Check if the block is still a dead bush
            boolean isStillBush = CLIENT.world.getBlockState(pos).isOf(net.minecraft.block.Blocks.DEAD_BUSH);
            
            if (!isStillBush) {
                iterator.remove();
                removedCount++;
                
                WiltedBerberisOverlayMod.LOGGER.info(String.format("[WB] Removed highlight at (%d, %d, %d) - block no longer a dead bush", pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        
        // no chat spam for cleanup count
    }
    
    /**
     * Force immediate validation of all highlighted blocks.
     * Useful for testing or when you know blocks have changed.
     */
    public static void forceValidation() {
        validateHighlightedBlocks();
    }
    
    /**
     * Clear all highlights (useful for debugging).
     */
    public static void clearAllHighlights() {
        HIGHLIGHT_BLOCKS.clear();
        ENTRIES.clear();
        WiltedBerberisOverlayMod.LOGGER.info("[WB] Cleared all highlights and entries");
    }

    private static class Entry {
        Vec3d position;
        long lastUpdate;

        Entry(Vec3d position, long lastUpdate) {
            this.position = position;
            this.lastUpdate = lastUpdate;
        }
    }
}