package net.teppan.shazo.jdbc;

import net.teppan.shazo.ShazoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Lightweight schema migration runner for JDBC databases.
 *
 * <p>Scans a classpath location for versioned SQL scripts, compares them
 * against previously applied migrations tracked in a
 * {@code _shazo_schema_migrations} table, and applies any scripts not yet
 * recorded — in version order, within a single transaction.
 *
 * <p>This class is intentionally JDBC-generic and works with any
 * {@link DataSource}, not only embedded H2. The migration tracking table
 * uses standard SQL compatible with H2, PostgreSQL, and most modern RDBMS.
 *
 * <h2>Script naming convention</h2>
 * <p>Files must follow the pattern {@code V<version>__<description>.sql}
 * where {@code <version>} is a positive integer (leading zeros allowed):
 * <pre>
 *   V001__create_users.sql
 *   V002__add_email_index.sql
 *   V010__rename_column.sql
 * </pre>
 * Scripts are applied in ascending version order. A script applied once is
 * never re-applied, even if the file content changes.
 *
 * <h2>Multiple modules</h2>
 * <p>Each classpath location maintains its own version namespace in the
 * shared tracking table, so different modules can independently manage
 * their schemas without interfering:
 * <pre>{@code
 * // Application schema
 * SchemaManager.apply(dataSource, "db/migration/");
 *
 * // Backbone infrastructure schema (independent version sequence)
 * SchemaManager.apply(dataSource, "net/teppan/backbone/schema/");
 * }</pre>
 *
 * <h2>Idempotency</h2>
 * <p>Calling {@code apply} multiple times with the same arguments is safe:
 * already-applied scripts are skipped.
 *
 * @see net.teppan.shazo.jdbc.embedded.EmbeddedDataSource
 */
public final class SchemaManager {

    private static final Logger log = LoggerFactory.getLogger(SchemaManager.class);

    private static final String MIGRATION_TABLE = "_shazo_schema_migrations";

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS _shazo_schema_migrations (
                id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                location    VARCHAR(500) NOT NULL,
                version     INT          NOT NULL,
                script_name VARCHAR(500) NOT NULL,
                applied_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT uq_shazo_migration UNIQUE (location, version)
            )
            """;

    private static final Pattern SCRIPT_PATTERN =
        Pattern.compile("V(\\d+)__(.+)\\.sql", Pattern.CASE_INSENSITIVE);

    /**
     * Scans {@code classpathLocation} for versioned SQL scripts and applies
     * any that have not yet been recorded in the migration tracking table.
     *
     * <p>All pending scripts are applied in a single JDBC transaction.
     * If any script fails, the transaction is rolled back and no migration
     * is recorded.
     *
     * @param dataSource        the target database; never {@code null}
     * @param classpathLocation the classpath directory containing
     *                          {@code V<n>__<desc>.sql} scripts
     *                          (e.g. {@code "db/migration/"}); never {@code null}
     * @throws ShazoException   if script discovery, SQL execution, or
     *                          transaction management fails
     */
    public static void apply(DataSource dataSource, String classpathLocation)
            throws ShazoException {
        var scripts = findScripts(classpathLocation);
        if (scripts.isEmpty()) {
            log.info("No migration scripts found at: {}", classpathLocation);
            return;
        }
        Collections.sort(scripts);

        try (var conn = dataSource.getConnection()) {
            ensureTrackingTable(conn);  // DDL outside the main transaction (idempotent)

            conn.setAutoCommit(false);
            try {
                var applied = loadAppliedVersions(conn, classpathLocation);
                int count   = 0;
                for (var script : scripts) {
                    if (!applied.contains(script.version())) {
                        applyScript(conn, classpathLocation, script);
                        count++;
                    }
                }
                conn.commit();
                if (count > 0) {
                    log.info("Applied {} migration(s) from: {}", count, classpathLocation);
                } else {
                    log.debug("All migrations already applied for: {}", classpathLocation);
                }
            } catch (ShazoException e) {
                safeRollback(conn);
                throw e;
            } catch (SQLException e) {
                safeRollback(conn);
                throw new ShazoException("SQL error during migration at: " + classpathLocation, e);
            }
        } catch (ShazoException e) {
            throw e;
        } catch (SQLException e) {
            throw new ShazoException("Failed to obtain connection for schema migration", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void ensureTrackingTable(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        }
    }

    private static Set<Integer> loadAppliedVersions(Connection conn, String location)
            throws SQLException {
        var applied = new HashSet<Integer>();
        try (var ps = conn.prepareStatement(
                "SELECT version FROM " + MIGRATION_TABLE + " WHERE location = ?")) {
            ps.setString(1, location);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) applied.add(rs.getInt(1));
            }
        }
        return applied;
    }

    private static void applyScript(Connection conn, String location, MigrationScript script)
            throws ShazoException, SQLException {
        log.info("Applying V{}  {}", script.version(), script.name());

        String sql;
        try (var in = SchemaManager.class.getClassLoader()
                .getResourceAsStream(script.resourcePath())) {
            if (in == null) {
                throw new ShazoException("Script not found on classpath: " + script.resourcePath());
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ShazoException("Failed to read script: " + script.resourcePath(), e);
        }

        for (var statement : splitStatements(sql)) {
            try (var stmt = conn.createStatement()) {
                stmt.execute(statement);
            }
        }

        try (var ps = conn.prepareStatement(
                "INSERT INTO " + MIGRATION_TABLE
                + " (location, version, script_name) VALUES (?, ?, ?)")) {
            ps.setString(1, location);
            ps.setInt(2, script.version());
            ps.setString(3, script.name());
            ps.executeUpdate();
        }
    }

    private static List<String> splitStatements(String sql) {
        var stripped = sql
            .replaceAll("(?m)--[^\n]*", "")      // remove -- line comments
            .replaceAll("(?s)/\\*.*?\\*/", " ");  // remove /* block */ comments
        return Arrays.stream(stripped.split(";"))
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .toList();
    }

    private static void safeRollback(Connection conn) {
        try { conn.rollback(); } catch (SQLException ignored) {}
    }

    // ── Classpath scanning ────────────────────────────────────────────────────

    private static List<MigrationScript> findScripts(String location) throws ShazoException {
        var normalized = location.endsWith("/") ? location : location + "/";
        var scripts    = new ArrayList<MigrationScript>();
        try {
            var urls = SchemaManager.class.getClassLoader().getResources(normalized);
            while (urls.hasMoreElements()) {
                var url = urls.nextElement();
                if ("file".equals(url.getProtocol())) {
                    collectFromDirectory(url, normalized, scripts);
                } else {
                    collectFromJar(url, normalized, scripts);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new ShazoException("Failed to scan classpath location: " + location, e);
        }
        return scripts;
    }

    private static void collectFromDirectory(URL url, String location,
                                             List<MigrationScript> into)
            throws URISyntaxException, IOException {
        var dir = Path.of(url.toURI());
        if (!Files.isDirectory(dir)) return;
        try (var entries = Files.list(dir)) {
            entries.filter(p -> p.getFileName().toString().endsWith(".sql"))
                   .forEach(p -> {
                       var fn = p.getFileName().toString();
                       var m  = SCRIPT_PATTERN.matcher(fn);
                       if (m.matches()) {
                           into.add(new MigrationScript(
                               Integer.parseInt(m.group(1)), m.group(2), location + fn));
                       }
                   });
        }
    }

    private static void collectFromJar(URL url, String location,
                                       List<MigrationScript> into) throws IOException {
        var conn = (JarURLConnection) url.openConnection();
        try (var jar = new JarFile(conn.getJarFile().getName())) {
            jar.entries().asIterator().forEachRemaining(entry -> {
                var name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(location) && name.endsWith(".sql")) {
                    var fn = name.substring(location.length());
                    var m  = SCRIPT_PATTERN.matcher(fn);
                    if (m.matches()) {
                        into.add(new MigrationScript(
                            Integer.parseInt(m.group(1)), m.group(2), name));
                    }
                }
            });
        }
    }

    // ── Value type ────────────────────────────────────────────────────────────

    private record MigrationScript(int version, String name, String resourcePath)
            implements Comparable<MigrationScript> {
        @Override
        public int compareTo(MigrationScript other) {
            return Integer.compare(this.version, other.version);
        }
    }

    private SchemaManager() {}
}
