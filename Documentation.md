# Wilted Berberis Overlay – Developer Documentation

*(generated automatically – June 2025)*

---

## 1. Overview
This Fabric **client-side** mod automates farming of *Wilted Berberis* bushes in Hypixel SkyBlock's Rift dimension and provides several visual helpers and utility commands.

Main features:
* ESP-style highlight of Wilted Berberis (dead bushes) and connected farmland clusters.
* Baritone-powered bot (`BushBot`) that walks to highlighted bushes, mines them with a Wand of Farming, and idles intelligently.
* High-level **RiftHarvestWorkflow** which performs the complete "enter Rift ➜ obtain Baba Yaga buff ➜ farm" sequence.
* Watch-dog that monitors chat/inventory to handle Rift collapse or a full inventory automatically.
* A toolbox of human-like input helpers, inventory utilities, task scheduler, and rich client-side chat commands (`/bot …`).

Everything the mod does remains **client-side only**; no custom packets are sent – it only uses vanilla play packets triggered by normal client actions such as key-presses, slot clicks, or chat messages.

*(Tip: use `/farmoverlay debug` to toggle **all console logging & in-game debug chat**.)

---

## 1.1  Changelog – late June 2025 refresh

These notes capture the incremental improvements made during the debug session on 24 June 2025.

### Rift workflow & watchdogs

* **Startup delay extended** – after `/warp rift` the scripts now wait **30 s (600 ticks)** before starting `RiftHarvestWorkflow` (both in `BotSkyblockJoiner` and `BuyingFusionRunner`).  This gives the server ample time to finish chunk-loading and prevents early path-finding hiccups.
* **Farm-overlay rescan on resume** – `RiftHarvestWorkflow.resumeAtMiningArea()` triggers `FarmOverlay.scanNow()` so `FastBushBot` instantly has cluster data and no longer idles for 30 s after injector storage.
* **FastBushBot watchdog refined** – inventory-stagnation reset only runs while the bot is `IDLE`. Travel/mining phases refresh the timer, and waiting-for-spawn periods pause it. The reset log now states the bot state, "waitingSpawn" flag and the exact idle duration.
* **Item-slot enforcement** – `RiftWatchdog` makes sure hot-bar **slot 2** (index 1) is always held whenever the bot is active. If the player scrolls away, the watchdog presses the "2" key for two ticks to correct it.
* **Stronger collapse detection** – chat listener is no longer gated by the `running` flag; Rift-collapse messages are caught even during injector storage. All pending tasks are cleared before disconnect.
* **Dimension change filter** – world-change reset now triggers only when the **dimension registry key** changes, avoiding false positives from internal world reloads.
* **Limbo escape** – any chat message containing the word "limbo" triggers a *deep reset* (disconnect ➜ auto-reconnect).

### Save-Injectors workflow

* Disables `FastBushBot`'s internal watchdog while crafting/transporting items, re-enables it on return.

### Logging additions

* Every forced reset now prints a reason prefix: `inventory full`, `Rift collapsing`, `dimension change`, `limbo chat`, or the detailed FastBushBot watchdog line.

---

## 2. Module Map (by file)
| File | Purpose |
|------|---------|
| `WiltedBerberisOverlayMod.java` | Fabric entry-point – initialises all subsystems during `onInitializeClient()`. |
| `WiltedBerberisTracker.java` | Scans the world for dead-bush blocks, keeps a live list, exposes it to renderers & bots. |
| `WiltedBerberisHighlightRenderer.java` | Renders coloured wire-frame boxes around tracked bushes each frame. |
| `FarmOverlay.java` | Periodically scans for connected farmland blocks, groups them into colourised *clusters*. Exposes API and toggles. |
| `FarmOverlayRenderer.java` | Draws 3-D outlines for farmland clusters, optionally limited to the player's current cluster. |
| `AutoBushBot.java` | Finite-state machine <IDLE→GOTO→MINING→PICKUP> that walks to highlighted bushes, mines them with a Wand of Farming, and idles intelligently. |
| `BotTaskScheduler.java` | Lightweight tick-based scheduler that runs Runnables after a given delay. |
| `HumanInputSimulator.java` | Generates human-like input: smooth camera rotations, timed key presses, inventory-key helper. |
| `BotInventoryUtils.java` | Low-level slot-click helpers and higher-level helpers (count, quick-move, swap) used by commands/bots. |
| `WandSelector.java` | (legacy) Searches hot-bar for an item called *Wand of Farming* and presses the corresponding number key. Currently superseded by `equipSlot2()` in Rift workflow. |
| `RiftHarvestWorkflow.java` | Orchestrates the full harvest routine inside the Rift. Steps: reach pos → kill zombie ➜ click Baba Yaga ➜ scan farmland ➜ path to farming area ➜ hold wand ➜ start BushBot. |
| `SaveInjectorsWorkflow.java` | Handles post-harvest injector storage: /craft → move from chest → /enderchest → "Next Page" → shift all injectors into Ender Chest → walk back to farm (−64 72 −184) and hands control back to RiftHarvestWorkflow **(disables FastBushBot watchdog during the process)**. |
| `RiftWatchdog.java` | Runs while BushBot is active. Detects Rift collapse, **limbo messages**, inventory full, or a real dimension change. Enforces holding slot 2, shuts down bot, disconnects if needed, and **auto-reconnects every 5 s until successful**, then restarts the standard `/skyblock → /warp rift` chain. |
| `RiftWarpAutomation.java` | Adds `/warp rift` convenience plus baritone camera settings. Used by joiner. |
| `BotSkyblockJoiner.java` | From the Hypixel lobby: executes `/skyblock`, waits, `/warp rift`, then (**after 30 s**) starts `RiftHarvestWorkflow`. |
| `MenuAutomation.java` | Injects a custom button into the Multiplayer screen to run the whole automation pipeline with a single click. |
| `OverlayCommands.java` | Client-side chat commands for overlay toggles: `/farmoverlay scan`, `/wiltexplore on|off` (see file). |
| `BotCommands.java` | Rich `/bot …` debug command hierarchy – rotate view, inventory operations, start workflows. |
| `DebugManager.java` | Simple helper to send coloured chat debug lines (with optional action-bar). |
| `BuyingFusionRunner.java` | ... Waits 30 s after the warp before restarting farming. |

---

## 3. End-to-End Automation Flow

Below is the typical path the mod follows once you press the **"Run Rift Macro"** button that `MenuAutomation` adds to the Multiplayer screen.

1. **Connect** – opens a vanilla `ConnectScreen` and joins `mc.hypixel.net`.
2. **/skyblock** – 7 s (140 ticks) after the join packet, `BotSkyblockJoiner` issues `/skyblock`.
3. **/warp rift** – 10 s (200 ticks) later it sends `/warp rift`.
4. **Chunk-settle wait** – 30 s (600 ticks) pause gives the client time to load Rift chunks.
5. **Harvest workflow** – `RiftHarvestWorkflow.start()` begins:  
   a. `#goto -50 104 72`  → walk to zombie platform  
   b. Rotate + attack zombie until *Baba Yaga* GUI shows  
   c. Click *The Baba Yaga* item  
   d. Equip hot-bar slot 2  
   e. `FarmOverlay.scanNow()` + `#goto -63 71 -182` (farming area)  
   f. When inside 1.5 blocks → `FastBushBot.toggle()` (begins mining).
6. **Active farming** – `FastBushBot` mines bushes, handles spawn cycles, while `RiftWatchdog` enforces slot 2 and guards against collapse, limbo, dimension change, full inventory, or 30 s of no pickups.
7. **Inventory Full?** – If ≥ 34 slots filled:  
   a. Watchdog logs reason, stops the bot.  
   b. Starts `SaveInjectorsWorkflow`: craft GUI → move injectors → Ender Chest page 2 → stash → walk back.  
   c. On arrival, triggers fresh farmland scan and resumes `FastBushBot`.
8. **Rift Collapse / Limbo / Dim-change** – Any of these events:  
   a. Watchdog logs reason, clears tasks, stops bot.  
   b. Disconnects after 2-5 s and enters an auto-reconnect loop every 5 s.  
   c. On successful login the whole sequence restarts from step 2.

9. **[Buy Infusion] chat button** – While farming, Hypixel may occasionally broadcast a clickable `[Buy Infusion]` link in chat.  
   a. `RiftWarpAutomation` listens for the message and, after 2 s, asks `BotInventoryUtils` how many **Grand Experience Bottles** are available.  
   b. If < 32 bottles, it starts **BuyingFusionRunner** which opens the Bazaar GUI and purchases a full inventory of bottles (7 click-steps, 2 s each).  
   c. When ≥ 32 bottles are confirmed, `RiftWarpAutomation` clicks the chat link (or its underlying `/buyfusion` command) and then waits 2 s before clicking the *Dimensional Infusion* item in the GUI.  
   d. Finally it sends `/warp rift` again and—after the usual 30-second settle—restarts `RiftHarvestWorkflow`.  
   e. Throughout this detour, `FastBushBot` is paused and all scheduled tasks are cleared to avoid overlap.

---

### Dimensional Infusion helper (BuyingFusionRunner)

Should the macro detect the need for Extra Experience Bottles, it performs the following GUI macro:

| Step | Action |
|------|--------|
| 1 | `/bz` (open Bazaar) |
| 2 | Click **Oddities** category |
| 3 | Click **EXP Bottles** |
| 4 | Click **Grand Experience Bottle** |
| 5 | Click **Buy Instantly** |
| 6 | Click **Fill my inventory!** |
| 7 | Close GUI, send `/skyblock`, wait 10 s, `/warp rift`, wait 30 s, resume full farm script |

All waits/delays are counted in **client ticks** (20 tps = 1 s) and can be tuned in the respective classes if the server's behaviour changes.