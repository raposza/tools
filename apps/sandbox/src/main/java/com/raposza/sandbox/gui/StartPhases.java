// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.StackComponent;

/**
 * What a component is doing right now, in four words, taken from its own
 * output.
 *
 * <h2>Why this exists</h2>
 *
 * `Starting participant` stands unchanged for twenty seconds on this machine
 * and nearer three minutes on a modest one, and a reader cannot tell
 * that from a hung start. The spinner says something is still arriving; this
 * says what. Both are read off the process's own lines, so neither can keep
 * moving after the process has stopped talking.
 *
 * <h2>The patterns are a vendor's words and are therefore FRAGILE</h2>
 *
 * Every match below is a substring of a line Canton, Flyway, Zonky or
 * PostgreSQL prints, and none of them is an interface any of those four
 * publishes. A wording change costs a phase and NOTHING ELSE: an unmatched
 * line returns null, the footer keeps the last phase it had, and the start
 * proceeds exactly as it did before this class existed. No decision anywhere
 * reads what comes out of here. That is the whole reason it is allowed to
 * match on prose.
 *
 * <h2>What was read to build it</h2>
 *
 * Two participant logs from 2026-08-22 - one 3.5.12, one 3.4.6 - and one
 * PostgreSQL log from the same morning. The 3.4 and 3.5 lines that matter are
 * the same on both, which is why there is one table rather than one per line.
 *
 * **THE 2.x COLUMN IS READ OFF A REAL LOG.** A 2.10.2 log arrived on
 * 2026-08-22 and the 2.x half of the table was written from it. The file
 * originally supplied as `participant_2_10_and_3_4_6.log` held four 3.4.6 runs
 * and no 2.x at all, which is why an earlier revision of this comment said the
 * 2.x column was unmeasured. It is not any more.
 *
 * Author Claude/bentzn
 */
public final class StartPhases {

    /** Longer than this and the footer wraps or truncates; neither reads. */
    public static final int N_PHASE_MAX = 48;

    private StartPhases() {
    }


    /**
     * @param strComponent one of {@link StackComponent}
     * @param strLine one raw line of that component's output
     * @return four words for the footer, or null when the line says nothing a
     *         reader waiting for a start would want
     */
    public static String strOf(String strComponent, String strLine) {
        if (strComponent == null || strLine == null || strLine.isBlank())
            return null;

        String strPhase;
        if (StackComponent.STR_POSTGRES.equals(strComponent))
            strPhase = strPostgres(strLine);
        else if (StackComponent.STR_PARTICIPANT.equals(strComponent))
            strPhase = strParticipant(strLine);
        else if (StackComponent.STR_PQS.equals(strComponent))
            strPhase = strPqs(strLine);
        else
            strPhase = null;

        if (strPhase == null || strPhase.isBlank())
            return null;
        return strPhase.length() <= N_PHASE_MAX ? strPhase
                : strPhase.substring(0, N_PHASE_MAX - 1) + "\u2026";
    }


    /**
     * The Canton side, BOTH GENERATIONS. Ordered most specific first, because
     * several of these lines contain the words a later one is matched on.
     *
     * <h2>2.x is not 3.x with different words</h2>
     *
     * The 2.x line has a DOMAIN where 3.x has a sequencer and a mediator, and
     * it starts no named nodes at all - there is no `Starting node X`, only
     * `Setting up database schemas for X` per node. Its index schema is 99
     * Flyway migrations against `ledger_api` rather than a handful against
     * `public`, and that is the six seconds a first 2.x start spends where a
     * 3.x start spends none. Where a phase reads the same on both lines the
     * two patterns sit on one branch; where the words differ they are separate
     * branches with the same text, so a reader is not asked to learn which
     * generation they are watching.
     *
     * @param strLine one line of Canton's log or standard output
     * @return the phase, or null
     */
    private static String strParticipant(String strLine) {
        if (strLine.contains("Starting Canton version "))
            return "version " + strWord(strLine, "Starting Canton version ");
        if (strLine.contains("Starting up with resolved config"))
            return "resolving the configuration";
        if (strLine.contains("topology written to"))
            return "writing the topology";
        if (strLine.contains("auth written to"))
            return "writing the auth configuration";
        if (strLine.contains("bootstrap written to"))
            return "writing the bootstrap script";
        if (strLine.contains("Automatically starting all instances"))
            return "starting the nodes";
        if (strLine.contains("Setting up database schemas for "))
            return "schemas for " + strWord(strLine, "Setting up database schemas for ");
        if (strLine.contains("Starting node "))
            return "node " + strWord(strLine, "Starting node ");
        if (strLine.contains("Migrating schema ") && strLine.contains("to version "))
            return "migrating to " + strQuoted(strLine, "to version ");
        if (strLine.contains("Successfully applied ") && strLine.contains("migration"))
            return "migrations applied";
        // THE 2.x INDEX SCHEMA, which is 99 migrations and about six seconds -
        // the single longest thing in a first 2.x start.
        if (strLine.contains("Running Flyway migration on empty database with "))
            return "the index schema - " + strWord(strLine, "on empty database with ")
                    + " migrations";
        if (strLine.contains("No pending migrations with "))
            return "the index schema is current";
        if (strLine.contains("Flyway schema migration finished"))
            return "the index schema is done";
        if (strLine.contains("Ensuring Flyway migration"))
            return "checking the index schema";
        if (strLine.contains("Attempting to connect to the database"))
            return "connecting to the index database";
        if (strLine.contains("Creating storage, num-indexer"))
            return "the ledger API storage";
        if (strLine.contains("Creating storage, num"))
            return "opening the connection pool";
        if (strLine.contains("Starting admin-api services"))
            return "the admin API";
        if (strLine.contains("Node is not initialized yet"))
            return "first-time initialisation";
        if (strLine.contains("Resuming as existing instance")
                || strLine.contains("Resuming with existing uid ")
                || strLine.contains("Initializing node with id "))
            return "the node identity";
        if (strLine.contains("Creating temporary topology store"))
            return "the topology store";
        if (strLine.contains("Finalizing initialization for synchronizer"))
            return "initialising the synchronizer";
        if (strLine.contains("Sending initial topology transactions"))
            return "the domain topology";
        if (strLine.contains("Attempting to build, sign"))
            return "signing the topology";
        if (strLine.contains("Storing topology transaction")
                || strLine.contains("Applied topology transaction"))
            return "topology transactions";
        if (strLine.contains("Successfully onboarded "))
            return "the participant is onboarded";
        if (strLine.contains("Preloading the events buffer"))
            return "loading the event buffer";
        if (strLine.contains("Sequencer runtime initialized")
                || strLine.contains("Sequencer is healthy"))
            return "the sequencer is up";
        if (strLine.contains("persistent-state initialized"))
            return "the persistent state";
        if (strLine.contains("Initializing next indexer")
                || strLine.contains("Starting Indexer Server"))
            return "the indexer";
        if (strLine.contains("Package Metadata View has been initialized"))
            return "the package metadata view";
        if (strLine.contains("Checking loaded packages for upgrade"))
            return "checking the package upgrades";
        if (strLine.contains("in-memory state not initialized on attempt"))
            return "waiting for the in-memory state";
        if (strLine.contains("Initializing participant in-memory state"))
            return "the in-memory state";
        if (strLine.contains("Indexer initialized, indexing started"))
            return "indexing";
        if (strLine.contains("Daml-LF Engine supports LF versions: "))
            return "LF " + strRest(strLine, "Daml-LF Engine supports LF versions: ");
        // BOTH GENERATIONS ON ONE BRANCH. 3.x says `listening to port = N
        // without tls`, 2.x says `with ledger-id = sandbox, port = N.`, and
        // the useful half is the same number in both.
        if (strLine.contains("Initialized API server"))
            return "ledger API on " + strWord(strLine, "port = ");
        if (strLine.contains("Starting ledger API server"))
            return "the ledger API";
        if (strLine.contains("HTTP JSON API Server started with "))
            return "JSON API on " + strUpTo(strLine, "port=", ',');
        if (strLine.contains("Starting JSON API server"))
            return "the JSON API";
        if (strLine.contains("Creating admin workflow service "))
            return "the admin workflows";
        if (strLine.contains("wait-for-admin-workflows"))
            return "waiting for the admin workflows";
        if (strLine.contains("PublicPackageUpload("))
            return "uploading the packages";
        if (strLine.contains("Listing known packages"))
            return "listing the packages";
        if (strLine.contains("with DomainConnectionConfig"))
            return "registering the domain";
        if (strLine.contains("Version handshake with sequencer"))
            return "the sequencer handshake";
        if (strLine.contains("Reconnecting to synchronizers"))
            return "connecting to the synchronizer";
        if (strLine.contains("Reconnecting to domains"))
            return "connecting to the domain";
        if (strLine.contains("Connected to synchronizer"))
            return "the synchronizer is connected";
        if (strLine.contains("re-connected to domains") || strLine.contains("Connected to domain"))
            return "the domain is connected";
        if (strLine.contains("Successfully started all nodes"))
            return "every node is up";
        if (strLine.contains("Bootstrap script successfully executed"))
            return "the bootstrap script is done";
        if (strLine.contains("Running script Some(") || strLine.contains("has already been"
                + " bootstrapped"))
            return "the bootstrap script";
        return null;
    }


    /**
     * The PQS side, which is scribe: a configuration dump, a Flyway migration
     * of its own schema, a token acquisition, and then a wait for the ledger
     * to have something in it.
     *
     * <h2>Where the patterns come from</h2>
     *
     * ONE LOG, verbatim: scribe v3.5.7 against a 3.5.12 participant,
     * 2026-08-24, first start against an empty `pqs` schema. Everything below
     * is a substring of a line in it. Two of the branches - the diagnostics
     * server and the OpenTelemetry notice - are printed before scribe's own
     * logger is configured and carry no timestamp; the rest carry ANSI colour
     * codes between the fields, which is why every match is on the message
     * text and never on the line's shape.
     *
     * <h2>The last two branches are the reason this exists</h2>
     *
     * A first PQS start against a ledger with no parties on it sits in
     * `No parties found matching *` and retries on a doubling backoff for as
     * long as the window is open. Nothing else on screen says so: the footer
     * held `Starting PQS` and the reader had to open the tab and read scribe's
     * prose to find out that it was not stuck but waiting. That line is the
     * phase worth having.
     *
     * The same caveat as {@link #strParticipant} applies in full - these are
     * a vendor's words, an unmatched line returns null, and no decision
     * anywhere reads what comes out of here.
     *
     * @param strLine one line of scribe's output
     * @return the phase, or null
     */
    private static String strPqs(String strLine) {
        if (strLine.contains("No parties found matching"))
            return "waiting for a party on the ledger";
        if (strLine.contains("Recoverable GRPC exception. Attempt "))
            return "retrying - attempt " + strWord(strLine, "Recoverable GRPC exception."
                    + " Attempt ");
        if (strLine.contains("PQS: real scribe from"))
            return "resolving scribe";
        if (strLine.contains("PQS: mock, because"))
            return "the in-process mock";
        if (strLine.contains("Initialising diagnostics server"))
            return "the diagnostics server";
        if (strLine.contains("OpenTelemetry Java Agent is not found"))
            return "no OpenTelemetry agent";
        if (strLine.contains("Applied configuration:"))
            return "reading the configuration";
        if (strLine.contains("scribe, version: "))
            return "scribe " + strWord(strLine, "scribe, version: ");
        if (strLine.contains("Database probe (select 1) successful"))
            return "the target database answered";
        if (strLine.contains("Acquiring auth token"))
            return "acquiring the auth token";
        if (strLine.contains("Successful auth token response")
                || strLine.contains("Token acquired, expires in"))
            return "the auth token is in";
        if (strLine.contains("Keep-alive (get ledger version) successful"))
            return "the ledger API answered";
        if (strLine.contains("Listed ") && strLine.contains(" packages"))
            return strWord(strLine, "Listed ") + " packages on the ledger";
        if (strLine.contains("Applying schema"))
            return "applying the schema";
        if (strLine.contains("Migrating schema ") && strLine.contains("to version "))
            return "migrating to " + strQuoted(strLine, "to version ");
        if (strLine.contains("Successfully applied ") && strLine.contains("migration"))
            return "migrations applied";
        if (strLine.contains("Successfully validated ") && strLine.contains("migration"))
            return strWord(strLine, "Successfully validated ") + " migrations validated";
        if (strLine.contains("No migration necessary"))
            return "the schema is current";
        if (strLine.contains("Schema history table") && strLine.contains("does not exist"))
            return "a new schema";
        if (strLine.contains("Applying mappings"))
            return "applying the mappings";
        if (strLine.contains("Schema and mappings applied"))
            return "the schema is done";
        if (strLine.contains("Initialised ") && strLine.contains(" entity types"))
            return strWord(strLine, "Initialised ") + " entity types";
        if (strLine.contains("Initialised ") && strLine.contains(" exercise types"))
            return strWord(strLine, "Initialised ") + " exercise types";
        if (strLine.contains("Initialised ") && strLine.contains(" packages"))
            return strWord(strLine, "Initialised ") + " packages recorded";
        if (strLine.contains("Retrieved ") && strLine.contains(" user rights"))
            return "reading the user rights";
        if (strLine.contains(" can actAs/readAs"))
            return "checking what the user may read";
        return null;
    }


    /**
     * The PostgreSQL side, which is three programs: Zonky resolving and
     * unpacking a binary, `initdb` building a cluster the first time, and the
     * postmaster coming up. The initdb steps are worth naming individually
     * because they are the six seconds a first start spends before anything
     * else happens.
     *
     * @param strLine one line of the embedded server's output
     * @return the phase, or null
     */
    private static String strPostgres(String strLine) {
        if (strLine.contains("Detected distribution") || strLine.contains("Detected a Linux")
                || strLine.contains("Detected a "))
            return "detecting the platform";
        if (strLine.contains("postgres binaries found"))
            return "resolving the binaries";
        if (strLine.contains("Postgres binaries at "))
            return "unpacking the binaries";
        if (strLine.contains("fixing permissions on existing directory"))
            return "initdb - permissions";
        if (strLine.contains("creating subdirectories"))
            return "initdb - subdirectories";
        if (strLine.contains("selecting dynamic shared memory"))
            return "initdb - shared memory";
        if (strLine.contains("selecting default max_connections"))
            return "initdb - max_connections";
        if (strLine.contains("selecting default shared_buffers"))
            return "initdb - shared buffers";
        if (strLine.contains("selecting default time zone"))
            return "initdb - time zone";
        if (strLine.contains("creating configuration files"))
            return "initdb - configuration files";
        if (strLine.contains("running bootstrap script"))
            return "initdb - bootstrap script";
        if (strLine.contains("performing post-bootstrap initialization"))
            return "initdb - post-bootstrap";
        if (strLine.contains("syncing data to disk"))
            return "initdb - syncing to disk";
        if (strLine.contains("The files belonging to this database system"))
            return "initdb - a new cluster";
        if (strLine.contains("initdb completed in "))
            return "the cluster is built";
        if (strLine.contains("postmaster started as Process"))
            return "waiting for the postmaster";
        if (strLine.contains("starting PostgreSQL "))
            return "PostgreSQL " + strWord(strLine, "starting PostgreSQL ");
        if (strLine.contains("database system was shut down at"))
            return "recovering the cluster";
        if (strLine.contains("database system is ready to accept connections"))
            return "accepting connections";
        if (strLine.contains("postmaster startup finished"))
            return "the server is up";
        if (strLine.contains("created database "))
            return "database " + strWord(strLine, "created database ");
        if (strLine.contains("starting embedded PostgreSQL on port "))
            return "port " + strWord(strLine, "on port ");
        return null;
    }


    /**
     * @param strLine the line
     * @param strMark what the value follows
     * @return the next whitespace-delimited word, trimmed of a trailing comma
     *         or full stop, or "" when there is none
     */
    /**
     * @param strLine the line
     * @param strMark what the value follows
     * @return everything after the marker, trimmed, or "" when the marker is
     *         absent
     */
    private static String strRest(String strLine, String strMark) {
        int idxFrom = strLine.indexOf(strMark);
        if (idxFrom < 0)
            return "";
        return strLine.substring(idxFrom + strMark.length()).trim();
    }


    private static String strWord(String strLine, String strMark) {
        return strUpTo(strLine, strMark, ' ');
    }


    /**
     * @param strLine the line
     * @param strMark what the value follows
     * @param chEnd what ends it, in addition to whitespace
     * @return the value, or "" when there is none
     */
    private static String strUpTo(String strLine, String strMark, char chEnd) {
        int idxFrom = strLine.indexOf(strMark);
        if (idxFrom < 0)
            return "";

        idxFrom += strMark.length();
        int idxTo = idxFrom;
        while (idxTo < strLine.length() && !Character.isWhitespace(strLine.charAt(idxTo))
                && strLine.charAt(idxTo) != chEnd) {
            idxTo++;
        }
        String strOut = strLine.substring(idxFrom, idxTo);
        while (!strOut.isEmpty() && (strOut.endsWith(",") || strOut.endsWith("."))) {
            strOut = strOut.substring(0, strOut.length() - 1);
        }
        return strOut;
    }


    /**
     * Flyway names a migration inside double quotes and the useful half is
     * the SECOND pair on the line - `Migrating schema "public" to version
     * "998 - blocks"`.
     *
     * @param strLine the line
     * @param strMark what the quoted value follows
     * @return what is between the next pair of quotes, or ""
     */
    private static String strQuoted(String strLine, String strMark) {
        int idxFrom = strLine.indexOf(strMark);
        if (idxFrom < 0)
            return "";

        int idxOpen = strLine.indexOf('"', idxFrom);
        if (idxOpen < 0)
            return "";
        int idxClose = strLine.indexOf('"', idxOpen + 1);
        if (idxClose < 0)
            return "";
        return strLine.substring(idxOpen + 1, idxClose);
    }
}
