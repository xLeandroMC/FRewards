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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static me.fRewards.gui.EditorMainGUI.item;

public class CooldownSelectorGUI implements Listener {

    private static final int SLOT_BACK   = 18;
    private static final int SLOT_CUSTOM = 22;

    private static final Map<UUID, String> sessions = new HashMap<>();
    private static final Map<UUID, Inventory> inventories = new HashMap<>();

    static String titlePrefix() {
        return HexUtil.format(Main.getInstance().getEditorConfig()
            .getString("cooldown.title-prefix", "&#FF9000▸ &#FF4C00Cooldown §8» &#FF9000"));
    }

    public static void open(Player player, String rewardId) {
        FileConfiguration cfg = Main.getInstance().getEditorConfig();
        String prefix = titlePrefix();
        Inventory inv = Bukkit.createInventory(null, 27, HexUtil.compTitle(prefix + rewardId));

        Reward reward = Main.getInstance().getRewardManager().getReward(rewardId);
        String currentStr = reward == null ? "&7?"
            : (reward.getCooldown() == -1 ? "&cUna sola vez" : "&a" + TimeUtil.formatSmart(reward.getCooldown()));

        // Filler
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Presets desde config
        List<Map<?, ?>> presets = cfg.getMapList("cooldown.presets");
        String presetNameTpl = cfg.getString("cooldown.preset-name", "&#FF9000⌚ &f%name%");
        List<String> presetLore = cfg.getStringList("cooldown.preset-lore");

        for (Map<?, ?> preset : presets) {
            int slot    = ((Number) preset.get("slot")).intValue();
            int secs    = ((Number) preset.get("seconds")).intValue();
            String name = (String) preset.get("name");
            String mat  = (String) preset.get("material");

            String timeStr = secs == -1 ? "&cUna sola vez" : "&a" + TimeUtil.formatSmart(secs);
            String itemName = presetNameTpl.replace("%name%", name);
            List<String> lore = presetLore.stream()
                .map(l -> l.replace("%time%", HexUtil.format(timeStr)))
                .collect(Collectors.toList());

            inv.setItem(slot, buildPreset(EditorMainGUI.parseMat(mat), itemName, lore));
        }

        // Botón personalizado
        inv.setItem(SLOT_CUSTOM, EditorMainGUI.cfgItem(cfg, "cooldown.buttons.custom",
            Map.of("%cooldown%", HexUtil.format(currentStr))));

        // Volver
        inv.setItem(SLOT_BACK, EditorMainGUI.cfgItem(cfg, "cooldown.buttons.back", Collections.emptyMap()));

        sessions.put(player.getUniqueId(), rewardId);
        inventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory expected = inventories.get(player.getUniqueId());
        if (expected == null || !expected.equals(event.getView().getTopInventory())) return;

        event.setCancelled(true);
        String rewardId = sessions.get(player.getUniqueId());
        if (rewardId == null) return;

        int slot = event.getRawSlot();
        if (slot >= 27) return;

        FileConfiguration cfg = Main.getInstance().getEditorConfig();
        List<Map<?, ?>> presets = cfg.getMapList("cooldown.presets");

        for (Map<?, ?> preset : presets) {
            if (slot == ((Number) preset.get("slot")).intValue()) {
                int secs  = ((Number) preset.get("seconds")).intValue();
                String name = (String) preset.get("name");
                applyCooldown(player, rewardId, secs, name);
                return;
            }
        }

        if (slot == SLOT_CUSTOM) {
            player.closeInventory();
            String prompt = HexUtil.format(cfg.getString("cooldown.custom-prompt",
                "&#FCD05C&l⌚ Cooldown §r&7— Escribe los segundos:"));
            EditorChatInput.request(player, rewardId, EditorChatInput.InputType.COOLDOWN_CUSTOM, prompt);
        } else if (slot == SLOT_BACK) {
            player.closeInventory();
            EditorMainGUI.open(player, rewardId);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory expected = inventories.get(player.getUniqueId());
        if (expected != null && expected.equals(event.getInventory())) {
            sessions.remove(player.getUniqueId());
            inventories.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        inventories.remove(event.getPlayer().getUniqueId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyCooldown(Player player, String rewardId, int seconds, String name) {
        FileConfiguration config = Main.getInstance().getRewardsGuiConfig();
        config.set("rewards." + rewardId + ".cooldown", seconds);
        Main.getInstance().saveRewardsGuiConfig();
        Main.getInstance().getRewardManager().reload();

        String timeStr = seconds == -1 ? "una sola vez" : TimeUtil.formatSmart(seconds);
        player.sendMessage(Main.getInstance().getMessage("editor-cooldown-set").replace("%time%", timeStr));
        player.closeInventory();
        EditorMainGUI.open(player, rewardId);
    }

    private static ItemStack buildPreset(Material mat, String name, List<String> loreRaw) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(HexUtil.comp(name));
        m.lore(loreRaw.stream().map(HexUtil::comp).collect(Collectors.toList()));
        it.setItemMeta(m);
        return it;
    }
}
