package net.fabricmc.example;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.fabricmc.example.mixin.ScreenAddChildAccessor;
import net.minecraft.text.Text;
import net.minecraft.client.network.CookieStorage;

/**
 * Injects a "Start Bot" button into the Multiplayer menu that, when clicked,
 * automatically begins the script by connecting to mc.hypixel.net.
 */
public final class MenuAutomation {

    private static final String TARGET_HOST = "mc.hypixel.net";

    private MenuAutomation() {}

    public static void init() {
        ScreenEvents.AFTER_INIT.register(MenuAutomation::onScreenInit);
    }

    private static void onScreenInit(MinecraftClient client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof MultiplayerScreen)) return;

        int buttonWidth = 100;
        int buttonHeight = 20;
        int x = scaledWidth - buttonWidth - 10; // 10px margin right
        int y = scaledHeight - buttonHeight - 10; // 10px margin bottom

        ButtonWidget button = ButtonWidget.builder(Text.literal("Start Bot"), b -> {
            BotSkyblockJoiner.expectJoin();
            startBot(screen);
        })
                .dimensions(x, y, buttonWidth, buttonHeight)
                .build();

        // Use accessor to bypass protected visibility of Screen#addDrawableChild
        ScreenAddChildAccessor accessor = (ScreenAddChildAccessor) screen;
        accessor.fabric_addDrawableChild(button);
    }

    private static void startBot(Screen parent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        ServerAddress address = ServerAddress.parse(TARGET_HOST);
        ServerInfo info = new ServerInfo("Hypixel", TARGET_HOST, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(parent, mc, address, info, false, null);
    }
} 