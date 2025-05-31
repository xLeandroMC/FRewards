package me.fRewards.gui;

import me.fRewards.config.RewardManager;
import me.fRewards.config.RewardManager.Reward;
import me.fRewards.main.Main;
import me.fRewards.utils.HexUtil;
import me.fRewards.utils.TimeUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class RewardsGUI implements Listener {

    private static final FileConfiguration config = Main.getInstance().getRewardsGuiConfig();
    private static final String TITLE = HexUtil.format(config.getString("gui.title", "§7▸ §6Recompensas §7◂"));

    public static void open(Player player) {
        int size = config.getInt("gui.size", 45);
        Inventory inv = Bukkit.createInventory(player, size, TITLE);

        RewardManager rewardManager = Main.getInstance().getRewardManager();

        for (Reward reward : rewardManager.getAllRewards()) {
            boolean hasPerm = player.hasPermission(reward.getPermission());
            boolean available = rewardManager.canClaim(player, reward.getId());
            ItemStack item = getDisplayItem(player, reward, hasPerm, available);
            inv.setItem(reward.getSlot(), item);
        }

        addStaticButtons(inv, player, size);

        player.openInventory(inv);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.getOpenInventory().getTitle().equals(TITLE)) {
                    cancel();
                    return;
                }
                for (Reward reward : rewardManager.getAllRewards()) {
                    boolean hasPerm = player.hasPermission(reward.getPermission());
                    boolean available = rewardManager.canClaim(player, reward.getId());
                    ItemStack updatedItem = getDisplayItem(player, reward, hasPerm, available);
                    inv.setItem(reward.getSlot(), updatedItem);
                }
            }
        }.runTaskTimer(Main.getInstance(), 20L, 20L);
    }

    private static void addStaticButtons(Inventory inv, Player player, int size) {
        if (config.isConfigurationSection("gui.info")) {
            Material material = getConfiguredMaterial("gui.info.material", "BOOK");
            ItemStack infoItem = new ItemStack(material);
            ItemMeta meta = infoItem.getItemMeta();
            meta.setDisplayName(HexUtil.format(PlaceholderAPI.setPlaceholders(player,
                    config.getString("gui.info.display-name", "§a¿Deseas más recompensas?"))));
            List<String> lore = config.getStringList("gui.info.lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                String parsed = PlaceholderAPI.setPlaceholders(player, line);
                coloredLore.add(HexUtil.format(parsed));
            }
            meta.setLore(coloredLore);
            infoItem.setItemMeta(meta);
            inv.setItem(config.getInt("gui.info.slot", size - 1), infoItem);
        }

        if (config.isConfigurationSection("gui.close")) {
            Material material = getConfiguredMaterial("gui.close.material", "BARRIER");
            ItemStack closeItem = new ItemStack(material);
            ItemMeta meta = closeItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(HexUtil.format(config.getString("gui.close.display-name", "§cSalir")));
                closeItem.setItemMeta(meta);
            }
            inv.setItem(config.getInt("gui.close.slot", size - 5), closeItem);
        }

        if (config.isConfigurationSection("gui.back")) {
            Material material = getConfiguredMaterial("gui.back.material", "ARROW");
            ItemStack backItem = new ItemStack(material);
            ItemMeta meta = backItem.getItemMeta();
            meta.setDisplayName(HexUtil.format(config.getString("gui.back.display-name", "§aVolver")));
            backItem.setItemMeta(meta);
            inv.setItem(config.getInt("gui.back.slot", size - 7), backItem);
        }

        ItemStack filler = new ItemStack(Material.valueOf(config.getString("gui.filler.material", "GRAY_STAINED_GLASS_PANE")));
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }
    }

    private static ItemStack getDisplayItem(Player player, Reward reward, boolean hasPerm, boolean available) {
        String state = !hasPerm ? "noperm" : !available ? "progreso" : "disponible";

        Material material;
        try {
            material = Material.valueOf(config.getString("material-state." + state, reward.getMaterial().name()));
        } catch (IllegalArgumentException e) {
            material = reward.getMaterial();
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String display = reward.getDisplayName().replace("%reward_name%", reward.getName());
        meta.setDisplayName(HexUtil.format(PlaceholderAPI.setPlaceholders(player, display)));

        List<String> lore = reward.getLoreSection(state);
        List<String> finalLore = new ArrayList<>();
        for (String line : lore) {
            line = line.replace("%reward_name%", reward.getName());
            if (line.contains("%cooldown%")) {
                long seconds = Main.getInstance().getRewardManager().getRemainingTime(player, reward.getId()); // ✅ ESTA ES LA BUENA
                line = line.replace("%cooldown%", TimeUtil.formatSeconds(seconds));
            }
            finalLore.add(HexUtil.format(PlaceholderAPI.setPlaceholders(player, line)));
        }

        meta.setLore(finalLore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot >= event.getInventory().getSize()) return;

        RewardManager rewardManager = Main.getInstance().getRewardManager();
        for (Reward reward : rewardManager.getAllRewards()) {
            if (reward.getSlot() == slot) {
                if (!player.hasPermission(reward.getPermission())) {
                    player.sendMessage("§cNo tienes permiso para esta recompensa.");
                    player.closeInventory();
                    return;
                }
                if (!rewardManager.canClaim(player, reward.getId())) {
                    player.sendMessage("§cNo puedes reclamar esta recompensa todavía.");
                    player.closeInventory();
                    return;
                }
                rewardManager.claimReward(player, reward.getId());
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                player.closeInventory();
                return;
            }
        }

        int defaultSlot = config.getInt("gui.size", 45) - 1;
        if (slot == config.getInt("gui.info.slot", defaultSlot)) {
            player.closeInventory();
            String command = config.getString("gui.info.command", "");
            if (!command.isEmpty()) {
                player.performCommand(command);
            }
        }

        if (slot == config.getInt("gui.close.slot", -1)) {
            String sound = config.getString("gui.close.sound", "");
            if (!sound.isEmpty()) {
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(sound), 1, 1);
                } catch (Exception ignored) {}
            }
            player.closeInventory();
        }

        if (slot == config.getInt("gui.back.slot", -1)) {
            String sound = config.getString("gui.back.sound", "");
            if (!sound.isEmpty()) {
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(sound), 1, 1);
                } catch (Exception ignored) {}
            }
            String command = config.getString("gui.back.command", "");
            if (!command.isEmpty()) {
                player.closeInventory();
                player.performCommand(command);
            }
        }
    }

    private static Material getConfiguredMaterial(String path, String fallback) {
        try {
            return Material.valueOf(config.getString(path, fallback));
        } catch (IllegalArgumentException e) {
            return Material.valueOf(fallback);
        }
    }
}
