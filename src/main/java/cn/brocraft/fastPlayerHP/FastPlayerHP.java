package cn.brocraft.fastPlayerHP;

import cn.brocraft.fastPlayerHP.command.FastPlayerHPCommand;
import cn.brocraft.fastPlayerHP.listener.HealthEventListener;
import cn.brocraft.fastPlayerHP.service.HealthDisplayService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class FastPlayerHP extends JavaPlugin {

    private HealthDisplayService healthDisplayService;
    private BukkitTask followTask;
    private BukkitTask fallbackTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        healthDisplayService = new HealthDisplayService(this);
        healthDisplayService.loadSettings();
        healthDisplayService.bootstrapOnlinePlayers();

        getServer().getPluginManager().registerEvents(new HealthEventListener(this, healthDisplayService), this);

        PluginCommand command = getCommand("fphp");
        if (command != null) {
            FastPlayerHPCommand executor = new FastPlayerHPCommand(this, healthDisplayService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        startFallbackTask();
        startFollowTask();
        getLogger().info("FastPlayerHP enabled.");
    }

    @Override
    public void onDisable() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }

        if (fallbackTask != null) {
            fallbackTask.cancel();
            fallbackTask = null;
        }

        if (healthDisplayService != null) {
            healthDisplayService.cleanup();
        }

        getLogger().info("FastPlayerHP disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        healthDisplayService.loadSettings();
        restartFollowTask();
        restartFallbackTask();
    }

    private void startFollowTask() {
        followTask = getServer().getScheduler().runTaskTimer(this, healthDisplayService::tickFollow, 1L, 1L);
    }

    private void startFallbackTask() {
        long interval = healthDisplayService.getPollIntervalTicks();
        fallbackTask = getServer().getScheduler().runTaskTimer(this, healthDisplayService::tickFallback, 20L, interval);
    }

    private void restartFollowTask() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        startFollowTask();
    }

    private void restartFallbackTask() {
        if (fallbackTask != null) {
            fallbackTask.cancel();
            fallbackTask = null;
        }
        startFallbackTask();
    }
}
