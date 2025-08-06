package net.fabricmc.example;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/**
 * Renders coloured outlines on top of farmland blocks that were grouped by {@link FarmOverlay}.
 */
public final class FarmOverlayRenderer {

    private static final double MAX_RENDER_DISTANCE_SQ = 400.0 * 400.0;  // 400-block radius

    // Level-of-detail distances (squared)
    private static final double LOD_DISTANCE_1 = 100.0 * 100.0;
    private static final double LOD_DISTANCE_2 = 200.0 * 200.0;

    private static final int MAX_OUTLINES_PER_FRAME = 1500;  // Safety cap – prevents lag spikes

    private static boolean linesVisible = false; // default OFF

    /** Toggle line visibility, returns new state. */
    public static boolean toggleVisibility() {
        linesVisible = !linesVisible;
        return linesVisible;
    }

    public static boolean isVisible() {
        return linesVisible;
    }

    private FarmOverlayRenderer() {}

    /** Register the renderer with Fabric's world-render event bus. */
    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(FarmOverlayRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (!linesVisible) return; // visibility off

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        List<FarmOverlay.Cluster> clusters = FarmOverlay.getClusters();

        if (clusters.isEmpty()) return;

        VertexConsumerProvider providers = ctx.consumers();
        if (providers == null) return;

        MatrixStack matrices = ctx.matrixStack();
        Vec3d cameraPos = ctx.camera().getPos();

        RenderSystem.lineWidth(2.0f);

        matrices.push();

        try {
            VertexConsumer lineConsumer = providers.getBuffer(RenderLayer.getLines());
            int rendered = 0;

            for (FarmOverlay.Cluster cluster : clusters) {
                if (rendered >= MAX_OUTLINES_PER_FRAME) break;

                double centerDistSq = cluster.seed.getSquaredDistance(cameraPos);
                if (centerDistSq > MAX_RENDER_DISTANCE_SQ) continue;

                float r = ((cluster.color >> 16) & 0xFF) / 255.0f;
                float g = ((cluster.color >> 8) & 0xFF) / 255.0f;
                float b = (cluster.color & 0xFF) / 255.0f;

                int skip = 1;
                if (centerDistSq > LOD_DISTANCE_2) skip = 8;
                else if (centerDistSq > LOD_DISTANCE_1) skip = 4;
                else if (centerDistSq > 50 * 50) skip = 2;

                int idx = 0;
                for (BlockPos pos : cluster.blocks) {
                    if (rendered >= MAX_OUTLINES_PER_FRAME) break;
                    if (idx++ % skip != 0) continue;
                    double distSq = pos.getSquaredDistance(cameraPos);
                    if (distSq > MAX_RENDER_DISTANCE_SQ) continue;

                    float alpha = (float) Math.max(0.3, 1.0 - Math.sqrt(distSq) / 300.0);
                    drawTopFace(lineConsumer, matrices, pos, cameraPos, r, g, b, alpha);
                    rendered++;
                }
            }
        } finally {
            matrices.pop();
            RenderSystem.lineWidth(1.0f);
        }
    }

    // Draw only the top face outline of the block.
    private static void drawTopFace(VertexConsumer vc, MatrixStack matrices, BlockPos pos, Vec3d cameraPos,
                                    float r, float g, float b, float a) {
        float x = (float) (pos.getX() - cameraPos.x);
        float y = (float) (pos.getY() - cameraPos.y) + 1; // top face
        float z = (float) (pos.getZ() - cameraPos.z);

        drawLine(vc, matrices, x, y, z, x + 1, y, z, r, g, b, a);
        drawLine(vc, matrices, x + 1, y, z, x + 1, y, z + 1, r, g, b, a);
        drawLine(vc, matrices, x + 1, y, z + 1, x, y, z + 1, r, g, b, a);
        drawLine(vc, matrices, x, y, z + 1, x, y, z, r, g, b, a);
    }

    private static void drawLine(VertexConsumer vc, MatrixStack matrices,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.0001f) {
            dx /= len; dy /= len; dz /= len;
        }

        vc.vertex(matrices.peek().getPositionMatrix(), x1, y1, z1)
                .color(r, g, b, a)
                .normal(dx, dy, dz)
                ;
        vc.vertex(matrices.peek().getPositionMatrix(), x2, y2, z2)
                .color(r, g, b, a)
                .normal(dx, dy, dz)
                ;
    }
} 