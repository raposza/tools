// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.pqs;

import com.raposza.runtime.db.DatabaseException;
import com.raposza.runtime.db.PostgresCoordinates;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What is actually in a scribe database, read rather than assumed.
 *
 * <h2>Why nothing here names a scribe table</h2>
 *
 * The ingest measurement is "a contract created, a row read back out of a
 * scribe table", and the obvious way to write it is to `SELECT` from a table
 * whose name someone remembers. That is the same move as diagnosing
 * `maxTokenLife` from the field's name: an inference where a read was
 * available. Scribe's schema is versioned - 021, 034, 035, 041 have all been
 * seen - and a table that exists in one revision is not promised in the next,
 * so a hard-coded name turns a schema change into a failed assertion that reads
 * like a broken pipeline.
 *
 * So this asks the catalogue. `information_schema` is SQL-standard and is not
 * scribe's to change. What comes back is evidence: these tables exist, these
 * hold rows. An assertion built on it says "something landed" without claiming
 * to know what scribe calls it.
 *
 * <h2>count(*), not an estimate</h2>
 *
 * `reltuples` is a planner statistic and is -1 until the table is analysed,
 * which on a table that has just received its first row it has not been. The
 * counts here are small by construction - a sandbox that has run for two
 * minutes - so the exact count is affordable and the estimate would be wrong in
 * exactly the case that matters.
 *
 * <h2>Read-only, and no schema of its own</h2>
 *
 * Nothing here creates, migrates or drops. Scribe owns its schema; this module
 * looks at it. That is also why it takes {@link PostgresCoordinates} rather
 * than a {@link PqsSpec}: the reader does not care which process wrote the
 * database, which is what makes it usable against a stack that is still up.
 *
 * <h2>Metadata is not data</h2>
 *
 * The first run of this against a real scribe returned twelve tables, and five
 * of them held rows before a single transaction had been ingested:
 * `flyway_schema_history` (42), `__exercise_tpe` (18), `__packages` (1),
 * `__pruning_metadata` (1) and `__watermark` (1). A schema-wide "is anything
 * non-zero" question is therefore ALWAYS true against a scribe that started
 * successfully, and it answers "did scribe migrate", not "did anything land".
 * {@link #cntRowsIn} exists because of that.
 *
 * Author Claude/bentzn
 */
public final class ScribeTables {

    /**
     * Schemas PostgreSQL owns. Excluded from {@link #schemas} because they are
     * present in every database and are never scribe's.
     */
    private static final List<String> LST_SCHEMA_SYSTEM =
            List.of("information_schema", "pg_catalog", "pg_toast");

    /**
     * Identifiers come out of `information_schema`, so they are real names and
     * not input - but they are interpolated into SQL, because an identifier
     * cannot be a bind parameter. The check is here so that the one place doing
     * the interpolation is also the place that refuses anything surprising.
     */
    private static final Pattern PAT_IDENT = Pattern.compile("[A-Za-z0-9_$]{1,63}");

    private static final String SQL_TABLES =
            "SELECT table_name FROM information_schema.tables"
                    + " WHERE table_schema = ? AND table_type = 'BASE TABLE'"
                    + " ORDER BY table_name";

    private static final String SQL_SCHEMAS =
            "SELECT schema_name FROM information_schema.schemata ORDER BY schema_name";


    private ScribeTables() {
    }


    /**
     * @param strTable a table or schema name
     * @return whether it is safe to interpolate
     */
    public static boolean isPlainIdentifier(String strTable) {
        return strTable != null && PAT_IDENT.matcher(strTable).matches();
    }


    /**
     * @param pg where the database is
     * @return every non-system schema, sorted; empty when the database has none
     * @throws DatabaseException when the database cannot be reached
     */
    public static List<String> schemas(PostgresCoordinates pg) {
        require(pg);
        List<String> lstSchema = new ArrayList<>();
        try (Connection conn = connect(pg);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(SQL_SCHEMAS)) {
            while (rs.next()) {
                String strSchema = rs.getString(1);
                if (!LST_SCHEMA_SYSTEM.contains(strSchema))
                    lstSchema.add(strSchema);
            }
        }
        catch (SQLException ex) {
            throw new DatabaseException("could not list schemas in " + pg, ex);
        }
        return Collections.unmodifiableList(lstSchema);
    }


    /**
     * @param pg where the database is
     * @param strSchema the schema to look in, e.g. scribe's `--target-schema`
     * @return one entry per base table, sorted by name, each with an exact row
     *         count; empty when the schema holds no tables or does not exist
     * @throws DatabaseException when the database cannot be reached, or a table
     *         name is not a plain identifier
     */
    public static List<TableCount> read(PostgresCoordinates pg, String strSchema) {
        require(pg);
        if (!isPlainIdentifier(strSchema))
            throw new DatabaseException("not a plain schema identifier: " + strSchema);

        List<String> lstTable = new ArrayList<>();
        List<TableCount> lstCount = new ArrayList<>();
        try (Connection conn = connect(pg)) {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_TABLES)) {
                stmt.setString(1, strSchema);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next())
                        lstTable.add(rs.getString(1));
                }
            }
            for (String strTable : lstTable) {
                if (!isPlainIdentifier(strTable)) {
                    throw new DatabaseException("refusing to count a table whose name is not a "
                            + "plain identifier: " + strSchema + "." + strTable);
                }
                String strSql = "SELECT count(*) FROM \"" + strSchema + "\".\"" + strTable + "\"";
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery(strSql)) {
                    rs.next();
                    lstCount.add(new TableCount(strSchema, strTable, rs.getLong(1)));
                }
            }
        }
        catch (SQLException ex) {
            throw new DatabaseException("could not read schema " + strSchema + " in " + pg, ex);
        }
        return Collections.unmodifiableList(lstCount);
    }


    /**
     * @param lstCount what {@link #read} returned
     * @return the entries holding at least one row
     */
    public static List<TableCount> nonEmpty(List<TableCount> lstCount) {
        List<TableCount> lstOut = new ArrayList<>();
        for (TableCount count : lstCount) {
            if (count.cntRows() > 0L)
                lstOut.add(count);
        }
        return Collections.unmodifiableList(lstOut);
    }


    /**
     * Rows in NAMED tables only.
     *
     * A total over the whole schema counts scribe's own bookkeeping, which is
     * populated by a successful migration and connection and says nothing about
     * ingest. Naming the tables is a cost - a renamed table makes this return
     * zero - but the failure that produces is a RED with the full inventory
     * beside it, which is diagnosable. The alternative was a green that
     * measured Flyway.
     *
     * @param lstCount what {@link #read} returned
     * @param collTable the table names to count
     * @return their combined row count; tables not present contribute nothing
     */
    public static long cntRowsIn(List<TableCount> lstCount, Collection<String> collTable) {
        if (lstCount == null || collTable == null)
            return 0L;
        long cntTotal = 0L;
        for (TableCount count : lstCount) {
            if (collTable.contains(count.strTable()))
                cntTotal += count.cntRows();
        }
        return cntTotal;
    }


    /**
     * The first few rows of one table, as text, for a failure message.
     *
     * Column types are not interpreted - everything goes through
     * `getString` - because this is for a human reading a diagnostic, and a
     * reader that has to know scribe's column types would be a reader that
     * breaks when scribe changes one.
     *
     * @param pg where the database is
     * @param strSchema the schema
     * @param strTable the table
     * @param cntMax how many rows at most
     * @return one line per row, `column=value` joined by commas; empty when the
     *         table is empty or absent
     */
    public static List<String> dump(PostgresCoordinates pg, String strSchema, String strTable,
            int cntMax) {
        require(pg);
        if (!isPlainIdentifier(strSchema) || !isPlainIdentifier(strTable))
            throw new DatabaseException("not plain identifiers: " + strSchema + "." + strTable);
        if (cntMax < 1)
            throw new IllegalArgumentException("cntMax must be positive: " + cntMax);

        List<String> lstLine = new ArrayList<>();
        String strSql = "SELECT * FROM \"" + strSchema + "\".\"" + strTable + "\" LIMIT " + cntMax;
        try (Connection conn = connect(pg);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strSql)) {
            int cntColumn = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int nCol = 1; nCol <= cntColumn; nCol++) {
                    if (nCol > 1)
                        sb.append(", ");
                    sb.append(rs.getMetaData().getColumnLabel(nCol)).append('=')
                            .append(rs.getString(nCol));
                }
                lstLine.add(sb.toString());
            }
        }
        catch (SQLException ex) {
            return List.of("(could not read " + strSchema + "." + strTable + ": "
                    + ex.getMessage() + ")");
        }
        return Collections.unmodifiableList(lstLine);
    }


    /**
     * @param lstCount what {@link #read} returned
     * @return every row in the schema
     */
    public static long cntRowsTotal(List<TableCount> lstCount) {
        long cntTotal = 0L;
        for (TableCount count : lstCount)
            cntTotal += count.cntRows();
        return cntTotal;
    }


    /**
     * One line per table, for a failure message that says what WAS there rather
     * than only what was expected.
     *
     * @param lstCount what {@link #read} returned
     * @return a multi-line description, never null
     */
    public static String describe(List<TableCount> lstCount) {
        if (lstCount == null || lstCount.isEmpty())
            return "(no tables)";
        StringBuilder sb = new StringBuilder();
        for (TableCount count : lstCount) {
            if (sb.length() > 0)
                sb.append('\n');
            sb.append(count.strSchema()).append('.').append(count.strTable())
                    .append(" = ").append(count.cntRows());
        }
        return sb.toString();
    }


    private static Connection connect(PostgresCoordinates pg) throws SQLException {
        return DriverManager.getConnection(pg.jdbcUrl(), pg.strUser(), pg.strPassword());
    }


    private static void require(PostgresCoordinates pg) {
        if (pg == null)
            throw new IllegalArgumentException("coordinates are required");
    }


    /**
     * @param strSchema the schema the table lives in
     * @param strTable the table
     * @param cntRows an exact count
     */
    public record TableCount(String strSchema, String strTable, long cntRows) {

        public TableCount {
            if (strSchema == null || strSchema.isBlank())
                throw new IllegalArgumentException("strSchema is required");
            if (strTable == null || strTable.isBlank())
                throw new IllegalArgumentException("strTable is required");
            if (cntRows < 0L)
                throw new IllegalArgumentException("a count cannot be negative: " + cntRows);
        }


        @Override
        public String toString() {
            return strSchema + "." + strTable + " = " + cntRows;
        }
    }
}
