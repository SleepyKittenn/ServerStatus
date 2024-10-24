package live.amsleepy.serverstatusbukkit;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class BotHandler extends ListenerAdapter {
    private final String TOKEN;
    private final String CHANNEL_ID;
    private final String SERVER_NAME;
    private final String SERVER_IP;
    private final String SUPPORTED_VERSION;
    private final int UPDATE_INTERVAL;
    private final double TPS_THRESHOLD;
    private final String ALERT_ROLE_ID;
    private final String MAINTENANCE_ROLE_ID;

    private net.dv8tion.jda.api.JDA jda;
    private TextChannel channel;
    private String messageId;
    private boolean alertSent = false;

    public BotHandler(FileConfiguration config) throws LoginException, InterruptedException {
        this.TOKEN = config.getString("bot-token");
        this.CHANNEL_ID = config.getString("channel-id");
        this.SERVER_NAME = config.getString("server-name");
        this.SERVER_IP = config.getString("server-ip");
        this.SUPPORTED_VERSION = config.getString("supported-version");
        this.messageId = config.getString("message-id");
        this.UPDATE_INTERVAL = config.getInt("update-interval", 60);
        this.TPS_THRESHOLD = config.getDouble("tps-threshold", 15.0);
        this.ALERT_ROLE_ID = config.getString("alert-role-id");
        this.MAINTENANCE_ROLE_ID = config.getString("maintenance-role-id");

        init();
    }

    private void init() throws LoginException, InterruptedException {
        jda = JDABuilder.createDefault(TOKEN)
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
                .disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                .build();
        jda.awaitReady();
        jda.addEventListener(this);

        // Register the maintenance command
        jda.upsertCommand("maintenance", "Toggle maintenance mode")
                .addOption(OptionType.STRING, "toggle", "Turn maintenance mode on or off", true)
                .queue();

        channel = jda.getTextChannelById(CHANNEL_ID);

        ServerStatus_Bukkit.getInstance().getLogger().info(ServerStatus_Bukkit.PREFIX + "Login Successful!");

        if (channel != null && messageId != null) {
            try {
                Message existingMessage = channel.retrieveMessageById(messageId).complete();
                if (existingMessage != null) {
                    this.messageId = existingMessage.getId();
                } else {
                    createNewStatusMessage();
                }
            } catch (Exception e) {
                createNewStatusMessage();
            }
        } else if (channel != null) {
            createNewStatusMessage();
        }
    }

    private void createNewStatusMessage() {
        Message newMessage = channel.sendMessage("Initializing server status..., sleepygoon9000").complete();
        this.messageId = newMessage.getId();
        updateConfigMessageId(this.messageId);
    }

    private void updateConfigMessageId(String messageId) {
        FileConfiguration config = ServerStatus_Bukkit.getPluginConfig();
        config.set("message-id", messageId);
        ServerStatus_Bukkit.getInstance().saveConfig();
    }

    public int getUpdateInterval() {
        return this.UPDATE_INTERVAL;
    }

    public void reloadConfig(FileConfiguration config) {
        // Optionally reload other parameters if needed
    }

    private double getAverageTPS() {
        return ((org.bukkit.Server) Bukkit.getServer()).getTPS()[0];
    }

    public void updateServerStatus(boolean isOnline) {
        if (channel == null || messageId == null) return;

        ServerStatus_Bukkit.getInstance().getLogger().info("Updating server status. Online: " + isOnline);

        boolean isMaintenance = ServerStatus_Bukkit.getInstance().isMaintenanceEnabled();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setAuthor(SERVER_NAME, null, "https://cdn.icon-icons.com/icons2/2699/PNG/512/minecraft_logo_icon_168974.png");

        String timestamp = "<t:" + Instant.now().getEpochSecond() + ":R>";

        String statusText = isOnline ? "Online" : "Offline";
        Color color = isOnline ? new Color(10, 255, 0) : new Color(255, 0, 0); // Green or Red

        if (isMaintenance) {
            color = new Color(255, 255, 0); // Yellow for maintenance
        }

        embedBuilder.setColor(color);

        String nativeVersion = Bukkit.getBukkitVersion().split("-")[0]; // Simplify version

        // First row of fields
        embedBuilder.addField("Status", statusText, true);
        embedBuilder.addField("Native Version", nativeVersion, true);
        embedBuilder.addField("Supported Version", SUPPORTED_VERSION, true);

        // Second row of fields
        if (isOnline) {
            embedBuilder.addField("TPS", String.format("%.2f", getAverageTPS()), true);
            embedBuilder.addField("Players", Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(), true);
        } else {
            embedBuilder.addField("TPS", "N/A", true);
            embedBuilder.addField("Players", "0/0", true);
        }

        embedBuilder.addField("Maintenance Status", isMaintenance ? "On" : "Off", true);

        if (isOnline) {
            List<String> playerDetails = Bukkit.getOnlinePlayers().stream()
                    .map(player -> player.getName() + " (" + player.getPing() + "ms)")
                    .collect(Collectors.toList());
            String playerList = playerDetails.stream()
                    .collect(Collectors.joining("\n"));
            if (playerDetails.size() > 20) { // Paginate if more than 20 players
                int pageSize = 20;
                int pageCount = (playerDetails.size() - 1) / pageSize + 1;
                for (int i = 0; i < pageCount; i++) {
                    String pageContent = playerDetails.subList(i * pageSize, Math.min((i + 1) * pageSize, playerDetails.size()))
                            .stream()
                            .collect(Collectors.joining("\n"));
                    embedBuilder.addField("Player List (Page " + (i + 1) + ")", pageContent.isEmpty() ? "No players online" : pageContent, false);
                }
            } else {
                embedBuilder.addField("Player List", playerList.isEmpty() ? "No players online" : playerList, false);
            }
        }

        embedBuilder.addField("Last Updated", timestamp, false);
        embedBuilder.setFooter(SERVER_IP + " | Made with <3 by sleepy", null);

        channel.retrieveMessageById(messageId).queue(
                msg -> msg.editMessageEmbeds(embedBuilder.build()).queue(),
                failure -> {
                    ServerStatus_Bukkit.getInstance().getLogger().warning("Message retrieval failed: " + failure.getMessage());
                    createNewStatusMessage();
                }
        );
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        if (commandName.equals("serverstatus")) {
            String status = event.getOption("status") != null ? event.getOption("status").getAsString() : "";
            if (!status.isEmpty()) {
                switch (status) {
                    case "online":
                        updateServerStatus(true);
                        break;
                    case "offline":
                        updateServerStatus(false);
                        break;
                    default:
                        event.reply(ServerStatus_Bukkit.PREFIX + "Invalid status option. Use 'online' or 'offline'.").setEphemeral(true).queue();
                        return;
                }
                event.reply(ServerStatus_Bukkit.PREFIX + "Server status updated to " + status).setEphemeral(true).queue();
            } else {
                event.reply(ServerStatus_Bukkit.PREFIX + "No status option provided. Use 'online' or 'offline'.").setEphemeral(true).queue();
            }
        } else if (commandName.equals("maintenance")) {
            Member member = event.getMember();
            if (member != null) {
                if (member.getRoles().stream().noneMatch(role -> role.getId().equals(MAINTENANCE_ROLE_ID))) {
                    event.reply(ServerStatus_Bukkit.PREFIX + "You don't have permission to perform this command.").setEphemeral(true).queue();
                    return;
                }
                String toggle = event.getOption("toggle") != null ? event.getOption("toggle").getAsString() : "";
                if (!toggle.isEmpty()) {
                    switch (toggle) {
                        case "on":
                            ServerStatus_Bukkit.getInstance().setMaintenanceEnabled(true);
                            updateServerStatus(true);
                            event.reply(ServerStatus_Bukkit.PREFIX + "Maintenance mode enabled.").queue();
                            break;
                        case "off":
                            ServerStatus_Bukkit.getInstance().setMaintenanceEnabled(false);
                            updateServerStatus(true);
                            event.reply(ServerStatus_Bukkit.PREFIX + "Maintenance mode disabled.").queue();
                            break;
                        default:
                            event.reply(ServerStatus_Bukkit.PREFIX + "Invalid toggle option. Use 'on' or 'off'.").setEphemeral(true).queue();
                            break;
                    }
                } else {
                    event.reply(ServerStatus_Bukkit.PREFIX + "No toggle option provided. Use 'on' or 'off'.").setEphemeral(true).queue();
                }
            } else {
                event.reply(ServerStatus_Bukkit.PREFIX + "You don't have permission to perform this command.").setEphemeral(true).queue();
            }
        }
    }

    public TextChannel getChannel() {
        return channel;
    }
}