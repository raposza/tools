// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.TokenSource_i;
import com.raposza.api.TypeRegistry_i;
import com.raposza.api.profile.HostProfile;
import com.raposza.caql.AuditLog;
import com.raposza.caql.Binding;
import com.raposza.caql.CaqlException;
import com.raposza.caql.CaqlParser;
import com.raposza.caql.Entry;
import com.raposza.caql.RunConfig;
import com.raposza.caql.Runner;
import com.raposza.caql.Stmt;
import com.raposza.caql.Transcript;
import com.raposza.jwt.ProfileAuthStore;
import com.raposza.lf.LfDecoder;
import com.raposza.render.LineRenderer;
import com.raposza.spi.LedgerClient_i;
import com.raposza.spi.Targets;
import com.raposza.types.FilePackageCache;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A script in, a transcript out, no window.
 *
 * <pre>
 *   workbench --script fixture.caql --transcript out.json [--profile NAME]
 *               [--validate] [--catalogue PATH]
 * </pre>
 *
 * <h2>The transcript path is REQUIRED, and that is a decision</h2>
 *
 * `dql-design.md` sec. 10 says the transcript lives where the run is told. It
 * does not say what happens when it is not told, and the tempting default is to
 * put it somewhere sensible. That is the exact shape the audit rules object
 * to in the
 * audit log: a file the TOOL writes, without being asked, that may contain
 * business data - and a transcript carries the argument twice and the operator's
 * source line as well. So there is no default. Say where it goes.
 *
 * The AUDIT log is the opposite case and needs no argument: it is the file the
 * tool is supposed to keep, it never carries a payload, and it lives in one
 * place.
 *
 * <h2>The exit code is the answer</h2>
 *
 * Headless means something else reads this. 0 when every statement got to a
 * state the run could continue past, 1 otherwise, 2 when the script or the
 * arguments could not be read at all. A caller must be able to tell "the
 * fixture did not apply" from "you invoked me wrongly".
 *
 * Author Claude/bentzn
 */
public final class CaqlRunApp {

    /** Every statement reached a state the run could continue past. */
    public static final int CODE_OK = 0;

    /** The run stopped: a rejection, a local failure, an unobserved outcome. */
    public static final int CODE_FAILED = 1;

    /** Nothing ran: bad arguments, missing script, unreadable catalogue. */
    public static final int CODE_UNUSABLE = 2;


    private CaqlRunApp() {
    }


    /**
     * @param arrArg the switches above
     * @return the exit code, so a test can call this without ending the JVM
     */
    public static int run(String[] arrArg) {
        Args args;
        try {
            args = Args.of(arrArg);
        }
        catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.err.println(strUsage());
            return CODE_UNUSABLE;
        }

        String strScript;
        List<Stmt> lstStmt;
        try {
            strScript = Files.readString(args.fileScript, StandardCharsets.UTF_8);
            // Parsed BEFORE connecting. A script with a syntax error must not
            // cost a connection, and must not leave a participant half changed
            // because statement 9 would not have parsed.
            lstStmt = CaqlParser.parse(strScript);
        }
        catch (CaqlException ex) {
            System.err.println(ex.getMessage());
            return CODE_UNUSABLE;
        }
        catch (Exception ex) {
            System.err.println("could not read " + args.fileScript.toAbsolutePath() + ": " + ex);
            return CODE_UNUSABLE;
        }

        HostProfile profile;
        try {
            profile = profile(args);
        }
        catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            return CODE_UNUSABLE;
        }

        return execute(args, profile, strScript, lstStmt);
    }


    private static int execute(Args args, HostProfile profile, String strScript,
            List<Stmt> lstStmt) {
        Path dirHome = Path.of(System.getProperty("user.home"));
        TokenSource_i source = ProfileAuthStore.sourceFor(
                ProfileAuthStore.load(ProfileAuthStore.fileFor(dirHome, profile.nameDisplay())),
                profile);

        // The generation is whichever canton-target-* module is on the
        // classpath, and there can be only one of those - the two Ledger API
        // binding jars collide on 306 class names. This class names none.
        try (LedgerClient_i client = Targets.only().connect(profile, source)) {
            TypeRegistry_i registry = RegistryLoader.load(client, new FilePackageCache(),
                    new LfDecoder());

            Runner runner = new Runner(client, registry,
                    args.flagValidate ? RunConfig.ofValidate() : RunConfig.ofRun(),
                    new AuditLog(), profile.nameDisplay());

            Transcript transcript = runner.run(strScript, lstStmt);
            write(args.fileTranscript, transcript);
            report(transcript);

            return transcript.flagOk() ? CODE_OK : CODE_FAILED;
        }
        catch (RuntimeException ex) {
            System.err.println("the run could not start: " + ex.getMessage());
            return CODE_UNUSABLE;
        }
    }


    /**
     * The transcript is written EVEN WHEN THE RUN FAILED, and especially then:
     * a failed run is when somebody needs to know what was actually sent.
     */
    private static void write(Path fileTranscript, Transcript transcript) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Path dirOut = fileTranscript.toAbsolutePath().getParent();
            if (dirOut != null)
                Files.createDirectories(dirOut);

            Files.writeString(fileTranscript,
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(transcript.json(mapper)) + "\n",
                    StandardCharsets.UTF_8);
        }
        catch (Exception ex) {
            // Not fatal to the RESULT, which the exit code already carries, but
            // loud: a run whose transcript was lost is a run nobody can review.
            System.err.println("WARNING: could not write the transcript to "
                    + fileTranscript.toAbsolutePath() + ": " + ex);
        }
    }


    private static void report(Transcript transcript) {
        for (Entry entry : transcript.lstEntry()) {
            System.out.println(String.format("line %-4d %-15s %s", entry.numLine(),
                    entry.status().strJson(), entry.strError().orElse("")).trim());
        }

        // The bindings ON THE CONSOLE, not only in the transcript file. Half of
        // wanting a print statement is wanting to see what a name holds without
        // opening a JSON document, and the run already knows.
        if (!transcript.lstBinding().isEmpty()) {
            System.out.println();
            for (Binding binding : transcript.lstBinding()) {
                System.out.println(String.format("  $%-12s %s", binding.name(),
                        LineRenderer.line(binding.value())));
            }
            System.out.println();
        }

        System.out.println(transcript.flagOk() ? "caql: OK" : "caql: FAILED");
    }


    private static HostProfile profile(Args args) {
        Path dirHome = Path.of(System.getProperty("user.home"));
        Path fileCatalogue = args.fileCatalogue == null
                ? Catalogue.resolve(new String[0], dirHome)
                : args.fileCatalogue;

        List<HostProfile> lstProfile = Catalogue.read(fileCatalogue);
        if (lstProfile.isEmpty())
            throw new IllegalStateException("no participants in " + fileCatalogue);

        if (args.nameProfile == null) {
            if (lstProfile.size() > 1) {
                // Picking the first would submit against whichever participant
                // happens to head the file. Name it.
                StringBuilder bld = new StringBuilder("--profile is required: "
                        + fileCatalogue + " lists " + lstProfile.size() + " participants");
                for (HostProfile each : lstProfile) {
                    bld.append("\n  ").append(each.nameDisplay());
                }
                throw new IllegalStateException(bld.toString());
            }
            return lstProfile.get(0);
        }

        for (HostProfile each : lstProfile) {
            if (each.nameDisplay().equalsIgnoreCase(args.nameProfile))
                return each;
        }
        throw new IllegalStateException("no participant named '" + args.nameProfile + "' in "
                + fileCatalogue);
    }


    private static String strUsage() {
        return "usage: workbench --script FILE --transcript FILE"
                + " [--profile NAME] [--validate] [--catalogue FILE]"
                + "\n\n  --transcript has no default ON PURPOSE: it carries the payloads"
                + "\n  the script wrote, so the tool does not choose where it lands.";
    }


    /** Parsed switches. */
    private static final class Args {

        private Path fileScript;
        private Path fileTranscript;
        private Path fileCatalogue;
        private String nameProfile;
        private boolean flagValidate;


        static Args of(String[] arrArg) {
            Args args = new Args();

            for (int cntLoop = 0; cntLoop < arrArg.length; cntLoop++) {
                switch (arrArg[cntLoop]) {
                    case "--script":
                        args.fileScript = Path.of(value(arrArg, ++cntLoop, "--script"));
                        break;

                    case "--transcript":
                        args.fileTranscript = Path.of(value(arrArg, ++cntLoop, "--transcript"));
                        break;

                    case "--catalogue":
                        args.fileCatalogue = Path.of(value(arrArg, ++cntLoop, "--catalogue"));
                        break;

                    case "--profile":
                        args.nameProfile = value(arrArg, ++cntLoop, "--profile");
                        break;

                    case "--validate":
                        args.flagValidate = true;
                        break;

                    default:
                        throw new IllegalArgumentException("unknown option '" + arrArg[cntLoop]
                                + "'");
                }
            }

            if (args.fileScript == null)
                throw new IllegalArgumentException("--script is required");
            if (!Files.isRegularFile(args.fileScript)) {
                throw new IllegalArgumentException("no such script: "
                        + args.fileScript.toAbsolutePath());
            }
            if (args.fileTranscript == null)
                throw new IllegalArgumentException("--transcript is required");

            return args;
        }


        private static String value(String[] arrArg, int numPos, String strOption) {
            if (numPos >= arrArg.length)
                throw new IllegalArgumentException(strOption + " needs a value");
            return arrArg[numPos];
        }

    }

}
