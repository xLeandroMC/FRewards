package me.fRewards.gui;

import me.fRewards.main.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EditorCommandSession implements Listener {

    private static final Map<String, List<String>> commandBuffers = new HashMap<>();
    private static final Map<String, String> editingRank = new HashMap<>();

    public static void startCommandEditing(Player player, String rank) {
        commandBuffers.put(player.getName(), new ArrayList<>());
        editingRank.put(player.getName(), rank);
        player.sendMessage("§7✔ Escribe cada comando (con %player% y %reward_id% si deseas). Escribe §aconfirmar§7 para guardar.");
        player.sendMessage("§7Ejemplo: §fkit give %player% ros");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (!commandBuffers.containsKey(name)) return;

        event.setCancelled(true);
        String msg = event.getMessage();

        if (msg.equalsIgnoreCase("confirmar")) {
            List<String> cmds = commandBuffers.get(name);
            String rank = editingRank.get(name);

            File file = new File(Main.getInstance().getDataFolder(), "config.yml");
            FileConfiguration config = Main.getInstance().getConfig();

            config.set("rewards." + rank + ".commands", cmds);
            try {
                config.save(file);
                Main.getInstance().getRewardManager().reload();
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> EditorGUI.openEditor(player, rank));
                player.sendMessage("§a✅ Comandos guardados. Se agregaron §f" + cmds.size() + "§a comando(s).");
            } catch (IOException e) {
                player.sendMessage("§c❌ Error al guardar los comandos.");
                e.printStackTrace();
            }

            commandBuffers.remove(name);
            editingRank.remove(name);
            return;
        }

        commandBuffers.get(name).add(msg);
        player.sendMessage("§7➕ Comando agregado: §f" + msg);
        player.sendMessage("§8(Escribe §aconfirmar§8 cuando termines)");
    }
}
