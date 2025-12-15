package org.macl.ctc.game;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.macl.ctc.Main;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final Main plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Get DB config from environment variables with defaults
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String database = System.getenv().getOrDefault("DB_NAME", "ctc_stats");
        String username = System.getenv().getOrDefault("DB_USER", "ctc");
        String password = System.getenv().getOrDefault("DB_PASS", "Moana1959");

        plugin.getLogger().info("Connecting to MySQL database at " + host + ":" + port);

        // Build JDBC connection string
        String jdbcUrl = String.format(
            "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            host, port, database
        );

        // Configure HikariCP
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            // Initialize connection pool
            dataSource = new HikariDataSource(config);

            // Test connection and create table
            try (Connection conn = dataSource.getConnection()) {
                plugin.getLogger().info("Database connection successful!");
                createTables(conn);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("FATAL: Cannot connect to database!");
            plugin.getLogger().severe("Error: " + e.getMessage());
            plugin.getLogger().severe("Make sure MySQL is running and credentials are correct.");
            plugin.getLogger().severe("Environment variables: DB_HOST=" + host + " DB_PORT=" + port +
                                    " DB_NAME=" + database + " DB_USER=" + username);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    private void createTables(Connection conn) throws SQLException {
        String createPlayerStatsTable = """
            CREATE TABLE IF NOT EXISTS player_stats (
                player_uuid CHAR(36) PRIMARY KEY,
                kills INT NOT NULL DEFAULT 0,
                deaths INT NOT NULL DEFAULT 0,
                wins INT NOT NULL DEFAULT 0,
                captures INT NOT NULL DEFAULT 0,
                core_cracks INT NOT NULL DEFAULT 0,
                games_played INT NOT NULL DEFAULT 0,
                damage_dealt INT NOT NULL DEFAULT 0,
                damage_taken INT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """;

        String createServerMapsTable = """
            CREATE TABLE IF NOT EXISTS server_maps (
                server_name VARCHAR(50) PRIMARY KEY,
                map_name VARCHAR(50) NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayerStatsTable);
            plugin.getLogger().info("Database table 'player_stats' ready");

            stmt.execute(createServerMapsTable);
            plugin.getLogger().info("Database table 'server_maps' ready");
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized");
        }
        return dataSource.getConnection();
    }

    public void updateServerMap(String serverName, String mapName) {
        try (Connection conn = getConnection()) {
            String upsert = """
                INSERT INTO server_maps (server_name, map_name)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE map_name = ?, updated_at = CURRENT_TIMESTAMP
            """;
            try (var ps = conn.prepareStatement(upsert)) {
                ps.setString(1, serverName.toLowerCase());
                ps.setString(2, mapName);
                ps.setString(3, mapName);
                ps.executeUpdate();
                plugin.getLogger().info("Updated database: " + serverName + " -> " + mapName);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to update server map in database: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            plugin.getLogger().info("Closing database connection pool...");
            dataSource.close();
        }
    }
}
