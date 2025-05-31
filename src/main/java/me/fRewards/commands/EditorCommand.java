package me.fRewards.commands;

import me.fRewards.config.RewardManager;
import me.fRewards.gui.EditorGUI;
import me.fRewards.main.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EditorCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando solo puede ser ejecutado por jugadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 2 || !args[1].equalsIgnoreCase("editor")) {
            player.sendMessage("§eUso correcto: §f/frewards <rango> editor");
            return true;
        }

        String rank = args[0].toLowerCase();

        // ✅ Verificar si la recompensa existe
        RewardManager rewardManager = Main.getInstance().getRewardManager();
        if (rewardManager.getReward(rank) == null) {
            player.sendMessage("§c❌ La recompensa '" + rank + "' no existe en config.yml.");
            return true;
        }

        EditorGUI.openEditor(player, rank);
        return true;
    }
}
