package me.fRewards.storage;

import me.fRewards.main.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Almacén de los tiempos de reclamo por jugador en SQLite.
 *
 * <p>El driver (org.xerial:sqlite-jdbc) se carga vía el <i>library loader</i> de
 * Paper declarado en plugin.yml, así que NO se incluye en el jar (sin shading ni
 * reglas extra de ProGuard).</p>
 *
 * <p>Diseño de concurrencia: una caché en memoria ({@link #cache}) sirve todas las
 * lecturas en el hilo principal de forma instantánea; las escrituras se aplican a la
 * caché al momento y se persisten a disco en un ejecutor de un solo hilo, de modo que
 * el tick del servidor nunca hace I/O de base de datos.</p>
 */
public class ClaimStorage {

    private final Main plugin;
    private final Connection connection;
    private final ExecutorService dbExecutor;

    /** uuid → (rewardId → epoch segundos del último reclamo). */
    private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();

    public ClaimStorage(Main plugin) throws Exception {
        this.plugin = plugin;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FRewards-DB");
            t.setDaemon(true);
            return t;
        });

        // El driver lo provee el library loader de Paper; forzar el registro.
        Class.forName("org.sqlite.JDBC");
        File dbFile = new File(plugin.getDataFolder(), "claims.db");
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

        createTable();
        loadCache();
    }

    private void createTable() throws SQLException {
        try (PreparedStatement st = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS claims (" +
                "  uuid       TEXT    NOT NULL," +
                "  reward_id  TEXT    NOT NULL," +
                "  claimed_at INTEGER NOT NULL," +
                "  PRIMARY KEY (uuid, reward_id))")) {
            st.executeUpdate();
        }
    }

    /** Carga toda la tabla a memoria una sola vez al arrancar (una consulta). */
    private void loadCache() throws SQLException {
        cache.clear();
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT uuid, reward_id, claimed_at FROM claims");
             ResultSet rs = st.executeQuery()) {
            int rows = 0;
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                         .put(rs.getString("reward_id"), rs.getLong("claimed_at"));
                    rows++;
                } catch (IllegalArgumentException ignored) { /* uuid corrupto: omitir */ }
            }
            plugin.getLogger().info("✔ " + rows + " registros de reclamo cargados desde claims.db");
        }
    }

    // ── API de lectura (hilo principal, desde caché) ──────────────────────────

    /** Último reclamo (epoch segundos) o 0 si nunca se reclamó. */
    public long getLastClaim(UUID uuid, String rewardId) {
        Map<String, Long> byReward = cache.get(uuid);
        if (byReward == null) return 0L;
        return byReward.getOrDefault(rewardId.toLowerCase(), 0L);
    }

    // ── API de escritura (caché inmediata + persistencia async) ───────────────

    public void setClaim(UUID uuid, String rewardId, long epochSeconds) {
        String rid = rewardId.toLowerCase();
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(rid, epochSeconds);
        runAsync(() -> {
            try (PreparedStatement st = connection.prepareStatement(
                    "INSERT INTO claims (uuid, reward_id, claimed_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(uuid, reward_id) DO UPDATE SET claimed_at = excluded.claimed_at")) {
                st.setString(1, uuid.toString());
                st.setString(2, rid);
                st.setLong(3, epochSeconds);
                st.executeUpdate();
            }
        });
    }

    public void clearReward(UUID uuid, String rewardId) {
        String rid = rewardId.toLowerCase();
        Map<String, Long> byReward = cache.get(uuid);
        if (byReward != null) byReward.remove(rid);
        runAsync(() -> {
            try (PreparedStatement st = connection.prepareStatement(
                    "DELETE FROM claims WHERE uuid = ? AND reward_id = ?")) {
                st.setString(1, uuid.toString());
                st.setString(2, rid);
                st.executeUpdate();
            }
        });
    }

    public void clearAll(UUID uuid) {
        cache.remove(uuid);
        runAsync(() -> {
            try (PreparedStatement st = connection.prepareStatement(
                    "DELETE FROM claims WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                st.executeUpdate();
            }
        });
    }

    // ── Migración de los datos legacy de data.yml ─────────────────────────────

    /**
     * Importa los reclamos antiguos guardados en data.yml (claves de primer nivel
     * con forma {@code <uuid>.<rewardId> = epoch}). Tras importar, elimina esas
     * claves de data.yml y guarda el archivo. Idempotente: si no hay nada que
     * migrar, no hace nada. Devuelve el número de registros importados.
     */
    public int migrateFromYaml(FileConfiguration dataConfig, File dataFile) {
        int imported = 0;
        boolean changed = false;
        for (String topKey : dataConfig.getKeys(false)) {
            // Las definiciones de ítems viven bajo "rewards.*"; saltarlas.
            if (topKey.equalsIgnoreCase("rewards")) continue;

            UUID uuid;
            try {
                uuid = UUID.fromString(topKey);
            } catch (IllegalArgumentException e) {
                continue; // no es una clave de jugador
            }

            ConfigurationSection sec = dataConfig.getConfigurationSection(topKey);
            if (sec == null) continue;

            Map<String, Long> batch = new HashMap<>();
            for (String rewardId : sec.getKeys(false)) {
                long ts = sec.getLong(rewardId, 0L);
                if (ts > 0) batch.put(rewardId.toLowerCase(), ts);
            }
            if (batch.isEmpty()) continue;

            try {
                upsertBatch(uuid, batch);
                cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).putAll(batch);
                imported += batch.size();
                dataConfig.set(topKey, null); // limpiar de data.yml
                changed = true;
            } catch (SQLException e) {
                plugin.getLogger().warning("⚠ No se pudo migrar reclamos de " + topKey + ": " + e.getMessage());
            }
        }

        if (changed) {
            try {
                dataConfig.save(dataFile);
            } catch (Exception e) {
                plugin.getLogger().severe("❌ No se pudo guardar data.yml tras la migración a SQLite");
            }
            plugin.getLogger().info("✔ Migrados " + imported + " reclamos de data.yml a claims.db");
        }
        return imported;
    }

    private void upsertBatch(UUID uuid, Map<String, Long> batch) throws SQLException {
        boolean prevAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement st = connection.prepareStatement(
                "INSERT INTO claims (uuid, reward_id, claimed_at) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, reward_id) DO UPDATE SET claimed_at = excluded.claimed_at")) {
            for (Map.Entry<String, Long> e : batch.entrySet()) {
                st.setString(1, uuid.toString());
                st.setString(2, e.getKey());
                st.setLong(3, e.getValue());
                st.addBatch();
            }
            st.executeBatch();
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(prevAutoCommit);
        }
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    private void runAsync(SqlTask task) {
        dbExecutor.submit(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().severe("❌ Error de SQLite: " + e.getMessage());
            }
        });
    }

    public void close() {
        dbExecutor.shutdown();
        try {
            // Esperar a que terminen las escrituras pendientes antes de cerrar.
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) dbExecutor.shutdownNow();
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("⚠ No se pudo cerrar claims.db: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SqlTask {
        void run() throws SQLException;
    }
}
