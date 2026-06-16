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

public class EditorMainGUI implements Listener {

    private static final Map<UUID, String> sessions = new HashMap<>();
    private static final Map<UUID, Inventory> inventories = new HashMap<>();

    // ── Título dinámico (leído de editor.yml) ─────────────────────────────────

    static String titlePrefix() {
        return HexUtil.format(Main.getInstance().getEditorConfig()
            .getString("main.title-prefix", "&#FF9000▸ &#FF4C00Editor §8» &#FF9000"));
    }

    public static void open(Player player, String rewardId) {
        Reward reward = Main.getInstance().getRewardManager().getReward(rewardId);
        if (reward == null) {
            player.sendMessage(Main.getInstance().getMessage("invalid-reward"));
            return;
        }

        FileConfiguration cfg = Main.getInstance().getEditorConfig();
        String prefix = titlePrefix();

        Inventory inv = Bukkit.createInventory(null, 45, HexUtil.compTitle(prefix + rewardId));

        // Filler
        String fillerMat = cfg.getString("main.filler", "GRAY_STAINED_GLASS_PANE");
        ItemStack filler = item(parseMat(fillerMat), " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        // Slots configurables
        int slotItems    = cfg.getInt("main.slots.items",    10);
        int slotCommands = cfg.getInt("main.slots.commands",  12);
        int slotCooldown = cfg.getInt("main.slots.cooldown",  14);
        int slotName     = cfg.getInt("main.slots.name",      16);
        int slotPreview  = cfg.getInt("main.slots.preview",   22);
        int slotBack     = cfg.getInt("main.slots.back",      36);
        int slotDelete   = cfg.getInt("main.slots.delete",    44);

        String coolStr = reward.getCooldown() == -1
            ? "&cUna sola vez"
            : "&a" + TimeUtil.formatSmart(reward.getCooldown());
        String displayName = HexUtil.format(
            reward.getDisplayName().replace("%reward_name%", reward.getName()));

        // Vista previa
        List<String> previewLore = cfg.getStringList("main.preview-lore");
        Map<String, String> previewVars = Map.of(
            "%reward_id%",  reward.getId(),
            "%cooldown%",   HexUtil.format(coolStr),
            "%permission%", reward.getPermission(),
            "%commands%",   String.valueOf(reward.getCommands().size()),
            "%items%",      String.valueOf(reward.getItems().size())
        );
        inv.setItem(slotPreview, buildFromLore(reward.getMaterial(), displayName, previewLore, previewVars));

        // Botones
        inv.setItem(slotItems,    cfgItem(cfg, "main.buttons.items",
            Map.of("%items%", String.valueOf(reward.getItems().size()))));
        inv.setItem(slotCommands, cfgItem(cfg, "main.buttons.commands",
            Map.of("%commands%", String.valueOf(reward.getCommands().size()))));
        inv.setItem(slotCooldown, cfgItem(cfg, "main.buttons.cooldown",
            Map.of("%cooldown%", HexUtil.format(coolStr))));
        inv.setItem(slotName,     cfgItem(cfg, "main.buttons.name",
            Map.of("%display_name%", displayName)));
        inv.setItem(slotBack,     cfgItem(cfg, "main.buttons.back",     Collections.emptyMap()));
        inv.setItem(slotDelete,   cfgItem(cfg, "main.buttons.delete",   Collections.emptyMap()));

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
        if (slot >= 45) return;

        FileConfiguration cfg = Main.getInstance().getEditorConfig();
        int slotItems    = cfg.getInt("main.slots.items",    10);
        int slotCommands = cfg.getInt("main.slots.commands",  12);
        int slotCooldown = cfg.getInt("main.slots.cooldown",  14);
        int slotName     = cfg.getInt("main.slots.name",      16);
        int slotBack     = cfg.getInt("main.slots.back",      36);
        int slotDelete   = cfg.getInt("main.slots.delete",    44);

        if (slot == slotItems)         ItemsEditorGUI.open(player, rewardId);
        else if (slot == slotCommands) CommandsEditorGUI.open(player, rewardId);
        else if (slot == slotCooldown) CooldownSelectorGUI.open(player, rewardId);
        else if (slot == slotName) {
            player.closeInventory();
            String prompt = HexUtil.format(cfg.getString("main.name-prompt",
                "&#FCD05C&l✎ Nombre §r&7— Escribe el nuevo nombre:"));
            EditorChatInput.request(player, rewardId, EditorChatInput.InputType.DISPLAY_NAME, prompt);
        }
        else if (slot == slotBack) {
            player.closeInventory();
            EditorSelectorGUI.open(player);
        }
        else if (slot == slotDelete) {
            if (event.isShiftClick()) {
                Main plugin = Main.getInstance();
                plugin.getRewardsGuiConfig().set("rewards." + rewardId, null);
                plugin.saveRewardsGuiConfig();
                plugin.getRewardManager().reload();
                player.closeInventory();
                player.sendMessage(plugin.getMessage("editor-deleted").replace("%id%", rewardId));
                EditorSelectorGUI.open(player);
            } else {
                player.sendMessage(Main.getInstance().getMessage("editor-delete-confirm"));
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

    // ── Helpers compartidos ───────────────────────────────────────────────────

    /** Construye un ItemStack desde una sección de editor.yml con placeholders. */
    static ItemStack cfgItem(FileConfiguration cfg, String path, Map<String, String> vars) {
        String matStr = cfg.getString(path + ".material", "STONE");
        String name   = applyVars(cfg.getString(path + ".name", "?"), vars);
        List<String> lore = cfg.getStringList(path + ".lore")
            .stream().map(l -> applyVars(l, vars)).collect(Collectors.toList());

        ItemStack it = new ItemStack(parseMat(matStr));
        ItemMeta m = it.getItemMeta();
        m.displayName(HexUtil.comp(name));
        m.lore(lore.stream().map(HexUtil::comp).collect(Collectors.toList()));
        it.setItemMeta(m);
        return it;
    }

    /** Construye un ItemStack con material+nombre+lore con placeholders (sin leer config de material/nombre). */
    static ItemStack buildFromLore(Material mat, String name, List<String> loreRaw, Map<String, String> vars) {
        List<String> lore = loreRaw.stream().map(l -> applyVars(l, vars)).collect(Collectors.toList());
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(HexUtil.comp(name));
        m.lore(lore.stream().map(HexUtil::comp).collect(Collectors.toList()));
        it.setItemMeta(m);
        return it;
    }

    static Material parseMat(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.STONE; }
    }

    private static String applyVars(String s, Map<String, String> vars) {
        for (Map.Entry<String, String> e : vars.entrySet()) s = s.replace(e.getKey(), e.getValue());
        return s;
    }

    // Helpers legacy para uso interno (filler, separator, etc.)
    static ItemStack item(Material mat, String name) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(HexUtil.comp(name));
        i.setItemMeta(m);
        return i;
    }

    static ItemStack itemLore(Material mat, String name, String... lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(HexUtil.comp(name));
        m.lore(java.util.Arrays.stream(lore).map(HexUtil::comp).collect(Collectors.toList()));
        i.setItemMeta(m);
        return i;
    }
}
