package me.fRewards.config;

import me.fRewards.main.Main;
import me.fRewards.utils.HexUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class RewardManager {

    private final Main plugin = Main.getInstance();
    private final Map<String, Reward> rewards = new HashMap<>();
    private final File dataFile;
    private final FileConfiguration dataConfig;
    private final FileConfiguration rewardsConfig;

    public RewardManager() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("❌ No se pudo crear data.yml");
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        File rewardsFile = new File(plugin.getDataFolder(), "rewardsgui.yml");
        if (!rewardsFile.exists()) {
            plugin.saveResource("rewardsgui.yml", false);
        }
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);

        loadRewards();
    }

    private void loadRewards() {
        rewards.clear();
        FileConfiguration config = rewardsConfig;

        if (!config.isConfigurationSection("rewards")) return;

        Set<Integer> usedSlots = new HashSet<>();

        for (String key : config.getConfigurationSection("rewards").getKeys(false)) {
            String path = "rewards." + key;
            String name = config.getString(path + ".name", key);
            String displayName = config.getString(path + ".display-name");
            String permission = config.getString(path + ".permission");
            int cooldown = config.getInt(path + ".cooldown");
            int slot = config.getInt(path + ".slot");
            String material = config.getString(path + ".material", "CHEST");
            List<String> commands = config.getStringList(path + ".commands");

            Map<String, String> stateMaterials = new HashMap<>();
            if (config.isConfigurationSection(path + ".material-state")) {
                for (String state : config.getConfigurationSection(path + ".material-state").getKeys(false)) {
                    stateMaterials.put(state, config.getString(path + ".material-state." + state));
                }
            }

            List<ItemStack> items = new ArrayList<>();
            if (dataConfig.isConfigurationSection("rewards." + key + ".items")) {
                for (String itemKey : dataConfig.getConfigurationSection("rewards." + key + ".items").getKeys(false)) {
                    try {
                        String base64 = dataConfig.getString("rewards." + key + ".items." + itemKey);
                        if (base64 != null) {
                            ItemStack item = fromBase64(base64);
                            if (item != null) items.add(item);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("⚠️ No se pudo cargar un ítem para " + key + " slot " + itemKey);
                        e.printStackTrace();
                    }
                }
            }

            if (usedSlots.contains(slot)) {
                plugin.getLogger().warning("❌ Slot duplicado en rewardsgui.yml: " + slot + " para la recompensa '" + key + "'");
                continue;
            }

            usedSlots.add(slot);
            Reward reward = new Reward(key, name, displayName, permission, cooldown, slot, material, stateMaterials, commands, items);
            rewards.put(key, reward);
        }
    }

    private ItemStack fromBase64(String data) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(data);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }

    public Collection<Reward> getAllRewards() {
        return rewards.values();
    }

    public Reward getReward(String id) {
        if (id == null) return null;
        return rewards.get(id);
    }

    public boolean canClaim(Player player, String id) {
        Reward reward = getReward(id);
        if (reward == null) return false;

        long lastClaim = dataConfig.getLong(player.getUniqueId() + "." + id.toLowerCase(), 0L);
        long now = System.currentTimeMillis() / 1000;

        if (reward.getCooldown() == -1) {
            return lastClaim == 0;
        }

        return (now - lastClaim) >= reward.getCooldown();
    }

    public long getRemainingTime(Player player, String id) {
        Reward reward = getReward(id);
        if (reward == null) return 0;

        long lastClaim = dataConfig.getLong(player.getUniqueId() + "." + id.toLowerCase(), 0L);
        long now = System.currentTimeMillis() / 1000;

        if (reward.getCooldown() == -1) {
            return lastClaim == 0 ? 0 : Long.MAX_VALUE;
        }

        return Math.max(0, reward.getCooldown() - (now - lastClaim));
    }

    public void claimReward(Player player, String id) {
        Reward reward = getReward(id);
        if (reward == null) {
            player.sendMessage("§c❌ Esta recompensa no existe.");
            player.closeInventory();
            return;
        }

        if (!player.hasPermission(reward.getPermission())) {
            player.sendMessage("§c❌ No tienes permiso para esta recompensa.");
            player.closeInventory();
            return;
        }

        if (!canClaim(player, id)) {
            player.sendMessage("§c⏳ Todavía no puedes reclamar esta recompensa.");
            player.closeInventory();
            return;
        }

        for (String cmd : reward.getCommands()) {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName())
                            .replace("%reward_id%", reward.getId())
            );
        }

        dataConfig.set(player.getUniqueId() + "." + id.toLowerCase(), System.currentTimeMillis() / 1000);
        saveData();

        for (ItemStack item : reward.getItems()) {
            if (item == null || item.getType() == Material.AIR) continue;

            switch (item.getType()) {
                case NETHERITE_HELMET:
                case DIAMOND_HELMET:
                case IRON_HELMET:
                case GOLDEN_HELMET:
                case LEATHER_HELMET:
                    if (player.getInventory().getHelmet() == null) {
                        player.getInventory().setHelmet(item);
                    } else {
                        player.getInventory().addItem(item);
                    }
                    break;

                case NETHERITE_CHESTPLATE:
                case DIAMOND_CHESTPLATE:
                case IRON_CHESTPLATE:
                case GOLDEN_CHESTPLATE:
                case LEATHER_CHESTPLATE:
                    if (player.getInventory().getChestplate() == null) {
                        player.getInventory().setChestplate(item);
                    } else {
                        player.getInventory().addItem(item);
                    }
                    break;

                case NETHERITE_LEGGINGS:
                case DIAMOND_LEGGINGS:
                case IRON_LEGGINGS:
                case GOLDEN_LEGGINGS:
                case LEATHER_LEGGINGS:
                    if (player.getInventory().getLeggings() == null) {
                        player.getInventory().setLeggings(item);
                    } else {
                        player.getInventory().addItem(item);
                    }
                    break;

                case NETHERITE_BOOTS:
                case DIAMOND_BOOTS:
                case IRON_BOOTS:
                case GOLDEN_BOOTS:
                case LEATHER_BOOTS:
                    if (player.getInventory().getBoots() == null) {
                        player.getInventory().setBoots(item);
                    } else {
                        player.getInventory().addItem(item);
                    }
                    break;

                default:
                    player.getInventory().addItem(item);
                    break;
            }
        }

        String soundName = reward.getSound();
        if (soundName != null && !soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1, 1);
            } catch (IllegalArgumentException ignored) {}
        }

        String title = reward.getTitle();
        String subtitle = reward.getSubtitle();
        if (!title.isEmpty() || !subtitle.isEmpty()) {
            player.sendTitle(HexUtil.format(title), HexUtil.format(subtitle), 10, 60, 10);
        }

        player.closeInventory();
    }

    public void resetRewardTime(OfflinePlayer player, String rewardId) {
        dataConfig.set(player.getUniqueId() + "." + rewardId.toLowerCase(), null);
        saveData();
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("❌ No se pudo guardar data.yml");
            e.printStackTrace();
        }
    }

    public void reload() {
        plugin.reloadConfig();
        try {
            dataConfig.load(dataFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("❌ Error al recargar data.yml");
            e.printStackTrace();
        }
        loadRewards();
    }

    public static class Reward {
        private final String id;
        private final String name;
        private final String displayName;
        private final String permission;
        private final int cooldown;
        private final int slot;
        private final String material;
        private final Map<String, String> stateMaterials;
        private final List<String> commands;
        private final List<ItemStack> items;

        public Reward(String id, String name, String displayName, String permission, int cooldown, int slot, String material, Map<String, String> stateMaterials, List<String> commands, List<ItemStack> items) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.permission = permission;
            this.cooldown = cooldown;
            this.slot = slot;
            this.material = material;
            this.stateMaterials = stateMaterials;
            this.commands = commands;
            this.items = items;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public String getPermission() { return permission; }
        public int getCooldown() { return cooldown; }
        public int getSlot() { return slot; }
        public List<String> getCommands() { return commands; }
        public List<ItemStack> getItems() { return items; }

        public Material getMaterial() {
            try {
                return Material.valueOf(material.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Material.CHEST;
            }
        }

        public Material getStateMaterial(String state) {
            String type = stateMaterials.getOrDefault(state, material);
            try {
                return Material.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Material.CHEST;
            }
        }

        public List<String> getLoreSection(String type) {
            FileConfiguration config = Main.getInstance().getRewardManager().rewardsConfig;
            String path = "rewards." + id + ".lore." + type;
            if (config.isList(path)) {
                return config.getStringList(path);
            } else {
                return List.of("§c⚠ Lore no definido para: " + type);
            }
        }

        public String getSound() {
            return Main.getInstance().getRewardManager().rewardsConfig.getString("rewards." + id + ".sound", "");
        }

        public String getTitle() {
            return Main.getInstance().getRewardManager().rewardsConfig.getString("rewards." + id + ".title", "");
        }

        public String getSubtitle() {
            return Main.getInstance().getRewardManager().rewardsConfig.getString("rewards." + id + ".subtitle", "");
        }
    }
}
