// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.pqs;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.PqsInstallation;
import com.raposza.canton.install.PqsInstallations;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What PQS a stack should run, if any.
 *
 * {@link ScribeProcess} already answers "real or mock" by whether a binary was
 * handed to it. What it does not answer is which database that binary writes
 * into, which schema, with which token and for how long the caller waits - and
 * those are the four things a stack has to decide before it can own the
 * process rather than leave it to a test.
 *
 * <h2>ONE database name, across every version</h2>
 *
 * The database is the PREFIX, unchanged - {@value #STR_DEFAULT_PREFIX} unless a
 * caller names another one. Real or mock, 2.x or 3.x, 3.4.1 or 3.5.7: the same
 * name. A stack that changes scribe version points the new binary at what the
 * previous one wrote, and what happens to a schema written at another revision
 * is scribe's answer to give.
 *
 * <h2>The GENERATION is carried even when no binary is</h2>
 *
 * A mock resolved for a 2.x participant is still a 2.x fact. Until
 * 2026-08-16 it was not: {@code ofMock()} carried nothing, so
 * {@link ScribeProcess} could not tell which column it was standing in for and
 * rendered `--target-postgres-schema` on both. On 2.x vendor scribe v0.5.5
 * ACCEPTS THAT FLAG AND DROPS IT, so the line offered as something to
 * paste on a licensed box was wrong in exactly the place it mattered, and
 * wrong silently.
 *
 * {@link #strCantonLine()} is therefore a component of the spec rather than a
 * property of the installation alone. A real spec takes it from the binary's
 * DECLARED line; a mock takes it from whatever resolved the mock, which is
 * always a Canton installation and always knows.
 *
 * <h2>The token is supplied, not minted</h2>
 *
 * scribe takes a STATIC access token on its command line, so nothing renews it
 * and a restart is the renewal mechanism. That is a property of scribe's
 * interface and it is not hidden here: the caller that owns the key material
 * mints a token with a life long enough for the run and passes it in. A spec
 * with no token produces `--source-ledger-auth NoAuth`, which is correct
 * against a participant with no auth-services and wrong against one with them,
 * and the failure is scribe's to report.
 *
 * @param installation the scribe binary, or null to run the mock
 * @param strCantonLine the Canton minor line this PQS stands in front of, e.g.
 *        "2.10"; taken from the installation when there is one, and null only
 *        when nothing said
 * @param strPrefix the database name; nothing is appended to it
 * @param strSchema the schema inside that database
 * @param strToken the static access token, or null for NoAuth
 * @param oauth credentials to re-mint from, or null; when set it WINS over
 *        strToken, because the one thing a static token cannot do is outlive
 *        its own lifetime
 * @param nHeapMb the JVM heap for scribe, or 0 for the JVM default
 * @param timeoutReady how long the stack waits for PQS to report ingesting
 *
 * Author Claude/bentzn
 */
public record PqsSpec(PqsInstallation installation, String strCantonLine, String strPrefix,
        String strSchema, String strToken, PqsOAuth oauth, int nHeapMb, Duration timeoutReady) {

    public static final String STR_DEFAULT_PREFIX = "pqs";

    /**
     * Long enough for a real scribe to migrate its schema and seed from an ACS
     * that is empty or nearly so. A corpus large enough to need more than this
     * is a reason to raise it deliberately rather than to wait longer by
     * default.
     */
    public static final Duration TIMEOUT_READY_DEFAULT = Duration.ofMinutes(3);

    /**
     * The prefix IS the database name, which PostgreSQL folds to lower case
     * when it is unquoted. Rejected here rather than at CREATE DATABASE, so
     * the complaint names the prefix where it was given.
     */
    private static final Pattern PAT_PREFIX = Pattern.compile("[a-z_][a-z0-9_]{0,40}");


    public PqsSpec {
        // The binary's DECLARED line wins over anything a caller passes. It is
        // the field PqsInstallation carries for exactly this question, and two
        // answers to one question is how a stale one survives.
        if (installation != null)
            strCantonLine = installation.strCantonLine();
        else if (strCantonLine != null && strCantonLine.isBlank())
            strCantonLine = null;
        if (strPrefix == null || strPrefix.isBlank())
            strPrefix = STR_DEFAULT_PREFIX;
        if (!PAT_PREFIX.matcher(strPrefix).matches())
            throw new IllegalArgumentException("not a usable database prefix (lower case, "
                    + "letters, digits and underscore, not starting with a digit): " + strPrefix);
        if (strSchema == null || strSchema.isBlank())
            strSchema = ScribeProcess.STR_DEFAULT_SCHEMA;
        if (nHeapMb < 0)
            throw new IllegalArgumentException("heap must not be negative: " + nHeapMb);
        if (timeoutReady == null)
            timeoutReady = TIMEOUT_READY_DEFAULT;
        if (timeoutReady.isZero() || timeoutReady.isNegative())
            throw new IllegalArgumentException("the ready timeout must be positive");
    }


    /**
     * The mock with NO generation, which is the one shape that cannot render a
     * correct command on both columns. Prefer {@link #ofMock(String)}: every
     * caller in the tree resolves from a Canton installation and therefore
     * knows the line.
     *
     * @return a spec that provisions the schema in-process and starts nothing
     */
    public static PqsSpec ofMock() {
        return new PqsSpec(null, null, null, null, null, null, 0, null);
    }


    /**
     * @param strCantonLine the Canton minor line the mock stands in front of,
     *        e.g. "2.10"
     * @return a spec that provisions the schema in-process, starts nothing,
     *         and renders the command the way that line's scribe takes it
     */
    public static PqsSpec ofMock(String strCantonLine) {
        return new PqsSpec(null, strCantonLine, null, null, null, null, 0, null);
    }


    /**
     * @param installation the scribe binary to run
     * @return a spec that runs it
     */
    public static PqsSpec of(PqsInstallation installation) {
        if (installation == null)
            throw new IllegalArgumentException("an installation is required; use ofMock()");
        return new PqsSpec(installation, null, null, null, null, null, 0, null);
    }


    /**
     * Whichever scribe this machine has for the Canton being started, and the
     * mock when it has none.
     *
     * The mock is the NORMAL answer for every 2.x line - PQS there needs a Daml
     * Enterprise entitlement - so a caller using this gets a stack that starts
     * everywhere and a run that says which half it measured.
     *
     * @param installation the Canton whose line to resolve against
     * @return a real spec when the line has a staged binary, else the mock
     */
    public static PqsSpec resolveFor(CantonInstallation installation) {
        if (installation == null)
            throw new IllegalArgumentException("an installation is required");
        return resolveForLine(installation.line());
    }


    /**
     * @param strCantonLine a Canton minor line such as "3.5"
     * @return a real spec when the line has a staged binary, else the mock,
     *         and either way one that knows which generation it faces
     */
    public static PqsSpec resolveForLine(String strCantonLine) {
        Optional<PqsInstallation> optPqs =
                PqsInstallations.ofDefaults().forCantonLine(strCantonLine);
        return optPqs.isPresent() ? of(optPqs.get()) : ofMock(strCantonLine);
    }


    public PqsSpec withPrefix(String strPrefixNew) {
        return new PqsSpec(installation, strCantonLine, strPrefixNew, strSchema, strToken, oauth,
                nHeapMb, timeoutReady);
    }


    public PqsSpec withSchema(String strSchemaNew) {
        return new PqsSpec(installation, strCantonLine, strPrefix, strSchemaNew, strToken, oauth,
                nHeapMb, timeoutReady);
    }


    public PqsSpec withToken(String strTokenNew) {
        return new PqsSpec(installation, strCantonLine, strPrefix, strSchema, strTokenNew, oauth,
                nHeapMb, timeoutReady);
    }


    /**
     * The credentials scribe re-mints from. THEY WIN over any static token the
     * spec also carries: the token stays because the mock path and the live
     * test mint one directly, and a spec that has both is asked for the thing
     * that can renew.
     *
     * @param oauthNew what to mint with, or null to fall back to the token
     * @return a spec carrying them
     */
    public PqsSpec withOAuth(PqsOAuth oauthNew) {
        return new PqsSpec(installation, strCantonLine, strPrefix, strSchema, strToken, oauthNew,
                nHeapMb, timeoutReady);
    }


    public PqsSpec withHeapMb(int nHeapMbNew) {
        return new PqsSpec(installation, strCantonLine, strPrefix, strSchema, strToken, oauth,
                nHeapMbNew, timeoutReady);
    }


    public PqsSpec withTimeout(Duration timeoutReadyNew) {
        return new PqsSpec(installation, strCantonLine, strPrefix, strSchema, strToken, oauth,
                nHeapMb, timeoutReadyNew);
    }


    /**
     * Only usable on a mock. A real spec takes its line from the binary, and
     * overriding that would produce a spec asserting a generation its own jar
     * contradicts.
     *
     * @param strCantonLineNew the line the mock stands in front of
     * @return a spec carrying it
     * @throws IllegalStateException when a binary already answered the question
     */
    public PqsSpec withCantonLine(String strCantonLineNew) {
        if (installation != null) {
            throw new IllegalStateException("the generation comes from the binary's declared"
                    + " line and is not overridable: " + installation);
        }
        return new PqsSpec(null, strCantonLineNew, strPrefix, strSchema, strToken, oauth, nHeapMb,
                timeoutReady);
    }


    /**
     * @return whether a binary was resolved at all, and therefore whether this
     *         spec launches a process or the in-process mock
     */
    public boolean isReal() {
        return installation != null;
    }


    /**
     * There are THREE states, not two: no binary, a stand-in binary, and vendor
     * scribe. {@link #isReal()} separates the first from the other two - it
     * decides whether a process starts - and this separates the third from the
     * second, which is what decides whether a green result is evidence about
     * PQS or only about the wiring that feeds it.
     *
     * @return whether the resolved binary is vendor scribe
     */
    public boolean isVendor() {
        return isReal() && installation.isVendor();
    }


    /**
     * @return whether anything said which generation this PQS faces; false only
     *         for a mock built by {@link #ofMock()}
     */
    public boolean isGenerationKnown() {
        return installation != null || strCantonLine != null;
    }


    /**
     * The generation, which decides whether the schema may be named on the
     * command line at all. It is answered for the mock as well as for a
     * real binary, which is the whole reason the line is a component here.
     *
     * @return whether this PQS faces a Canton 2.x participant; false when
     *         nothing said, which is the 3.x-shaped default
     */
    public boolean isCanton2x() {
        return installation != null ? installation.isCanton2x()
                : PqsInstallation.isCanton2xLine(strCantonLine);
    }


    /**
     * @return the database this PQS writes into; the prefix, the same for
     *         every version and for the mock
     */
    public String databaseName() {
        return isReal() ? installation.databaseName(strPrefix) : strPrefix;
    }


    /**
     * Which of the three ledger-api credentials this spec renders.
     *
     * NAMED IN THE LOG, because "OAuth" alone covers both a string that stops
     * working after 240 s on a sub-floor participant and an endpoint
     * that cannot. A run that ended at four minutes and one that never
     * authenticated read the same otherwise.
     *
     * @return the state in force, never the secret in it
     */
    public String strAuth() {
        if (oauth != null)
            return oauth.describe();
        if (strToken != null && !strToken.isBlank())
            return "OAuth, STATIC token - nothing renews it";
        return "NoAuth";
    }


    /** Safe for a log line: what will run and where it writes, never the token. */
    public String describe() {
        if (!isReal()) {
            String strLine = strCantonLine == null ? "generation UNKNOWN"
                    : "canton " + strCantonLine;
            // The mock's own table lands in the schema it created. The command
            // it PRINTS is a different claim, and on 2.x it names no schema,
            // so a paste of it puts scribe's tables in `public`. Both
            // are stated, because stating only the first is how `db.pqs` gets
            // read later as evidence about the 2.x column.
            String strFlag = isCanton2x()
                    ? "; the rendered command names NO schema - inert on 2.x, so a real"
                            + " scribe run from it lands in public"
                    : "";
            return "mock (no scribe binary resolved, " + strLine + ") -> " + databaseName() + "."
                    + strSchema + strFlag + "; ledger auth " + strAuth();
        }

        String strKind = isVendor() ? "scribe " : "STAND-IN scribe ";
        // The schema is only part of the destination where the flag naming it
        // is honoured. Printing `db.pqs` for a 2.x run would be the line a
        // later reader takes as evidence that isolation happened.
        String strWhere = installation.isCanton2x()
                ? databaseName() + " (schema NOT named: inert on the 2.x column)"
                : databaseName() + "." + strSchema;
        return strKind + installation.version() + ", schema revision "
                + installation.strSchemaRevision() + " -> " + strWhere + "; ledger auth "
                + strAuth();
    }


    @Override
    public String toString() {
        return "pqs: " + describe();
    }
}
