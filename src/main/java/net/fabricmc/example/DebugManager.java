package net.fabricmc.example;

import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

/** Utility to centrally handle in-game debug/informational chat messages. */
public final class DebugManager {

    private DebugManager() {}

    static {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            LoggerConfig root = ctx.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            root.setLevel(Level.ERROR);
            ctx.updateLoggers();
        } catch (Exception ignore) {}
    }

    private static boolean visible = false; // hidden by default

    public static boolean toggle() {
        visible = !visible;

        // Adjust root logger level to suppress/enable INFO+ messages
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            LoggerConfig root = ctx.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            root.setLevel(visible ? Level.INFO : Level.ERROR);
            ctx.updateLoggers();
        } catch (Exception ignore) {}

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("[Debug] Messages " + (visible ? "enabled" : "hidden")), false);
        }
        return visible;
    }

    public static boolean isVisible() { return visible; }

    public static void send(String msg, boolean actionBar) {
        if (!visible) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal(msg), actionBar);
        }
    }
} 