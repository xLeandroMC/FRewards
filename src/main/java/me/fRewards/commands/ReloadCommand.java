package me.fRewards.commands;

import me.fRewards.main.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("frewards.reload")) {
            sender.sendMessage(Main.getInstance().getMessage("no-permission"));
            return true;
        }

        // Recargar todos los archivos y sistemas
        Main.getInstance().reloadConfig();               // config.yml
        Main.getInstance().reloadMessages();             // messages.yml
        Main.getInstance().reloadRewardsGuiConfig();     // rewardsgui.yml (si es usado)
        Main.getInstance().reloadData();                 // data.yml (si es usado)
        Main.getInstance().getRewardManager().reload();  // recompensas

        sender.sendMessage(Main.getInstance().getMessage("reload-success"));
        return true;
    }
}