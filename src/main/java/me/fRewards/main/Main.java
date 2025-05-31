package me.fRewards.main;

import me.fRewards.commands.EditorCommand;
import me.fRewards.commands.ReloadCommand;
import me.fRewards.commands.ResetCommand;
import me.fRewards.config.RewardManager;
import me.fRewards.gui.EditorGUI;
import me.fRewards.gui.EditorInputListener;
import me.fRewards.gui.RewardsGUI;
import me.fRewards.placeholders.FRewardsPlaceholder;
import me.fRewards.utils.HexUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class Main extends JavaPlugin implements Listener {

    private static Main instance;
    private RewardManager rewardManager;
    private FileConfiguration messages;
    private FileConfiguration rewardsGuiConfig;
    private File rewardsGuiFile;
    private FileConfiguration data;

    public static Main getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("rewardsgui.yml", false);

        File dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
            getLogger().info("✔ Se ha creado data.yml por primera vez.");
        } else {
            getLogger().info("✔ data.yml detectado, no se sobrescribirá.");
        }

        rewardManager = new RewardManager();

        // Registrar comando principal con seguridad
        try {
            getCommand("frewards").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Este comando solo puede ser usado por jugadores.");
                    return true;
                }

                if (!player.hasPermission("frewards.use")) {
                    player.sendMessage(getMessage("no-permission"));
                    return true;
                }

                if (args.length == 1 && args[0].equalsIgnoreCase("editor")) {
                    new EditorCommand().onCommand(sender, command, label, args);
                    return true;
                }

                if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
                    player.sendMessage(getMessage("help-title"));
                    player.sendMessage(getMessage("help-1"));
                    player.sendMessage(getMessage("help-2"));
                    player.sendMessage(getMessage("help-3"));
                    player.sendMessage(getMessage("help-4"));
                    player.sendMessage(getMessage("help-5"));
                    player.sendMessage(getMessage("help-footer"));
                    return true;
                }

                if (args.length > 0) {
                    player.sendMessage(getMessage("command-unknown"));
                    return true;
                }

                RewardsGUI.open(player);
                return true;
            });
        } catch (Exception e) {
            getLogger().severe("❌ Error registrando el comando /frewards:");
            e.printStackTrace();
        }

        getCommand("frewardsreload").setExecutor(new ReloadCommand());
        getCommand("frewardsreset").setExecutor(new ResetCommand());

        // Eventos
        Bukkit.getPluginManager().registerEvents(new RewardsGUI(), this);
        Bukkit.getPluginManager().registerEvents(new JoinNotifier(), this);
        Bukkit.getPluginManager().registerEvents(new EditorGUI(), this);
        Bukkit.getPluginManager().registerEvents(new EditorInputListener(), this);

        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FRewardsPlaceholder().register();
            getLogger().info("✅ PlaceholderAPI detectado y registrado.");
        }

        getLogger().info("✅ FRewards v1.0 habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        getLogger().info("⛔ FRewards deshabilitado.");
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public FileConfiguration getMessages() {
        if (messages == null) {
            File file = new File(getDataFolder(), "messages.yml");
            messages = YamlConfiguration.loadConfiguration(file);
        }
        return messages;
    }

    public void reloadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public void reloadData() {
        File file = new File(getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public String getMessage(String key) {
        String prefix = getConfig().getString("prefix", "§6[FRewards] ");
        String raw = getMessages().getString(key, "Mensaje no encontrado");
        return HexUtil.format(prefix + raw);
    }

    // Soporte rewardsgui.yml
    public FileConfiguration getRewardsGuiConfig() {
        if (rewardsGuiConfig == null) reloadRewardsGuiConfig();
        return rewardsGuiConfig;
    }

    public void reloadRewardsGuiConfig() {
        if (rewardsGuiFile == null)
            rewardsGuiFile = new File(getDataFolder(), "rewardsgui.yml");
        rewardsGuiConfig = YamlConfiguration.loadConfiguration(rewardsGuiFile);
    }

    public void saveRewardsGuiConfig() {
        if (rewardsGuiConfig == null || rewardsGuiFile == null) return;
        try {
            rewardsGuiConfig.save(rewardsGuiFile);
        } catch (IOException e) {
            getLogger().severe("❌ No se pudo guardar rewardsgui.yml");
            e.printStackTrace();
        }
    }

    // Evento de bienvenida con notificación de recompensas
    public static class JoinNotifier implements Listener {
        @org.bukkit.event.EventHandler
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            for (RewardManager.Reward reward : getInstance().getRewardManager().getAllRewards()) {
                if (player.hasPermission(reward.getPermission()) &&
                        getInstance().getRewardManager().canClaim(player, reward.getId())) {
                    player.sendMessage(getInstance().getMessage("join-notify"));
                    break;
                }
            }
        }
    }
}
