package net.fabricmc.example;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RiftWarpAutomation {

    private static final Logger LOG = LoggerFactory.getLogger("RiftWarpAutomation");
    private static final String BUY_INFUSION_BRACKET = "[Buy Infusion]";
    private static final String GRAND_BOTTLE_NAME = "Grand Experience Bottle";

    private RiftWarpAutomation() {}

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            handleGameMessage(message);
        });
    }

    private static void handleGameMessage(Text message) {
        String plain = message.getString();
        if (!plain.contains(BUY_INFUSION_BRACKET)) return;

        LOG.info("Detected new chat button {}", BUY_INFUSION_BRACKET);
        BotTaskScheduler.schedule(40, () -> processBuyInfusion(message)); // 2 seconds delay
    }

    private static void processBuyInfusion(Text originalMessage) {
        int count = BotInventoryUtils.countItemsByDisplayName(GRAND_BOTTLE_NAME);
        LOG.info("We currently have {}x {}", count, GRAND_BOTTLE_NAME);

        if (count < 32) {
            LOG.info("Less than 32 bottles – starting BuyingFusion script");
            BuyingFusionRunner.start(() -> BotTaskScheduler.schedule(40, () -> processBuyInfusion(originalMessage)));
            return;
        }

        // We have sufficient bottles → click the chat button
        LOG.info(">=32 bottles – clicking chat button {}");
        String command = extractRunCommand(originalMessage);
        if (command != null) {
            sendChat(command);
        } else {
            // fallback: simulate click by resending the same text string (server may parse it)
            sendChat(BUY_INFUSION_BRACKET);
        }

        // After server opens the infusion GUI, wait a bit then click the item.
        BotTaskScheduler.schedule(40, RiftWarpAutomation::clickDimensionalInfusion);
    }

    private static String extractRunCommand(Text txt) {
        ClickEvent ce = txt.getStyle().getClickEvent();
        if (ce != null && ce.getAction() == ClickEvent.Action.RUN_COMMAND) {
            // In older versions (e.g., 1.21.4), the ClickEvent may not expose a dedicated
            // record type for run commands. Instead, we can directly use its value.
            return ce.getValue();
        }
        for (Text sibling : txt.getSiblings()) {
            String nested = extractRunCommand(sibling);
            if (nested != null) return nested;
        }
        return null;
    }

    private static void sendChat(String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.networkHandler.sendChatMessage(cmd);
        }
    }

    private static void clickDimensionalInfusion() {
        boolean ok = BotInventoryUtils.leftClickByDisplayName("Dimensional Infusion");
        LOG.info(ok ? "Clicked Dimensional Infusion in GUI" : "Failed to find Dimensional Infusion item");
        // After clicking, restart warp process
        BotTaskScheduler.schedule(60, () -> sendChat("/warp rift"));
    }
} 