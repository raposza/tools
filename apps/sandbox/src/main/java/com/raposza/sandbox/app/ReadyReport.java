// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.topology.Sandbox2xPorts;
import com.raposza.canton.topology.SandboxPorts;
import com.raposza.runtime.db.PostgresCoordinates;
import com.raposza.runtime.localnet.LocalNetPorts;
import com.raposza.runtime.localnet.LocalNetSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a started sandbox is, for a person and for a script.
 *
 * Two renderings of the same facts and no third source: the block printed to
 * the terminal and the properties file left in the work directory come from one
 * map, so a script reading the file and a developer reading the screen cannot
 * disagree about a port.
 *
 * The file is the reason this class exists at all. A headless process that only
 * prints cannot be consumed - a test harness, a `psql` invocation or a
 * saved DBeaver connection all need the numbers as data - and the alternative,
 * parsing the log, is how a port ends up hard-coded somewhere.
 *
 * Author Claude/bentzn
 */
public final class ReadyReport {

    /** Left in the work directory while the stack runs, removed when it stops. */
    public static final String STR_FILE = "sandbox.properties";

    public static final String KEY_CANTON = "canton.version";

    public static final String KEY_EDITION = "canton.edition";

    /**
     * The window's own discovery endpoint.
     *
     * NOT PART OF THE STACK, which is why no constructor sets it: it is bound
     * by the window and outlives any one start. It is added by the window with
     * {@link #withFirst}, and it goes first because it is the address anything
     * outside asks for all the others.
     */
    public static final String KEY_DISCOVERY = "port.discovery";

    public static final String KEY_LEDGER_API = "port.ledger-api";

    public static final String KEY_ADMIN_API = "port.admin-api";

    public static final String KEY_JSON_API = "port.json-api";

    public static final String KEY_SEQUENCER_PUBLIC = "port.sequencer-public";

    /** 2.x only. A domain is not a sequencer and is not reported as one. */
    public static final String KEY_DOMAIN_PUBLIC = "port.domain-public";

    /** 2.x only. */
    public static final String KEY_DOMAIN_ADMIN = "port.domain-admin";

    public static final String KEY_SEQUENCER_ADMIN = "port.sequencer-admin";

    public static final String KEY_MEDIATOR_ADMIN = "port.mediator-admin";

    public static final String KEY_POSTGRES = "port.postgres";

    /**
     * The host every port and every url in this report is on.
     *
     * SAID RATHER THAN ASSUMED. It has always been the loopback address and it
     * was never written down, so a consumer handed `port.ledger-api` had to
     * guess what to put in front of it - and a guess that happens to be right
     * is still a consumer that would break the day it stopped being.
     */
    public static final String KEY_HOST = "host";

    /**
     * The Canton nodes whose databases this cluster holds, comma-separated -
     * `participant,sequencer,sequencer_driver,mediator` on 3.x and
     * `participant,domain` on 2.x.
     *
     * The list is here so a consumer can WALK the databases rather than know
     * the topology of two Canton generations. For each name in it there is a
     * `url.jdbc.`, a `user.jdbc.` and a `password.jdbc.` key.
     */
    public static final String KEY_DB_NAMES = "db.names";

    /** What separates the names in {@link #KEY_DB_NAMES}. */
    public static final String STR_SEP_DB = ",";

    public static final String STR_PREFIX_JDBC_URL = "url.jdbc.";

    public static final String STR_PREFIX_JDBC_USER = "user.jdbc.";

    public static final String STR_PREFIX_JDBC_PASSWORD = "password.jdbc.";

    /**
     * THE URL, AND SAYING SO IN THE KEY. `jdbc.participant` read as though it
     * were the whole connection, which it is not - a consumer still needs the
     * role and the password, and both are beside it under the same shape.
     */
    public static final String KEY_JDBC_PARTICIPANT = "url.jdbc.participant";

    public static final String KEY_USER_PARTICIPANT = "user.jdbc.participant";

    /**
     * The embedded server runs trust auth, so this is accepted rather than
     * checked - and it is still reported, because Canton and scribe both put
     * it in their configuration and a client that omits it may be refused by
     * a driver that will not send an empty one.
     */
    public static final String KEY_PASSWORD_PARTICIPANT = "password.jdbc.participant";

    public static final String KEY_PARTICIPANT_ID = "participant.id";

    public static final String KEY_PARTY_ID = "party.id";

    public static final String KEY_USER_ID = "user.id";

    public static final String KEY_PQS = "pqs";

    public static final String KEY_PQS_JDBC = "url.jdbc.pqs";

    public static final String KEY_PQS_USER = "user.jdbc.pqs";

    public static final String KEY_PQS_PASSWORD = "password.jdbc.pqs";

    public static final String KEY_WORK_DIR = "dir.work";

    public static final String KEY_DATA_DIR = "dir.data";

    public static final String KEY_PID = "pid";

    private final Map<String, String> mapValue = new LinkedHashMap<>();


    /**
     * @param strCanton the version that was started
     * @param strEdition which build of it
     * @param ports the six Canton ports
     * @param nPortPostgres the embedded server's port
     * @param pgParticipant where the participant's own database is
     * @param dirWork the work directory
     * @param dirData the persistent cluster, or null when it is temporary
     */
    /**
     * The 2.x shape of the same report.
     *
     * FIVE PORTS, NOT SIX, and the two that differ are not renamings: 2.x has
     * a DOMAIN where 3.x has a sequencer and a mediator, so there is no
     * mediator-admin port to report and the sequencer keys would be a lie
     * about what is listening. The keys used here say `domain`.
     *
     * @param strCanton the version that was started
     * @param strEdition which build of it
     * @param ports the five 2.x ports
     * @param nPortPostgres the embedded server's port
     * @param pgParticipant where the participant's own database is
     * @param dirWork the work directory
     * @param dirData the persistent cluster, or null when it is temporary
     */
    public ReadyReport(String strCanton, String strEdition, Sandbox2xPorts ports,
            int nPortPostgres, PostgresCoordinates pgParticipant, Path dirWork, Path dirData) {
        put(KEY_CANTON, strCanton);
        put(KEY_EDITION, strEdition);
        put(KEY_LEDGER_API, String.valueOf(ports.nPortLedgerApi()));
        put(KEY_ADMIN_API, String.valueOf(ports.nPortAdminApi()));
        put(KEY_JSON_API, String.valueOf(ports.nPortJsonApi()));
        put(KEY_DOMAIN_PUBLIC, String.valueOf(ports.nPortDomainPublic()));
        put(KEY_DOMAIN_ADMIN, String.valueOf(ports.nPortDomainAdmin()));
        put(KEY_POSTGRES, String.valueOf(nPortPostgres));
        put(KEY_HOST, pgParticipant == null ? null : pgParticipant.strHost());
        put(KEY_JDBC_PARTICIPANT, pgParticipant == null ? null : pgParticipant.jdbcUrl());
        put(KEY_USER_PARTICIPANT, pgParticipant == null ? null : pgParticipant.strUser());
        put(KEY_PASSWORD_PARTICIPANT, pgParticipant == null ? null : pgParticipant.strPassword());
        put(KEY_WORK_DIR, dirWork == null ? null : dirWork.toString());
        put(KEY_DATA_DIR, dirData == null ? null : dirData.toString());
        put(KEY_PID, String.valueOf(ProcessHandle.current().pid()));
    }


    public ReadyReport(String strCanton, String strEdition, SandboxPorts ports, int nPortPostgres,
            PostgresCoordinates pgParticipant, Path dirWork, Path dirData) {
        put(KEY_CANTON, strCanton);
        put(KEY_EDITION, strEdition);
        put(KEY_LEDGER_API, String.valueOf(ports.nPortLedgerApi()));
        put(KEY_ADMIN_API, String.valueOf(ports.nPortAdminApi()));
        put(KEY_JSON_API, String.valueOf(ports.nPortJsonApi()));
        put(KEY_SEQUENCER_PUBLIC, String.valueOf(ports.nPortSequencerPublic()));
        put(KEY_SEQUENCER_ADMIN, String.valueOf(ports.nPortSequencerAdmin()));
        put(KEY_MEDIATOR_ADMIN, String.valueOf(ports.nPortMediatorAdmin()));
        put(KEY_POSTGRES, String.valueOf(nPortPostgres));
        put(KEY_HOST, pgParticipant == null ? null : pgParticipant.strHost());
        put(KEY_JDBC_PARTICIPANT, pgParticipant == null ? null : pgParticipant.jdbcUrl());
        put(KEY_USER_PARTICIPANT, pgParticipant == null ? null : pgParticipant.strUser());
        put(KEY_PASSWORD_PARTICIPANT, pgParticipant == null ? null : pgParticipant.strPassword());
        put(KEY_WORK_DIR, dirWork == null ? null : dirWork.toString());
        put(KEY_DATA_DIR, dirData == null ? null : dirData.toString());
        put(KEY_PID, String.valueOf(ProcessHandle.current().pid()));
    }


    /**
     * ONE ROLE OF A LOCALNETND STACK - `todo.md` A-31 (1).
     *
     * <h2>Three ports, and not the infrastructure's</h2>
     *
     * A node publishes what a consumer dials to reach IT: its Ledger API, its
     * admin API and its JSON Ledger API. The sequencers, the mediators, the
     * scan and the SV app are shared infrastructure rather than any one node's,
     * and putting them under all three would state one fact three times.
     *
     * <h2>The cluster is one, and every role owns two databases in it</h2>
     *
     * There is a single embedded PostgreSQL for the whole stack -
     * `LocalNetRunner.startPostgres` - so the port is the same on each node.
     * What differs is WHICH databases are the node's: its participant's and
     * its validator app's, and those two are written with the credentials to
     * open them. The other eight are infrastructure and are not listed here.
     *
     * @param strCanton the Canton the three participants run inside
     * @param strEdition its edition
     * @param strRole `sv`, `app-provider` or `app-user`
     * @param strHost what a consumer dials
     * @param ports the block this stack was started on
     * @param dirRun the run directory
     * @param dirData the cluster inside it, or null
     */
    public ReadyReport(String strCanton, String strEdition, String strRole, String strHost,
            LocalNetPorts ports, Path dirRun, Path dirData) {
        put(KEY_CANTON, strCanton);
        put(KEY_EDITION, strEdition);
        put(KEY_LEDGER_API, String.valueOf(ports.nPortLedger(strRole)));
        put(KEY_ADMIN_API, String.valueOf(ports.nPortAdmin(strRole)));
        put(KEY_JSON_API, String.valueOf(ports.nPortJson(strRole)));
        put(KEY_POSTGRES, String.valueOf(ports.nPortPostgres()));
        put(KEY_HOST, strHost);
        put(KEY_WORK_DIR, dirRun == null ? null : dirRun.toString());
        put(KEY_DATA_DIR, dirData == null ? null : dirData.toString());
        // THE TWO DATABASES THIS NODE OWNS, each with the credentials to open
        // it - operator instruction, 2026-09-22. `withDatabase` is what the
        // Sandbox path already uses, so the keys are spelt the one way and
        // `db.names` lists what is actually reachable rather than all twelve.
        //
        // NOT THE WHOLE CLUSTER. The sequencers, the mediators, the scan and
        // the SV app are infrastructure; a consumer that wants them has the
        // host, the port and the credentials, and `LocalNetSpec` names them.
        //
        // PQS GOES IN THE SAME WAY when it attaches - `withDatabase(STR_PQS,
        // ...)`, which is the shape `KEY_PQS_JDBC` and its two neighbours
        // already carry on the Sandbox path.
        putDatabase(strHost, ports.nPortPostgres(), LocalNetSpec.strDbParticipant(strRole));
        putDatabase(strHost, ports.nPortPostgres(), LocalNetSpec.strDbValidator(strRole));
        // NO PID. It was the WINDOW's - `ProcessHandle.current()` - and on this
        // topology Canton and Splice are two child JVMs, so the number said
        // nothing about who is serving the ports beside it. Operator
        // instruction, 2026-09-22: consumers will not use it.
    }


    /**
     * One database of the embedded cluster, with what opens it.
     *
     * @param strHost where the cluster is
     * @param nPort its port
     * @param strDb the database; null or blank records nothing
     */
    private void putDatabase(String strHost, int nPort, String strDb) {
        if (strDb == null || strDb.isBlank())
            return;
        withDatabase(strDb, new PostgresCoordinates(strHost, nPort, strDb,
                LocalNetSpec.STR_DB_USER, LocalNetSpec.STR_DB_PASSWORD));
    }


    /**
     * One Canton node's database, named and connectable.
     *
     * THE THREE KEYS ARE WRITTEN FOR EVERY DATABASE even though the role and
     * the password are the same for all of them today. A consumer that had to
     * know they are shared would be relying on a fact about the embedded server
     * that this report never states, and would break on the first cluster where
     * it stopped being true.
     *
     * @param strNode the node name - `participant`, `sequencer`,
     *        `sequencer_driver`, `mediator` on 3.x, `participant` and `domain`
     *        on 2.x. It is the NODE and not the database, which may carry a
     *        prefix; the url beside it names the database
     * @param coord where that database is, or null to record nothing
     * @return this report
     */
    public ReadyReport withDatabase(String strNode, PostgresCoordinates coord) {
        if (strNode == null || strNode.isBlank() || coord == null)
            return this;

        String strTrim = strNode.trim();
        put(STR_PREFIX_JDBC_URL + strTrim, coord.jdbcUrl());
        put(STR_PREFIX_JDBC_USER + strTrim, coord.strUser());
        put(STR_PREFIX_JDBC_PASSWORD + strTrim, coord.strPassword());

        String strNames = mapValue.get(KEY_DB_NAMES);
        if (strNames == null || strNames.isEmpty()) {
            mapValue.put(KEY_DB_NAMES, strTrim);
        }
        else if (!lstDatabase().contains(strTrim)) {
            mapValue.put(KEY_DB_NAMES, strNames + STR_SEP_DB + strTrim);
        }
        return this;
    }


    /**
     * @return the node names recorded by {@link #withDatabase}, in the order
     *         they were recorded; empty when none were
     */
    public List<String> lstDatabase() {
        String strNames = mapValue.get(KEY_DB_NAMES);
        if (strNames == null || strNames.isBlank())
            return new ArrayList<>();

        List<String> lstOut = new ArrayList<>();
        for (String strName : strNames.split(STR_SEP_DB)) {
            String strTrim = strName.trim();
            if (!strTrim.isEmpty() && !lstOut.contains(strTrim))
                lstOut.add(strTrim);
        }
        return lstOut;
    }


    /**
     * @param strKey one of the KEY_ constants
     * @param strValue what to record, or null to record nothing
     * @return this report
     */
    public ReadyReport with(String strKey, String strValue) {
        put(strKey, strValue);
        return this;
    }


    /**
     * Records a value at the FRONT of the report rather than at the end.
     *
     * The map is insertion-ordered and the order is what the Sandbox tab and
     * the log block print, so a key that belongs at the top cannot be added
     * with {@link #with}. An existing key is moved.
     *
     * @param strKey one of the KEY_ constants
     * @param strValue what to record, or null to record nothing
     * @return this report
     */
    public ReadyReport withFirst(String strKey, String strValue) {
        if (strValue == null)
            return this;

        Map<String, String> mapWas = new LinkedHashMap<>(mapValue);
        mapWas.remove(strKey);
        mapValue.clear();
        mapValue.put(strKey, strValue);
        mapValue.putAll(mapWas);
        return this;
    }


    /**
     * @param strKey one of the KEY_ constants
     * @return what was recorded, or null
     */
    public String value(String strKey) {
        return mapValue.get(strKey);
    }


    /**
     * @return every recorded key, in the order they were recorded
     */
    public Map<String, String> map() {
        return new LinkedHashMap<>(mapValue);
    }


    /**
     * @return the block to print, one `key = value` per line under a heading
     */
    public List<String> lstLines() {
        List<String> lstLine = new ArrayList<>();
        lstLine.add("sandbox is up");
        int cntWidth = 0;
        for (String strKey : mapValue.keySet()) {
            cntWidth = Math.max(cntWidth, strKey.length());
        }
        for (Map.Entry<String, String> entry : mapValue.entrySet()) {
            lstLine.add("  " + pad(entry.getKey(), cntWidth) + "  " + entry.getValue());
        }
        return lstLine;
    }


    /**
     * Properties by hand rather than through {@link java.util.Properties},
     * which writes a timestamp comment and reorders the keys - both of which
     * make a diff of two runs unreadable.
     *
     * @param dirWork where the file goes
     * @return the file written
     */
    public Path writeTo(Path dirWork) {
        StringBuilder sb = new StringBuilder();
        sb.append("# raposza sandbox - written while the stack runs, removed when it stops\n");
        for (Map.Entry<String, String> entry : mapValue.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }

        Path file = dirWork.resolve(STR_FILE);
        try {
            if (!Files.isDirectory(dirWork))
                Files.createDirectories(dirWork);
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("could not write " + file, ex);
        }
        return file;
    }


    /**
     * @param dirWork where the file is
     */
    public static void removeFrom(Path dirWork) {
        try {
            Files.deleteIfExists(dirWork.resolve(STR_FILE));
        }
        catch (IOException ex) {
            // A status file left behind says a stack is up when it is not, and
            // that is worth a line of noise rather than an exception thrown
            // out of a shutdown hook that has already stopped everything.
            System.err.println("could not remove " + dirWork.resolve(STR_FILE) + ": " + ex);
        }
    }


    private void put(String strKey, String strValue) {
        if (strValue != null)
            mapValue.put(strKey, strValue);
    }


    private static String pad(String strText, int cntWidth) {
        StringBuilder sb = new StringBuilder(strText);
        while (sb.length() < cntWidth) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
