package me.fRewards.gui;

import me.fRewards.config.RewardManager.Reward;
import me.fRewards.main.Main;
import me.fRewards.utils.HexUtil;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static me.fRewards.gui.EditorMainGUI.item;

public class CommandsEditorGUI implements Listener {

    private static final int CMD_AREA   = 45;
    private static final int SLOT_BACK  = 45;
    private static final int SLOT_ADD   = 49;
    private static final int SLOT_CLEAR = 53;

    private static final Map<UUID, String> sessions = new HashMap<>();
    private static final Map<UUID, Inventory> inventories = new HashMap<>();

    static String titlePrefix() {
        return HexUtil.format(Main.getInstance().getEditorConfig()
            .getString("commands.title-prefix", "&#FF9000▸ &#FF4C00Comandos §8» &#FF9000"));
    }

    public static void open(Player player, String rewardId) {
        FileConfiguration cfg = Main.getInstance().getEditorConfig();
        String prefix = titlePrefix();
        Inventory inv = Bukkit.createInventory(null, 54, HexUtil.compTitle(prefix + rewardId));

        Reward reward = Main.getInstance().getRewardManager().getReward(rewardId);
        List<String> commands = reward != null ? reward.getCommands() : new ArrayList<>();

        for (int i = 0; i < Math.min(commands.size(), CMD_AREA); i++) {
            inv.setItem(i, buildCommandItem(cfg, i + 1, commands.get(i)));
        }

        // Fila separadora
        ItemStack sep = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = CMD_AREA; i < 54; i++) inv.setItem(i, sep);

        // Botones desde config
        inv.setItem(SLOT_BACK,  EditorMainGUI.cfgItem(cfg, "commands.buttons.back",  Collections.emptyMap()));
        inv.setItem(SLOT_ADD,   EditorMainGUI.cfgItem(cfg, "commands.buttons.add",   Collections.emptyMap()));
        inv.setItem(SLOT_CLEAR, EditorMainGUI.cfgItem(cfg, "commands.buttons.clear", Collections.emptyMap()));

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
        if (slot >= 54) return;

        Reward reward = Main.getInstance().getRewardManager().getReward(rewardId);
        if (reward == null) { player.closeInventory(); return; }

        if (slot < CMD_AREA) {
            ItemStack clicked = event.getInventory().getItem(slot);
            if (clicked == null || clicked.getType() == Material.AIR) return;
            List<String> cmds = new ArrayList<>(reward.getCommands());
            if (slot < cmds.size()) {
                String removed = cmds.remove(slot);
                saveCommands(rewardId, cmds);
                player.sendMessage(Main.getInstance().getMessage("editor-cmd-removed")
                    .replace("%cmd%", removed));
                open(player, rewardId);
            }
            return;
        }

        switch (slot) {
            case SLOT_BACK -> {
                player.closeInventory();
                EditorMainGUI.open(player, rewardId);
            }
            case SLOT_ADD -> {
                player.closeInventory();
                String prompt = HexUtil.format(Main.getInstance().getEditorConfig()
                    .getString("commands.add-prompt", "&#FCD05C&l✎ Comando §r&7— Escribe el comando:"));
                EditorChatInput.request(player, rewardId, EditorChatInput.InputType.ADD_COMMAND, prompt);
            }
            case SLOT_CLEAR -> {
                saveCommands(rewardId, new ArrayList<>());
                player.sendMessage(Main.getInstance().getMessage("editor-cmds-cleared"));
                open(player, rewardId);
            }
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

    // ── Persistencia ─────────────────────────────────────────────────────────

    public static void saveCommands(String rewardId, List<String> commands) {
        FileConfiguration config = Main.getInstance().getRewardsGuiConfig();
        config.set("rewards." + rewardId + ".commands", commands);
        Main.getInstance().saveRewardsGuiConfig();
        Main.getInstance().getRewardManager().reload();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack buildCommandItem(FileConfiguration cfg, int number, String command) {
        String matStr = cfg.getString("commands.command-item.material", "PAPER");
        String name   = cfg.getString("commands.command-item.name", "&#FCD05C✎ &f%number%. &e%command%");
        List<String> loreRaw = cfg.getStringList("commands.command-item.lore");

        Map<String, String> vars = Map.of(
            "%number%",  String.valueOf(number),
            "%command%", command
        );

        name = applyVars(name, vars);
        List<String> lore = loreRaw.stream().map(l -> applyVars(l, vars)).collect(Collectors.toList());

        ItemStack it = new ItemStack(EditorMainGUI.parseMat(matStr));
        ItemMeta m = it.getItemMeta();
        m.displayName(HexUtil.comp(name));
        m.lore(lore.stream().map(HexUtil::comp).collect(Collectors.toList()));
        it.setItemMeta(m);
        return it;
    }

    private static String applyVars(String s, Map<String, String> vars) {
        for (Map.Entry<String, String> e : vars.entrySet()) s = s.replace(e.getKey(), e.getValue());
        return s;
    }
}
