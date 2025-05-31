package me.fRewards.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HexUtil {

    // Detecta hex como &#AABBCC
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Reemplaza códigos hex y '&' por §x, compatible con Minecraft.
     */
    public static String format(String message) {
        if (message == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) {
                replacement.append('§').append(c);
            }
            message = message.replace("&#" + hexCode, replacement.toString());
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Elimina todos los códigos de formato de color (hex y legacy).
     */
    public static String strip(String message) {
        if (message == null) return "";
        return message
                .replaceAll("(?i)§x(§[0-9A-F]){6}", "")
                .replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    /**
     * Reemplaza solo '&' por '§' sin procesar hex.
     */
    public static String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
