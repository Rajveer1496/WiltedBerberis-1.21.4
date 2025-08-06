package net.fabricmc.example;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Small set of client-side chat commands (all under /bot …) to interactively test the new
 * human-like automation helpers. These are strictly for local debugging – the commands never
 * contact the server.
 */
public final class BotCommands {

    private BotCommands() {}

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("bot")
                            // /bot look <yaw> <pitch> <duration>
                            .then(literal("look")
                                    .then(argument("yaw", FloatArgumentType.floatArg())
                                            .then(argument("pitch", FloatArgumentType.floatArg())
                                                    .then(argument("duration", IntegerArgumentType.integer(1))
                                                            .executes(ctx -> {
                                                                float yaw = FloatArgumentType.getFloat(ctx, "yaw");
                                                                float pitch = FloatArgumentType.getFloat(ctx, "pitch");
                                                                int dur = IntegerArgumentType.getInteger(ctx, "duration");
                                                                HumanInputSimulator.lookAt(yaw, pitch, dur);
                                                                ctx.getSource().sendFeedback(Text.literal(String.format("Rotating to (%.1f, %.1f) in %d ticks", yaw, pitch, dur)));
                                                                return 1;
                                                            })))))
                            // /bot inv open  → presses the inventory key briefly
                            .then(literal("inv")
                                    .then(literal("open")
                                            .executes(ctx -> {
                                                HumanInputSimulator.openInventoryHumanLike();
                                                ctx.getSource().sendFeedback(Text.literal("Inventory key simulated"));
                                                return 1;
                                            }))
                                    // /bot inv count "Display Name"
                                    .then(literal("count")
                                            .then(argument("name", StringArgumentType.string())
                                                    .executes(ctx -> {
                                                        String name = StringArgumentType.getString(ctx, "name");
                                                        int total = BotInventoryUtils.countItemsByDisplayName(name);
                                                        ctx.getSource().sendFeedback(Text.literal("You have " + total + "x \"" + name + "\""));
                                                        return 1;
                                                    })))
                                    // /bot inv click <slotId>
                                    .then(literal("click")
                                            .then(argument("slot", IntegerArgumentType.integer(0))
                                                    .executes(ctx -> {
                                                        int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                                        boolean ok = BotInventoryUtils.leftClickSlot(slot);
                                                        ctx.getSource().sendFeedback(Text.literal((ok ? "Clicked" : "Failed to click") + " slot " + slot));
                                                        return ok ? 1 : 0;
                                                    })))
                                    // /bot inv clickname "Display Name"
                                    .then(literal("clickname")
                                            .then(argument("name", StringArgumentType.string())
                                                    .executes(ctx -> {
                                                        String name = StringArgumentType.getString(ctx, "name");
                                                        HumanInputSimulator.openInventoryHumanLike();
                                                        int delay = 6 + new java.util.Random().nextInt(8);
                                                        BotTaskScheduler.schedule(delay, () -> BotInventoryUtils.leftClickByDisplayName(name));
                                                        ctx.getSource().sendFeedback(Text.literal("Attempting click on \"" + name + "\" after ~" + delay + " ticks"));
                                                        return 1;
                                                    })))
                                    // /bot inv quickmove <slotId>
                                    .then(literal("quickmove")
                                            .then(argument("slot", IntegerArgumentType.integer(0))
                                                    .executes(ctx -> {
                                                        int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                                        boolean ok = BotInventoryUtils.quickMoveStack(slot);
                                                        ctx.getSource().sendFeedback(Text.literal((ok ? "Quick-moved" : "Failed to quick-move") + " slot " + slot));
                                                        return ok ? 1 : 0;
                                                    })))
                                    // /bot inv shift "Display Name"
                                    .then(literal("shift")
                                            .then(argument("name", StringArgumentType.string())
                                                    .executes(ctx -> {
                                                        String name = StringArgumentType.getString(ctx, "name");
                                                        // open inventory first, then run the shift-click a few ticks later so it feels human.
                                                        HumanInputSimulator.openInventoryHumanLike();
                                                        int delay = 6 + new java.util.Random().nextInt(8);
                                                        BotTaskScheduler.schedule(delay, () -> BotInventoryUtils.quickMoveByDisplayName(name));
                                                        ctx.getSource().sendFeedback(Text.literal("Attempting shift-click on \"" + name + "\" after ~" + delay + " ticks"));
                                                        return 1;
                                                    })))
                                    // /bot inv swap <slotId> <hotbarIdx>
                                    .then(literal("swap")
                                            .then(argument("slot", IntegerArgumentType.integer(0))
                                                    .then(argument("hotbar", IntegerArgumentType.integer(0, 8))
                                                            .executes(ctx -> {
                                                                int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                                                int hb = IntegerArgumentType.getInteger(ctx, "hotbar");
                                                                boolean ok = BotInventoryUtils.swapWithHotbar(slot, hb);
                                                                ctx.getSource().sendFeedback(Text.literal((ok ? "Swapped" : "Failed to swap") + " slot " + slot + " with hotbar " + hb));
                                                                return ok ? 1 : 0;
                                                            }))))
                                    // /bot hotbar toInv <idx>
                                    .then(literal("hotbar")
                                            // /bot hotbar shift "Display Name"  (moves item from hotbar to inventory)
                                            .then(literal("shift")
                                                    .then(argument("name", StringArgumentType.string())
                                                            .executes(ctx -> {
                                                                String name = StringArgumentType.getString(ctx, "name");
                                                                HumanInputSimulator.openInventoryHumanLike();
                                                                int delay = 6 + new java.util.Random().nextInt(8);
                                                                BotTaskScheduler.schedule(delay, () -> BotInventoryUtils.quickMoveHotbarByDisplayName(name));
                                                                ctx.getSource().sendFeedback(Text.literal("Attempting hotbar shift-click of \"" + name + "\" after ~" + delay + " ticks"));
                                                                return 1;
                                                            })))
                                            .then(literal("toInv")
                                                    .then(argument("idx", IntegerArgumentType.integer(0,8))
                                                            .executes(ctx -> {
                                                                int idx = IntegerArgumentType.getInteger(ctx, "idx");
                                                                boolean ok = BotInventoryUtils.moveHotbarToInventory(idx);
                                                                ctx.getSource().sendFeedback(Text.literal((ok ? "Moved" : "Failed to move") + " hotbar " + idx + " back to inventory"));
                                                                return ok ? 1 : 0;
                                                            })))
                            ) // end inv
                            // /bot riftworkflow start – sibling of inv
                            .then(literal("riftworkflow")
                                    .then(literal("start")
                                            .executes(ctx -> {
                                                net.fabricmc.example.RiftHarvestWorkflow.start();
                                                ctx.getSource().sendFeedback(Text.literal("RiftHarvest workflow started"));
                                                return 1;
                                            })))
            ));
        });
    }
} 