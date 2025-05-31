package me.fRewards.placeholders;

import me.fRewards.config.RewardManager;
import me.fRewards.main.Main;
import me.fRewards.utils.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FRewardsPlaceholder extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "cooldown"; // ejemplo: %cooldown_ros%
    }

    @Override
    public @NotNull String getAuthor() {
        return "xLeandroMC";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null || identifier.isEmpty()) return "";

        RewardManager manager = Main.getInstance().getRewardManager();
        RewardManager.Reward reward = manager.getReward(identifier.toLowerCase());

        if (reward == null) return "";

        long tiempo = manager.getRemainingTime(player, reward.getId());
        return tiempo <= 0 ? "" : TimeUtil.formatSeconds(tiempo);
    }
}
