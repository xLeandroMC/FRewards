package me.fRewards.config;

import me.fRewards.main.Main;
import me.fRewards.storage.ClaimStorage;
import me.fRewards.utils.HexUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class RewardManager {

    private final Main plugin = Main.getInstance();
    private final Map<String, Reward> rewards = new LinkedHashMap<>();
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final ClaimStorage claims;

    public RewardManager() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("❌ No se pudo crear data.yml");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Tiempos de reclamo en SQLite (data.yml queda solo para definiciones de ítems).
        try {
            this.claims = new ClaimStorage(plugin);
            this.claims.migrateFromYaml(dataConfig, dataFile);
            // Recargar dataConfig tras la posible limpieza de claves legacy.
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        } catch (Exception e) {
            plugin.getLogger().severe("════════════════════════════════════════════════════");
            plugin.getLogger().severe("  FRewards: no se pudo inicializar el almacén SQLite.");
            plugin.getLogger().severe("  El driver sqlite-jdbc lo descarga Paper (library loader)");
            plugin.getLogger().severe("  en el primer arranque: requiere conexión a internet.");
            plugin.getLogger().severe("  Detalle: " + e.getMessage());
            plugin.getLogger().severe("════════════════════════════════════════════════════");
            throw new RuntimeException("ClaimStorage init falló", e);
        }

        loadRewards();
    }

    public ClaimStorage getClaimStorage() { return claims; }

    private FileConfiguration cfg() {
        return plugin.getRewardsGuiConfig();
    }

    private void loadRewards() {
        rewards.clear();
        FileConfiguration config = cfg();

        if (!config.isConfigurationSection("rewards")) return;

        Set<Integer> usedSlots = new HashSet<>();

        for (String rawKey : config.getConfigurationSection("rewards").getKeys(false)) {
            // Normalizar a minúsculas: getReward() siempre busca en minúsculas,
            // así una key con mayúsculas en el YAML seguiría siendo recuperable.
            String key = rawKey.toLowerCase();
            String path = "rewards." + rawKey;
            String name        = config.getString(path + ".name", key);
            String displayName = config.getString(path + ".display-name", "&f" + name);
            String permission  = config.getString(path + ".permission", "frewards." + key);
            int cooldown       = config.getInt(path + ".cooldown", 86400);
            int slot           = config.getInt(path + ".slot", 0);
            String material    = config.getString(path + ".material", "CHEST");
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
                        plugin.getLogger().warning("⚠ No se pudo cargar item '" + itemKey + "' de recompensa '" + key + "'");
                    }
                }
            }

            if (usedSlots.contains(slot)) {
                plugin.getLogger().warning("❌ Slot duplicado (" + slot + ") para la recompensa '" + key + "' — omitida.");
                continue;
            }
            usedSlots.add(slot);
            rewards.put(key, new Reward(key, name, displayName, permission, cooldown, slot, material, stateMaterials, commands, items));
        }

        plugin.getLogger().info("✔ " + rewards.size() + " recompensas cargadas.");
    }

    private ItemStack fromBase64(String data) {
        return me.fRewards.gui.ItemsEditorGUI.fromBase64(data);
    }

    // ── Acceso ──────────────────────────────────────────────────────────────

    public Collection<Reward> getAllRewards() { return rewards.values(); }
    public Reward getReward(String id)        { return id == null ? null : rewards.get(id.toLowerCase()); }
    public Set<String> getRewardIds()         { return rewards.keySet(); }

    // ── Lógica de cooldown ───────────────────────────────────────────────────

    public boolean canClaim(Player player, String id) {
        Reward reward = getReward(id);
        if (reward == null) return false;
        long lastClaim = claims.getLastClaim(player.getUniqueId(), id);
        long now = System.currentTimeMillis() / 1000;
        if (reward.getCooldown() == -1) return lastClaim == 0;
        return (now - lastClaim) >= reward.getCooldown();
    }

    public long getRemainingTime(Player player, String id) {
        Reward reward = getReward(id);
        if (reward == null) return 0;
        long lastClaim = claims.getLastClaim(player.getUniqueId(), id);
        long now = System.currentTimeMillis() / 1000;
        if (reward.getCooldown() == -1) return lastClaim == 0 ? 0 : Long.MAX_VALUE;
        return Math.max(0, reward.getCooldown() - (now - lastClaim));
    }

    // ── Reclamar recompensa ──────────────────────────────────────────────────

    public void claimReward(Player player, String id) {
        Reward reward = getReward(id);
        if (reward == null) {
            player.sendMessage(plugin.getMessage("invalid-reward"));
            return;
        }
        if (!player.hasPermission(reward.getPermission())) {
            player.sendMessage(plugin.getMessage("no-permission"));
            return;
        }
        if (!canClaim(player, id)) {
            player.sendMessage(plugin.getMessage("reward-cooldown"));
            return;
        }

        // Ejecutar comandos de consola
        for (String cmd : reward.getCommands()) {
            Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                cmd.replace("%player%", player.getName()).replace("%reward_id%", reward.getId())
            );
        }

        // Registrar tiempo de reclamo (caché + persistencia async en SQLite)
        claims.setClaim(player.getUniqueId(), id, System.currentTimeMillis() / 1000);

        // Entregar items
        giveItems(player, reward);

        // Efectos
        playEffects(player, reward);

        player.sendMessage(plugin.getMessage("reward-claimed"));
        player.closeInventory();
    }

    private void giveItems(Player player, Reward reward) {
        for (ItemStack item : reward.getItems()) {
            if (item == null || item.getType() == Material.AIR) continue;
            String type = item.getType().name();
            if (type.endsWith("_HELMET") && player.getInventory().getHelmet() == null) {
                player.getInventory().setHelmet(item);
            } else if (type.endsWith("_CHESTPLATE") && player.getInventory().getChestplate() == null) {
                player.getInventory().setChestplate(item);
            } else if (type.endsWith("_LEGGINGS") && player.getInventory().getLeggings() == null) {
                player.getInventory().setLeggings(item);
            } else if (type.endsWith("_BOOTS") && player.getInventory().getBoots() == null) {
                player.getInventory().setBoots(item);
            } else {
                // addItem devuelve los ítems que NO cupieron; soltarlos al suelo
                // evita que la recompensa se pierda con el inventario lleno.
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
    }

    private void playEffects(Player player, Reward reward) {
        String soundName = reward.getSound();
        if (soundName != null && !soundName.isEmpty()) {
            try {
                player.playSound(net.kyori.adventure.sound.Sound.sound(
                    net.kyori.adventure.key.Key.key(soundName.toLowerCase().replace("_", ".")),
                    net.kyori.adventure.sound.Sound.Source.MASTER, 1f, 1f));
            } catch (Exception ignored) {}
        }
        String titleStr    = reward.getTitle();
        String subtitleStr = reward.getSubtitle();
        if (!titleStr.isEmpty() || !subtitleStr.isEmpty()) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                HexUtil.comp(titleStr),
                HexUtil.comp(subtitleStr),
                net.kyori.adventure.title.Title.Times.times(
                    net.kyori.adventure.util.Ticks.duration(10),
                    net.kyori.adventure.util.Ticks.duration(60),
                    net.kyori.adventure.util.Ticks.duration(10))));
        }
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    public void resetRewardTime(OfflinePlayer player, String rewardId) {
        claims.clearReward(player.getUniqueId(), rewardId);
    }

    public void resetAllRewards(OfflinePlayer player) {
        claims.clearAll(player.getUniqueId());
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    public void reload() {
        plugin.reloadConfig();
        plugin.reloadRewardsGuiConfig();
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadRewards();
    }

    // ── Inner class: Reward ──────────────────────────────────────────────────

    public static class Reward {
        private final String id, name, displayName, permission, material;
        private final int cooldown, slot;
        private final Map<String, String> stateMaterials;
        private final List<String> commands;
        private final List<ItemStack> items;

        public Reward(String id, String name, String displayName, String permission,
                      int cooldown, int slot, String material,
                      Map<String, String> stateMaterials, List<String> commands, List<ItemStack> items) {
            this.id = id; this.name = name; this.displayName = displayName;
            this.permission = permission; this.cooldown = cooldown; this.slot = slot;
            this.material = material; this.stateMaterials = stateMaterials;
            this.commands = commands; this.items = items;
        }

        public String getId()             { return id; }
        public String getName()           { return name; }
        public String getDisplayName()    { return displayName; }
        public String getPermission()     { return permission; }
        public int    getCooldown()       { return cooldown; }
        public int    getSlot()           { return slot; }
        public List<String>    getCommands() { return commands; }
        public List<ItemStack> getItems()    { return items; }

        public Material getMaterial() {
            try { return Material.valueOf(material.toUpperCase()); }
            catch (IllegalArgumentException e) { return Material.CHEST; }
        }

        public Material getStateMaterial(String state) {
            // 1º: material-state por recompensa
            String type = stateMaterials.get(state);
            // 2º: material-state global en rewardsgui.yml
            if (type == null) {
                type = Main.getInstance().getRewardsGuiConfig().getString("material-state." + state);
            }
            // 3º: material base
            if (type == null) type = material;
            try { return Material.valueOf(type.toUpperCase()); }
            catch (IllegalArgumentException e) { return getMaterial(); }
        }

        public List<String> getLoreSection(String type) {
            FileConfiguration config = Main.getInstance().getRewardsGuiConfig();
            String path = "rewards." + id + ".lore." + type;
            return config.isList(path) ? config.getStringList(path) : List.of("§c⚠ Lore no definido: " + type);
        }

        public String getSound() {
            return Main.getInstance().getRewardsGuiConfig().getString("rewards." + id + ".sound", "");
        }
        public String getTitle() {
            return Main.getInstance().getRewardsGuiConfig().getString("rewards." + id + ".title", "");
        }
        public String getSubtitle() {
            return Main.getInstance().getRewardsGuiConfig().getString("rewards." + id + ".subtitle", "");
        }
    }
}
