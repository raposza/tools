// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.pqs.ScribeProcess;
import com.raposza.canton.process.CantonProcess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * What a process was started with, as text a reader can copy.
 *
 * Pure formatting over a process that is already running: no Swing, no window
 * state, nothing to construct. That is what made it worth taking out of
 * `SandboxWindow` ahead of anything else there - it was the one cluster with no
 * thread affinity and no reason to change when the window does.
 *
 * `strFirstLine` also lived in `ScriptsPane`, byte-identical, until that tab
 * went with the pet shop harness on 2026-09-25 - D-850. A Canton refusal is
 * routinely a page, and a status line has room for its first sentence.
 *
 * Author Claude/bentzn
 */
final class LaunchText {

    private LaunchText() {
    }


    /**
     * The participant: its command line, then every configuration overlay it
     * was given, whole.
     *
     * THE FILES AS THEY ARE ON DISK, read at the moment of the call rather than
     * remembered from what was rendered. Canton merges its own bundled
     * configuration with these, and the question a reader has is what was in
     * the file the error message names.
     *
     * @param proc the process
     * @return what to put in the dialog
     */
    static String strOf(CantonProcess proc) {
        StringBuilder sb = new StringBuilder();
        sb.append("command\n");
        sb.append(strShell(proc.lstCommandStarted()));
        sb.append("\n\n");
        sb.append("log\n  ").append(proc.fileLog()).append("\n");

        for (Path fileConf : proc.lstFileConf()) {
            sb.append("\n").append("--- ").append(fileConf).append(" ---\n");
            try {
                sb.append(Files.readString(fileConf));
            }
            catch (IOException ex) {
                sb.append("<unreadable: ").append(ex.getMessage()).append(">\n");
            }
        }
        return sb.toString();
    }


    /**
     * scribe has no configuration file: every setting is on the command line,
     * and the effective configuration it derives from it is the block it prints
     * at the top of its own log.
     *
     * @param proc the scribe process
     * @return what to put in the dialog
     */
    static String strOf(ScribeProcess proc) {
        StringBuilder sb = new StringBuilder();
        sb.append("command\n");
        sb.append("  ").append(proc.commandForShell()).append("\n\n");
        sb.append("configuration\n");
        sb.append("  scribe takes no configuration file. Every setting is above,\n");
        sb.append("  and the configuration it derived from them is printed at the\n");
        sb.append("  top of the PQS log.\n");
        return sb.toString();
    }


    /**
     * @param lstArg a command line as it was handed to the operating system
     * @return it as a shell line, one argument per row
     */
    static String strShell(List<String> lstArg) {
        StringBuilder sb = new StringBuilder();
        for (int idxArg = 0; idxArg < lstArg.size(); idxArg++) {
            sb.append("  ").append(lstArg.get(idxArg));
            if (idxArg < lstArg.size() - 1)
                sb.append(" \\");
            sb.append("\n");
        }
        return sb.toString();
    }


    /**
     * @param strMessage a failure, which is routinely many lines
     * @return its first non-blank line
     */
    static String strFirstLine(String strMessage) {
        for (String strLine : strMessage.split("\n")) {
            if (!strLine.isBlank())
                return strLine.trim();
        }
        return strMessage;
    }

}
