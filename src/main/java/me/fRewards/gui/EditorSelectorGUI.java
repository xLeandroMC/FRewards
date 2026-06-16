package me.fRewards.gui;

import me.fRewards.config.RewardManager.Reward;
import me.fRewards.main.Main;
import me.fRewards.utils.HexUtil;
import me.fRewards.utils.TimeUtil;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class EditorSelectorGUI implements Listener {

    private static final Map<UUID, Inventory> sessions = new HashMap<>();

    private static String title() {
        return HexUtil.format(Main.getInstance().getEditorConfig()
            .getString("selector.title", "&#FF9000▸ &#FF4C00Editor de Recompensas &#FF9000◂"));
    }

    public static void open(Player player) {
        FileConfiguration cfg = Main.getInstance().getEditorConfig();
        Collection<Reward> rewards = Main.getInstance().getRewardManager().getAllRewards();
        int rewardCount = rewards.size();

        int rows = Math.max(2, (int) Math.ceil(rewardCount / 9.0) + 1);
        rows = Math.min(rows, 6);
        int size = rows * 9;

        Inventory inv = Bukkit.createInventory(null, size, HexUtil.compTitle(title()));

        int slot = 0;
        List<String> rewardLore = cfg.getStringList("selector.reward-item.lore");
        for (Reward reward : rewards) {
            if (slot >= size - 9) break;

            String coolStr = reward.getCooldown() == -1
                ? "&cUna sola vez"
                : "&a" + TimeUtil.formatSmart(reward.getCooldown());
            String displayName = HexUtil.format(
                reward.getDisplayName().replace("%reward_name%", reward.getName()));

            Map<String, String> vars = Map.of(
                "%reward_id%", reward.getId(),
                "%cooldown%",  HexUtil.format(coolStr),
                "%items%",     String.valueOf(reward.getItems().size()),
                "%commands%",  String.valueOf(reward.getCommands().size())
            );
            List<String> lore = rewardLore.stream()
                .map(l -> { String r = l; for (var e : vars.entrySet()) r = r.replace(e.getKey(), e.getValue()); return r; })
                .collect(Collectors.toList());

            inv.setItem(slot, EditorMainGUI.buildFromLore(reward.getMaterial(), displayName, lore, Map.of()));
            slot++;
        }

        Material fillerMat = EditorMainGUI.parseMat(cfg.getString("selector.filler", "GRAY_STAINED_GLASS_PANE"));
        ItemStack filler = EditorMainGUI.item(fillerMat, " ");
        for (int i = slot; i < size - 9; i++) inv.setItem(i, filler);

        Material sepMat = EditorMainGUI.parseMat(cfg.getString("selector.separator", "BLACK_STAINED_GLASS_PANE"));
        ItemStack sep = EditorMainGUI.item(sepMat, " ");
        for (int i = size - 9; i < size; i++) inv.setItem(i, sep);

        inv.setItem(size - 5, EditorMainGUI.cfgItem(cfg, "selector.close", Map.of()));

        sessions.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory expected = sessions.get(player.getUniqueId());
        if (expected == null || !expected.equals(event.getView().getTopInventory())) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        int size = expected.getSize();
        if (slot < 0 || slot >= size) return;

        if (slot == size - 5) {
            player.closeInventory();
            return;
        }

        if (slot < size - 9) {
            List<Reward> rewards = new ArrayList<>(Main.getInstance().getRewardManager().getAllRewards());
            if (slot < rewards.size()) {
                EditorMainGUI.open(player, rewards.get(slot).getId());
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory expected = sessions.get(player.getUniqueId());
        if (expected != null && expected.equals(event.getInventory())) {
            sessions.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
