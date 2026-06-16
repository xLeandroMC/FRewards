package me.fRewards.commands;

import me.fRewards.main.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommandTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();

        // /frewards
        if (cmd.equals("frewards") || cmd.equals("rewards") || cmd.equals("recompensas")) {
            if (args.length == 1) {
                List<String> subs = new ArrayList<>();
                if (sender.hasPermission("frewards.use"))    subs.add("help");
                if (sender.hasPermission("frewards.editor")) subs.add("editor");
                if (sender.hasPermission("frewards.reload")) subs.add("reload");
                if (sender.hasPermission("frewards.admin"))  subs.add("reset");
                return filter(subs, args[0]);
            }
            // /frewards editor <id>
            if (args.length == 2 && args[0].equalsIgnoreCase("editor")
                    && sender.hasPermission("frewards.editor")) {
                return filter(rewardIds(), args[1]);
            }
            // /frewards reset <jugador>
            if (args.length == 2 && args[0].equalsIgnoreCase("reset")
                    && sender.hasPermission("frewards.admin")) {
                return filter(onlinePlayers(), args[1]);
            }
        }

        // /frewardsreload — sin args
        if (cmd.equals("frewardsreload")) return Collections.emptyList();

        // /frewardsreset <jugador>
        if (cmd.equals("frewardsreset") && sender.hasPermission("frewards.admin")) {
            if (args.length == 1) return filter(onlinePlayers(), args[0]);
        }

        return Collections.emptyList();
    }

    private List<String> rewardIds() {
        return new ArrayList<>(Main.getInstance().getRewardManager().getRewardIds());
    }

    private List<String> onlinePlayers() {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .collect(Collectors.toList());
    }

    private List<String> filter(List<String> list, String prefix) {
        if (prefix == null || prefix.isEmpty()) return list;
        String lower = prefix.toLowerCase();
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(lower))
            .collect(Collectors.toList());
    }
}
