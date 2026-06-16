package me.fRewards.gui;

import me.fRewards.main.Main;
import me.fRewards.utils.HexUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static me.fRewards.gui.EditorMainGUI.item;

public class ItemsEditorGUI implements Listener {

    private static final int ITEM_AREA  = 45;
    private static final int SLOT_BACK  = 45;
    private static final int SLOT_CLEAR = 49;
    private static final int SLOT_SAVE  = 53;

    private static final Map<UUID, String> sessions = new HashMap<>();
    private static final Map<UUID, Inventory> inventories = new HashMap<>();

    static String titlePrefix() {
        return HexUtil.format(Main.getInstance().getEditorConfig()
            .getString("items.title-prefix", "&#FF9000▸ &#FF4C00Items §8» &#FF9000"));
    }

    public static void open(Player player, String rewardId) {
        FileConfiguration cfg = Main.getInstance().getEditorConfig();
        String prefix = titlePrefix();
        Inventory inv = Bukkit.createInventory(null, 54, HexUtil.compTitle(prefix + rewardId));

        // Cargar ítems guardados
        File dataFile = new File(Main.getInstance().getDataFolder(), "data.yml");
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.isConfigurationSection("rewards." + rewardId + ".items")) {
            for (String key : data.getConfigurationSection("rewards." + rewardId + ".items").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && slot < ITEM_AREA) {
                        String b64 = data.getString("rewards." + rewardId + ".items." + key);
                        if (b64 != null) {
                            ItemStack loaded = fromBase64(b64);
                            if (loaded != null) inv.setItem(slot, loaded);
                        }
                    }
                } catch (Exception e) {
                    Main.getInstance().getLogger().warning("⚠ Error al cargar item " + key + " de " + rewardId);
                }
            }
        }

        // Fila separadora
        ItemStack sep = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = ITEM_AREA; i < 54; i++) inv.setItem(i, sep);

        // Botones desde config
        inv.setItem(SLOT_BACK,  EditorMainGUI.cfgItem(cfg, "items.buttons.back",  Collections.emptyMap()));
        inv.setItem(SLOT_CLEAR, EditorMainGUI.cfgItem(cfg, "items.buttons.clear", Collections.emptyMap()));
        inv.setItem(SLOT_SAVE,  EditorMainGUI.cfgItem(cfg, "items.buttons.save",  Collections.emptyMap()));

        sessions.put(player.getUniqueId(), rewardId);
        inventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory expected = inventories.get(player.getUniqueId());
        if (expected == null || !expected.equals(event.getView().getTopInventory())) return;

        String rewardId = sessions.get(player.getUniqueId());
        if (rewardId == null) return;

        int slot = event.getRawSlot();

        if (slot < ITEM_AREA || slot >= 54) return;
        event.setCancelled(true);

        switch (slot) {
            case SLOT_BACK, SLOT_SAVE -> {
                saveItems(event.getInventory(), rewardId, player);
                player.closeInventory();
                EditorMainGUI.open(player, rewardId);
            }
            case SLOT_CLEAR -> clearItems(event.getInventory(), rewardId, player);
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

    private void saveItems(Inventory inv, String rewardId, Player player) {
        File dataFile = new File(Main.getInstance().getDataFolder(), "data.yml");
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);

        data.set("rewards." + rewardId + ".items", null);
        int count = 0;

        for (int i = 0; i < ITEM_AREA; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() != Material.AIR) {
                data.set("rewards." + rewardId + ".items." + i, toBase64(it));
                count++;
            }
        }

        try {
            data.save(dataFile);
            Main.getInstance().getRewardManager().reload();
            player.sendMessage(Main.getInstance().getMessage("editor-saved-items")
                .replace("%count%", String.valueOf(count)));
        } catch (IOException e) {
            player.sendMessage(HexUtil.format("&c❌ Error al guardar data.yml"));
        }
    }

    private void clearItems(Inventory inv, String rewardId, Player player) {
        for (int i = 0; i < ITEM_AREA; i++) inv.setItem(i, null);

        File dataFile = new File(Main.getInstance().getDataFolder(), "data.yml");
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        data.set("rewards." + rewardId + ".items", null);

        try {
            data.save(dataFile);
            Main.getInstance().getRewardManager().reload();
            player.sendMessage(Main.getInstance().getMessage("editor-cleared-items"));
        } catch (IOException e) {
            player.sendMessage(HexUtil.format("&c❌ Error al guardar data.yml"));
        }
    }

    // ── Serialización ─────────────────────────────────────────────────────────

    public static String toBase64(ItemStack item) {
        return "v2:" + Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack fromBase64(String base64) {
        if (base64 == null) return null;
        try {
            if (base64.startsWith("v2:")) {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64.substring(3)));
            } else {
                return deserializeLegacy(Base64.getDecoder().decode(base64));
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("⚠ No se pudo deserializar un ítem: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static ItemStack deserializeLegacy(byte[] bytes) throws Exception {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ItemStack) in.readObject();
        }
    }
}
