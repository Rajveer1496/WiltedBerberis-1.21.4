package net.fabricmc.example;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.example.DebugManager;

import java.util.*;

/**
 * Periodically scans the nearby world for connected farmland blocks and groups them into clusters.
 * Each cluster receives a pseudo-random colour which is used by {@link FarmOverlayRenderer} to
 * render an outline on the client side. The scan is lightweight (every 4 seconds) and only runs
 * when a world and player are present.
 */
public final class FarmOverlay {

    /** Container class holding all farmland positions belonging to the same cluster. */
    public static class Cluster {
        public final Set<BlockPos> blocks = new HashSet<>();
        public final int color;
        public final BlockPos seed;

        private int minX, maxX, minZ, maxZ;

        Cluster(int color, BlockPos seed) {
            this.color = color;
            this.seed = seed;
            this.minX = this.maxX = seed.getX();
            this.minZ = this.maxZ = seed.getZ();
        }

        /** Calculates the arithmetic centre of all blocks in this cluster (integer coordinates). */
        public BlockPos calculateCenter() {
            if (blocks.isEmpty()) return seed;
            long sx = 0, sy = 0, sz = 0;
            for (BlockPos p : blocks) {
                sx += p.getX();
                sy += p.getY();
                sz += p.getZ();
            }
            int size = blocks.size();
            return new BlockPos((int) (sx / size), (int) (sy / size), (int) (sz / size));
        }

        /** Updates cached horizontal bounding box with the given block position. */
        private void updateBounds(BlockPos p) {
            int x = p.getX();
            int z = p.getZ();
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        /** Checks if the given X/Z position lies within the cluster's bounding rectangle. */
        public boolean containsXZ(BlockPos pos) {
            int x = pos.getX();
            int z = pos.getZ();
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static final List<Cluster> CLUSTERS = new ArrayList<>();

    private static final int MAX_SCAN_RADIUS = 120;         // Horizontal scan radius (blocks)
    private static final int MAX_BLOCKS_PER_CLUSTER = 1200;  // Safety cap per cluster
    private static final int RESCAN_INTERVAL_TICKS = 20 * 5;   // 5-second interval while searching
    private static final double NEARBY_DISTANCE = 250.0;

    // Map used so that clusters inside the same large region share stable colours between scans.
    private static final Map<String, Integer> REGION_COLOURS = new HashMap<>();
    private static int nextColourIdx = 0;

    private static int tickCounter = 0;

    private static boolean autoScanEnabled = true; // can be toggled via command

    // When true, only messages/overlays regarding the cluster the player currently stands on will be displayed.
    private static boolean localClusterMode = true; // enabled by default as requested

    private static net.minecraft.client.world.ClientWorld lastWorld = null;

    private FarmOverlay() {}

    /** Register the periodic scanner with Fabric's client-tick event bus. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            // Detect world change (including dimension switches or server transfers)
            if (client.world != lastWorld) {
                lastWorld = client.world;
                CLUSTERS.clear();
                tickCounter = RESCAN_INTERVAL_TICKS; // force immediate scan next tick
            }

            if (!autoScanEnabled) return; // user-disabled scanning

            // Determine whether any cluster is within 250-block radius of the player
            boolean clusterNearby = !getClustersNearPlayer(client.player.getPos(), NEARBY_DISTANCE).isEmpty();

            if (clusterNearby) {
                // Reset counter; we only rescan after clusters vanish
                tickCounter = 0;
                return;
            }

            // No nearby clusters – accumulate ticks and rescan every 5 s
            if (++tickCounter >= RESCAN_INTERVAL_TICKS) {
                tickCounter = 0;

                if (client.player != null) {
                    DebugManager.send("[FarmOverlay] Scanning for clusters…", true);
                }

                rescan(client);

                // Feedback after scan
                boolean nowNearby = !getClustersNearPlayer(client.player.getPos(), NEARBY_DISTANCE).isEmpty();
                if (client.player != null) {
                    if (nowNearby) {
                        DebugManager.send("[FarmOverlay] Cluster(s) found. Scans paused.", false);
                    } else {
                        DebugManager.send("[FarmOverlay] No clusters present.", false);
                    }
                }
            }
        });
    }

    private static void rescan(MinecraftClient client) {
        CLUSTERS.clear();

        // Allow colour reuse once we accumulated many different regions.
        if (REGION_COLOURS.size() > 32) {
            REGION_COLOURS.clear();
            nextColourIdx = 0;
        }

        Set<BlockPos> visited = new HashSet<>();
        BlockPos player = client.player.getBlockPos();

        int radius = MAX_SCAN_RADIUS;
        int yMin = player.getY() - 25;
        int yMax = player.getY() + 25;

        // Sample every second block horizontally – we also offset by (0,0) and (1,1)
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = yMin; dy <= yMax; dy++) {
                    BlockPos sample = new BlockPos(player.getX() + dx, dy, player.getZ() + dz);

                    // Examine the 2×2 neighbourhood around the sample so we do not miss isolated farmland.
                    for (int ox = 0; ox < 2; ox++) {
                        for (int oz = 0; oz < 2; oz++) {
                            BlockPos pos = sample.add(ox, 0, oz);
                            if (visited.contains(pos)) continue;
                            if (!client.world.getBlockState(pos).isOf(Blocks.FARMLAND)) continue;

                            int colour = pickColour(pos);
                            Cluster cluster = new Cluster(colour, pos);
                            floodFill(pos, client, visited, cluster);
                            if (!cluster.blocks.isEmpty()) {
                                CLUSTERS.add(cluster);
                            }
                        }
                    }
                }
            }
        }

        // Send informational chat message
        if (client.player != null) {
            if (localClusterMode) {
                Cluster current = getClusterContaining(client.player.getBlockPos());
                if (current != null) {
                    DebugManager.send("[FarmOverlay] Current cluster contains " + current.blocks.size() + " farmland blocks", true);
                }
            } else {
                int totalBlocks = CLUSTERS.stream().mapToInt(c -> c.blocks.size()).sum();
                DebugManager.send("[FarmOverlay] " + CLUSTERS.size() + " clusters, " + totalBlocks + " farmland blocks", true);

                if (CLUSTERS.isEmpty()) {
                    DebugManager.send("[FarmOverlay] No clusters present", false);
                }
            }
        }
    }

    private static int pickColour(BlockPos seed) {
        int regionSize = 64;
        String key = (seed.getX() / regionSize) + "," + (seed.getZ() / regionSize);
        Integer existing = REGION_COLOURS.get(key);
        if (existing != null) return existing;

        // Evenly distributed hues using the golden-ratio multiplier.
        float hue = (nextColourIdx * 0.618034f) % 1.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.9f, 0.95f);
        REGION_COLOURS.put(key, rgb);
        nextColourIdx++;
        return rgb;
    }

    private static void floodFill(BlockPos seed, MinecraftClient client, Set<BlockPos> visited, Cluster cluster) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed);
        cluster.blocks.add(seed);

        while (!queue.isEmpty() && cluster.blocks.size() < MAX_BLOCKS_PER_CLUSTER) {
            BlockPos current = queue.poll();
            // Explore the full 26-neighbourhood around the current block.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = current.add(dx, dy, dz);
                        if (visited.contains(n)) continue;
                        if (!client.world.getBlockState(n).isOf(Blocks.FARMLAND)) continue;
                        visited.add(n);
                        cluster.blocks.add(n);
                        cluster.updateBounds(n);
                        queue.add(n);
                    }
                }
            }
        }
    }

    /** Returns the list of farmland clusters most recently detected. */
    public static List<Cluster> getClusters() {
        return CLUSTERS;
    }

    /** Convenience helper returning only clusters whose centre is within {@code maxDistance} of the player. */
    public static List<Cluster> getClustersNearPlayer(Vec3d playerPos, double maxDistance) {
        double maxSq = maxDistance * maxDistance;
        return CLUSTERS.stream()
                .filter(c -> c.calculateCenter().getSquaredDistance(playerPos) <= maxSq)
                .toList();
    }

    /** Toggle automatic farmland scanning. Returns the new enabled state. */
    public static boolean toggleAutoScan() {
        autoScanEnabled = !autoScanEnabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal("[FarmOverlay] Auto-scan " + (autoScanEnabled ? "enabled" : "disabled")), false);
        }
        return autoScanEnabled;
    }

    /** Perform a manual scan immediately, regardless of auto-scan setting. */
    public static void scanNow() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;
        rescan(client);
        // Auto-disable continuous scanning if we found clusters during the manual scan.
        if (!CLUSTERS.isEmpty()) {
            autoScanEnabled = false;
        }
        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal("[FarmOverlay] Scan complete (" + CLUSTERS.size() + " clusters)"), false);
        }
    }

    /** Returns the cluster whose farmland contains the given position (x & z match), or null. */
    public static Cluster getClusterContaining(BlockPos pos) {
        for (Cluster c : CLUSTERS) {
            if (c.containsXZ(pos)) {
                return c;
            }
        }
        return null;
    }

    /** Toggle whether only the current cluster should be considered for overlay/messages. */
    public static boolean toggleLocalClusterMode() {
        localClusterMode = !localClusterMode;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal("[FarmOverlay] Local-cluster-only mode " + (localClusterMode ? "enabled" : "disabled")), false);
        }
        return localClusterMode;
    }

    public static boolean isLocalClusterMode() {
        return localClusterMode;
    }
} 