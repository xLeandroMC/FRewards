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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class EditorGUI implements Listener {

    private static final Map<String, Boolean> markedForDeletion = new HashMap<>();

    public static void openEditor(Player player, String rank) {
        Inventory inv = Bukkit.createInventory(null, 27, "Editar Recompensas: " + rank);

        inv.setItem(19, createEditItem("Editar Nombre", Material.NAME_TAG));
        inv.setItem(20, createEditItem("Editar Cooldown", Material.CLOCK));
        inv.setItem(21, createEditItem("Comandos Personalizados", Material.PAPER));
        inv.setItem(25, createEditItem("Eliminar Items", Material.BARRIER));
        inv.setItem(26, createEditItem("Guardar", Material.GREEN_WOOL));

        File dataFile = new File(Main.getInstance().getDataFolder(), "data.yml");
        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.isConfigurationSection("rewards." + rank + ".items")) {
            for (String key : dataConfig.getConfigurationSection("rewards." + rank + ".items").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && slot < 27 && (slot < 19 || slot > 21 && slot != 25 && slot != 26)) {
                        ItemStack item = fromBase64(dataConfig.getString("rewards." + rank + ".items." + key));
                        inv.setItem(slot, item);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        player.openInventory(inv);
    }

    private static ItemStack createEditItem(String name, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(HexUtil.colorize("&a" + name));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player player = (Player) e.getWhoClicked();
        Inventory inv = e.getInventory();
        InventoryView view = e.getView();

        if (!view.getTitle().startsWith("Editar Recompensas: ")) return;

        String rank = view.getTitle().substring("Editar Recompensas: ".length());
        int slot = e.getRawSlot();

        // Cancel edit button clicks
        if (slot == 19 || slot == 20 || slot == 21 || slot == 25 || slot == 26) {
            e.setCancelled(true);
            if (e.getClick().isLeftClick()) {
                switch (slot) {
                    case 19 -> {
                        EditorInputListener.startEditing(player, rank, EditorInputListener.EditMode.NAME);
                        player.closeInventory();
                    }
                    case 20 -> {
                        EditorInputListener.startEditing(player, rank, EditorInputListener.EditMode.COOLDOWN);
                        player.closeInventory();
                    }
                    case 21 -> {
                        EditorInputListener.startEditing(player, rank, EditorInputListener.EditMode.COMMANDS);
                        player.closeInventory();
                    }
                    case 25 -> {
                        markedForDeletion.put(rank, true);
                        player.sendMessage("§cSe eliminaron todos los items para el rango: §f" + rank);
                        saveInventory(inv, rank);
                        player.closeInventory();
                    }
                    case 26 -> {
                        player.sendMessage("§a¡Guardado manual de ítems ejecutado!");
                        saveInventory(inv, rank);
                        player.closeInventory();
                    }
                }
            }
        } else if (slot < inv.getSize() && e.getView().getType() != InventoryType.CRAFTING) {
            // Guardado automático al modificar ítems válidos
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> saveInventory(inv, rank), 1L);
        }
    }

    private static int saveInventory(Inventory inv, String rank) {
        File dataFile = new File(Main.getInstance().getDataFolder(), "data.yml");
        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        dataConfig.set("rewards." + rank + ".items", null);
        int count = 0;

        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot == 19 || slot == 20 || slot == 21 || slot == 25 || slot == 26) continue;
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                try {
                    dataConfig.set("rewards." + rank + ".items." + slot, toBase64(item));
                    count++;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Main.getInstance().getRewardManager().reload();
        return count;
    }

    private static String toBase64(ItemStack item) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        dataOutput.writeObject(item);
        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private static ItemStack fromBase64(String base64) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(base64);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }
}
