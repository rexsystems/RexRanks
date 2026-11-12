package me.rexsystems.rexranks.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class Schedulers {
    private final Plugin plugin;
    private final boolean folia;
    private final Executor sync;
    private final Executor async;

    public Schedulers(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
        this.sync = this::runSync;
        this.async = this::runAsync;
    }

    public static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isFolia() {
        return folia;
    }

    public Executor sync() {
        return sync;
    }

    public Executor async() {
        return async;
    }

    public void runSync(Runnable task) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runAsync(Runnable task) {
        if (folia) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public void runLater(Runnable task, long ticks) {
        long ms = ticks * 50L;
        if (folia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, ticks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        }
    }

    public void runLaterAsync(Runnable task, long ticks) {
        if (folia) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, ticks) * 50L, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, ticks);
        }
    }

    public void runRepeatingAsync(Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
                    Math.max(1, delayTicks) * 50L, Math.max(1, periodTicks) * 50L, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    public void runOnEntity(Entity entity, Runnable task) {
        if (folia) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            runSync(task);
        }
    }
}
