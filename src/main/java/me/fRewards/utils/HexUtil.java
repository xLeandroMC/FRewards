package me.fRewards.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HexUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static final LegacyComponentSerializer LEGACY =
        LegacyComponentSerializer.legacySection();

    /**
     * Convierte códigos hex (&#RRGGBB) y códigos & a § para Minecraft.
     */
    public static String format(String message) {
        if (message == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(message);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder sb = new StringBuilder("§x");
            for (char c : hex.toCharArray()) sb.append('§').append(c);
            message = message.replace("&#" + hex, sb.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Convierte un texto formateado (hex + &) a un Component de Adventure.
     * Usar esto para títulos de inventario y nombres/lore de ítems en 1.21+.
     */
    /**
     * Para nombres/lore de ítems: desactiva italic (Minecraft los muestra italic por defecto).
     */
    public static Component comp(String text) {
        return LEGACY.deserialize(format(text))
            .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Para títulos de inventario: NO desactiva italic.
     * Usar este método en createInventory() para que el round-trip comp→fromComp sea estable.
     */
    public static Component compTitle(String text) {
        return LEGACY.deserialize(format(text));
    }

    /**
     * Serializa un Component a String con códigos §.
     * Útil para comparar títulos de inventario.
     */
    public static String fromComp(Component component) {
        return LEGACY.serialize(component);
    }

    /**
     * Elimina todos los códigos de color (hex y legacy) de un texto.
     */
    public static String strip(String message) {
        if (message == null) return "";
        return message
            .replaceAll("(?i)§x(§[0-9A-F]){6}", "")
            .replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    /**
     * Conversión básica & → §, sin procesar hex.
     */
    public static String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
