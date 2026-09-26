// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.Edition;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.settings.RaposzaSettings;
import com.raposza.sandbox.SandboxStack;
import com.raposza.sandbox.process.SandboxLauncher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the headless sandbox was asked to start.
 *
 * Parsing is separated from starting so that the argument surface can be tested
 * without a Canton on the machine. Everything a live run needs beyond this is
 * resolution - which installation, which scribe - and that is not parsing.
 *
 * <h2>What is deliberately not here</h2>
 *
 * No auth. A stack with an authenticated Ledger API needs something to serve a
 * JWKS, and the only thing in this repository that does is
 * `raposza-test-idp`, a TEST-scoped module. Wiring it into a shipped
 * application is a decision about what the product carries, not a flag, so
 * this entry point starts the unauthenticated stack and says so.
 *
 * No `parties.txt` and no init-script directory. Both are named work items and
 * neither is guessed at here.
 *
 * <h2>The launcher is a choice and is not inferred</h2>
 *
 * `--launcher daemon` is what makes a `--data-dir` stack startable twice, and
 * it would be easy to switch to it automatically whenever `--data-dir` is
 * given. It is not switched. The two launchers run the bootstrap in different
 * PROCESSES - the subcommand in a remote console, `daemon` inside the node JVM
 * - and a flag about where the database lives should not decide that. What
 * `--data-dir` does instead is print which launcher it is on and what that
 * means for a second start.
 *
 * @param flagHelp print the usage and stop
 * @param flagList print what is installed and stop
 * @param version the exact Canton version, or null to take the newest of a line
 * @param strLine the Canton minor line to take the newest of; ignored when a
 *        version is given
 * @param edition the required edition, or null for any
 * @param nPortOffset how far to move Canton's own default port block
 * @param nPortPostgres the embedded server's port
 * @param strDbPrefix prefix for the four node databases
 * @param dirWork where overlays, logs and the status file go
 * @param dirData the persistent PostgreSQL cluster, or null for a temporary one
 * @param lstFileDar the DARs to upload at start, in no particular order; never
 *        null, and empty means none
 * @param flagDev run the development protocol version
 * @param flagStaticTime advance time only through the time service
 * @param nHeapMb the Canton JVM heap, or 0 for the JVM default
 * @param launcher which of the two 3.x launchers to start on; never null
 * @param pqs whether to run PQS
 * @param strPartyHint a party hint for the bootstrap, or null
 * @param strUserId a ledger user for the bootstrap, or null
 * @param flagPing ping the participant from itself once it is up
 * @param timeoutReady how long to wait for the stack to serve
 *
 * Author Claude/bentzn
 */
public record SandboxOptions(boolean flagHelp, boolean flagList, VersionId version, String strLine,
        Edition edition, int nPortOffset, int nPortPostgres, String strDbPrefix, Path dirWork,
        Path dirData, List<Path> lstFileDar, boolean flagDev, boolean flagStaticTime, int nHeapMb,
        SandboxLauncher launcher, PqsMode pqs, String strPartyHint, String strUserId,
        boolean flagPing, Duration timeoutReady) {

    /** Whether the stack runs PQS. */
    public enum PqsMode {

        /** None. The default: PQS starts when it is asked for. */
        OFF,

        /**
         * PQS, resolved for the Canton line by `PqsSpec.resolveFor`.
         *
         * ONE MODE, not a choice between binaries. Which binary serves a line
         * is a property of the machine rather than a question for the caller,
         * and every one of them is PQS as far as anything above here is
         * concerned.
         */
        ON
    }

    /**
     * The line taken when no version and no line were given. 3.5 rather than
     * the newest installed of anything: 3.4 and 2.x are supported to different
     * degrees, and a default that moved with what a machine happens to have
     * would make two developers' `sandbox` mean different things.
     */
    public static final String STR_DEFAULT_LINE = RaposzaSettings.STR_LINE_DEFAULT;

    /**
     * The subcommand, and it stays the default.
     *
     * Not because it is better - it cannot restart - but because it is the one
     * a start-time `--dar`, the JSON API port and every 2.x-shaped assumption
     * in this application were measured against. `daemon` is opt-in until the
     * headless entry point has been run on it as widely.
     */
    public static final SandboxLauncher LAUNCHER_DEFAULT = SandboxLauncher.SUBCOMMAND;

    public static final Duration TIMEOUT_READY_DEFAULT = Duration.ofMinutes(5);


    public SandboxOptions {
        // Normalised, not refused. An EMPTY prefix is the default now and
        // means the databases are named after their nodes and nothing else.
        strDbPrefix = strDbPrefix == null ? "" : strDbPrefix.trim();
        // A LIST OF FILES, not a directory. The window uploads a SUBSET of a
        // directory, and a subset cannot be expressed as a directory - so the
        // terminal expands `--dars <dir>` into this at parse time and the two
        // entry points say the same thing to the same field. Null becomes
        // empty: `no DARs` and `an empty list of DARs` are one state and having
        // two spellings of it is how one of them gets missed.
        lstFileDar = lstFileDar == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(lstFileDar));
        if (dirWork == null)
            throw new IllegalArgumentException("a work directory is required");
        if (nHeapMb < 0)
            throw new IllegalArgumentException("heap must not be negative: " + nHeapMb);
        if (launcher == null)
            throw new IllegalArgumentException("a launcher is required");
        if (pqs == null)
            throw new IllegalArgumentException("a PQS mode is required");
        if ((strPartyHint == null) != (strUserId == null))
            throw new IllegalArgumentException(
                    "--party and --user go together: the console allocates the party and creates"
                            + " the user in one script");
    }


    /**
     * @return the run-directory ROOT, which is global; the per-version
     *         directory under it is
     *         {@link com.raposza.sandbox.gui.SandboxProfile#dirRunDefault}
     */
    public static Path dirWorkDefault() {
        return RaposzaSettings.current().dirSandbox();
    }


    /**
     * @return the line a start takes when none was given
     */
    public static String strLineDefault() {
        return RaposzaSettings.current().strLine();
    }


    public static SandboxOptions ofDefaults() {
        return new SandboxOptions(false, false, null, strLineDefault(), null, 0,
                RaposzaSettings.current().nPortPostgres(), SandboxStack.STR_DEFAULT_PREFIX,
                dirWorkDefault(), null, List.of(), false, false, 0, LAUNCHER_DEFAULT, PqsMode.OFF,
                null, null, false, TIMEOUT_READY_DEFAULT);
    }


    /**
     * @return whether the cluster outlives the process
     */
    public boolean isPersistent() {
        return dirData != null;
    }


    /**
     * @return whether this stack runs on `daemon` and can therefore be started
     *         again against a kept cluster
     */
    public boolean isDaemon() {
        return launcher == SandboxLauncher.DAEMON;
    }


    /**
     * @return whether a console script has anything to do beyond writing the
     *         participant id
     */
    public boolean flagProvisions() {
        return strUserId != null;
    }


    /**
     * @param arrArg the command line
     * @return what it asks for
     * @throws IllegalArgumentException naming the argument that is wrong, so
     *         the message is about the argument and not about the parser
     */
    public static SandboxOptions parse(String[] arrArg) {
        if (arrArg == null)
            throw new IllegalArgumentException("no arguments");

        boolean flagHelp = false;
        boolean flagList = false;
        VersionId version = null;
        String strLine = null;
        Edition edition = null;
        int nPortOffset = 0;
        int nPortPostgres = RaposzaSettings.current().nPortPostgres();
        String strDbPrefix = SandboxStack.STR_DEFAULT_PREFIX;
        Path dirWork = dirWorkDefault();
        Path dirData = null;
        List<Path> lstFileDar = new ArrayList<>();
        boolean flagDev = false;
        boolean flagStaticTime = false;
        int nHeapMb = 0;
        SandboxLauncher launcher = LAUNCHER_DEFAULT;
        PqsMode pqs = PqsMode.OFF;
        String strPartyHint = null;
        String strUserId = null;
        boolean flagPing = false;
        Duration timeoutReady = TIMEOUT_READY_DEFAULT;

        int idx = 0;
        while (idx < arrArg.length) {
            String strArg = arrArg[idx];
            switch (strArg) {
                case "--help":
                case "-h":
                    flagHelp = true;
                    idx++;
                    break;
                case "--list":
                    flagList = true;
                    idx++;
                    break;
                case "--canton":
                    version = parseVersion(value(arrArg, idx));
                    idx += 2;
                    break;
                case "--line":
                    strLine = value(arrArg, idx);
                    idx += 2;
                    break;
                case "--edition":
                    edition = parseEdition(value(arrArg, idx));
                    idx += 2;
                    break;
                case "--port-offset":
                    nPortOffset = parseInt(value(arrArg, idx), strArg);
                    idx += 2;
                    break;
                case "--pg-port":
                    nPortPostgres = parseInt(value(arrArg, idx), strArg);
                    idx += 2;
                    break;
                case "--db-prefix":
                    strDbPrefix = value(arrArg, idx);
                    idx += 2;
                    break;
                case "--work-dir":
                    dirWork = Paths.get(value(arrArg, idx));
                    idx += 2;
                    break;
                case "--data-dir":
                    dirData = Paths.get(value(arrArg, idx));
                    idx += 2;
                    break;
                case "--dars":
                    // EXPANDED HERE, not carried as a directory. The record
                    // holds files, so the one place a directory turns into a
                    // list is this line - and a directory that is not there is
                    // a usage error named after the argument rather than a
                    // start that fails a minute later.
                    lstFileDar.addAll(lstDarsIn(Paths.get(value(arrArg, idx))));
                    idx += 2;
                    break;
                case "--dar":
                    lstFileDar.add(requireDar(Paths.get(value(arrArg, idx))));
                    idx += 2;
                    break;
                case "--dev":
                    flagDev = true;
                    idx++;
                    break;
                case "--static-time":
                    flagStaticTime = true;
                    idx++;
                    break;
                case "--heap-mb":
                    nHeapMb = parseInt(value(arrArg, idx), strArg);
                    idx += 2;
                    break;
                case "--launcher":
                    launcher = parseLauncher(value(arrArg, idx));
                    idx += 2;
                    break;
                case "--pqs":
                    pqs = parsePqs(value(arrArg, idx));
                    idx += 2;
                    break;
                case "--party":
                    strPartyHint = value(arrArg, idx);
                    idx += 2;
                    break;
                case "--user":
                    strUserId = value(arrArg, idx);
                    idx += 2;
                    break;
                case "--ping":
                    flagPing = true;
                    idx++;
                    break;
                case "--timeout":
                    timeoutReady = parseSeconds(value(arrArg, idx), strArg);
                    idx += 2;
                    break;
                default:
                    throw new IllegalArgumentException("unknown argument: " + strArg);
            }
        }

        // A version and a line together is not an error to guess at: the two
        // can disagree, and picking one silently is how a run measures a
        // Canton nobody asked for.
        if (version != null && strLine != null)
            throw new IllegalArgumentException("--canton and --line are alternatives, not both");
        if (version == null && strLine == null)
            strLine = STR_DEFAULT_LINE;

        return new SandboxOptions(flagHelp, flagList, version, strLine, edition, nPortOffset,
                nPortPostgres, strDbPrefix, dirWork, dirData, lstFileDar, flagDev, flagStaticTime,
                nHeapMb, launcher, pqs, strPartyHint, strUserId, flagPing, timeoutReady);
    }


    /**
     * @param dirDars a directory of `*.dar` files
     * @return every DAR in it, sorted by file name - the same order
     *         {@link com.raposza.canton.dar.DarCatalog} uploads in,
     *         because a directory listing is filesystem order and differs
     *         between two machines holding identical files
     * @throws IllegalArgumentException when it is not a directory
     */
    private static List<Path> lstDarsIn(Path dirDars) {
        if (!Files.isDirectory(dirDars))
            throw new IllegalArgumentException("--dars is not a directory: " + dirDars);

        List<Path> lstOut = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirDars, "*.dar")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file))
                    lstOut.add(file);
            }
        }
        catch (IOException ex) {
            throw new IllegalArgumentException("--dars could not be listed: " + dirDars + ": "
                    + ex.getMessage(), ex);
        }
        lstOut.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return lstOut;
    }


    /**
     * @param fileDar one DAR
     * @return the same path
     * @throws IllegalArgumentException when it is not a file. Refused HERE
     *         rather than at start: the argument is wrong, and the exit code
     *         for a wrong argument is not the exit code for a failed start
     */
    private static Path requireDar(Path fileDar) {
        if (!Files.isRegularFile(fileDar))
            throw new IllegalArgumentException("--dar is not a file: " + fileDar);
        return fileDar;
    }


    private static String value(String[] arrArg, int idx) {
        if (idx + 1 >= arrArg.length)
            throw new IllegalArgumentException(arrArg[idx] + " needs a value");
        String strValue = arrArg[idx + 1];
        if (strValue.startsWith("--"))
            throw new IllegalArgumentException(
                    arrArg[idx] + " needs a value, and " + strValue + " is the next option");
        return strValue;
    }


    /**
     * `VersionId.parse` throws an InstallException, which is the right type
     * when an installation is being read and the wrong one for a typo on a
     * command line: everything the parser rejects should arrive at the caller
     * as the same kind of failure and print the same usage line.
     *
     * @param strValue what was given for --canton
     * @return the version
     */
    private static VersionId parseVersion(String strValue) {
        return VersionId.tryParse(strValue).orElseThrow(() -> new IllegalArgumentException(
                "--canton needs a three-part version such as 3.5.11: " + strValue));
    }


    private static int parseInt(String strValue, String strWhat) {
        try {
            return Integer.parseInt(strValue);
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(strWhat + " needs a whole number: " + strValue, ex);
        }
    }


    private static Duration parseSeconds(String strValue, String strWhat) {
        int nSeconds = parseInt(strValue, strWhat);
        if (nSeconds < 1)
            throw new IllegalArgumentException(strWhat + " needs a positive number of seconds: "
                    + strValue);
        return Duration.ofSeconds(nSeconds);
    }


    private static Edition parseEdition(String strValue) {
        String strNormal = strValue.toLowerCase().replace('-', '_');
        if ("any".equals(strNormal))
            return null;
        try {
            return Edition.valueOf(strNormal.toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "--edition takes open-source, enterprise, unknown or any: " + strValue, ex);
        }
    }


    private static SandboxLauncher parseLauncher(String strValue) {
        try {
            return SandboxLauncher.valueOf(strValue.toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("--launcher takes subcommand or daemon: "
                    + strValue, ex);
        }
    }


    private static PqsMode parsePqs(String strValue) {
        try {
            return PqsMode.valueOf(strValue.toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("--pqs takes off or on: "
                    + strValue, ex);
        }
    }


    /**
     * @return the usage text, which names every argument this parser accepts
     */
    public static String usage() {
        return """
                raposza sandbox - a local Canton 2.x or 3.x stack

                  ./run-sandbox.sh [options]

                Which Canton
                  --canton <version>     exact version, e.g. 3.5.11
                  --line <line>          newest installed of a minor line (default 3.5)
                  --edition <edition>    open-source, enterprise, unknown or any (default any)
                  --list                 print what is installed and stop

                Where it listens
                  --port-offset <n>      move Canton's 6864-6869 block by n (default 0)
                  --pg-port <n>          the embedded PostgreSQL port (default 32101)

                What it keeps
                  --work-dir <path>      overlays, logs, status file (default ~/.raposza/sandbox)
                  --data-dir <path>      a PostgreSQL cluster that outlives the process
                  --db-prefix <name>     prefix for the node databases (default none)

                What it loads
                  --dars <dir>           upload every *.dar in a directory at start
                  --dar <file>           upload one *.dar; repeatable, and combines
                                         with --dars
                  --party <hint>         allocate a party (needs --user)
                  --user <id>            create a ledger user (needs --party)
                  --ping                 ping the participant from itself once it is up

                How it runs
                  --launcher <name>      subcommand or daemon (default subcommand)
                  --dev                  the development protocol version
                  --static-time          time advances only through the time service
                  --heap-mb <n>          Canton's JVM heap
                  --pqs <mode>           off or on (default off)
                  --timeout <s>          how long to wait for the stack (default 300)
                  --cli                  start here, in the terminal. Without it the
                                         window opens and every other argument
                                         pre-fills its form
                  --fixture              build the Aviation fixture for the selected
                                         Canton, upload it at start and run its setup
                                         script; needs a toolchain that builds for
                                         that version
                  --help                 this text

                Terminal only, with --cli
                  --auth <mode>          authenticate the Ledger API, e.g. JWKS; starts
                                         the local OpenID Provider first
                  --token-shape <shape>  AUDIENCE or SCOPE, with --auth
                  --discovery <port>     publish the discovery document on 127.0.0.1

                Without --auth the Ledger API is UNAUTHENTICATED.

                2.x AND 3.x, and they are different stacks rather than one with a flag.
                3.x is a participant, a sequencer and a mediator; 2.x is a participant
                and a DOMAIN. --launcher, --dev, --static-time, --party, --user and
                --ping belong to the 3.x launchers: on a 2.x install they are REPORTED
                as ignored rather than dropped in silence.

                A --data-dir stack cannot be restarted ON THE SUBCOMMAND: Canton's own
                generated bootstrap re-proposes a topology mapping that already exists
                and exits. --launcher daemon runs a bootstrap that asks first, and a
                stack on it starts again against a kept cluster as the same participant
                with the same party and the same packages.

                --launcher daemon is the newer of the two here and is opt-in for that
                reason. PQS has never been run on it.
                """;
    }
}
