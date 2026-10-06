// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.sandbox.app.ReadyReport;
import com.raposza.sandbox.app.SandboxService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What is on the ledger, read out of Canton's own schema over JDBC.
 *
 * <h2>Why this is a class and not a method of the pane</h2>
 *
 * It WAS a method of {@link ParticipantDataPane}, which was correct while the
 * only reader was a tree. An unattended harness is a second reader, and one
 * that asserted on its own copy of these queries would be green over a pane
 * that renders something else - the failure this extraction exists to
 * avoid. One copy, two callers: the tree shows what the assertion measured.
 *
 * <h2>Nothing about the schema is assumed. All of it is discovered</h2>
 *
 * The prototype's queries are 2.10's, and the corpus has never measured 3.x's
 * internal layout. So the SCHEMA is found by asking which one owns
 * `participant_events_create`, every COLUMN is chosen from what
 * `information_schema` reports, and each missing piece degrades to a blank
 * rather than to an exception.
 *
 * Templates come out of `string_interning`, which stores them with a
 * one-character tag - `t|` for a template. Parties do NOT: that table holds
 * what has been SEEN in an event, so a freshly started ledger interns the one
 * party the bootstrap touched and nothing else. Parties are read from the
 * tables whose name carries `part`, with the identifying column chosen the
 * same way every other column here is.
 *
 * Author Claude/bentzn
 */
public final class LedgerProbe {

    private static final int CNT_CONTRACT_MAX = 2000;

    private static final int CNT_ENTITY_MAX = 2000;

    private static final int CNT_EXERCISE_MAX = 500;

    /** What a party is called, in order of preference. */
    private static final String[] ARR_COLUMN_PARTY =
            { "party_id", "party", "party_identifier" };

    /** What a user is called. */
    private static final String[] ARR_COLUMN_USER = { "user_id", "identifier", "internal_id" };

    /**
     * What identifies a contract, in order of preference - and it has to be
     * the SAME column on the create and the archive table.
     *
     * MEASURED on 3.5.11: `lapi_events_activate_contract` has no
     * `contract_id`, only `internal_contract_id`, while
     * `lapi_events_deactivate_contract` has BOTH. Taking each table's own first
     * choice would compare an integer against a bytea and match nothing, so
     * every create would read as active: 12 active and 5 archived on a ledger
     * holding 12 contracts of which 5 are gone. The column is therefore chosen
     * from what the two tables have IN COMMON.
     */
    private static final String[] ARR_COLUMN_CONTRACT = { "contract_id", "internal_contract_id" };

    /**
     * The create-event table, by every name it has been MEASURED under.
     *
     * 2.x calls it `participant_events_create` in `ledger_api`. 3.x calls it
     * `lapi_events_activate_contract` in `public` - measured on 3.4.4, after
     * a reader that knew only the 2.x name reported an empty ledger against a
     * participant the pet shop had just been written to.
     *
     * NAMES FIRST, then patterns. A name that is banked is exact and cannot
     * match the wrong table; the patterns are the reach for a generation
     * nobody here has started yet, and `activate` and `create` are different
     * enough words that one pattern would not have covered both.
     */
    private static final String[] ARR_TABLE_CREATE =
            { "participant_events_create", "lapi_events_activate_contract" };

    private static final String[] ARR_LIKE_CREATE = { "%events_create%", "%events_activate%" };

    /** The archive-event table, same rule. 3.x deactivates where 2.x consumes. */
    private static final String[] ARR_TABLE_ARCHIVE =
            { "participant_events_consuming_exercise", "lapi_events_deactivate_contract" };

    private static final String[] ARR_LIKE_ARCHIVE =
            { "%events_consuming%", "%events_deactivate%" };

    /** Where parties and templates are interned. Renamed on 3.x like the rest. */
    private static final String[] ARR_TABLE_INTERN = { "string_interning", "lapi_string_interning" };

    private static final String[] ARR_LIKE_INTERN = { "%string_interning%" };


    private LedgerProbe() {
    }


    /**
     * @param service the running service, or null
     * @return where the participant's database is, or null when the report
     *         carries no JDBC url
     */
    public static PostgresCoordinates coordinatesParticipant(SandboxService service) {
        if (service == null || !service.isRunning())
            return null;

        ReadyReport report = service.report();
        String strUrl = report == null ? null : report.value(ReadyReport.KEY_JDBC_PARTICIPANT);
        if (strUrl == null)
            return null;

        int nSlash = strUrl.lastIndexOf('/');
        if (nSlash < 0 || nSlash + 1 >= strUrl.length())
            return null;
        return service.coordinatesFor(strUrl.substring(nSlash + 1));
    }


    /**
     * @param coord where the participant's database is
     * @return everything one refresh reads; never null
     * @throws SQLException when the database cannot be read at all
     */
    public static Ledger read(PostgresCoordinates coord) throws SQLException {
        Ledger ledger = new Ledger();
        try (Connection conn = DriverManager.getConnection(coord.jdbcUrl(), coord.strUser(),
                coord.strPassword())) {

            String[] arrCreate = tableRichest(conn, ARR_TABLE_CREATE, ARR_LIKE_CREATE);
            if (arrCreate == null) {
                // EVERY TABLE THERE IS, when the create table cannot be found.
                // A reader told only that a name is absent has to run the whole
                // thing again to learn what IS there, and this read is the one
                // that costs a whole run.
                ledger.strSchemaNote = "no create-event table under any known name;"
                        + " what this database holds: " + String.join(", ", lstTableAll(conn));
                return ledger;
            }
            String strSchema = arrCreate[0];
            String strTableCreate = arrCreate[1];
            ledger.strSchemaNote = strSchema + "." + strTableCreate;

            // THE ASSERTION IS A COUNT, and a count does not depend on knowing
            // what the identity column is called. The list below is for the
            // tree and degrades to nothing when a column is named something
            // this has not measured; the number does not.
            ledger.cntCreateRow = cntRowsIn(conn, strSchema, strTableCreate);

            readEntities(conn, strSchema, "part", ARR_COLUMN_PARTY, ledger.lstParty,
                    ledger.lstSourceParty);
            readEntities(conn, strSchema, "user", ARR_COLUMN_USER, ledger.lstUser,
                    ledger.lstSourceUser);
            // IN THE SCHEMA THE CREATES CAME FROM. Resolving each table on its
            // own let the interning of one schema label the contracts of
            // another.
            String[] arrIntern = tableIn(conn, strSchema, ARR_TABLE_INTERN, ARR_LIKE_INTERN);
            Map<String, String> mapTemplate = arrIntern == null
                    ? new LinkedHashMap<>()
                    : internedById(conn, arrIntern[0], arrIntern[1], "t|");
            ledger.lstTemplate.addAll(new LinkedHashSet<>(mapTemplate.values()));
            Set<String> setArchived = new LinkedHashSet<>();

            String[] arrArchive = tableIn(conn, strSchema, ARR_TABLE_ARCHIVE, ARR_LIKE_ARCHIVE);

            // ONE COLUMN FOR BOTH TABLES, chosen from what they share.
            Set<String> setColCreate = columnsOf(conn, strSchema, strTableCreate);
            Set<String> setColArchive = arrArchive == null ? new LinkedHashSet<>()
                    : columnsOf(conn, arrArchive[0], arrArchive[1]);
            String strColId = null;
            for (String strCandidate : ARR_COLUMN_CONTRACT) {
                boolean flagShared = setColCreate.contains(strCandidate)
                        && (arrArchive == null || setColArchive.contains(strCandidate));
                if (flagShared) {
                    strColId = strCandidate;
                    break;
                }
            }
            if (strColId == null) {
                ledger.strSchemaNote += " (no shared contract column; the create table has "
                        + setColCreate + ")";
            }

            // WHERE THE TEMPLATE IS. 2.x carries it on the create table; 3.x
            // does not, and where it keeps it has not been measured - D-415.
            // So it is FOUND: a column of the create table named for a
            // template, else a table that carries the same contract column and
            // one named for a template, which every row is looked up in.
            String[] arrTemplate = strColId == null ? null
                    : templateSource(conn, strSchema, strTableCreate, setColCreate, strColId);
            ledger.strTemplateSource = arrTemplate == null
                    ? "not found; the create table has " + setColCreate
                    : arrTemplate[0] + "." + arrTemplate[1] + "." + arrTemplate[2];

            if (arrArchive != null) {
                readContracts(conn, arrArchive[0], arrArchive[1], strColId, arrTemplate,
                        mapTemplate, ledger.lstArchived);
                for (Contract contract : ledger.lstArchived) {
                    setArchived.add(contract.strId());
                }
            }

            List<Contract> lstCreated = new ArrayList<>();
            readContracts(conn, strSchema, strTableCreate, strColId, arrTemplate, mapTemplate,
                    lstCreated);
            for (Contract contract : lstCreated) {
                if (!setArchived.contains(contract.strId()))
                    ledger.lstActive.add(contract);
            }

            // THE CHOICES THAT ARCHIVE NOTHING - his instruction, 2026-10-04,
            // that choices are logged. Every other table of the schema with a
            // column named for a choice is read; on 2.x that is
            // `participant_events_non_consuming_exercise`, on 3.x it has not
            // been measured, so it is found the same way the template was.
            Map<String, String> mapIntern = arrIntern == null ? new LinkedHashMap<>()
                    : internedAll(conn, arrIntern[0], arrIntern[1]);
            readExercises(conn, strSchema, strTableCreate,
                    arrArchive == null ? null : arrArchive[1], strColId, arrTemplate,
                    mapTemplate, mapIntern, ledger.lstExercise, ledger.lstSourceExercise);
            // AND THE CONSUMING ONES BY NAME, where the archive table says
            // which choice archived the contract.
            if (!mapIntern.isEmpty()) {
                for (int idx = 0; idx < ledger.lstArchived.size(); idx++) {
                    Contract contract = ledger.lstArchived.get(idx);
                    String strHit = mapIntern.get(contract.strChoice());
                    if (strHit != null)
                        ledger.lstArchived.set(idx, contract.withChoice(strHit));
                }
            }
        }
        return ledger;
    }


    /**
     * Every row of every table in the schema, other than the two event tables
     * already read, that carries a column named for a choice and one that
     * orders it.
     *
     * @param conn an open connection
     * @param strSchema the schema the creates came out of
     * @param strTableCreate the create table, which is not read again
     * @param strTableArchive the archive table, likewise, or null
     * @param strColId the contract column, for the template lookup
     * @param arrTemplate where the template is, or null
     * @param mapTemplate interned id to template
     * @param mapIntern interned id to any interned string, for a choice
     *        stored as an id
     * @param lstOut where the exercises land, newest first per table
     * @param lstSource where `schema.table.column` is noted for each table read
     */
    private static void readExercises(Connection conn, String strSchema,
            String strTableCreate, String strTableArchive, String strColId,
            String[] arrTemplate, Map<String, String> mapTemplate,
            Map<String, String> mapIntern, List<Exercise> lstOut, List<String> lstSource)
            throws SQLException {
        List<String> lstTable = new ArrayList<>();
        String strQuery = "SELECT DISTINCT table_name FROM information_schema.columns"
                + " WHERE table_schema = '" + strSchema + "' AND column_name LIKE '%choice%'"
                + " ORDER BY 1";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            while (rs.next()) {
                lstTable.add(rs.getString(1));
            }
        }
        for (String strTable : lstTable) {
            if (strTable.equals(strTableCreate) || strTable.equals(strTableArchive))
                continue;
            Set<String> setColumn = columnsOf(conn, strSchema, strTable);
            String strColChoice = firstOf(setColumn, "exercise_choice");
            if (strColChoice == null)
                strColChoice = firstContaining(setColumn, "choice");
            String strColOrder = firstOf(setColumn, "event_sequential_id", "event_offset");
            if (strColChoice == null || strColOrder == null)
                continue;
            String strColTemplate = firstOf(setColumn, "template_id");
            if (strColTemplate == null)
                strColTemplate = firstContaining(setColumn, "template");
            String strColContract = strColId != null && setColumn.contains(strColId) ? strColId
                    : firstOf(setColumn, ARR_COLUMN_CONTRACT);

            String strExprTemplate = "NULL";
            if (strColTemplate != null)
                strExprTemplate = "a." + strColTemplate;
            else if (arrTemplate != null && strColContract != null
                    && strColContract.equals(strColId))
                strExprTemplate = "(SELECT t." + arrTemplate[2] + " FROM " + arrTemplate[0]
                        + "." + arrTemplate[1] + " t WHERE t." + strColId + " = a." + strColId
                        + " LIMIT 1)";

            String strRead = "SELECT a." + strColOrder + " AS c_off, " + strExprTemplate
                    + " AS c_tpl, a." + strColChoice + " AS c_choice, "
                    + (strColContract == null ? "NULL" : "a." + strColContract) + " AS c_id"
                    + " FROM " + strSchema + "." + strTable + " a"
                    + " WHERE a." + strColChoice + " IS NOT NULL"
                    + " ORDER BY a." + strColOrder + " DESC LIMIT " + CNT_EXERCISE_MAX;
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(strRead)) {
                while (rs.next()) {
                    String strChoice = text(rs.getObject("c_choice"));
                    String strHit = mapIntern.get(strChoice);
                    lstOut.add(new Exercise(strTable + "#" + text(rs.getObject("c_off")),
                            resolved(text(rs.getObject("c_tpl")), mapTemplate),
                            strHit == null ? strChoice : strHit, text(rs.getObject("c_off")),
                            text(rs.getObject("c_id"))));
                }
            }
            catch (SQLException ex) {
                // A TABLE THAT CANNOT BE READ THIS WAY is noted and skipped:
                // the other tables, and the contracts, are still worth having.
                lstSource.add(strSchema + "." + strTable + " not read: " + ex.getMessage());
                continue;
            }
            lstSource.add(strSchema + "." + strTable + "." + strColChoice);
        }
    }


    /**
     * Every interned string, by its id, with its one-character tag taken off
     * - `t|`, `p|` and the rest - so a choice stored as an id reads as its
     * name whatever tag it was interned under.
     */
    private static Map<String, String> internedAll(Connection conn, String strSchema,
            String strTable) throws SQLException {
        Map<String, String> mapOut = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : internedById(conn, strSchema, strTable, "")
                .entrySet()) {
            String strValue = entry.getValue();
            mapOut.put(entry.getKey(), strValue.length() > 2 && strValue.charAt(1) == '|'
                    ? strValue.substring(2) : strValue);
        }
        return mapOut;
    }


    /**
     * The copy of this table WITH THE MOST ROWS IN IT, when more than one
     * schema has one.
     *
     * MEASURED: a 3.x participant database carries
     * `lapi_events_activate_contract` in `debug` as well as in `public`, and a
     * reader that took the first schema alphabetically took `debug` - which
     * reported `0 active` on a ledger holding seven contracts.
     *
     * Row count rather than a name, because `debug` is a name this corpus has
     * measured once on one generation, and the question being asked is which
     * copy holds the ledger. On an empty ledger every copy is empty and the
     * choice does not matter.
     *
     * @param conn an open connection
     * @param arrName banked names, tried in order
     * @param arrLike SQL LIKE patterns, tried after every name has missed
     * @return `{schema, table}`, or null when none of them is there
     */
    private static String[] tableRichest(Connection conn, String[] arrName, String[] arrLike)
            throws SQLException {
        String[] arrBest = null;
        long cntBest = -1L;

        for (String strName : arrName) {
            for (String strSchema : lstSchemaOwning(conn, strName)) {
                long cntHere = cntRowsIn(conn, strSchema, strName);
                if (cntHere > cntBest) {
                    cntBest = cntHere;
                    arrBest = new String[] { strSchema, strName };
                }
            }
        }
        if (arrBest != null)
            return arrBest;

        for (String strLike : arrLike) {
            String[] arrHit = tableLike(conn, strLike);
            if (arrHit != null)
                return arrHit;
        }
        return null;
    }


    /**
     * The same lookup, confined to one schema.
     *
     * @param conn an open connection
     * @param strSchema the schema the creates came out of
     * @param arrName banked names, tried in order
     * @param arrLike SQL LIKE patterns, tried after every name has missed
     * @return `{schema, table}`, or null when that schema has none of them
     */
    private static String[] tableIn(Connection conn, String strSchema, String[] arrName,
            String[] arrLike) throws SQLException {
        for (String strName : arrName) {
            if (lstSchemaOwning(conn, strName).contains(strSchema))
                return new String[] { strSchema, strName };
        }
        for (String strLike : arrLike) {
            String strQuery = "SELECT table_name FROM information_schema.tables"
                    + " WHERE table_type = 'BASE TABLE' AND table_schema = '" + strSchema + "'"
                    + " AND table_name LIKE '" + strLike + "' ORDER BY 1 LIMIT 1";
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(strQuery)) {
                if (rs.next())
                    return new String[] { strSchema, rs.getString(1) };
            }
        }
        return null;
    }


    /**
     * @param conn an open connection
     * @param strSchema the schema
     * @param strTable the table
     * @return how many rows it holds, or -1 when it cannot be counted
     */
    private static long cntRowsIn(Connection conn, String strSchema, String strTable) {
        String strQuery = "SELECT count(*) FROM " + strSchema + "." + strTable;
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
        catch (SQLException ex) {
            return -1L;
        }
    }


    /**
     * @param conn an open connection
     * @param strTable the table to find
     * @return every schema that owns one, in name order; never null
     */
    private static List<String> lstSchemaOwning(Connection conn, String strTable)
            throws SQLException {
        List<String> lstOut = new ArrayList<>();
        String strQuery = "SELECT table_schema FROM information_schema.tables"
                + " WHERE table_name = '" + strTable + "' AND table_type = 'BASE TABLE'"
                + " AND table_schema NOT IN ('information_schema', 'pg_catalog') ORDER BY 1";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            while (rs.next()) {
                lstOut.add(rs.getString(1));
            }
        }
        return lstOut;
    }


    /**
     * The first table anywhere in the database whose name matches, with the
     * schema that owns it.
     *
     * @param conn an open connection
     * @param strLike a SQL LIKE pattern
     * @return `{schema, table}`, or null when nothing matches
     */
    private static String[] tableLike(Connection conn, String strLike) throws SQLException {
        String strQuery = "SELECT table_schema, table_name FROM information_schema.tables"
                + " WHERE table_type = 'BASE TABLE' AND table_name LIKE '" + strLike + "'"
                + " AND table_schema NOT IN ('information_schema', 'pg_catalog')"
                + " ORDER BY 1, 2 LIMIT 1";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            return rs.next() ? new String[] { rs.getString(1), rs.getString(2) } : null;
        }
    }


    /**
     * @param conn an open connection
     * @return every non-system table as `schema.table`, for a diagnostic
     */
    private static List<String> lstTableAll(Connection conn) throws SQLException {
        List<String> lstOut = new ArrayList<>();
        String strQuery = "SELECT table_schema, table_name FROM information_schema.tables"
                + " WHERE table_type = 'BASE TABLE'"
                + " AND table_schema NOT IN ('information_schema', 'pg_catalog')"
                + " ORDER BY 1, 2";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            while (rs.next()) {
                lstOut.add(rs.getString(1) + "." + rs.getString(2));
            }
        }
        return lstOut;
    }


    /**
     * Every distinct identifier in the tables of this schema whose name
     * carries the keyword.
     *
     * A UNION over tables rather than one named table, because which one holds
     * parties differs between the generations - and a reader that named a table
     * would be right on the version it was written against and empty on the
     * other. Tables that match the keyword but carry none of the candidate
     * columns are skipped, so this costs nothing when it guesses wide.
     *
     * @param conn an open connection
     * @param strSchema the ledger schema
     * @param strKeyword what the table name has to contain
     * @param arrColumn the identifying column, in order of preference
     * @param lstOut where distinct values land
     * @param lstSource the tables that actually contributed, for the note line
     */
    private static void readEntities(Connection conn, String strSchema, String strKeyword,
            String[] arrColumn, List<String> lstOut, List<String> lstSource) throws SQLException {
        Set<String> setValue = new LinkedHashSet<>();

        List<String> lstTable = new ArrayList<>();
        String strFind = "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = '" + strSchema + "' AND table_type = 'BASE TABLE'"
                + " AND table_name LIKE '%" + strKeyword + "%' ORDER BY 1";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strFind)) {
            while (rs.next()) {
                lstTable.add(rs.getString(1));
            }
        }

        for (String strTable : lstTable) {
            Map<String, String> mapColumn = columnsTypedOf(conn, strSchema, strTable);
            String strColumn = null;
            for (String strCandidate : arrColumn) {
                String strType = mapColumn.get(strCandidate);
                // An ARRAY column renders as `{a,b}` and is a set of ids
                // rather than one; skipped rather than shown as a blob.
                if (strType != null && !strType.equalsIgnoreCase("ARRAY")) {
                    strColumn = strCandidate;
                    break;
                }
            }
            if (strColumn == null)
                continue;

            String strQuery = "SELECT DISTINCT " + strColumn + " FROM " + strSchema + "."
                    + strTable + " WHERE " + strColumn + " IS NOT NULL ORDER BY 1 LIMIT "
                    + CNT_ENTITY_MAX;
            int cntBefore = setValue.size();
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(strQuery)) {
                while (rs.next()) {
                    String strValue = text(rs.getObject(1));
                    if (!strValue.isEmpty())
                        setValue.add(strValue);
                }
            }
            catch (SQLException ex) {
                // One unreadable table is not a reason to lose the others.
                continue;
            }
            if (setValue.size() > cntBefore)
                lstSource.add(strTable + "." + strColumn);
        }
        lstOut.addAll(setValue);
    }


    /**
     * @param conn an open connection
     * @param strSchema the schema
     * @param strTable the table
     * @return column name to SQL data type
     */
    private static Map<String, String> columnsTypedOf(Connection conn, String strSchema,
            String strTable) throws SQLException {
        Map<String, String> mapOut = new LinkedHashMap<>();
        String strQuery = "SELECT column_name, data_type FROM information_schema.columns"
                + " WHERE table_schema = '" + strSchema + "' AND table_name = '"
                + strTable + "'";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            while (rs.next()) {
                mapOut.put(rs.getString(1), rs.getString(2));
            }
        }
        return mapOut;
    }


    /**
     * @param conn an open connection
     * @param strSchema the schema owning the interning table
     * @param strTable what that table is called here - `string_interning` on
     *        2.x and `lapi_string_interning` on 3.x
     * @param strTag the one-character tag and its bar
     * @return interned id to value, tag stripped; empty when the table or its
     *         columns are not what this expects
     */
    private static Map<String, String> internedById(Connection conn, String strSchema,
            String strTable, String strTag) throws SQLException {
        Map<String, String> mapOut = new LinkedHashMap<>();
        String strKey = null;
        String strValue = null;

        String strProbe = "SELECT column_name, data_type FROM information_schema.columns"
                + " WHERE table_schema = '" + strSchema + "' AND table_name = '"
                + strTable + "' ORDER BY ordinal_position";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strProbe)) {
            while (rs.next()) {
                String strName = rs.getString(1);
                String strType = String.valueOf(rs.getString(2)).toLowerCase();
                if (strKey == null && strType.contains("int"))
                    strKey = strName;
                else if (strValue == null && isTextType(strType))
                    strValue = strName;
            }
        }
        if (strKey == null || strValue == null)
            return mapOut;

        String strQuery = "SELECT " + strKey + ", " + strValue + " FROM " + strSchema + "."
                + strTable + " WHERE " + strValue + " LIKE '" + strTag + "%' ORDER BY 1";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            while (rs.next()) {
                String strRaw = String.valueOf(rs.getString(2));
                mapOut.put(String.valueOf(rs.getString(1)), strRaw.substring(strTag.length()));
            }
        }
        return mapOut;
    }


    private static boolean isTextType(String strType) {
        return strType.equals("text") || strType.startsWith("character")
                || strType.equals("varchar") || strType.equals("citext");
    }


    /**
     * Where the template of a contract is kept.
     *
     * FIRST the create table itself: `template_id`, which is 2.x's, or any
     * column whose name carries `template`. THEN a table elsewhere that
     * carries the same contract column AND a column named for a template -
     * one in the creates' own schema before any other, and one whose name is
     * not an event table before one that is, because an event table covers
     * only the contracts that had that event.
     *
     * @param conn an open connection
     * @param strSchema the schema the creates came out of
     * @param strTableCreate the create table
     * @param setColCreate its columns
     * @param strColId the contract column both event tables share
     * @return `{schema, table, column}`, or null when nothing carries one
     */
    private static String[] templateSource(Connection conn, String strSchema,
            String strTableCreate, Set<String> setColCreate, String strColId)
            throws SQLException {
        String strOwn = firstOf(setColCreate, "template_id");
        if (strOwn == null)
            strOwn = firstContaining(setColCreate, "template");
        if (strOwn != null)
            return new String[] { strSchema, strTableCreate, strOwn };

        String strQuery = "SELECT c.table_schema, c.table_name, c.column_name"
                + " FROM information_schema.columns c"
                + " WHERE c.column_name LIKE '%template%'"
                + " AND c.table_schema NOT IN ('information_schema', 'pg_catalog')"
                + " AND EXISTS (SELECT 1 FROM information_schema.columns k"
                + " WHERE k.table_schema = c.table_schema AND k.table_name = c.table_name"
                + " AND k.column_name = '" + strColId + "')"
                + " ORDER BY (c.table_schema = '" + strSchema + "') DESC,"
                + " (c.table_name LIKE '%event%') ASC, c.table_name, c.column_name LIMIT 1";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            if (rs.next())
                return new String[] { rs.getString(1), rs.getString(2), rs.getString(3) };
        }
        return null;
    }


    /**
     * @param setColumn the columns of a table
     * @param strPart what the name must carry
     * @return the first in name order that carries it, or null
     */
    private static String firstContaining(Set<String> setColumn, String strPart) {
        String strBest = null;
        for (String strColumn : setColumn) {
            if (strColumn.contains(strPart) && (strBest == null || strColumn.compareTo(strBest) < 0))
                strBest = strColumn;
        }
        return strBest;
    }


    /**
     * @param conn an open connection
     * @param strSchema the schema owning the table
     * @param strTable the create or the archive table
     * @param strColId what identifies a contract on BOTH tables, or null when
     *        they share nothing
     * @param arrTemplate where the template is, from {@link #templateSource},
     *        or null when nothing carries one
     * @param mapTemplate interned id to template name, for the id column
     * @param lstOut where the rows land, newest first
     */
    private static void readContracts(Connection conn, String strSchema, String strTable,
            String strColId, String[] arrTemplate, Map<String, String> mapTemplate,
            List<Contract> lstOut) throws SQLException {
        if (strColId == null)
            return;
        Set<String> setColumn = columnsOf(conn, strSchema, strTable);
        if (!setColumn.contains(strColId))
            return;

        String strColTemplate = firstOf(setColumn, "template_id");
        if (strColTemplate == null)
            strColTemplate = firstContaining(setColumn, "template");
        String strColOffset = firstOf(setColumn, "event_offset", "event_sequential_id");
        String strColTime = firstOf(setColumn, "ledger_effective_time", "record_time");
        // THE CHOICE THAT ARCHIVED IT, where the table says - 2.x's consuming
        // table names it `exercise_choice`; on 3.x it is found.
        String strColChoice = firstOf(setColumn, "exercise_choice");
        if (strColChoice == null)
            strColChoice = firstContaining(setColumn, "choice");

        // THIS TABLE'S OWN COLUMN, else ONE LOOKUP PER ROW in the table that
        // carries it. A scalar subquery rather than a join: a table holding
        // more than one row per contract would otherwise multiply the list.
        String strExprTemplate = "NULL";
        if (strColTemplate != null)
            strExprTemplate = "a." + strColTemplate;
        else if (arrTemplate != null)
            strExprTemplate = "(SELECT t." + arrTemplate[2] + " FROM " + arrTemplate[0] + "."
                    + arrTemplate[1] + " t WHERE t." + strColId + " = a." + strColId + " LIMIT 1)";

        StringBuilder sb = new StringBuilder("SELECT ");
        sb.append("a.").append(strColId).append(" AS c_id, ");
        sb.append(strExprTemplate).append(" AS c_tpl, ");
        sb.append(strColOffset == null ? "NULL" : "a." + strColOffset).append(" AS c_off, ");
        sb.append(strColTime == null ? "NULL" : "a." + strColTime).append(" AS c_time, ");
        sb.append(strColChoice == null ? "NULL" : "a." + strColChoice).append(" AS c_choice");
        sb.append(" FROM ").append(strSchema).append('.').append(strTable).append(" a");
        sb.append(" ORDER BY a.").append(strColOffset == null ? strColId : strColOffset);
        sb.append(" DESC LIMIT ").append(CNT_CONTRACT_MAX);

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sb.toString())) {
            while (rs.next()) {
                lstOut.add(new Contract(text(rs.getObject("c_id")),
                        resolved(text(rs.getObject("c_tpl")), mapTemplate),
                        text(rs.getObject("c_off")), text(rs.getObject("c_time")),
                        text(rs.getObject("c_choice"))));
            }
        }
    }


    private static Set<String> columnsOf(Connection conn, String strSchema, String strTable)
            throws SQLException {
        Set<String> setOut = new LinkedHashSet<>();
        String strQuery = "SELECT column_name FROM information_schema.columns"
                + " WHERE table_schema = '" + strSchema + "' AND table_name = '"
                + strTable + "'";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(strQuery)) {
            while (rs.next()) {
                setOut.add(rs.getString(1));
            }
        }
        return setOut;
    }


    private static String firstOf(Set<String> setColumn, String... arrCandidate) {
        for (String strCandidate : arrCandidate) {
            if (setColumn.contains(strCandidate))
                return strCandidate;
        }
        return null;
    }


    /**
     * @param strRaw whatever the template column held
     * @param mapTemplate interned id to name
     * @return the name, or the raw value when nothing maps it - the integer is
     *         a poor label and a blank is a worse one
     */
    private static String resolved(String strRaw, Map<String, String> mapTemplate) {
        String strHit = mapTemplate.get(strRaw);
        return strHit == null ? strRaw : strHit;
    }


    /**
     * A cell as text. A bytea comes back as `byte[]`, whose `toString` is an
     * object address; it is decoded when it is printable text, and hex
     * otherwise, so an id still compares equal to itself across two reads.
     */
    private static String text(Object objValue) {
        if (objValue == null)
            return "";
        if (objValue instanceof byte[] arrByte) {
            boolean flagText = arrByte.length > 0;
            for (byte bValue : arrByte) {
                if (bValue < 0x20 || bValue > 0x7e) {
                    flagText = false;
                    break;
                }
            }
            if (flagText)
                return new String(arrByte, StandardCharsets.US_ASCII);
            StringBuilder bld = new StringBuilder(arrByte.length * 2);
            for (byte bValue : arrByte) {
                bld.append(Character.forDigit((bValue >> 4) & 0xf, 16))
                        .append(Character.forDigit(bValue & 0xf, 16));
            }
            return bld.toString();
        }
        return String.valueOf(objValue);
    }


    /**
     * The last segment of an interned template name, which is the template's
     * own name.
     *
     * `string_interning` holds a fully qualified name - package, module and
     * template - and the fixture is asserted on the template. Split rather than
     * matched as a substring: `contains(":Pet")` would also be true of a
     * template called `PetShop`, and a harness that passed on the wrong name is
     * worse than one that fails.
     *
     * @param strTemplate an interned template name
     * @return its last colon-separated segment; the whole string when there is
     *         no colon
     */
    public static String strSimpleName(String strTemplate) {
        if (strTemplate == null)
            return "";
        int idxColon = strTemplate.lastIndexOf(':');
        return idxColon < 0 ? strTemplate : strTemplate.substring(idxColon + 1);
    }


    /**
     * The party's own name, which is what a fixture names it.
     *
     * A party id is `&lt;hint&gt;::&lt;fingerprint&gt;`, and the fingerprint
     * is different on every start - so the hint is the only half worth
     * showing.
     *
     * IT IS NOT WHAT THE PET SHOP IS ASSERTED ON. `allocateParty "PetShop"`
     * sets a DISPLAY NAME in Daml Script and the id hint is generated, so
     * that ledger's hints read `party-63ea2de5-...`. This stays
     * because a hint is the readable half of an id wherever one was given.
     *
     * @param strParty a party id
     * @return the text before the first `::`; the whole string when there is
     *         none
     */
    public static String strPartyHint(String strParty) {
        if (strParty == null)
            return "";
        int idxColon = strParty.indexOf("::");
        return idxColon < 0 ? strParty : strParty.substring(0, idxColon);
    }


    /** One create or archive row, in the four fields a reader needs. */
    /**
     * @param strChoice the choice that archived it, on the archive side and
     *        where the table says; empty otherwise
     */
    public record Contract(String strId, String strTemplate, String strOffset, String strTime,
            String strChoice) {

        public Contract(String strId, String strTemplate, String strOffset, String strTime) {
            this(strId, strTemplate, strOffset, strTime, "");
        }


        Contract withChoice(String strChoiceNew) {
            return new Contract(strId, strTemplate, strOffset, strTime, strChoiceNew);
        }


        @Override
        public String toString() {
            String strShort = strId.length() > 12 ? strId.substring(strId.length() - 8) : strId;
            return (strTemplate.isEmpty() ? "?" : strTemplate) + "  #" + strShort;
        }
    }


    /**
     * A choice that archived nothing.
     *
     * @param strKey unique on this ledger - the table and its ordering column
     * @param strTemplate the template, as interned
     * @param strChoice the choice's name
     * @param strOffset what orders it
     * @param strContract the contract it was exercised on, or empty
     */
    public record Exercise(String strKey, String strTemplate, String strChoice, String strOffset,
            String strContract) {
    }


    /** Everything one read returned. */
    public static final class Ledger {

        private final List<Exercise> lstExercise = new ArrayList<>();

        private final List<String> lstSourceExercise = new ArrayList<>();

        private final List<String> lstParty = new ArrayList<>();

        private final List<String> lstTemplate = new ArrayList<>();

        private final List<Contract> lstActive = new ArrayList<>();

        private final List<Contract> lstArchived = new ArrayList<>();

        private final List<String> lstUser = new ArrayList<>();

        private final List<String> lstSourceParty = new ArrayList<>();

        private final List<String> lstSourceUser = new ArrayList<>();

        private String strSchemaNote = "";

        private String strTemplateSource = "";

        private long cntCreateRow;


        public List<String> lstParty() {
            return lstParty;
        }


        public List<String> lstTemplate() {
            return lstTemplate;
        }


        public List<String> lstUser() {
            return lstUser;
        }


        public List<Contract> lstActive() {
            return lstActive;
        }


        public List<Contract> lstArchived() {
            return lstArchived;
        }


        public List<String> lstSourceParty() {
            return lstSourceParty;
        }


        public List<String> lstSourceUser() {
            return lstSourceUser;
        }


        public String strSchemaNote() {
            return strSchemaNote;
        }


        /**
         * @return the choices that archived nothing, newest first per table
         */
        public List<Exercise> lstExercise() {
            return lstExercise;
        }


        /**
         * @return `schema.table.column` for each table the exercises came
         *         from - empty when none was found
         */
        public List<String> lstSourceExercise() {
            return lstSourceExercise;
        }


        /**
         * @return `schema.table.column` the templates were read from, or
         *         `not found` with the create table's columns - which is the
         *         measurement D-415 left open
         */
        public String strTemplateSource() {
            return strTemplateSource;
        }


        /**
         * @return how many create events were READ into the lists, active or
         *         archived; zero when the identity column could not be found
         */
        public int cntCreate() {
            return lstActive.size() + lstArchived.size();
        }


        /**
         * @return how many rows the create-event table holds, counted rather
         *         than parsed; the measure of "anything reached this ledger at
         *         all", and the one that does not depend on a column name
         */
        public long cntCreateRow() {
            return cntCreateRow;
        }


        @Override
        public String toString() {
            return lstParty.size() + " parties, " + lstUser.size() + " users, "
                    + lstTemplate.size() + " templates, " + cntCreateRow + " create rows, "
                    + lstActive.size() + " active, " + lstArchived.size()
                    + " archived - schema " + strSchemaNote;
        }
    }
}
