package me.fRewards.gui;

import me.fRewards.main.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EditorInputListener implements Listener {

    public enum EditMode { NAME, COOLDOWN, COMMANDS }

    private static final Map<String, EditMode> editing = new HashMap<>();
    private static final Map<String, String> editingRank = new HashMap<>();

    public static void startEditing(Player player, String rank, EditMode mode) {
        editing.put(player.getName(), mode);
        editingRank.put(player.getName(), rank);
        player.sendMessage("§7✏️ Escribe el nuevo valor por el chat. Escribe §ccancelar§7 para anular.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (!editing.containsKey(name)) return;

        event.setCancelled(true);
        String input = event.getMessage();
        EditMode mode = editing.get(name);
        String rank = editingRank.get(name);
        File file = new File(Main.getInstance().getDataFolder(), "config.yml");
        FileConfiguration config = Main.getInstance().getConfig();

        if (input.equalsIgnoreCase("cancelar")) {
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> EditorGUI.openEditor(player, rank));
            player.sendMessage("§cEdición cancelada.");
            cleanup(name);
            return;
        }

        switch (mode) {
            case NAME -> {
                if (!config.isConfigurationSection("rewards." + rank)) {
                    player.sendMessage("§c❌ La recompensa '" + rank + "' no existe en config.yml.");
                    cleanup(name);
                    return;
                }
                String newId = input.toLowerCase().replace(" ", "_");

                if (config.isConfigurationSection("rewards." + newId)) {
                    player.sendMessage("§cYa existe una recompensa con ese ID.");
                    cleanup(name);
                    return;
                }

                // Mover en config.yml
                config.set("rewards." + newId, config.get("rewards." + rank));
                config.set("rewards." + rank, null);

                // Mover en data.yml
                File dataFile = new File(Main.getInstance().getDataFolder(), "data.yml");
                FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
                if (dataConfig.contains("rewards." + rank)) {
                    dataConfig.set("rewards." + newId, dataConfig.get("rewards." + rank));
                    dataConfig.set("rewards." + rank, null);
                    try {
                        dataConfig.save(dataFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                player.sendMessage("§a✅ Recompensa renombrada a: §f" + newId);
            }
            case COOLDOWN -> {
                try {
                    int seconds = Integer.parseInt(input);
                    config.set("rewards." + rank + ".cooldown", seconds);
                    player.sendMessage("§a✅ Cooldown actualizado a " + seconds + " segundos.");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cDebes escribir un número válido.");
                    return;
                }
            }
            case COMMANDS -> {
                player.sendMessage("§7✍️ Escribe ahora los comandos uno por uno. Escribe §aconfirmar§7 cuando termines.");
                EditorCommandSession.startCommandEditing(player, rank);
                return;
            }
        }

        try {
            config.save(file);
            Main.getInstance().getRewardManager().reload();
        } catch (IOException e) {
            player.sendMessage("§c❌ Error al guardar el archivo.");
            e.printStackTrace();
        }

        cleanup(name);
    }

    private void cleanup(String name) {
        editing.remove(name);
        editingRank.remove(name);
    }
}
