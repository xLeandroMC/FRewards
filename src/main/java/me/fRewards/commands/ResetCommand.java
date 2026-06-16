package me.fRewards.commands;

import me.fRewards.main.Main;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ResetCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("frewards.admin")) {
            sender.sendMessage(Main.getInstance().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Main.getInstance().getMessage("no-permission")); // fallback
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(Main.getInstance().getMessage("reset-player-not-found")
                .replace("%player%", args[0]));
            return true;
        }

        Main.getInstance().getRewardManager().resetAllRewards(target);
        sender.sendMessage(Main.getInstance().getMessage("reset-success")
            .replace("%player%", target.getName() != null ? target.getName() : args[0]));
        return true;
    }
}
