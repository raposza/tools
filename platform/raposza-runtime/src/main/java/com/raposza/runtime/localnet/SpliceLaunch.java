// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a Splice bundle is started on this machine, and what has to be on disk
 * before it can be - `todo.md` W-14.
 *
 * <h2>The bundle carries one launcher, and it is a Unix shell script</h2>
 *
 * `splice-node/bin/` holds exactly one file, `splice-node`, a `#!/bin/sh`
 * script - listed from `0.8.3_splice-node.tar.gz`, D-920. Windows cannot
 * execute it, so until this class every Splice the released Sandbox installed
 * on Windows failed its start check and was removed again.
 *
 * <h2>On Windows the JVM is started directly, as the script itself does</h2>
 *
 * The script's own last statement is `exec java -XX:+CrashOnOutOfMemoryError
 * -Dprog.home=$PROG_HOME -Dprog.version=$PROG_VERSION -cp
 * $PROG_HOME/lib/splice-node.jar org.lfdecentralizedtrust.splice.SpliceApp
 * "$@"`, read off the bundle - D-601. That is what is run there.
 * `prog.revision` is a per-release git sha baked into the script and nothing
 * on this side can derive it, so it is not set; LocalNetND ran on Windows
 * without it - D-607, D-611.
 *
 * <h2>Linux is unchanged</h2>
 *
 * There the script is run as the vendor ships it, which is the path every
 * LocalNetND measurement on Linux was taken on.
 *
 * <h2>What counts as installed follows what is run</h2>
 *
 * `bin/splice-node` executable on Linux; `lib/splice-node.jar` a regular file
 * on Windows, because nothing there executes the script and
 * `Files.isExecutable` on NTFS answers about an ACL rather than about whether
 * Windows can start the file. The Splice box, the install's own check and the
 * runner's refusal all ask this one question, so a version the box offers is
 * one the runner will take.
 *
 * Author Claude/bentzn
 */
public final class SpliceLaunch {

    /** The jar the script runs, relative to the bundle - D-601. */
    public static final String STR_JAR = "lib/splice-node.jar";

    /** The main class the script names - D-601. */
    public static final String STR_MAIN = "org.lfdecentralizedtrust.splice.SpliceApp";

    /** `PROG_VERSION` in the script is the jar's file name, literally - D-601. */
    public static final String STR_PROG_VERSION = "splice-node.jar";

    /** The script's one JVM option - D-601. */
    public static final String STR_OPT_OOM = "-XX:+CrashOnOutOfMemoryError";

    private SpliceLaunch() {
    }


    /**
     * @return whether this JVM runs on Windows - the rule
     *         `RaposzaSettings.dirHomeDefault` already uses
     */
    public static boolean flagWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }


    /**
     * @param dirBundle a `splice-node` directory
     * @return the file whose presence makes it installed on this machine
     */
    public static Path fileRequired(Path dirBundle) {
        return fileRequired(dirBundle, flagWindows());
    }


    /**
     * @param dirBundle a `splice-node` directory
     * @param flagWindowsHere whether the Windows rule applies
     * @return `lib/splice-node.jar` on Windows, `bin/splice-node` elsewhere
     */
    static Path fileRequired(Path dirBundle, boolean flagWindowsHere) {
        return flagWindowsHere ? dirBundle.resolve(STR_JAR)
                : dirBundle.resolve("bin").resolve(SpliceInstallations.STR_BUNDLE);
    }


    /**
     * @param dirBundle a `splice-node` directory
     * @return whether this machine can start it
     */
    public static boolean isInstalled(Path dirBundle) {
        return isInstalled(dirBundle, flagWindows());
    }


    /**
     * @param dirBundle a `splice-node` directory
     * @param flagWindowsHere whether the Windows rule applies
     * @return whether the file {@link #fileRequired} names is usable
     */
    static boolean isInstalled(Path dirBundle, boolean flagWindowsHere) {
        Path file = fileRequired(dirBundle, flagWindowsHere);
        return flagWindowsHere ? Files.isRegularFile(file) : Files.isExecutable(file);
    }


    /**
     * The command up to and excluding Splice's own arguments - `daemon` and
     * what follows are the caller's.
     *
     * @param dirBundle a `splice-node` directory
     * @param lstClasspathFirst entries put AHEAD of the jar on Windows, so a
     *        class there answers first; ignored on Linux, where the script
     *        assembles its own classpath
     * @return the command's head
     */
    public static List<String> lstCommand(Path dirBundle, List<Path> lstClasspathFirst) {
        return lstCommand(dirBundle, lstClasspathFirst, flagWindows(),
                Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }


    /**
     * @param dirBundle a `splice-node` directory
     * @param lstClasspathFirst entries ahead of the jar, Windows only
     * @param flagWindowsHere whether the Windows form applies
     * @param strJava the java launcher the Windows form starts
     * @return the command's head
     */
    static List<String> lstCommand(Path dirBundle, List<Path> lstClasspathFirst,
            boolean flagWindowsHere, String strJava) {
        Path dirAbs = dirBundle.toAbsolutePath().normalize();
        List<String> lstOut = new ArrayList<>();
        if (!flagWindowsHere) {
            lstOut.add(fileRequired(dirAbs, false).toString());
            return lstOut;
        }

        StringBuilder bldCp = new StringBuilder();
        if (lstClasspathFirst != null) {
            for (Path pathEntry : lstClasspathFirst) {
                bldCp.append(pathEntry.toAbsolutePath().normalize()).append(File.pathSeparator);
            }
        }
        bldCp.append(dirAbs.resolve(STR_JAR));

        lstOut.add(strJava);
        lstOut.add(STR_OPT_OOM);
        lstOut.add("-Dprog.home=" + dirAbs);
        lstOut.add("-Dprog.version=" + STR_PROG_VERSION);
        lstOut.add("-cp");
        lstOut.add(bldCp.toString());
        lstOut.add(STR_MAIN);
        return lstOut;
    }

}
