package net.fabricmc.example;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders a yellow outline on top of every confirmed Wilted Berberis block.
 */
public class WiltedBerberisHighlightRenderer {

    private static final double MAX_DISTANCE_SQ = 120.0 * 120.0;

    public static void init() {
        // AFTER_TRANSLUCENT so our lines appear on top of most world content.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WiltedBerberisHighlightRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Set<BlockPos> highlights = WiltedBerberisTracker.getHighlightBlocks();

        // In local-cluster-only mode, filter highlights to the player's cluster and suppress drawing farmland overlay elsewhere.
        if (FarmOverlay.isLocalClusterMode()) {
            FarmOverlay.Cluster playerCluster = FarmOverlay.getClusterContaining(client.player.getBlockPos());
            if (playerCluster != null) {
                highlights = highlights.stream().filter(playerCluster::containsXZ).collect(java.util.stream.Collectors.toSet());
            } else {
                highlights = java.util.Collections.emptySet();
            }
        }

        if (highlights.isEmpty()) return;

        VertexConsumerProvider provider = ctx.consumers();
        if (provider == null) return;

        MatrixStack matrices = ctx.matrixStack();
        Vec3d cameraPos = ctx.camera().getPos();

        // Configure render state - matching working renderer
        RenderSystem.lineWidth(2.5f);

        matrices.push();

        try {
            VertexConsumer lineConsumer = provider.getBuffer(RenderLayer.getLines());

            for (BlockPos pos : highlights) {
                if (pos.getSquaredDistance(cameraPos) > MAX_DISTANCE_SQ) continue;
                drawBlockOutline(lineConsumer, matrices, pos, cameraPos, 1.0f, 1.0f, 0.0f, 1.0f);
            }
        } catch (Exception e) {
            System.err.println("[WiltedBerberisHighlightRenderer] Exception during rendering: " + e.getMessage());
            e.printStackTrace();
        } finally {
            matrices.pop();
            RenderSystem.lineWidth(1.0f);
        }
    }

    private static void drawBlockOutline(VertexConsumer vertexConsumer, MatrixStack matrices, BlockPos pos, Vec3d cameraPos, float r, float g, float b, float a) {
        float x = (float)(pos.getX() - cameraPos.x);
        float y = (float)(pos.getY() - cameraPos.y);
        float z = (float)(pos.getZ() - cameraPos.z);
        
        // Draw top face outline
        drawLine(vertexConsumer, matrices, x, y + 1, z, x + 1, y + 1, z, r, g, b, a);
        drawLine(vertexConsumer, matrices, x + 1, y + 1, z, x + 1, y + 1, z + 1, r, g, b, a);
        drawLine(vertexConsumer, matrices, x + 1, y + 1, z + 1, x, y + 1, z + 1, r, g, b, a);
        drawLine(vertexConsumer, matrices, x, y + 1, z + 1, x, y + 1, z, r, g, b, a);
    }

    private static void drawLine(VertexConsumer vertexConsumer, MatrixStack matrices,
                               float x1, float y1, float z1, float x2, float y2, float z2,
                               float r, float g, float b, float a) {
        // Calculate and normalize direction vector - CRITICAL FOR RENDERING
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0.001f) {
            dx /= length;
            dy /= length;
            dz /= length;
        }

        // Add line vertices with proper normal vectors
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x1, y1, z1)
                .color(r, g, b, a)
                .normal(dx, dy, dz);
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x2, y2, z2)
                .color(r, g, b, a)
                .normal(dx, dy, dz);
    }
}