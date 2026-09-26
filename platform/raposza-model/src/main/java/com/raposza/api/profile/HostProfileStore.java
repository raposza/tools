// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.api.profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the host catalogue.
 *
 * The format is DevTools3's "ledger_hosts" file, extended by two optional
 * trailing fields:
 *
 *   name...  protocol  host  ledger-port  json-port  scope  audience  [mode  colour]
 *
 * The name may contain spaces, so parsing anchors on the protocol token, which
 * is always "http" or "https". The fixed part is therefore either six fields
 * (DevTools3 compatible) or eight (this tool). A "-" means absent.
 *
 * A line without a mode is READ_ONLY. That default is deliberate: a catalogue
 * copied from DevTools3, or shared by a colleague, must not silently grant
 * write access to a shared participant.
 *
 * The "-" placeholders are NOT optional. Omitting them on a line that carries a
 * mode and a colour leaves six fields that parse, with the mode swallowed as a
 * scope and the profile reading READ_ONLY - a writable participant declared in
 * a file and a window that says otherwise. That line is rejected by name.
 *
 * Author Claude/bentzn
 */
public final class HostProfileStore {

    private static final String STR_ABSENT = "-";
    private static final String COLOUR_READ_ONLY = "#c62828";
    private static final String COLOUR_READ_WRITE = "#2e7d32";


    private HostProfileStore() {
    }


    /**
     * @param fileCatalogue the catalogue file
     * @return profiles in file order
     * @throws UncheckedIOException when the file cannot be read
     */
    public static List<HostProfile> load(Path fileCatalogue) {
        List<String> lstLine;
        try {
            lstLine = Files.readAllLines(fileCatalogue, StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            throw new UncheckedIOException("cannot read host catalogue: " + fileCatalogue, ex);
        }

        List<HostProfile> lstProfile = new ArrayList<>();
        for (int idxLine = 0; idxLine < lstLine.size(); idxLine++) {
            String strLine = lstLine.get(idxLine).trim();
            if (strLine.isEmpty() || strLine.startsWith("#"))
                continue;

            HostProfile profile = parse(strLine, idxLine + 1);
            lstProfile.add(profile);
        }
        return Collections.unmodifiableList(lstProfile);
    }


    /**
     * @param strLine one catalogue line, already trimmed and known non-comment
     * @param numLine line number, for the error message only
     * @return the parsed profile
     * @throws IllegalArgumentException when the line does not parse
     */
    public static HostProfile parse(String strLine, int numLine) {
        String[] arrTok = strLine.split("\\s+");

        int idxProto = -1;
        for (int idx = 0; idx < arrTok.length; idx++) {
            if (!isProtocol(arrTok[idx]))
                continue;
            int cntTail = arrTok.length - idx;
            if (cntTail == 6 || cntTail == 8) {
                idxProto = idx;
                break;
            }
        }

        if (idxProto < 0) {
            throw new IllegalArgumentException("line " + numLine
                    + ": expected 6 or 8 fields after an http/https token, got: " + strLine);
        }

        if (arrTok.length - idxProto == 6)
            checkSixFieldTail(arrTok, idxProto, numLine);

        String nameDisplay = joinRange(arrTok, 0, idxProto);
        String strProtocol = arrTok[idxProto].toLowerCase();
        String nameHost = arrTok[idxProto + 1];
        int portLedger = parsePort(arrTok[idxProto + 2], numLine);
        int portJson = parsePort(arrTok[idxProto + 3], numLine);
        String strScope = orNull(arrTok[idxProto + 4]);
        String strAudience = orNull(arrTok[idxProto + 5]);

        AccessMode mode = AccessMode.READ_ONLY;
        String strColour = null;
        if (arrTok.length - idxProto == 8) {
            mode = parseMode(arrTok[idxProto + 6], numLine);
            strColour = orNull(arrTok[idxProto + 7]);
        }

        if (nameDisplay.isEmpty())
            nameDisplay = nameHost;
        if (strColour == null)
            strColour = defaultColour(mode);

        return new HostProfile(nameDisplay, strProtocol, nameHost, portLedger, portJson,
                strScope, strAudience, mode, strColour);
    }


    public static String defaultColour(AccessMode mode) {
        if (mode == AccessMode.READ_WRITE)
            return COLOUR_READ_WRITE;
        return COLOUR_READ_ONLY;
    }


    /**
     * Rejects a six-field tail that is an eight-field line written without its
     * two "-" placeholders.
     *
     * Such a line parses. It is wrong in the one direction that matters: "rw"
     * becomes the scope, the colour becomes the audience, and the profile reads
     * READ_ONLY with nothing anywhere naming the line. The operator declared a
     * writable participant in a file and the status bar disagreed with it.
     *
     * Rejected: reading the tail as mode and colour anyway. Guessing turns a
     * mistyped line into a write-enabled profile, which is the one outcome the
     * READ_ONLY default exists to prevent.
     *
     * @param arrTok the whitespace-split line
     * @param idxProto index of the protocol token
     * @param numLine line number, for the message only
     * @throws IllegalArgumentException when the tail reads as mode and colour
     */
    private static void checkSixFieldTail(String[] arrTok, int idxProto, int numLine) {
        String strScope = arrTok[idxProto + 4];
        String strAudience = arrTok[idxProto + 5];

        if (!isMode(strScope) && !strAudience.startsWith("#"))
            return;

        throw new IllegalArgumentException("line " + numLine
                + ": six fields after the protocol, but the last two read as mode and colour: \""
                + strScope + " " + strAudience + "\". Scope and audience are not optional -"
                + " write \"-\" for each.");
    }


    private static boolean isMode(String strTok) {
        return "ro".equalsIgnoreCase(strTok) || "rw".equalsIgnoreCase(strTok)
                || "read-only".equalsIgnoreCase(strTok) || "read-write".equalsIgnoreCase(strTok);
    }


    private static boolean isProtocol(String strTok) {
        return "http".equalsIgnoreCase(strTok) || "https".equalsIgnoreCase(strTok);
    }


    private static String joinRange(String[] arrTok, int idxFrom, int idxTo) {
        StringBuilder bld = new StringBuilder();
        for (int idx = idxFrom; idx < idxTo; idx++) {
            if (bld.length() > 0)
                bld.append(' ');
            bld.append(arrTok[idx]);
        }
        return bld.toString();
    }


    private static int parsePort(String strTok, int numLine) {
        if (STR_ABSENT.equals(strTok))
            return -1;
        try {
            return Integer.parseInt(strTok);
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("line " + numLine + ": bad port: " + strTok, ex);
        }
    }


    private static AccessMode parseMode(String strTok, int numLine) {
        if ("ro".equalsIgnoreCase(strTok) || "read-only".equalsIgnoreCase(strTok))
            return AccessMode.READ_ONLY;
        if ("rw".equalsIgnoreCase(strTok) || "read-write".equalsIgnoreCase(strTok))
            return AccessMode.READ_WRITE;
        throw new IllegalArgumentException("line " + numLine + ": bad mode: " + strTok
                + " (expected ro or rw)");
    }


    private static String orNull(String strTok) {
        if (STR_ABSENT.equals(strTok))
            return null;
        return strTok;
    }

}
