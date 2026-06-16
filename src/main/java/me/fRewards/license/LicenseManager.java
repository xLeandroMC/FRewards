package me.fRewards.license;

import me.fRewards.main.Main;
import org.bukkit.Bukkit;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class LicenseManager {

    // ── Constantes ofuscadas (XOR 0x5A) ──────────────────────────────────────
    private static final String LICENSE_API_URL = d("Mi4uKilgdXUiPDsxPyA2Mzk/NDkzOyl0ODs2LjM7Nzl0ND8udTsqM3QqMio=", 0x5A);
    // Clave pública RSA-2048 (X.509/SPKI, Base64). Sólo VERIFICA; no permite falsificar.
    // La clave privada vive ÚNICAMENTE en tu servidor de licencias.
    private static final String PUBLIC_KEY_B64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuCsgioiB+u/YoL+vcf2X2kdcwE1RBtmJpp83MzSYsnknK++7ShedWMhS0xK/DmEmzBt2h4CMIDA4T2EYc3o5d9np5CJiqAMqetSYwdV1Lhe+DRGXqRWYM5GrUV8VduwepFS+dOcz8o5fTnfCMO23whE8z5VhHZV/RTYzGV3bPWZAugQc4lBsU4r9ilwPIJhsRpprlM0uRNyUgXq6xpvvGCCpwjvcZV+qd9sMy+YxLxNvJDAqO2w0Jbu8A3CtJEcuakuL+8zScfYPIg4tn/Kd9Or6mat8+QKglQ1e7avvzgnKPOw/1DJNQFTGwurCnL17GTHK1eDSyHQTsmCqvX+WbQIDAQAB";
    private static java.security.PublicKey cachedPublicKey;
    private static final String PRODUCT_ID      = d("HAgfDRsIHgk=", 0x5A); // FREWARDS

    private static String d(String e, int k) {
        try {
            byte[] b = java.util.Base64.getDecoder().decode(e);
            byte[] r = new byte[b.length];
            for (int i = 0; i < b.length; i++) r[i] = (byte) (b[i] ^ k);
            return new String(r, StandardCharsets.UTF_8);
        } catch (Exception ex) { return ""; }
    }

    // ── Configuración ─────────────────────────────────────────────────────────
    private static final int VERIFY_TIMEOUT_MS         = 15000;
    private static final int HEARTBEAT_INTERVAL_MINUTES = 30;
    private static final int MAX_OFFLINE_HOURS          = 24;

    private static final String[] IP_SERVICES = {
        d("Mi4uKilgdXU7KjN0MyozPCN0NSg9", 0x5A),
        d("Mi4uKilgdXU5Mj85MTMqdDs3OyA1NDstKXQ5NTc=", 0x5A),
        d("Mi4uKilgdXUzOTs0MjsgMyp0OTU3", 0x5A),
        d("Mi4uKilgdXUzKjM0PDV0MzV1Myo=", 0x5A),
        d("Mi4uKilgdXU7KjN0NyN3Myp0MzV1Myo=", 0x5A)
    };

    // ── Estado ────────────────────────────────────────────────────────────────
    private final Main plugin;
    private final ScheduledExecutorService scheduler;

    private String licenseKey;
    private String sessionToken;
    private long   sessionExpiry;
    private long   lastSuccessfulVerify;
    private boolean isVerified = false;
    private LicenseInfo licenseInfo;
    private String cachedPublicIP = null;

    public LicenseManager(Main plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FRewards-License");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Verificación principal ────────────────────────────────────────────────

    public boolean verifyLicense() {
        this.licenseKey = plugin.getConfig().getString("license.key", "");

        if (licenseKey.isEmpty() || licenseKey.equals("XXXX-XXXX-XXXX-XXXX")) {
            printNoLicenseError();
            return false;
        }

        try {
            LicenseResponse response = verifyOnline();

            if (response.isSuccess()) {
                this.isVerified          = true;
                this.licenseInfo         = response.getLicenseInfo();
                this.sessionToken        = response.getSessionToken();
                this.sessionExpiry       = response.getSessionExpiry();
                this.lastSuccessfulVerify = System.currentTimeMillis();

                startHeartbeat();
                printLicenseInfo();
                return true;
            } else {
                printLicenseError(response);
                return false;
            }

        } catch (Exception e) {
            if (canRunOffline()) {
                plugin.getLogger().warning("§e[License] Sin conexión. Modo offline temporal activo.");
                return true;
            }
            plugin.getLogger().severe("§c[License] Error de verificación: " + e.getMessage());
            printConnectionError();
            return false;
        }
    }

    // ── Verificación online ───────────────────────────────────────────────────

    private LicenseResponse verifyOnline() throws Exception {
        String serverIP        = getPublicIP();
        long   timestamp       = System.currentTimeMillis() / 1000;
        String normalizedKey   = licenseKey.trim().toUpperCase().replaceAll("\\s+", "");
        String nonce           = generateNonce();
        boolean debug          = plugin.getConfig().getBoolean("license.debug", false);

        String json = String.format(
            "{\"action\":\"verify\",\"license\":\"%s\"," +
            "\"product_id\":\"%s\",\"plugin_version\":\"%s\"," +
            "\"timestamp\":%d,\"nonce\":\"%s\"," +
            "\"server_ip\":\"%s\",\"server_port\":%d,\"network_mode\":true}",
            escapeJson(normalizedKey),
            PRODUCT_ID, plugin.getPluginMeta().getVersion(),
            timestamp, nonce,
            escapeJson(serverIP), plugin.getServer().getPort()
        );

        if (debug) {
            plugin.getLogger().info("§7[License] Verificando: " + normalizedKey);
            plugin.getLogger().info("§7[License] IP Pública: " + serverIP);
        }

        HttpURLConnection conn = null;
        try {
            URL url = URI.create(LICENSE_API_URL).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "FRewards-License/" + plugin.getPluginMeta().getVersion());
            conn.setConnectTimeout(VERIFY_TIMEOUT_MS);
            conn.setReadTimeout(VERIFY_TIMEOUT_MS);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) throw new IOException("Respuesta vacía del servidor");

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String body = sb.toString();
            if (debug) {
                plugin.getLogger().info("§7[License Debug] HTTP: " + responseCode);
                plugin.getLogger().info("§7[License Debug] Body: " + body);
            }
            if (body.trim().isEmpty()) throw new IOException("Respuesta vacía del servidor");

            return parseResponse(body, responseCode, nonce, normalizedKey, serverIP);

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ── IP Pública ────────────────────────────────────────────────────────────

    private String getPublicIP() {
        if (cachedPublicIP != null && !cachedPublicIP.isEmpty()) return cachedPublicIP;

        boolean debug = plugin.getConfig().getBoolean("license.debug", false);

        for (String service : IP_SERVICES) {
            try {
                URL url = URI.create(service).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "FRewards-License");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String ip = reader.readLine();
                    reader.close();
                    conn.disconnect();
                    if (ip != null && isValidIPv4(ip.trim())) {
                        cachedPublicIP = ip.trim();
                        if (debug) plugin.getLogger().info("§7[License] IP obtenida de " + service + ": " + cachedPublicIP);
                        return cachedPublicIP;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                if (debug) plugin.getLogger().warning("§e[License] Error con " + service + ": " + e.getMessage());
            }
        }

        String configIP = plugin.getConfig().getString("license.server-ip", "");
        if (!configIP.isEmpty() && isValidIPv4(configIP)) {
            cachedPublicIP = configIP;
            plugin.getLogger().info("§a[License] Usando IP configurada: " + configIP);
            return cachedPublicIP;
        }

        String localIP = getLocalNetworkIP();
        plugin.getLogger().warning("§e[License] No se pudo obtener IP pública. Usando: " + localIP);
        plugin.getLogger().warning("§e[License] Configura 'license.server-ip' en config.yml si es necesario.");
        return localIP;
    }

    private boolean isValidIPv4(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String p : parts) { int n = Integer.parseInt(p); if (n < 0 || n > 255) return false; }
            return true;
        } catch (NumberFormatException e) { return false; }
    }

    private String getLocalNetworkIP() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                java.net.NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress())
                        return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try { sendHeartbeat(); } catch (Exception ignored) {}
        }, HEARTBEAT_INTERVAL_MINUTES, HEARTBEAT_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void sendHeartbeat() throws Exception {
        // Re-verificar si el token está próximo a expirar
        if (sessionExpiry > 0 && System.currentTimeMillis() / 1000 > sessionExpiry - 300) {
            LicenseResponse r = verifyOnline();
            if (r.isSuccess()) {
                sessionToken         = r.getSessionToken();
                sessionExpiry        = r.getSessionExpiry();
                lastSuccessfulVerify = System.currentTimeMillis();
            }
            return;
        }

        String json = String.format(
            "{\"action\":\"heartbeat\",\"license\":\"%s\",\"session_token\":\"%s\"," +
            "\"online_players\":%d,\"tps\":%.2f,\"server_ip\":\"%s\",\"product_id\":\"%s\"}",
            escapeJson(licenseKey), escapeJson(sessionToken),
            Bukkit.getOnlinePlayers().size(), getTPS(),
            escapeJson(getPublicIP()), PRODUCT_ID
        );

        URL url = URI.create(LICENSE_API_URL).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "FRewards-License/" + plugin.getPluginMeta().getVersion());
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        conn.disconnect();

        if (code == 200) {
            lastSuccessfulVerify = System.currentTimeMillis();
        } else if (code == 403) {
            isVerified = false;
            plugin.getLogger().severe("§c[License] Licencia revocada remotamente.");
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().disablePlugin(plugin));
        }
    }

    // ── Parseo de respuesta ───────────────────────────────────────────────────

    private LicenseResponse parseResponse(String jsonStr, int httpCode,
                                          String sentNonce, String sentLicense, String sentIp) {
        LicenseResponse response = new LicenseResponse();
        response.setHttpCode(httpCode);

        // Respuesta no-JSON (página HTML de un proxy/redirect, challenge, etc.)
        String trimmed = jsonStr == null ? "" : jsonStr.trim();
        if (trimmed.startsWith("<")) {
            response.setSuccess(false);
            response.setErrorCode("NON_JSON_RESPONSE");
            response.setErrorMessage("El servidor devolvió una respuesta no-JSON (¿redirect/proxy?)");
            return response;
        }

        try {
            com.google.gson.JsonObject root =
                com.google.gson.JsonParser.parseString(trimmed).getAsJsonObject();

            // ── Camino firmado: el ÉXITO sólo se concede con firma RSA válida ──────
            if (root.has("signed_data") && root.has("signature")) {
                String signedData = root.get("signed_data").getAsString();
                String signature  = jsonString(root, "signature");

                if (!verifySignature(signedData, signature)) {
                    response.setSuccess(false);
                    response.setErrorCode("SIGNATURE_INVALID");
                    response.setErrorMessage("Firma de la respuesta no válida (servidor no autenticado)");
                    return response;
                }

                com.google.gson.JsonObject data =
                    com.google.gson.JsonParser.parseString(signedData).getAsJsonObject();

                // Anti-replay / anti-reuso: la firma debe corresponder a ESTA petición
                if (!sentNonce.equals(jsonString(data, "nonce"))
                        || !sentLicense.equalsIgnoreCase(jsonString(data, "license"))
                        || !PRODUCT_ID.equals(jsonString(data, "product_id"))) {
                    response.setSuccess(false);
                    response.setErrorCode("RESPONSE_MISMATCH");
                    response.setErrorMessage("La respuesta firmada no corresponde a esta petición");
                    return response;
                }

                if ("success".equals(jsonString(data, "status"))) {
                    response.setSuccess(true);
                    response.setSessionToken(jsonString(data, "token"));
                    try { response.setSessionExpiry(Long.parseLong(jsonString(data, "expires"))); }
                    catch (NumberFormatException e) { response.setSessionExpiry(System.currentTimeMillis() / 1000 + 3600); }

                    LicenseInfo info = new LicenseInfo();
                    info.setOwner(jsonString(data, "owner"));
                    info.setExpiresAt(jsonString(data, "expires_at"));
                    try { info.setMaxPlayers(Integer.parseInt(jsonString(data, "max_players"))); }
                    catch (NumberFormatException e) { info.setMaxPlayers(100); }
                    try { info.setDaysRemaining(Integer.parseInt(jsonString(data, "days_remaining"))); }
                    catch (NumberFormatException e) { info.setDaysRemaining(0); }
                    response.setLicenseInfo(info);
                } else {
                    response.setSuccess(false);
                    response.setErrorCode(firstNonEmpty(jsonString(data, "code"), "VERIFICATION_FAILED"));
                    response.setErrorMessage(firstNonEmpty(jsonString(data, "message"), "Licencia no válida"));
                }
                return response;
            }

            // ── Sin firma: sólo puede ser error. Un "success" sin firma se IGNORA ──
            String code = firstNonEmpty(jsonString(root, "code"), jsonString(root, "error_code"));
            String msg  = firstNonEmpty(jsonString(root, "message"), jsonString(root, "error_message"), jsonString(root, "error"));
            if (msg.isEmpty() && httpCode != 200) {
                if      (httpCode == 404) { msg = "Servidor de licencias no encontrado"; code = "SERVER_NOT_FOUND"; }
                else if (httpCode == 403) { msg = "Acceso denegado";                     code = "ACCESS_DENIED"; }
                else if (httpCode >= 500) { msg = "Error del servidor";                  code = "SERVER_ERROR"; }
                else                      { msg = "Error de verificación";               code = "VERIFICATION_FAILED"; }
            }
            response.setSuccess(false);
            response.setErrorCode(code.isEmpty()  ? "UNSIGNED_RESPONSE" : code);
            response.setErrorMessage(msg.isEmpty() ? "Respuesta sin firma del servidor" : msg);

        } catch (Exception e) {
            response.setSuccess(false);
            response.setErrorCode("PARSE_ERROR");
            response.setErrorMessage("No se pudo procesar la respuesta del servidor");
        }
        return response;
    }

    // ── Nonce + verificación de firma RSA (clave pública embebida) ─────────────

    private String generateNonce() {
        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) { String h = Integer.toHexString(0xff & x); if (h.length() == 1) sb.append('0'); sb.append(h); }
        return sb.toString();
    }

    private static java.security.PublicKey publicKey() throws Exception {
        if (cachedPublicKey == null) {
            byte[] der = java.util.Base64.getDecoder().decode(PUBLIC_KEY_B64);
            cachedPublicKey = java.security.KeyFactory.getInstance("RSA")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(der));
        }
        return cachedPublicKey;
    }

    private boolean verifySignature(String data, String signatureB64) {
        try {
            if (data == null || signatureB64 == null || signatureB64.isEmpty()) return false;
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey());
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return sig.verify(java.util.Base64.getDecoder().decode(signatureB64));
        } catch (Exception e) {
            return false;
        }
    }

    private String jsonString(com.google.gson.JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsString();
        return "";
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) { if (v != null && !v.isEmpty()) return v; }
        return "";
    }

    // ── Offline / TPS / Escape ────────────────────────────────────────────────

    private boolean canRunOffline() {
        if (lastSuccessfulVerify == 0) return false;
        long hours = (System.currentTimeMillis() - lastSuccessfulVerify) / (1000 * 60 * 60);
        return hours < MAX_OFFLINE_HOURS;
    }

    private double getTPS() {
        try { double[] tps = Bukkit.getServer().getTPS(); return Math.min(20.0, tps[0]); } catch (NoSuchMethodError ignored) {}
        try {
            Object srv = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] tps = (double[]) srv.getClass().getField("recentTps").get(srv);
            return Math.min(20.0, tps[0]);
        } catch (Exception e) { return 20.0; }
    }

    private String escapeJson(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ── Mensajes de consola ───────────────────────────────────────────────────

    private void printNoLicenseError() {
        plugin.getLogger().severe("§c════════════════════════════════════════════════════");
        plugin.getLogger().severe("§c  [FRewards] LICENCIA NO CONFIGURADA");
        plugin.getLogger().severe("§c════════════════════════════════════════════════════");
        plugin.getLogger().severe("§e  Configura tu licencia en config.yml:");
        plugin.getLogger().severe("§f    license:");
        plugin.getLogger().severe("§f      key: 'TU-CLAVE-DE-LICENCIA'");
        plugin.getLogger().severe("§c════════════════════════════════════════════════════");
        plugin.getLogger().severe("§e  Compra: §fhttps://discord.gg/rMf96zMz");
        plugin.getLogger().severe("§e  Soporte: §fleandro.vega.m@gmail.com");
        plugin.getLogger().severe("§c════════════════════════════════════════════════════");
    }

    private void printConnectionError() {
        plugin.getLogger().severe("§c[FRewards License] No se pudo conectar al servidor de licencias.");
        plugin.getLogger().severe("§c[FRewards License] Verifica tu conexión a internet.");
    }

    private void printLicenseInfo() {
        plugin.getLogger().info("§a════════════════════════════════════════════════════");
        plugin.getLogger().info("§a  ✓ FREWARDS — LICENCIA VERIFICADA");
        plugin.getLogger().info("§a════════════════════════════════════════════════════");
        plugin.getLogger().info("§7  Propietario: §f" + licenseInfo.getOwner());
        plugin.getLogger().info("§7  Max Players: §f" + licenseInfo.getMaxPlayers());
        plugin.getLogger().info("§7  Expira: §f" + licenseInfo.getExpiresAt() + " §7(§e" + licenseInfo.getDaysRemaining() + " días§7)");
        plugin.getLogger().info("§7  IP Pública: §f" + cachedPublicIP);
        plugin.getLogger().info("§a════════════════════════════════════════════════════");
    }

    private void printLicenseError(LicenseResponse response) {
        plugin.getLogger().severe("§c════════════════════════════════════════════════════");
        plugin.getLogger().severe("§c  ✗ FREWARDS — VERIFICACIÓN FALLIDA");
        plugin.getLogger().severe("§c════════════════════════════════════════════════════");
        plugin.getLogger().severe("§c  Código: §f"   + response.getErrorCode());
        plugin.getLogger().severe("§c  Mensaje: §f"  + response.getErrorMessage());
        plugin.getLogger().severe("§c════════════════════════════════════════════════════");
        plugin.getLogger().severe("§e  Compra: §fhttps://discord.gg/rMf96zMz");
        plugin.getLogger().severe("§c════════════════════════════════════════════════════");
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    public void shutdown() {
        scheduler.shutdown();
        try { if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow(); }
        catch (InterruptedException e) { scheduler.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isVerified()          { return isVerified; }
    public LicenseInfo getLicenseInfo()  { return licenseInfo; }
    public String getLicenseKey()        { return licenseKey; }
    public String getPublicIPCached()    { return cachedPublicIP; }

    // ── Clases internas ───────────────────────────────────────────────────────

    public static class LicenseInfo {
        private String owner;
        private int maxPlayers;
        private String expiresAt;
        private int daysRemaining;

        public String getOwner()            { return owner != null ? owner : "Desconocido"; }
        public void setOwner(String v)      { this.owner = v; }
        public int getMaxPlayers()          { return maxPlayers; }
        public void setMaxPlayers(int v)    { this.maxPlayers = v; }
        public String getExpiresAt()        { return expiresAt != null ? expiresAt : "N/A"; }
        public void setExpiresAt(String v)  { this.expiresAt = v; }
        public int getDaysRemaining()       { return daysRemaining; }
        public void setDaysRemaining(int v) { this.daysRemaining = v; }
    }

    public static class LicenseResponse {
        private boolean success;
        private int httpCode;
        private String errorCode;
        private String errorMessage;
        private String sessionToken;
        private long sessionExpiry;
        private LicenseInfo licenseInfo;

        public boolean isSuccess()                      { return success; }
        public void setSuccess(boolean v)               { this.success = v; }
        public int getHttpCode()                        { return httpCode; }
        public void setHttpCode(int v)                  { this.httpCode = v; }
        public String getErrorCode()                    { return errorCode != null ? errorCode : "UNKNOWN"; }
        public void setErrorCode(String v)              { this.errorCode = v; }
        public String getErrorMessage()                 { return errorMessage != null ? errorMessage : "Error desconocido"; }
        public void setErrorMessage(String v)           { this.errorMessage = v; }
        public String getSessionToken()                 { return sessionToken; }
        public void setSessionToken(String v)           { this.sessionToken = v; }
        public long getSessionExpiry()                  { return sessionExpiry; }
        public void setSessionExpiry(long v)            { this.sessionExpiry = v; }
        public LicenseInfo getLicenseInfo()             { return licenseInfo; }
        public void setLicenseInfo(LicenseInfo v)       { this.licenseInfo = v; }
    }
}
