package net.fabricmc.example;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side commands for dynamically adjusting automation destination coordinates at runtime.
 *
 * Commands:
 *   /secondgoal <x> <y> <z>   → sets the mining-area target used by {@link RiftHarvestWorkflow}.
 *   /goalpos   <x> <y> <z>    → sets the farm-area return point used by {@link SaveInjectorsWorkflow}.
 */
public final class GoalCommands {

    private GoalCommands() {}

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /secondgoal x y z
            dispatcher.register(
                    literal("secondgoal")
                            .then(argument("x", IntegerArgumentType.integer())
                                    .then(argument("y", IntegerArgumentType.integer())
                                            .then(argument("z", IntegerArgumentType.integer())
                                                    .executes(ctx -> {
                                                        int x = IntegerArgumentType.getInteger(ctx, "x");
                                                        int y = IntegerArgumentType.getInteger(ctx, "y");
                                                        int z = IntegerArgumentType.getInteger(ctx, "z");
                                                        RiftHarvestWorkflow.setSecondGoal(x, y, z);
                                                        ctx.getSource().sendFeedback(Text.literal("Second goal set to " + x + " " + y + " " + z));
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
            );

            // /goalpos x y z
            dispatcher.register(
                    literal("goalpos")
                            .then(argument("x", IntegerArgumentType.integer())
                                    .then(argument("y", IntegerArgumentType.integer())
                                            .then(argument("z", IntegerArgumentType.integer())
                                                    .executes(ctx -> {
                                                        int x = IntegerArgumentType.getInteger(ctx, "x");
                                                        int y = IntegerArgumentType.getInteger(ctx, "y");
                                                        int z = IntegerArgumentType.getInteger(ctx, "z");
                                                        SaveInjectorsWorkflow.setGoalPos(x, y, z);
                                                        ctx.getSource().sendFeedback(Text.literal("Injector save goal set to " + x + " " + y + " " + z));
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
            );
        });
    }
}