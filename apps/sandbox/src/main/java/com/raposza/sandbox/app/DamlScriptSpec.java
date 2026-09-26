// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One `daml script` invocation, as a value rather than as a string built at the
 * point of use.
 *
 * <h2>The command line is BANKED, not composed</h2>
 *
 * Every element below was run before this class existed: the test harness
 * drives exactly this line on every installed version. Nothing here is a
 * flag somebody expected `daml script` to have - a guessed option in a shipped
 * command line is how an unrun invocation becomes cited evidence.
 *
 * <pre>
 * daml script --dar &lt;dar&gt; --script-name &lt;name&gt;
 *             --ledger-host &lt;host&gt; --ledger-port &lt;port&gt;
 *             [--upload-dar yes]
 *             [--access-token-file &lt;file&gt; --user-id &lt;user&gt;]
 * </pre>
 *
 * `--access-token-file` and `--user-id` were added on 2026-08-19 and both are
 * banked like the rest: both are in `script-runner --help` on the 3.4.11
 * assistant. Nothing beside them was taken from that help text.
 *
 * <h2>The two travel together, and the second is not optional</h2>
 *
 * MEASURED. With the token file alone the DAR uploads and the first `submit`
 * is refused:
 *
 * <pre>
 * INVALID_TOKEN(8): The submitted request is missing a user-id: Cannot
 * default user_id field because claims do not specify an user-id.
 * </pre>
 *
 * The runner leaves `user_id` empty and lets the participant fill it in from
 * the token's claims. A USER token carries one; the pinned admin token does
 * not - it is a fixed string Canton compares before it parses anything, so
 * there are no claims to read a user out of. The field therefore has to be
 * stated, and `participant_admin` is the user Canton creates for itself, so it
 * is on every participant without this application provisioning one.
 *
 * The runner's own default is `daml-script`, which no participant has. It is
 * not used here because a user id naming something that does not exist is a
 * second failure waiting behind the first.
 *
 * <h2>The project directory is what selects the SDK, and it is not optional</h2>
 *
 * `daml` on PATH is the ASSISTANT. It resolves which SDK actually runs from the
 * `daml.yaml` of the directory it is invoked in, so the same binary name drives
 * a 2.8 ledger and a 3.5 one. Invoked from anywhere else it silently uses the
 * default SDK, and a 3.x script runner against a 2.x ledger fails in a way that
 * reads as a fault in the fixture - which is why the harness makes every call
 * inside the project and why this record carries the directory rather than
 * letting a caller forget it.
 *
 * <h2>Uploading is OFF by default, because the window already did it</h2>
 *
 * The DARs tab uploads its selection when Start is pressed. `--upload-dar yes`
 * here would be a second route for a package to reach the ledger with no way to
 * tell afterwards which one put it there. It stays available because a script
 * run against a stack started with nothing ticked needs it, and because the
 * option is what was measured.
 *
 * @param dirProject the directory whose `daml.yaml` selects the SDK
 * @param fileDar the DAR the script lives in
 * @param strName the script, as `Module:name`
 * @param strHost where the Ledger API is
 * @param nPortLedger the Ledger API port
 * @param flagUpload whether the run also uploads the DAR
 * @param nSecondsTimeout how long the run may take before it is killed
 * @param fileToken the file holding the bearer, or null against a participant
 *        that checks nothing
 * @param fileInput the file holding the script's argument, or null for a
 *        script that takes none
 * @param fileOutput where the script's RESULT is written, or null to discard
 *        it. A two-phase fixture reads the first half's output here and passes
 *        it to the second half as {@link #fileInput}
 * @param strUserId the ledger user the token speaks for, or null for
 *        {@link #STR_USER_ID_DEFAULT}. It is NOT always the participant's own
 *        administrator: a phase that submits runs as a fixture user whose
 *        rights reach contracts, which admin rights do not
 *
 * Author Claude/bentzn
 */
public record DamlScriptSpec(Path dirProject, Path fileDar, String strName, String strHost,
        int nPortLedger, boolean flagUpload, int nSecondsTimeout, Path fileToken,
        Path fileInput, Path fileOutput, String strUserId) {

    /**
     * The assistant, resolved from PATH. Drives the 2.x line and nothing else.
     */
    public static final String STR_BIN_ASSISTANT = "daml";

    /**
     * The package manager, resolved from PATH. Drives every 3.x project.
     *
     * MEASURED: `dpm script` IS the same binary - it reports itself
     * as `script-runner` and carries every flag this class writes, including
     * `--access-token-file` and `--user-id`. Nothing in the argv below changes
     * with the executable.
     */
    public static final String STR_BIN_DPM = "dpm";

    /** What `daml.yaml` calls the field that selects the toolchain. */
    public static final String STR_KEY_SDK = "sdk-version:";

    /** What `daml.yaml` is called, which is the file that selects the SDK. */
    public static final String STR_FILE_YAML = "daml.yaml";

    public static final String STR_HOST_DEFAULT = "localhost";

    /**
     * The user id a submission carries when a token is presented.
     *
     * Canton's own user, present on every participant. See the type comment:
     * the admin token has no claims to default this from.
     */
    public static final String STR_USER_ID_DEFAULT = "participant_admin";

    /** The pet shop fixture's entry point, and the only one measured. */
    public static final String STR_NAME_DEFAULT = "Main:setup";

    /** Measured against the slowest installed version, with margin. */
    public static final int N_SECONDS_DEFAULT = 600;

    public static final int N_SECONDS_MIN = 5;

    public static final int N_SECONDS_MAX = 7200;

    public static final int N_PORT_MIN = 1;

    public static final int N_PORT_MAX = 65535;


    public DamlScriptSpec {
        if (dirProject == null)
            throw new IllegalArgumentException("a project directory is required");
        if (fileDar == null)
            throw new IllegalArgumentException("a DAR is required");
        if (strName == null || strName.trim().isEmpty())
            throw new IllegalArgumentException("a script name is required, as Module:name");
        if (strHost == null || strHost.trim().isEmpty())
            throw new IllegalArgumentException("a ledger host is required");
        if (nPortLedger < N_PORT_MIN || nPortLedger > N_PORT_MAX)
            throw new IllegalArgumentException("the ledger port is outside " + N_PORT_MIN + "-"
                    + N_PORT_MAX + ": " + nPortLedger);
        if (nSecondsTimeout < N_SECONDS_MIN || nSecondsTimeout > N_SECONDS_MAX) {
            throw new IllegalArgumentException("the timeout is outside " + N_SECONDS_MIN + "-"
                    + N_SECONDS_MAX + " s: " + nSecondsTimeout);
        }
        dirProject = dirProject.toAbsolutePath().normalize();
        fileDar = fileDar.toAbsolutePath().normalize();
        strName = strName.trim();
        strHost = strHost.trim();
        if (fileToken != null)
            fileToken = fileToken.toAbsolutePath().normalize();
        if (fileInput != null)
            fileInput = fileInput.toAbsolutePath().normalize();
        if (fileOutput != null)
            fileOutput = fileOutput.toAbsolutePath().normalize();
        if (strUserId != null && strUserId.trim().isEmpty())
            strUserId = null;
        if (strUserId != null)
            strUserId = strUserId.trim();
    }


    /**
     * The seven-component form, for a caller with no token to present.
     *
     * @param dirProject the directory whose `daml.yaml` selects the SDK
     * @param fileDar the DAR the script lives in
     * @param strName the script, as `Module:name`
     * @param strHost where the Ledger API is
     * @param nPortLedger the Ledger API port
     * @param flagUpload whether the run also uploads the DAR
     * @param nSecondsTimeout how long the run may take
     */
    public DamlScriptSpec(Path dirProject, Path fileDar, String strName, String strHost,
            int nPortLedger, boolean flagUpload, int nSecondsTimeout) {
        this(dirProject, fileDar, strName, strHost, nPortLedger, flagUpload, nSecondsTimeout,
                null, null, null, null);
    }


    /**
     * @param dirProject the directory whose `daml.yaml` selects the SDK
     * @param fileDar the DAR the script lives in
     * @param strName the script, as `Module:name`
     * @param nPortLedger the Ledger API port
     * @return a spec on localhost, not uploading, with the banked timeout
     */
    public static DamlScriptSpec of(Path dirProject, Path fileDar, String strName,
            int nPortLedger) {
        return new DamlScriptSpec(dirProject, fileDar, strName, STR_HOST_DEFAULT, nPortLedger,
                false, N_SECONDS_DEFAULT, null);
    }


    /**
     * @param fileTokenNew the file holding the bearer, or null for none
     * @return the same spec presenting that token
     */
    public DamlScriptSpec withToken(Path fileTokenNew) {
        return new DamlScriptSpec(dirProject, fileDar, strName, strHost, nPortLedger,
                flagUpload, nSecondsTimeout, fileTokenNew, fileInput, fileOutput, strUserId);
    }


    /**
     * @param fileInputNew the file holding the script's argument, or null
     * @return the same spec passing that file
     */
    public DamlScriptSpec withInput(Path fileInputNew) {
        return new DamlScriptSpec(dirProject, fileDar, strName, strHost, nPortLedger,
                flagUpload, nSecondsTimeout, fileToken, fileInputNew, fileOutput, strUserId);
    }


    /**
     * @param fileOutputNew where the script's result is written, or null
     * @return the same spec writing its result there
     */
    public DamlScriptSpec withOutput(Path fileOutputNew) {
        return new DamlScriptSpec(dirProject, fileDar, strName, strHost, nPortLedger,
                flagUpload, nSecondsTimeout, fileToken, fileInput, fileOutputNew, strUserId);
    }


    /**
     * @param strUserIdNew the ledger user the token speaks for, or null for
     *        the default
     * @return the same spec naming that user
     */
    public DamlScriptSpec withUserId(String strUserIdNew) {
        return new DamlScriptSpec(dirProject, fileDar, strName, strHost, nPortLedger,
                flagUpload, nSecondsTimeout, fileToken, fileInput, fileOutput, strUserIdNew);
    }


    /**
     * @param strNameNew the script, as `Module:name`
     * @return the same spec running that script
     */
    public DamlScriptSpec withName(String strNameNew) {
        return new DamlScriptSpec(dirProject, fileDar, strNameNew, strHost, nPortLedger,
                flagUpload, nSecondsTimeout, fileToken, fileInput, fileOutput, strUserId);
    }


    /**
     * The eight-component form, for a caller with a token and no argument.
     *
     * @param dirProject the directory whose `daml.yaml` selects the SDK
     * @param fileDar the DAR the script lives in
     * @param strName the script, as `Module:name`
     * @param strHost where the Ledger API is
     * @param nPortLedger the Ledger API port
     * @param flagUpload whether the run also uploads the DAR
     * @param nSecondsTimeout how long the run may take
     * @param fileToken the file holding the bearer, or null
     */
    public DamlScriptSpec(Path dirProject, Path fileDar, String strName, String strHost,
            int nPortLedger, boolean flagUpload, int nSecondsTimeout, Path fileToken) {
        this(dirProject, fileDar, strName, strHost, nPortLedger, flagUpload, nSecondsTimeout,
                fileToken, null, null, null);
    }


    /**
     * @return the argv, in the measured order; never null
     */
    /**
     * Which executable can drive this project.
     *
     * THE PROJECT DECIDES, not the caller and not the install. A staging
     * project's `daml.yaml` names the SDK it was built under, and on the 3.x
     * line that is a dpm BUNDLE - `sdk-version: 3.5.5` for Canton 3.5.12.
     * The assistant has no such SDK and would resolve its default
     * instead, which is a 3.5 runner against whatever happens to be installed.
     *
     * The assistant is RETIRED above 2.x, decided 2026-08-21: the vendor
     * removes it in 3.5, and `dpm` covers every 3.x install on this machine.
     * So 2.x reads `daml` and everything else reads `dpm`, including the two
     * versions that also have an assistant tree.
     *
     * An unreadable or absent `daml.yaml` reads `dpm`. The alternative is
     * defaulting to a toolchain that is being removed, which would fail later
     * and further from the cause.
     *
     * @return the executable name; never null
     */
    public String strBin() {
        String strVersion = "";
        try {
            for (String strLine : java.nio.file.Files.readAllLines(fileYaml())) {
                String strTrim = strLine.trim();
                if (!strTrim.startsWith(STR_KEY_SDK))
                    continue;
                strVersion = strTrim.substring(STR_KEY_SDK.length()).trim();
                break;
            }
        }
        catch (java.io.IOException ex) {
            return STR_BIN_DPM;
        }
        return strVersion.startsWith("2.") ? STR_BIN_ASSISTANT : STR_BIN_DPM;
    }


    public List<String> lstCommand() {
        List<String> lstOut = new ArrayList<>();
        lstOut.add(strBin());
        lstOut.add("script");
        lstOut.add("--dar");
        lstOut.add(fileDar.toString());
        lstOut.add("--script-name");
        lstOut.add(strName);
        // WHERE `probes/auth/fixture.sh` PUTS IT - after the script name and
        // before the ledger, which is the only ordering this workspace has
        // run. Omitted entirely for a script that takes no argument, because
        // the runner refuses an input file against a `Script ()`.
        if (fileInput != null) {
            lstOut.add("--input-file");
            lstOut.add(fileInput.toString());
        }
        lstOut.add("--ledger-host");
        lstOut.add(strHost);
        lstOut.add("--ledger-port");
        lstOut.add(String.valueOf(nPortLedger));
        // OMITTED WHEN FALSE rather than written as `--upload-dar no`. Only
        // the `yes` form has been run in this workspace, and the absent option
        // is the assistant's own default.
        if (flagUpload) {
            lstOut.add("--upload-dar");
            lstOut.add("yes");
        }
        // THE FILE, never the token: this argv is printed into a log pane.
        // The user id goes with it and only with it - see the type comment.
        if (fileOutput != null) {
            lstOut.add("--output-file");
            lstOut.add(fileOutput.toString());
        }
        if (fileToken != null) {
            lstOut.add("--access-token-file");
            lstOut.add(fileToken.toString());
            // DPM ONLY. The 2.x assistant has no `--user-id` and rejects
            // it outright, and it is not needed there either. The case that
            // forced the flag is
            // the PINNED ADMIN TOKEN, which carries no claims for the
            // participant to read a user-id from; a token minted with `sub`
            // carries one, and every token presented on the 2.x line is
            // minted.
            if (STR_BIN_DPM.equals(strBin())) {
                lstOut.add("--user-id");
                lstOut.add(strUserId == null ? STR_USER_ID_DEFAULT : strUserId);
            }
        }
        return Collections.unmodifiableList(lstOut);
    }


    /**
     * @return the argv as one line, for a log
     */
    public String strCommandLine() {
        return String.join(" ", lstCommand());
    }


    /**
     * @return the `daml.yaml` this run will resolve its SDK from
     */
    public Path fileYaml() {
        return dirProject.resolve(STR_FILE_YAML);
    }
}
