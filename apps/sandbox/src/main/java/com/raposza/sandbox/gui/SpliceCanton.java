// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.Edition;
import com.raposza.runtime.localnet.SpliceInstallations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * THE CANTON A SPLICE RELEASE RUNS INSIDE, chosen by the release - his
 * instruction and his rule, 2026-10-30: the same major and minor line as the
 * Canton the release names, the newest patch of it installed, and a note when
 * that is not the exact one.
 *
 * Until then the window took the newest Canton on the machine, of ANY line. A
 * machine with the 2.9.6 Assistant and an unbootstrapped DPM started Splice
 * 0.9.4 on a Canton it was never built for, and nothing said so before the
 * start failed - reported 2026-10-29.
 *
 * <h2>The release says what it needs</h2>
 *
 * Every bundle carries `version_information.html`, read by
 * {@link SpliceInstallations#versionsOf}: "Canton version used for validator
 * and SV nodes". Read on 0.6.0 to 0.9.4; on 0.8.4 to 0.9.4 it is exactly the
 * Canton the release notes name - 3.5.19, 3.6.1, 3.6.1, 3.6.2, 3.6.3, 3.6.4.
 * The page itself recommends "the same major and minor version as the Canton
 * version used in this Splice release". Some values are snapshots,
 * `3.5.14-snapshot.20260815...`; the line is all that is read off them.
 *
 * <h2>Splice 0.8 is the lowest - his decision, 2026-10-30</h2>
 *
 * Older releases are refused before anything else is asked of them. 0.5.10 on
 * its own line, Canton 3.4.11, was measured that day: Canton refuses
 * LocalNetND's configuration - `canton.mediators.app-mediator.storage` and
 * `canton.sequencers.app-sequencer.storage`, "Key not found: 'type'".
 *
 * <h2>Never a 2.x Canton</h2>
 *
 * A bundle whose page cannot be read gets the newest 3.x installed, with a
 * note saying the pairing is unchecked. Neither path ever hands Splice a 2.x
 * Canton.
 *
 * Author Claude/bentzn
 */
public final class SpliceCanton {

    /** The lowest major a Splice release runs on. */
    private static final int N_MAJOR_MIN = 3;

    /** The lowest Splice LocalNetND runs - his decision, 2026-10-30. */
    public static final String STR_SPLICE_MIN = "0.8";

    private static final int N_SPLICE_MAJOR_MIN = 0;

    private static final int N_SPLICE_MINOR_MIN = 8;

    private static final Pattern PAT_LINE = Pattern.compile("^\\s*(\\d+)\\.(\\d+)\\.");


    /**
     * @param inst the Canton to run inside, or null when none will do
     * @param strNote what was chosen and why, for the main log; null on a refusal
     * @param strRefusal why none will do and what to install; null when one was chosen
     * @param strLineNeeded the Canton line an SDK install would supply, `3.6`, when
     *        that is what is missing; null when an install would not help
     */
    public record Choice(CantonInstallation inst, String strNote, String strRefusal,
            String strLineNeeded) {
    }


    private SpliceCanton() {
    }


    /**
     * @param strSplice the Splice version, or null when none is selected
     * @param versions what its bundle states, or null when the page is absent
     * @param lstCanton every Canton on the machine
     * @return the choice, never null
     */
    public static Choice choose(String strSplice, SpliceInstallations.Versions versions,
            List<CantonInstallation> lstCanton) {
        if (strSplice == null)
            return new Choice(null, null, "no Splice bundle is selected", null);
        if (isBelowFloor(strSplice)) {
            return new Choice(null, null, "LocalNetND runs Splice " + STR_SPLICE_MIN
                    + " and later; " + strSplice + " is older. Install Splice " + STR_SPLICE_MIN
                    + " or newer.", null);
        }

        String strNamed = versions == null ? null : versions.strCanton();
        String strLine = strLineOf(strNamed);
        CantonInstallation instBest = null;
        for (CantonInstallation inst : lstCanton == null ? List.<CantonInstallation>of() : lstCanton) {
            if (!inst.hasRuntime() || inst.version().major() < N_MAJOR_MIN)
                continue;
            if (strLine != null && !inst.version().isLine(strLine))
                continue;
            if (instBest == null || inst.version().compareTo(instBest.version()) > 0)
                instBest = inst;
        }

        if (strLine == null) {
            if (instBest == null) {
                return new Choice(null, null, "Splice " + strSplice + " needs a 3.x Canton and"
                        + " none is installed. Install a Daml SDK 3.x through DPM, then start"
                        + " again.", null);
            }
            return new Choice(instBest, "Splice " + strSplice + " on Canton " + strOf(instBest)
                    + " - UNCHECKED: the bundle does not say which Canton it is released on, so"
                    + " this is the newest 3.x installed. " + instBest.fileRuntime(), null, null);
        }

        if (instBest == null) {
            return new Choice(null, null, "Splice " + strSplice + " needs a Canton of the "
                    + strLine + " line - it is released on " + strNamed + " - and none is"
                    + " installed. Install the newest Daml SDK " + strLine + " release through"
                    + " DPM, then start again.", strLine);
        }
        String strWhy = instBest.version().toString().equals(strNamed)
                ? "the Canton it is released on"
                : "released on " + strNamed + "; the newest " + strLine + " installed";
        return new Choice(instBest, "Splice " + strSplice + " on Canton " + strOf(instBest)
                + " - " + strWhy + ". " + instBest.fileRuntime(), null, null);
    }


    /**
     * @param strCanton a Canton version as a bundle states it, snapshots included
     * @return its major and minor line, `3.6`, or null when it does not start
     *         with one
     */
    static String strLineOf(String strCanton) {
        if (strCanton == null)
            return null;
        Matcher matcher = PAT_LINE.matcher(strCanton);
        if (!matcher.find())
            return null;
        return Integer.parseInt(matcher.group(1)) + "." + Integer.parseInt(matcher.group(2));
    }


    /**
     * @param lstVersion Splice versions, in any order
     * @return those LocalNetND runs, in the same order - what Install Splice
     *         offers, since an older row would install and then be refused
     */
    public static List<String> lstFromFloor(List<String> lstVersion) {
        List<String> lstOut = new ArrayList<>();
        if (lstVersion == null)
            return lstOut;
        for (String strVersion : lstVersion) {
            if (strVersion != null && !isBelowFloor(strVersion))
                lstOut.add(strVersion);
        }
        return lstOut;
    }


    /**
     * @param strSplice a Splice version
     * @return whether it is below {@link #STR_SPLICE_MIN}; a version that does
     *         not parse is not refused here
     */
    static boolean isBelowFloor(String strSplice) {
        Matcher matcher = PAT_LINE.matcher(strSplice);
        if (!matcher.find())
            return false;
        int nMajor = Integer.parseInt(matcher.group(1));
        int nMinor = Integer.parseInt(matcher.group(2));
        return nMajor < N_SPLICE_MAJOR_MIN
                || (nMajor == N_SPLICE_MAJOR_MIN && nMinor < N_SPLICE_MINOR_MIN);
    }


    private static String strOf(CantonInstallation inst) {
        if (inst.edition() == Edition.UNKNOWN)
            return inst.version().toString();
        return inst.version() + " " + inst.edition().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
