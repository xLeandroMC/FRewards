package me.fRewards.commands;

import me.fRewards.gui.EditorMainGUI;
import me.fRewards.gui.EditorSelectorGUI;
import me.fRewards.gui.RewardsGUI;
import me.fRewards.main.Main;
import me.fRewards.utils.HexUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Ejecutor del comando principal /frewards (y alias). Antes vivía como un lambda
 * gigante dentro de Main; extraído para mantener la lógica de comandos separada.
 */
public class FRewardsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Main plugin = Main.getInstance();

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        // Sin args → abrir GUI principal
        if (args.length == 0) {
            if (!player.hasPermission("frewards.use")) {
                player.sendMessage(plugin.getMessage("no-permission"));
                return true;
            }
            RewardsGUI.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "help" -> {
                if (!player.hasPermission("frewards.use")) {
                    player.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                player.sendMessage(plugin.getMessage("help-title"));
                player.sendMessage(plugin.getMessage("help-1"));
                player.sendMessage(plugin.getMessage("help-2"));
                player.sendMessage(plugin.getMessage("help-3"));
                player.sendMessage(plugin.getMessage("help-4"));
                player.sendMessage(plugin.getMessage("help-footer"));
            }

            case "editor" -> {
                if (!player.hasPermission("frewards.editor")) {
                    player.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    EditorSelectorGUI.open(player);
                    return true;
                }
                String rewardId = args[1].toLowerCase();
                if (plugin.getRewardManager().getReward(rewardId) == null) {
                    player.sendMessage(plugin.getMessage("invalid-reward"));
                    return true;
                }
                EditorMainGUI.open(player, rewardId);
            }

            case "reload" -> {
                if (!player.hasPermission("frewards.reload")) {
                    player.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.reloadMessages();
                plugin.reloadRewardsGuiConfig();
                plugin.reloadEditorConfig();
                plugin.getRewardManager().reload();
                player.sendMessage(plugin.getMessage("reload-success"));
            }

            case "reset" -> {
                if (!player.hasPermission("frewards.admin")) {
                    player.sendMessage(plugin.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(HexUtil.format("&cUso: &e/frewards reset &f<jugador>"));
                    return true;
                }
                new ResetCommand().onCommand(sender, cmd, label, new String[]{args[1]});
            }

            case "activate" -> player.sendMessage(HexUtil.format("&aFRewards ya está activado."));

            default -> player.sendMessage(plugin.getMessage("command-unknown"));
        }
        return true;
    }
}
