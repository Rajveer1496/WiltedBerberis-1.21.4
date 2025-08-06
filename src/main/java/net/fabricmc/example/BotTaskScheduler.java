package net.fabricmc.example;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extremely light-weight ticker that lets us run a Runnable after <n> client-ticks.
 * Keeps everything client-side; useful for spacing out automated actions so they
 * appear more human-like.
 */
public final class BotTaskScheduler {

    private BotTaskScheduler() {}

    private static final List<ScheduledTask> TASKS = new CopyOnWriteArrayList<>();
    private static boolean initialised = false;

    private static void ensureInit() {
        if (initialised) return;
        initialised = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick());
    }

    private static void onTick() {
        if (TASKS.isEmpty()) return;
        for (ScheduledTask t : TASKS) {
            t.ticks--;
            if (t.ticks <= 0) {
                try {
                    t.action.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                TASKS.remove(t);
            }
        }
    }

    /** Schedule {@code action} to run after {@code delayTicks} client-ticks. */
    public static void schedule(int delayTicks, Runnable action) {
        if (delayTicks <= 0) {
            action.run();
            return;
        }
        ensureInit();
        TASKS.add(new ScheduledTask(delayTicks, action));
    }

    /** Removes all pending tasks (useful when the automation flow needs a hard reset). */
    public static void clearAll() {
        TASKS.clear();
    }

    // ---------------------------------------------------------------------
    private static class ScheduledTask {
        int ticks;
        final Runnable action;

        ScheduledTask(int ticks, Runnable action) {
            this.ticks = ticks;
            this.action = action;
        }
    }
} 