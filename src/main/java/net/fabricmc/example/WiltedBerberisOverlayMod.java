package net.fabricmc.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.example.RiftWatchdog;

/**
 * Client initializer for the Wilted Berberis overlay mod.
 */
public class WiltedBerberisOverlayMod implements ClientModInitializer {

    public static final String MOD_ID = "wiltedberberisoverlay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        // Register tick handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> WiltedBerberisTracker.onClientTick());

        // Initialise highlight renderer
        WiltedBerberisHighlightRenderer.init();

        // Initialise farmland overlay feature
        FarmOverlay.init();
        FarmOverlayRenderer.init();
        AutoBushBot.init();
        FastBushBot.init();

        // -------------------- New automation foundation --------------------
        HumanInputSimulator.init();

        // Multiplayer menu button for bot automation
        MenuAutomation.init();

        // Register client-side commands
        OverlayCommands.init();
        BotCommands.init();
        GoalCommands.init();

        BotSkyblockJoiner.init();

        RiftWarpAutomation.init();

        RiftWatchdog.init();

        LOGGER.info("Wilted Berberis overlay initialised");
    }
} 