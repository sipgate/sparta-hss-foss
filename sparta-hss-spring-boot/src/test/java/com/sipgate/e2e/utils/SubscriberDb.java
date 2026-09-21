package com.sipgate.e2e.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class SubscriberDb {
    /// The HSS and these tests write to the same SQLite database concurrently, so
    /// WAL plus a busy timeout are mandatory (the HSS is configured to match, see
    /// docker-compose.e2e.yml).
    private static final String URL = System.getProperty(
        "e2e.db.url", "jdbc:sqlite:/var/lib/sparta-hss/sparta-hss.db?journal_mode=wal&busy_timeout=10000");
    private static final String USER = System.getProperty("e2e.db.user", "");
    private static final String PASS = System.getProperty("e2e.db.pass", "");

    private SubscriberDb() {}

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(PreparedStatement ps) throws SQLException;
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    private static void execUpdate(final String sql, final SqlConsumer binder, final String ctx) {
        try (final var conn = connection();
             final var ps = conn.prepareStatement(sql)) {
            binder.accept(ps);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("failed to execute update for " + ctx, e);
        }
    }

    public static void setSimAuthKeys(final String imsi, final byte[] ki, final byte[] op, final long sqn) {
        try (final var conn = connection();
             final var ps = conn.prepareStatement(
                 "UPDATE sim SET secretKey = ?, op = ?, sqn = ? WHERE id = (SELECT simId FROM imsi WHERE imsi = ?)")) {
            ps.setBytes(1, ki);
            ps.setBytes(2, op);
            ps.setLong(3, sqn);
            ps.setString(4, imsi);
            final var updated = ps.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException("expected to update 1 sim row for imsi " + imsi + " but updated " + updated);
            }
        } catch (final SQLException e) {
            throw new RuntimeException("failed to set sim auth keys for imsi " + imsi, e);
        }
    }

    /** Upserts the LTE location for the given IMSI (PK is imsiId; lastUpdate defaults). */
    public static void setLteLocation(final String imsi, final String mmeHostname, final String mmeRealm, final String visitedPlmnId) {
        try (final var conn = connection()) {
            final int imsiId = imsiId(conn, imsi);
            try (final var ps = conn.prepareStatement("DELETE FROM location_lte WHERE imsiId = ?")) {
                ps.setInt(1, imsiId);
                ps.executeUpdate();
            }
            try (final var ps = conn.prepareStatement(
                    "INSERT INTO location_lte (imsiId, mmeHostname, mmeRealm, visitedPlmnId) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, imsiId);
                ps.setString(2, mmeHostname);
                ps.setString(3, mmeRealm);
                ps.setString(4, visitedPlmnId);
                ps.executeUpdate();
            }
        } catch (final SQLException e) {
            throw new RuntimeException("setLteLocation failed for " + imsi, e);
        }
    }

    /** Upserts the S-CSCF registration for the given IMSI (PK is imsiId; lastUpdate defaults). */
    public static void setScscf(final String imsi, final String scscf, final String diameterHost, final String diameterRealm) {
        try (final var conn = connection()) {
            final int imsiId = imsiId(conn, imsi);
            try (final var ps = conn.prepareStatement("DELETE FROM imsi_scscf WHERE imsiId = ?")) {
                ps.setInt(1, imsiId);
                ps.executeUpdate();
            }
            try (final var ps = conn.prepareStatement(
                    "INSERT INTO imsi_scscf (imsiId, scscf, diameter_host, diameter_realm) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, imsiId);
                ps.setString(2, scscf);
                ps.setString(3, diameterHost);
                ps.setString(4, diameterRealm);
                ps.executeUpdate();
            }
        } catch (final SQLException e) {
            throw new RuntimeException("setScscf failed for " + imsi, e);
        }
    }

    public static void clearScscf(final String imsi) {
        execUpdate("DELETE FROM imsi_scscf WHERE imsiId = (SELECT id FROM imsi WHERE imsi = ?)", ps -> ps.setString(1, imsi), imsi);
    }

    public static void clearIpSmGw(final String imsi) {
        execUpdate("DELETE FROM location_ip_sm_gw WHERE imsiId = (SELECT id FROM imsi WHERE imsi = ?)", ps -> ps.setString(1, imsi), imsi);
    }

    /** S-CSCF name currently stored for the IMSI, or null if none. */
    public static String scscf(final String imsi) {
        try (final var conn = connection();
             final var ps = conn.prepareStatement(
                 "SELECT scscf FROM imsi_scscf WHERE imsiId = (SELECT id FROM imsi WHERE imsi = ?)")) {
            ps.setString(1, imsi);
            try (final var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (final SQLException e) {
            throw new RuntimeException("scscf lookup failed for " + imsi, e);
        }
    }

    public static void clearImsiProfile(final String imsi) {
        execUpdate("DELETE FROM imsi_profile WHERE imsiId = (SELECT id FROM imsi WHERE imsi = ?)", ps -> ps.setString(1, imsi), imsi);
    }

    /** The full imsi -> profile-name map currently stored in imsi_profile. */
    public static Map<String, String> allImsiProfiles() {
        final var profiles = new HashMap<String, String>();
        try (final var conn = connection();
             final var ps = conn.prepareStatement("SELECT i.imsi, p.name FROM imsi_profile p JOIN imsi i ON p.imsiId = i.id");
             final var rs = ps.executeQuery()) {
            while (rs.next()) {
                profiles.put(rs.getString(1), rs.getString(2));
            }
            return profiles;
        } catch (final SQLException e) {
            throw new RuntimeException("allImsiProfiles failed", e);
        }
    }

    private static int imsiId(final Connection conn, final String imsi) throws SQLException {
        try (final var ps = conn.prepareStatement("SELECT id FROM imsi WHERE imsi = ?")) {
            ps.setString(1, imsi);
            try (final var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("no imsi row for " + imsi);
                }
                return rs.getInt(1);
            }
        }
    }

    /** MME hostname currently stored for the IMSI, or null if none. */
    public static String lteLocationMmeHostname(final String imsi) {
        try (final var conn = connection();
             final var ps = conn.prepareStatement(
                 "SELECT mmeHostname FROM location_lte WHERE imsiId = (SELECT id FROM imsi WHERE imsi = ?)")) {
            ps.setString(1, imsi);
            try (final var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (final SQLException e) {
            throw new RuntimeException("lteLocationMmeHostname failed for " + imsi, e);
        }
    }

    /** IP-SM-GW name currently stored for the IMSI, or null if none. */
    public static String ipSmGwName(final String imsi) {
        try (final var conn = connection();
             final var ps = conn.prepareStatement(
                 "SELECT ipSmGwName FROM location_ip_sm_gw WHERE imsiId = (SELECT id FROM imsi WHERE imsi = ?)")) {
            ps.setString(1, imsi);
            try (final var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (final SQLException e) {
            throw new RuntimeException("ipSmGwName failed for " + imsi, e);
        }
    }
}
