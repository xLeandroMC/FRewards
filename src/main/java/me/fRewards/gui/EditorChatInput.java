package me.fRewards.gui;

import me.fRewards.config.RewardManager.Reward;
import me.fRewards.main.Main;
import me.fRewards.utils.HexUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Captura entrada de texto del jugador vía chat de forma segura:
 *  - El mensaje NO se muestra públicamente (evento cancelado).
 *  - El procesamiento corre en el hilo principal (no async I/O).
 *  - Si el jugador se desconecta, la sesión se limpia automáticamente.
 *  - Siempre guarda en rewardsgui.yml (nunca en config.yml).
 */
public class EditorChatInput implements Listener {

    public enum InputType { DISPLAY_NAME, COOLDOWN_CUSTOM, ADD_COMMAND }

    private record Session(String rewardId, InputType type) {}

    private static final Map<UUID, Session> sessions = new HashMap<>();

    // ── API pública ───────────────────────────────────────────────────────────

    public static void request(Player player, String rewardId, InputType type, String prompt) {
        sessions.put(player.getUniqueId(), new Session(rewardId, type));
        player.sendMessage("");
        player.sendMessage(HexUtil.format("&#FCD05C&l▶ &r" + prompt));
        player.sendMessage(HexUtil.format("&8(Escribe &ccancelar &8para anular)"));
        player.sendMessage("");
    }

    // ── Eventos ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;

        // Cancelar para que no aparezca en el chat público
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();

        // Procesar en el hilo principal (obligatorio para API de Bukkit)
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> process(player, session, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    // ── Procesamiento ─────────────────────────────────────────────────────────

    private void process(Player player, Session session, String input) {
        sessions.remove(player.getUniqueId());

        if (input.equalsIgnoreCase("cancelar")) {
            player.sendMessage(Main.getInstance().getMessage("editor-cancelled"));
            EditorMainGUI.open(player, session.rewardId());
            return;
        }

        FileConfiguration config = Main.getInstance().getRewardsGuiConfig();
        String path = "rewards." + session.rewardId();

        switch (session.type()) {

            case DISPLAY_NAME -> {
                config.set(path + ".display-name", input);
                Main.getInstance().saveRewardsGuiConfig();
                Main.getInstance().getRewardManager().reload();
                player.sendMessage(Main.getInstance().getMessage("editor-saved")
                    .replace("%field%", "nombre")
                    .replace("%value%", HexUtil.format(input)));
                EditorMainGUI.open(player, session.rewardId());
            }

            case COOLDOWN_CUSTOM -> {
                try {
                    int secs = Integer.parseInt(input);
                    if (secs < -1 || secs == 0) throw new NumberFormatException();

                    config.set(path + ".cooldown", secs);
                    Main.getInstance().saveRewardsGuiConfig();
                    Main.getInstance().getRewardManager().reload();

                    String timeStr = secs == -1
                        ? "una sola vez"
                        : me.fRewards.utils.TimeUtil.formatSmart(secs);
                    player.sendMessage(Main.getInstance().getMessage("editor-cooldown-set")
                        .replace("%time%", timeStr));
                    EditorMainGUI.open(player, session.rewardId());

                } catch (NumberFormatException e) {
                    player.sendMessage(HexUtil.format("&c❌ Valor inválido. Usa -1 (una vez) o un número positivo en segundos."));
                    // Volver a pedir
                    request(player, session.rewardId(), InputType.COOLDOWN_CUSTOM,
                        "&#FCD05C§lCooldown §r§7— Escribe los segundos &8(ej: 86400 = 24h)§7:");
                }
            }

            case ADD_COMMAND -> {
                Reward reward = Main.getInstance().getRewardManager().getReward(session.rewardId());
                List<String> cmds = reward != null
                    ? new ArrayList<>(reward.getCommands())
                    : new ArrayList<>();
                cmds.add(input);
                config.set(path + ".commands", cmds);
                Main.getInstance().saveRewardsGuiConfig();
                Main.getInstance().getRewardManager().reload();
                player.sendMessage(Main.getInstance().getMessage("editor-cmd-added")
                    .replace("%cmd%", input));
                CommandsEditorGUI.open(player, session.rewardId());
            }
        }
    }
}
