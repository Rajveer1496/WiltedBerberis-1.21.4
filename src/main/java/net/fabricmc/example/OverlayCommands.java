package net.fabricmc.example;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import net.minecraft.text.Text;
import net.fabricmc.example.FarmOverlayRenderer;
import net.fabricmc.example.AutoBushBot;
import net.fabricmc.example.DebugManager;
import net.fabricmc.example.RiftHarvestWorkflow;
import net.fabricmc.example.FastBushBot;

/**
 * Registers simple client-side chat commands for controlling the FarmOverlay feature.
 */
public final class OverlayCommands {

    private OverlayCommands() {}

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("farmoverlay")
                            .then(literal("toggle")
                                    .executes(ctx -> {
                                        boolean state = FarmOverlay.toggleAutoScan();
                                        ctx.getSource().sendFeedback(Text.literal("Farm overlay auto scan " + (state ? "enabled" : "disabled")));
                                        return 1;
                                    }))
                            .then(literal("scan")
                                    .executes(ctx -> {
                                        FarmOverlay.scanNow();
                                        ctx.getSource().sendFeedback(Text.literal("Farm overlay scan triggered"));
                                        return 1;
                                    }))
                            .then(literal("local")
                                    .executes(ctx -> {
                                        boolean state = FarmOverlay.toggleLocalClusterMode();
                                        ctx.getSource().sendFeedback(Text.literal("Farm overlay local-cluster mode " + (state ? "enabled" : "disabled")));
                                        return 1;
                                    }))
                            .then(literal("lines")
                                    .executes(ctx -> {
                                        boolean visible = FarmOverlayRenderer.toggleVisibility();
                                        ctx.getSource().sendFeedback(Text.literal("Farm overlay lines " + (visible ? "shown" : "hidden")));
                                        return 1;
                                    }))
                            .then(literal("bushbot")
                                    .executes(ctx -> {
                                        boolean en = AutoBushBot.toggle();
                                        ctx.getSource().sendFeedback(Text.literal("BushBot " + (en ? "enabled" : "disabled")));
                                        return 1;
                                    }))
                            .then(literal("debug")
                                    .executes(ctx -> {
                                        boolean vis = DebugManager.toggle();
                                        ctx.getSource().sendFeedback(Text.literal("Debug messages " + (vis ? "shown" : "hidden")));
                                        return 1;
                                    }))
                            .then(literal("inv")
                                    .then(literal("riftworkflow")
                                            .executes(ctx -> {
                                                RiftHarvestWorkflow.start();
                                                ctx.getSource().sendFeedback(Text.literal("RiftHarvest workflow started"));
                                                return 1;
                                            })))
            );

            // standalone bushbot command for convenience
            dispatcher.register(
                    literal("bushbot")
                            .executes(ctx -> {
                                boolean en = AutoBushBot.toggle();
                                ctx.getSource().sendFeedback(Text.literal("BushBot " + (en ? "enabled" : "disabled")));
                                return 1;
                            })
            );

            // standalone fastbushbot command (fast mode)
            dispatcher.register(
                    literal("fastbushbot")
                            .executes(ctx -> {
                                boolean en = FastBushBot.toggle();
                                ctx.getSource().sendFeedback(Text.literal("FastBushBot " + (en ? "enabled" : "disabled")));
                                return 1;
                            })
            );

            // Separate convenience command: /botriftworkflow
            dispatcher.register(
                    literal("botriftworkflow")
                            .executes(ctx -> {
                                RiftHarvestWorkflow.start();
                                ctx.getSource().sendFeedback(Text.literal("RiftHarvest workflow started"));
                                return 1;
                            })
            );
        });
    }
} 