package live.amsleepy.serverstatusbukkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ServerStatus_Bukkit extends JavaPlugin implements TabExecutor {

    private BotHandler botHandler;
    private static FileConfiguration config;
    private static ServerStatus_Bukkit instance;

    private boolean isMaintenanceEnabled = false; // Maintenance status

    public static final String PREFIX = ChatColor.DARK_PURPLE + "[ServerStatus] " + ChatColor.WHITE;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        config = getConfig();

        CompletableFuture.runAsync(() -> {
            try {
                botHandler = new BotHandler(config);
                int updateIntervalTicks = botHandler.getUpdateInterval() * 20;  // Convert seconds to ticks
                Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                        () -> botHandler.updateServerStatus(true), 0L, updateIntervalTicks);
            } catch (Exception e) {
                getLogger().severe(PREFIX + "Failed to initialize the bot: " + e.getMessage());
            }
        });

        // Register the /serverstatus command and alias
        this.getCommand("serverstatus").setExecutor(this);
        this.getCommand("serverstatus").setTabCompleter(this);
        this.getCommand("ss").setExecutor(this);
        this.getCommand("ss").setTabCompleter(this);

        getLogger().info(PREFIX + "ServerStatus_Bukkit enabled!");
    }

    @Override
    public void onDisable() {
        if (botHandler != null) {
            botHandler.updateServerStatus(false); // Update status to offline
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(2000); // Wait for 2 seconds to ensure the message is updated
                } catch (InterruptedException e) {
                    // Handle exception
                    Thread.currentThread().interrupt(); // restore the interrupted status
                }
                botHandler.shutdown();
            });
        }

        getLogger().info(PREFIX + "ServerStatus_Bukkit disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("serverstatus") || command.getName().equalsIgnoreCase("ss")) {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("reload")) {
                    if (sender.hasPermission("serverstatus.reload")) {
                        this.reloadConfig();
                        config = getConfig();
                        if (botHandler != null) {
                            botHandler.reloadConfig(config);
                        }
                        sender.sendMessage(PREFIX + "Config reloaded!");
                        return true;
                    } else {
                        sender.sendMessage(PREFIX + "You don't have permission to perform this command.");
                        return true;
                    }
                } else if (args[0].equalsIgnoreCase("maintenance")) {
                    if (args.length > 1) {
                        if (sender.hasPermission("serverstatus.maintenance")) {
                            if (args[1].equalsIgnoreCase("on")) {
                                isMaintenanceEnabled = true;
                                botHandler.updateServerStatus(true); // Update the status
                                sender.sendMessage(PREFIX + "Maintenance mode enabled.");
                                return true;
                            } else if (args[1].equalsIgnoreCase("off")) {
                                isMaintenanceEnabled = false;
                                botHandler.updateServerStatus(true); // Update the status
                                sender.sendMessage(PREFIX + "Maintenance mode disabled.");
                                return true;
                            } else {
                                sender.sendMessage(PREFIX + "Usage: /serverstatus maintenance [on|off]");
                                return true;
                            }
                        } else {
                            sender.sendMessage(PREFIX + "You don't have permission to perform this command.");
                            return true;
                        }
                    } else {
                        sender.sendMessage(PREFIX + "Usage: /serverstatus maintenance [on|off]");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("serverstatus") || command.getName().equalsIgnoreCase("ss")) {
            if (args.length == 1) {
                return Arrays.asList("reload", "maintenance")
                        .stream()
                        .filter(s -> s.startsWith(args[0]))
                        .collect(Collectors.toList());
            } else if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) {
                return Arrays.asList("on", "off")
                        .stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }
        return null;
    }

    // Getter and setter methods
    public static FileConfiguration getPluginConfig() {
        return config;
    }

    public static ServerStatus_Bukkit getInstance() {
        return instance;
    }

    public boolean isMaintenanceEnabled() {
        return isMaintenanceEnabled;
    }

    public void setMaintenanceEnabled(boolean maintenanceEnabled) {
        isMaintenanceEnabled = maintenanceEnabled;
    }
}