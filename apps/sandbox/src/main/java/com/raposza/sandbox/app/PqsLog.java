// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scribe's output, as a person would write it.
 *
 * <h2>HIS INSTRUCTION, 2026-09-22: "Not tech. Simple. Informative."</h2>
 *
 * A scribe start prints its own command line, a diagnostics banner, an
 * OpenTelemetry notice, the whole applied configuration as HOCON, and then one
 * line per retry for as long as the retry lasts. On a refused token that is
 * ninety lines of the same sentence and then a stack trace repeated a dozen
 * times, and the one fact in it - the provider said 401 - is somewhere in the
 * middle.
 *
 * <h2>WHAT IS KEPT</h2>
 *
 * The facts an operator acts on: which binary and schema, that the database
 * answered, that the token was obtained or refused and why, that ingestion
 * started, and any error. Everything else is dropped.
 *
 * <h2>AND EACH ONE ONCE</h2>
 *
 * A message already shown is dropped, so a retry storm is one line rather than
 * ninety. The set is per run - {@link #reset} - so the same message on the
 * next start is shown again.
 *
 * THE FULL OUTPUT IS NOT LOST. `ScribeProcess` writes its own log file and
 * that is untouched; this is the pane a person reads while it starts.
 *
 * Author Claude/bentzn
 */
public final class PqsLog {

    /** What scribe prints its version banner as. */
    private static final Pattern RE_VERSION = Pattern.compile(
            "scribe, version: (\\S+).*postgres-document\\.schema: (\\d+)");

    private static final Pattern RE_ANSI = Pattern.compile("\u001b\\[[0-9;]*m");

    /**
     * scribe's structured tail. `application=scribe` rides on the end of every
     * line and made two spellings of one message - which is what let the same
     * refusal through twice before it was stripped.
     */
    private static final Pattern RE_TAIL = Pattern.compile("\\s+application=\\S+\\s*$");

    /** The noise, matched on what is left after the escapes are stripped. */
    private static final String[] ARR_DROP = {
        "[diagnostics]",
        "ATTN! OpenTelemetry",
        "Please provide OpenTelemetry",
        "See also https://opentelemetry.io",
        "Applied configuration:",
        "Acquiring auth token",
        "Retrying auth token acquisition",
        "at com.digitalasset.",
        "at zio.",
        "$ java -jar",
    };

    private final Set<String> setShown = new LinkedHashSet<>();

    private boolean flagInConfig;


    /** Forgets what it has shown, for a new run. */
    public void reset() {
        setShown.clear();
        flagInConfig = false;
    }


    /**
     * @param strLineRaw one line as scribe printed it
     * @return what to show, or null to drop it
     */
    public String strFor(String strLineRaw) {
        if (strLineRaw == null)
            return null;
        String strLine = RE_ANSI.matcher(strLineRaw).replaceAll("");
        strLine = RE_TAIL.matcher(strLine).replaceAll("").trim();
        if (strLine.isEmpty())
            return null;

        // THE CONFIGURATION DUMP IS A BLOCK, not a line: scribe prints
        // `Applied configuration:` and then sixty lines of HOCON that carry no
        // marker of their own. It ends at the closing brace in column one.
        if (flagInConfig) {
            if ("}".equals(strLine))
                flagInConfig = false;
            return null;
        }
        if (strLine.contains("Applied configuration:")) {
            flagInConfig = true;
            return null;
        }

        for (String strDrop : ARR_DROP) {
            if (strLine.contains(strDrop))
                return null;
        }

        String strOut = strSaid(strLine);
        if (strOut == null)
            return null;
        return setShown.add(strOut) ? strOut : null;
    }


    /**
     * @param strLine the line, escapes stripped
     * @return what it means in one sentence, or null when it means nothing to
     *         a reader
     */
    private static String strSaid(String strLine) {
        Matcher matVersion = RE_VERSION.matcher(strLine);
        if (matVersion.find())
            return "scribe " + matVersion.group(1) + ", schema " + matVersion.group(2);

        if (strLine.contains("PQS: real scribe from "))
            return "using " + strLine.substring(strLine.indexOf("PQS: real scribe from ") + 22);
        if (strLine.contains("PQS: mock"))
            return "no scribe binary for this line - running the mock";
        if (strLine.contains("Database probe") && strLine.contains("successful"))
            return "database ready";

        if (strLine.contains("Auth token couldn't be acquired due to: ")) {
            return "the token was REFUSED: "
                    + strLine.substring(strLine.indexOf("due to: ") + 8).trim()
                    + " - scribe is retrying and will not ingest until it succeeds";
        }
        if (strLine.contains("Auth token acquired") || strLine.contains("token acquired"))
            return "token acquired";

        // WHATEVER SCRIBE CALLS AN ERROR, once, without its stack.
        if (strLine.contains("level=ERROR") || strLine.startsWith("java.")
                || strLine.contains(" ERROR ")) {
            return "scribe reported an error: " + strTail(strLine);
        }
        if (strLine.contains("Ingesting") || strLine.contains("ingestion")
                || strLine.contains("Pipeline started")) {
            return "ingesting from the ledger";
        }
        return null;
    }


    /**
     * @param strLine a log line
     * @return the part worth reading - what follows the last `message=` or
     *         `cause=` when scribe used its structured form, else the line
     */
    private static String strTail(String strLine) {
        int idxCause = strLine.indexOf("cause=\"");
        if (idxCause >= 0) {
            String strRest = strLine.substring(idxCause + 7);
            int idxEnd = strRest.indexOf('\n');
            return idxEnd < 0 ? strRest : strRest.substring(0, idxEnd);
        }
        return strLine.length() > 200 ? strLine.substring(0, 200) + "..." : strLine;
    }

}
