package org.macl.ctc.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.macl.ctc.Main;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

public class StatsManager {
    public record PlayerStats(
            int kills,
            int deaths,
            int wins,
            int captures,
            int coreCracks,
            int gamesPlayed,
            int damageDealt,
            int damageTaken
    ) {}

    private final ConcurrentMap<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PlayerStats> sessionStart = new ConcurrentHashMap<>();
    private final DatabaseManager db;
    private final Main plugin;

    public StatsManager(DatabaseManager db, Main plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    // ── Core recorders ───────────────────────────────────────────────────────
    public void recordKill(UUID id)      { modify(id, old -> new PlayerStats(old.kills()+1, old.deaths(), old.wins(), old.captures(), old.coreCracks(), old.gamesPlayed(), old.damageDealt(), old.damageTaken())); }
    public void recordDeath(UUID id)     { modify(id, old -> new PlayerStats(old.kills(), old.deaths()+1, old.wins(), old.captures(), old.coreCracks(), old.gamesPlayed(), old.damageDealt(), old.damageTaken())); }
    public void recordWin(UUID id)       { modify(id, old -> new PlayerStats(old.kills(), old.deaths(), old.wins()+1, old.captures(), old.coreCracks(), old.gamesPlayed(), old.damageDealt(), old.damageTaken())); }
    public void recordCapture(UUID id)   { modify(id, old -> new PlayerStats(old.kills(), old.deaths(), old.wins(), old.captures()+1, old.coreCracks(), old.gamesPlayed(), old.damageDealt(), old.damageTaken())); }
    public void recordCoreCrack(UUID id) { modify(id, old -> new PlayerStats(old.kills(), old.deaths(), old.wins(), old.captures(), old.coreCracks()+1, old.gamesPlayed(), old.damageDealt(), old.damageTaken())); }
    public void recordGamePlayed(UUID id){ modify(id, old -> new PlayerStats(old.kills(), old.deaths(), old.wins(), old.captures(), old.coreCracks(), old.gamesPlayed()+1, old.damageDealt(), old.damageTaken())); }
    public void recordDamage(UUID id, int amount)     { modify(id, old -> new PlayerStats(old.kills(), old.deaths(), old.wins(), old.captures(), old.coreCracks(), old.gamesPlayed(), old.damageDealt()+amount, old.damageTaken())); }
    public void recordDamageTaken(UUID id, int amount){ modify(id, old -> new PlayerStats(old.kills(), old.deaths(), old.wins(), old.captures(), old.coreCracks(), old.gamesPlayed(), old.damageDealt(), old.damageTaken()+amount)); }

    // ── Convenience overloads ────────────────────────────────────────────────
    public void recordKill(Player p)      { recordKill(p.getUniqueId()); }
    public void recordDeath(Player p)     { recordDeath(p.getUniqueId()); }
    public void recordWin(Player p)       { recordWin(p.getUniqueId()); }
    public void recordCapture(Player p)   { recordCapture(p.getUniqueId()); }
    public void recordCoreCrack(Player p) { recordCoreCrack(p.getUniqueId()); }
    public void recordGamePlayed(Player p){ recordGamePlayed(p.getUniqueId()); }

    /** Reset this player's stats entirely */
    public void resetPlayer(UUID id) {
        // Reset in-memory cache
        cache.put(id, new PlayerStats(0,0,0,0,0,0,0,0));
        // Reset session snapshot so delta calculation works
        sessionStart.put(id, new PlayerStats(0,0,0,0,0,0,0,0));

        // Delete from DB async
        CompletableFuture.runAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM player_stats WHERE player_uuid = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to reset stats for " + id);
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public void resetPlayer(Player p) { resetPlayer(p.getUniqueId()); }

    private void modify(UUID id, UnaryOperator<PlayerStats> op) {
        cache.compute(id, (__, old) -> {
            PlayerStats base = old==null
                    ? new PlayerStats(0,0,0,0,0,0,0,0)
                    : old;
            return op.apply(base);
        });
        // NO DB write here - only write on player quit (delta-based)
    }

    public PlayerStats get(UUID id) {
        return cache.computeIfAbsent(id, uuid -> {
            // If not in cache, load from DB (for offline player queries)
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM player_stats WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new PlayerStats(
                        rs.getInt("kills"),
                        rs.getInt("deaths"),
                        rs.getInt("wins"),
                        rs.getInt("captures"),
                        rs.getInt("core_cracks"),
                        rs.getInt("games_played"),
                        rs.getInt("damage_dealt"),
                        rs.getInt("damage_taken")
                    );
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load stats for " + uuid);
            }
            return new PlayerStats(0,0,0,0,0,0,0,0);
        });
    }

    /** Get all stats entries for iteration */
    public java.util.Set<java.util.Map.Entry<UUID, PlayerStats>> entrySet() {
        return cache.entrySet();
    }

    // ── Database Operations ─────────────────────────────────────────────────────

    /** Load player stats from DB into cache (called on player join) */
    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM player_stats WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                PlayerStats stats;
                if (rs.next()) {
                    // Load from DB
                    stats = new PlayerStats(
                        rs.getInt("kills"),
                        rs.getInt("deaths"),
                        rs.getInt("wins"),
                        rs.getInt("captures"),
                        rs.getInt("core_cracks"),
                        rs.getInt("games_played"),
                        rs.getInt("damage_dealt"),
                        rs.getInt("damage_taken")
                    );
                } else {
                    // New player - defaults
                    stats = new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0);
                }

                // Store in cache AND snapshot for delta calculation
                cache.put(uuid, stats);
                sessionStart.put(uuid, stats);

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load stats for " + uuid + ": " + e.getMessage());
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    /** Save player stats to DB using delta-based updates (called on player quit) */
    public CompletableFuture<Void> savePlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            PlayerStats current = cache.get(uuid);
            PlayerStats start = sessionStart.get(uuid);

            if (current == null || start == null) return;

            // Calculate deltas (change during this session)
            int deltaKills = current.kills() - start.kills();
            int deltaDeaths = current.deaths() - start.deaths();
            int deltaWins = current.wins() - start.wins();
            int deltaCaptures = current.captures() - start.captures();
            int deltaCoreCracks = current.coreCracks() - start.coreCracks();
            int deltaGamesPlayed = current.gamesPlayed() - start.gamesPlayed();
            int deltaDamageDealt = current.damageDealt() - start.damageDealt();
            int deltaDamageTaken = current.damageTaken() - start.damageTaken();

            try (Connection conn = db.getConnection()) {
                // First, ensure row exists (INSERT if not exists)
                String insertSql = """
                    INSERT INTO player_stats (player_uuid, kills, deaths, wins, captures,
                        core_cracks, games_played, damage_dealt, damage_taken)
                    VALUES (?, 0, 0, 0, 0, 0, 0, 0, 0)
                    ON DUPLICATE KEY UPDATE player_uuid = player_uuid
                """;
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }

                // Then, increment by deltas
                String updateSql = """
                    UPDATE player_stats SET
                        kills = kills + ?,
                        deaths = deaths + ?,
                        wins = wins + ?,
                        captures = captures + ?,
                        core_cracks = core_cracks + ?,
                        games_played = games_played + ?,
                        damage_dealt = damage_dealt + ?,
                        damage_taken = damage_taken + ?
                    WHERE player_uuid = ?
                """;
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, deltaKills);
                    ps.setInt(2, deltaDeaths);
                    ps.setInt(3, deltaWins);
                    ps.setInt(4, deltaCaptures);
                    ps.setInt(5, deltaCoreCracks);
                    ps.setInt(6, deltaGamesPlayed);
                    ps.setInt(7, deltaDamageDealt);
                    ps.setInt(8, deltaDamageTaken);
                    ps.setString(9, uuid.toString());
                    ps.executeUpdate();
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save stats for " + uuid + ": " + e.getMessage());
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    /** Remove player from cache after saving */
    public void removeFromCache(UUID uuid) {
        cache.remove(uuid);
        sessionStart.remove(uuid);
    }

    /** Get all cached player UUIDs for shutdown flush */
    public Set<UUID> getAllCachedPlayers() {
        return cache.keySet();
    }
}
