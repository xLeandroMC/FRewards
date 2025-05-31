package me.fRewards.utils;

public class TimeUtil {

    // ⏰ Formato tipo "02h 15m 09s"
    public static String formatSeconds(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60; // ✅ Esto ahora está bien
        long s = seconds % 60;
        return String.format("%02dh %02dm %02ds", h, m, s);
    }

    // 💡 Formato smart tipo "1h 15m" o "5m 12s"
    public static String formatSmart(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || h > 0) sb.append(m).append("m ");
        sb.append(s).append("s");

        return sb.toString().trim();
    }
}
