package me.fRewards.main;

import me.fRewards.commands.CommandTabCompleter;
import me.fRewards.commands.FRewardsCommand;
import me.fRewards.commands.ReloadCommand;
import me.fRewards.commands.ResetCommand;
import me.fRewards.config.RewardManager;
import me.fRewards.license.LicenseManager;
import me.fRewards.gui.CooldownSelectorGUI;
import me.fRewards.gui.CommandsEditorGUI;
import me.fRewards.gui.EditorChatInput;
import me.fRewards.gui.EditorMainGUI;
import me.fRewards.gui.EditorSelectorGUI;
import me.fRewards.gui.ItemsEditorGUI;
import me.fRewards.gui.RewardsGUI;
import me.fRewards.placeholders.FRewardsPlaceholder;
import me.fRewards.utils.HexUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;

public class Main extends JavaPlugin implements Listener {

    private static Main instance;
    private LicenseManager licenseManager;
    private RewardManager rewardManager;
    private boolean fullyEnabled = false;

    private FileConfiguration messages;
    private FileConfiguration rewardsGuiConfig;
    private File rewardsGuiFile;
    private FileConfiguration editorConfig;
    private File editorFile;

    public static Main getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;

        // ── Licencia ──────────────────────────────────────────────────────────
        saveDefaultConfig();
        licenseManager = new LicenseManager(this);

        // Registrar de inmediato el modo bloqueado (sólo /frewards activate) y
        // verificar la licencia en segundo plano: la verificación hace llamadas
        // de red HTTP que NO deben bloquear el arranque del servidor.
        enterLockedMode();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (licenseManager.verifyLicense()) {
                Bukkit.getScheduler().runTask(this, this::enableFull);
            } else {
                printLockedBanner();
            }
        });
    }

    /**
     * Modo bloqueado: el plugin queda cargado pero sin funciones; sólo se registra
     * /frewards activate <clave> para activar la licencia en caliente. No imprime
     * nada: el banner se muestra sólo si la verificación falla (printLockedBanner).
     */
    private void enterLockedMode() {
        org.bukkit.command.PluginCommand cmd = getCommand("frewards");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                if (args.length >= 1 && args[0].equalsIgnoreCase("activate")) {
                    return handleActivate(sender, args);
                }
                sender.sendMessage(HexUtil.format("&cFRewards está &lBLOQUEADO&r&c (licencia no válida)."));
                sender.sendMessage(HexUtil.format("&eActívalo con: &f/frewards activate <clave>"));
                return true;
            });
            cmd.setTabCompleter((sender, command, alias, args) ->
                args.length == 1 ? java.util.List.of("activate") : java.util.Collections.emptyList());
        }
    }

    /** Banner de consola cuando la licencia no es válida o no está configurada. */
    private void printLockedBanner() {
        getLogger().severe("════════════════════════════════════════════════════");
        getLogger().severe("  FRewards BLOQUEADO — licencia no válida o no configurada");
        getLogger().severe("  Actívalo con:  /frewards activate <clave>");
        getLogger().severe("  (desde consola o siendo OP). El plugin sigue cargado, sin funciones.");
        getLogger().severe("════════════════════════════════════════════════════");
    }

    /**
     * Escribe la clave en config.yml, re-verifica y, si es válida, habilita el plugin
     * en caliente. Sólo consola, OP o permiso frewards.admin.
     */
    private boolean handleActivate(org.bukkit.command.CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)
                && !sender.hasPermission("frewards.admin") && !sender.isOp()) {
            sender.sendMessage(HexUtil.format("&cNo tienes permiso para activar la licencia."));
            return true;
        }
        if (fullyEnabled) {
            sender.sendMessage(HexUtil.format("&aFRewards ya está activado."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(HexUtil.format("&cUso: &e/frewards activate &f<clave>"));
            return true;
        }

        String key = args[1].trim();
        sender.sendMessage(HexUtil.format("&7Validando licencia &f" + key + "&7..."));

        getConfig().set("license.key", key);
        saveConfig();
        reloadConfig();

        // La verificación hace una llamada de red: ejecutarla async para no
        // congelar el servidor mientras se contacta al servidor de licencias.
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean ok = licenseManager.verifyLicense();
            Bukkit.getScheduler().runTask(this, () -> {
                if (ok) {
                    enableFull();
                    sender.sendMessage(HexUtil.format("&a✔ Licencia válida. FRewards activado correctamente."));
                } else {
                    sender.sendMessage(HexUtil.format("&c✗ No se pudo activar. Revisa la consola para el detalle."));
                }
            });
        });
        return true;
    }

    /**
     * Habilitación completa del plugin (configs, comandos, listeners, placeholders).
     * Idempotente: sólo corre una vez (al arrancar con licencia válida o tras activar).
     */
    private void enableFull() {
        if (fullyEnabled) return;
        fullyEnabled = true;

        // ── Configuraciones ───────────────────────────────────────────────────
        saveResource("messages.yml", false);
        saveResource("rewardsgui.yml", false);
        saveResource("editor.yml", false);

        File dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
            getLogger().info("✔ Se ha creado data.yml por primera vez.");
        }

        rewardManager = new RewardManager();

        // ── Comandos ──────────────────────────────────────────────────────────
        CommandTabCompleter tabCompleter = new CommandTabCompleter();

        registerCommand("frewards",       new FRewardsCommand(), tabCompleter);
        registerCommand("frewardsreload", new ReloadCommand(),   tabCompleter);
        registerCommand("frewardsreset",  new ResetCommand(),    tabCompleter);

        // ── Listeners ─────────────────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new RewardsGUI(),          this);
        Bukkit.getPluginManager().registerEvents(new EditorSelectorGUI(),  this);
        Bukkit.getPluginManager().registerEvents(new EditorMainGUI(),      this);
        Bukkit.getPluginManager().registerEvents(new ItemsEditorGUI(),     this);
        Bukkit.getPluginManager().registerEvents(new CommandsEditorGUI(),  this);
        Bukkit.getPluginManager().registerEvents(new CooldownSelectorGUI(), this);
        Bukkit.getPluginManager().registerEvents(new EditorChatInput(),    this);
        Bukkit.getPluginManager().registerEvents(new JoinNotifier(),       this);

        // ── PlaceholderAPI ────────────────────────────────────────────────────
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FRewardsPlaceholder().register();
            getLogger().info("✅ PlaceholderAPI detectado y registrado.");
        }

        getLogger().info("✅ FRewards habilitado correctamente.");
    }

    /** Registra ejecutor + tab-completer de un comando, con guard si plugin.yml no lo declara. */
    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor,
                                 org.bukkit.command.TabCompleter completer) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("❌ Comando '" + name + "' no declarado en plugin.yml — omitido.");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(completer);
    }

    @Override
    public void onDisable() {
        if (licenseManager != null) licenseManager.shutdown();
        RewardsGUI.cancelAll();
        getLogger().info("⛔ FRewards deshabilitado.");
    }

    // ── Acceso a configuraciones ──────────────────────────────────────────────

    public LicenseManager getLicenseManager() { return licenseManager; }
    public RewardManager getRewardManager()   { return rewardManager; }

    public FileConfiguration getMessages() {
        if (messages == null) reloadMessages();
        return messages;
    }

    public void reloadMessages() {
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }


    public FileConfiguration getRewardsGuiConfig() {
        if (rewardsGuiConfig == null) reloadRewardsGuiConfig();
        return rewardsGuiConfig;
    }

    public void reloadRewardsGuiConfig() {
        if (rewardsGuiFile == null)
            rewardsGuiFile = new File(getDataFolder(), "rewardsgui.yml");
        rewardsGuiConfig = YamlConfiguration.loadConfiguration(rewardsGuiFile);
    }

    public FileConfiguration getEditorConfig() {
        if (editorConfig == null) reloadEditorConfig();
        return editorConfig;
    }

    public void reloadEditorConfig() {
        if (editorFile == null) editorFile = new File(getDataFolder(), "editor.yml");
        editorConfig = YamlConfiguration.loadConfiguration(editorFile);
    }

    public void saveRewardsGuiConfig() {
        if (rewardsGuiConfig == null || rewardsGuiFile == null) return;
        try {
            rewardsGuiConfig.save(rewardsGuiFile);
        } catch (IOException e) {
            getLogger().severe("❌ No se pudo guardar rewardsgui.yml");
        }
    }

    public String getMessage(String key) {
        String prefix = getConfig().getString("prefix",
            "&#FCD05C&lRECOMPENSAS &7›› ");
        String raw = getMessages().getString(key, "&cMensaje no encontrado: " + key);
        return HexUtil.format(prefix + raw);
    }

    // ── JoinNotifier ──────────────────────────────────────────────────────────

    public static class JoinNotifier implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            int count = 0;
            for (RewardManager.Reward reward : getInstance().getRewardManager().getAllRewards()) {
                if (player.hasPermission(reward.getPermission())
                        && getInstance().getRewardManager().canClaim(player, reward.getId())) {
                    count++;
                }
            }
            if (count == 0) return;

            int finalCount = count;
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isOnline()) return;
                    String key = finalCount == 1 ? "join-notify.singular" : "join-notify.plural";
                    String raw = getInstance().getMessages().getString(key);
                    if (raw != null) {
                        player.sendMessage(HexUtil.format(raw.replace("%rewards%", String.valueOf(finalCount))));
                        player.playSound(net.kyori.adventure.sound.Sound.sound(
                            net.kyori.adventure.key.Key.key("block.amethyst.block.break"),
                            net.kyori.adventure.sound.Sound.Source.MASTER, 1f, 1f));
                    }
                }
            }.runTaskLater(getInstance(), 40L);
        }
    }
}
