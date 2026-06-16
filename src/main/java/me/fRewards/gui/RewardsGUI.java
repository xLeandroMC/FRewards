package me.fRewards.gui;

import me.fRewards.config.RewardManager;
import me.fRewards.config.RewardManager.Reward;
import me.fRewards.main.Main;
import me.fRewards.utils.HexUtil;
import me.fRewards.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class RewardsGUI implements Listener {

    // Tarea de actualización activa por jugador (para cancelarla al cerrar)
    private static final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    private static Component titleComponent() {
        return HexUtil.comp(Main.getInstance().getRewardsGuiConfig().getString("gui.title", "§6Recompensas"));
    }

    public static void open(Player player) {
        FileConfiguration config = Main.getInstance().getRewardsGuiConfig();
        Component title = titleComponent();
        int size = config.getInt("gui.size", 45);

        Inventory inv = Bukkit.createInventory(null, size, title);
        fillRewards(inv, player);
        fillStaticButtons(inv, player, config, size);
        player.openInventory(inv);

        // Cancelar tarea previa si el jugador reabre el GUI
        BukkitTask old = activeTasks.remove(player.getUniqueId());
        if (old != null) old.cancel();

        // Actualizar los cooldowns cada segundo
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (!player.isOnline()) {
                BukkitTask self = activeTasks.remove(player.getUniqueId());
                if (self != null) self.cancel();
                return;
            }
            Component openTitle = player.getOpenInventory().title();
            if (!title.equals(openTitle)) {
                BukkitTask self = activeTasks.remove(player.getUniqueId());
                if (self != null) self.cancel();
                return;
            }
            fillRewards(inv, player);
        }, 20L, 20L);

        activeTasks.put(player.getUniqueId(), task);
    }

    // ── Construcción del inventario ───────────────────────────────────────────

    private static void fillRewards(Inventory inv, Player player) {
        RewardManager rm = Main.getInstance().getRewardManager();
        for (Reward reward : rm.getAllRewards()) {
            boolean hasPerm   = player.hasPermission(reward.getPermission());
            boolean available = rm.canClaim(player, reward.getId());
            inv.setItem(reward.getSlot(), buildRewardItem(player, reward, hasPerm, available));
        }
    }

    private static void fillStaticButtons(Inventory inv, Player player, FileConfiguration config, int size) {
        if (config.isConfigurationSection("gui.info")) {
            inv.setItem(config.getInt("gui.info.slot", size - 3),
                buildButton(config.getString("gui.info.material", "WRITABLE_BOOK"),
                    config.getString("gui.info.display-name", "§aInfo"),
                    config.getStringList("gui.info.lore"), player));
        }
        if (config.isConfigurationSection("gui.close")) {
            inv.setItem(config.getInt("gui.close.slot", size - 5),
                buildButton(config.getString("gui.close.material", "IRON_DOOR"),
                    config.getString("gui.close.display-name", "§cSalir"),
                    Collections.emptyList(), player));
        }
        if (config.isConfigurationSection("gui.back")) {
            inv.setItem(config.getInt("gui.back.slot", size - 7),
                buildButton(config.getString("gui.back.material", "ARROW"),
                    config.getString("gui.back.display-name", "§aVolver"),
                    Collections.emptyList(), player));
        }

        // Relleno de slots vacíos
        String fillerName = config.getString("gui.filler.material", "GRAY_STAINED_GLASS_PANE");
        Material fillerMat;
        try { fillerMat = Material.valueOf(fillerName.toUpperCase()); }
        catch (IllegalArgumentException e) { fillerMat = Material.GRAY_STAINED_GLASS_PANE; }

        ItemStack filler = new ItemStack(fillerMat);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.empty());
        filler.setItemMeta(fm);

        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }
    }

    private static ItemStack buildRewardItem(Player player, Reward reward, boolean hasPerm, boolean available) {
        String state = !hasPerm ? "noperm" : !available ? "progreso" : "disponible";
        Material material = reward.getStateMaterial(state);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String display = reward.getDisplayName().replace("%reward_name%", reward.getName());
        meta.displayName(HexUtil.comp(applyPAPI(player, display)));

        List<String> loreRaw = reward.getLoreSection(state);
        List<Component> lore = loreRaw.stream().map(line -> {
            line = line.replace("%reward_name%", reward.getName());
            if (line.contains("%cooldown%")) {
                long secs = Main.getInstance().getRewardManager().getRemainingTime(player, reward.getId());
                line = line.replace("%cooldown%", TimeUtil.formatSeconds(secs));
            }
            return HexUtil.comp(applyPAPI(player, line));
        }).collect(Collectors.toList());

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildButton(String materialName, String displayName, List<String> loreRaw, Player player) {
        Material mat;
        try { mat = Material.valueOf(materialName.toUpperCase()); }
        catch (IllegalArgumentException e) { mat = Material.PAPER; }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(HexUtil.comp(applyPAPI(player, displayName)));
        meta.lore(loreRaw.stream()
            .map(l -> HexUtil.comp(applyPAPI(player, l)))
            .collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    // PlaceholderAPI solo si está instalado
    private static String applyPAPI(Player player, String text) {
        if (text == null) return "";
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    // ── Eventos ───────────────────────────────────────────────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().title().equals(titleComponent())) return;
        cancelTask(player);
    }

    // Seguridad defensiva: si el jugador se desconecta bruscamente
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelTask(event.getPlayer());
    }

    private static void cancelTask(Player player) {
        BukkitTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    /** Cancela todas las tareas activas. Llamar desde Main.onDisable(). */
    public static void cancelAll() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(titleComponent())) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        FileConfiguration config = Main.getInstance().getRewardsGuiConfig();
        int size = config.getInt("gui.size", 45);
        if (slot >= size) return;

        // Click en recompensa
        for (Reward reward : Main.getInstance().getRewardManager().getAllRewards()) {
            if (reward.getSlot() == slot) {
                handleRewardClick(player, reward);
                return;
            }
        }

        // Botones estáticos
        if (slot == config.getInt("gui.close.slot", -1)) {
            playSound(player, config.getString("gui.close.sound", ""));
            player.closeInventory();
            return;
        }
        if (slot == config.getInt("gui.back.slot", -1)) {
            playSound(player, config.getString("gui.back.sound", ""));
            String cmd = config.getString("gui.back.command", "");
            if (!cmd.isEmpty()) { player.closeInventory(); player.performCommand(cmd); }
            return;
        }
        if (slot == config.getInt("gui.info.slot", -1)) {
            player.closeInventory();
            String cmd = config.getString("gui.info.command", "");
            if (!cmd.isEmpty()) player.performCommand(cmd);
        }
    }

    private void handleRewardClick(Player player, Reward reward) {
        if (!player.hasPermission(reward.getPermission())) {
            player.sendMessage(Main.getInstance().getMessage("no-permission"));
            return;
        }
        if (!Main.getInstance().getRewardManager().canClaim(player, reward.getId())) {
            player.sendMessage(Main.getInstance().getMessage("reward-cooldown"));
            return;
        }
        Main.getInstance().getRewardManager().claimReward(player, reward.getId());
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty()) return;
        try {
            player.playSound(net.kyori.adventure.sound.Sound.sound(
                net.kyori.adventure.key.Key.key(soundName.toLowerCase().replace("_", ".")),
                net.kyori.adventure.sound.Sound.Source.MASTER, 1f, 1f));
        } catch (Exception ignored) {}
    }
}
