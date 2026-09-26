// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.profile.HostProfile;

import com.formdev.flatlaf.FlatLightLaf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * Entry point.
 *
 * Reads the catalogue, then opens the window. If the catalogue is missing the
 * application says which file it looked for and stops, rather than opening an
 * empty window that looks like a working tool with nothing configured.
 *
 * Usage:
 *   workbench [path-to-ledger_hosts]
 *   workbench --discovery URL
 *   workbench --script FILE --transcript FILE [--profile NAME] [--validate]
 *
 * The SECOND form is the Sandbox one and needs nothing else. A Sandbox
 * publishes a discovery document naming its Ledger API port, the Canton behind
 * it and a url that hands back a token, so one argument replaces a catalogue
 * line, a profile file and a pasted credential. The first form remains what a
 * standalone participant needs, and the two produce the same pair - a profile
 * and a token source - so the window cannot tell them apart afterwards.
 *
 * The third form runs a fixture script and never opens a window - design sec.
 * 6.4. It is a branch here rather than a second main class so that the shaded
 * launcher jar has one entry point and one manifest: two would mean two things
 * to keep in step, and the one that is used less would be the one that breaks.
 *
 * Author Claude/bentzn
 */
public final class WorkbenchApp {

    /** The Sandbox form. One argument replaces the whole catalogue line. */
    public static final String STR_ARG_DISCOVERY = "--discovery";


    private WorkbenchApp() {
    }


    /** @param arrArg optional catalogue path, or the headless switches */
    public static void main(String[] arrArg) {
        // BEFORE the look and feel. A headless run must not touch Swing at all:
        // on a machine with no display, initialising it is the failure rather
        // than anything about the script.
        for (String strArg : arrArg) {
            if ("--script".equals(strArg)) {
                System.exit(CaqlRunApp.run(arrArg));
                return;
            }
        }

        try {
            // NO SCALE IS SET HERE, and that is the fix rather than an
            // omission. `sun.java2d.uiScale=2` and `flatlaf.uiScale=1.03`
            // were written unconditionally here, so a window sized for one 4K
            // desktop came out at double size on every other display - and the
            // Sandbox window beside it rendered correctly on both, because it
            // sets neither.
            // The desktop's own scaling is the answer on both machines, and a
            // display that needs a factor takes one on the command line:
            // -Dsun.java2d.uiScale, which this no longer overwrites.
            // The design package - Inter, Hack, the accent, the arcs - BEFORE
            // the look and feel instance exists. D-848, todo.md DS-8.
            GuiDesign.install();
            UIManager.setLookAndFeel(new FlatLightLaf());
            FlatLightLaf.setup();
        }
        catch (Exception ex) {
            // The stock look and feel is not a reason to refuse to start.
        }

        String strDiscovery = valueOf(arrArg, STR_ARG_DISCOVERY);
        if (strDiscovery != null) {
            openOnSandbox(strDiscovery);
            return;
        }

        Path fileCatalogue = Catalogue.resolve(arrArg, Path.of(System.getProperty("user.home")));

        // A MISSING CATALOGUE IS NOT FATAL SINCE THE CONNECT DIALOG. The
        // file used to be the only way to name a participant, so refusing to
        // start without it was right. It is now a convenience that pre-fills
        // the standalone form, and the Sandbox case needs nothing from it at
        // all - so an unreadable one is reported to the console and the window
        // opens on an empty catalogue.
        //
        // A BAD file and an ABSENT one are still different things: the first
        // is a defect the operator wants to know about, and the second is the
        // ordinary state of a machine that has only ever used a Sandbox.
        List<HostProfile> lstProfile;
        try {
            lstProfile = Catalogue.read(fileCatalogue);
        }
        catch (RuntimeException ex) {
            if (Files.exists(fileCatalogue)) {
                System.err.println("the participant catalogue at "
                        + fileCatalogue.toAbsolutePath() + " could not be read, so the"
                        + " standalone form starts empty: " + ex.getMessage());
            }
            lstProfile = List.of();
        }

        MainWindow.open(lstProfile);
    }


    /**
     * Reads the document and opens the window on the one participant it names.
     *
     * The read happens BEFORE the window, and a failure is reported instead of
     * one: a window offering a profile built from a document that could not be
     * read would fail again on Connect, one layer further from the cause.
     *
     * @param strUrl the discovery endpoint
     */
    private static void openOnSandbox(String strUrl) {
        Discovery doc;
        HostProfile profile;
        try {
            doc = Discovery.fetch(strUrl);
            profile = doc.profile();
        }
        catch (RuntimeException ex) {
            String strMsg = "Could not read the Sandbox discovery document.\n\n"
                    + strUrl + "\n\n" + ex.getMessage();
            System.err.println(strMsg);
            JOptionPane.showMessageDialog(null, strMsg, "workbench",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        System.out.println("sandbox " + doc.strVersion() + " " + doc.strEdition()
                + " at " + profile.nameHost() + ":" + profile.portLedger()
                + ", ledger api " + doc.generation());
        MainWindow.open(List.of(profile), doc);
    }


    /**
     * @param arrArg the command line
     * @param strName the switch wanted
     * @return its value, or null when the switch is absent
     * @throws IllegalArgumentException when it is present with nothing after it
     */
    private static String valueOf(String[] arrArg, String strName) {
        for (int idxArg = 0; idxArg < arrArg.length; idxArg++) {
            if (!strName.equals(arrArg[idxArg]))
                continue;
            if (idxArg + 1 >= arrArg.length)
                throw new IllegalArgumentException(strName + " needs a value");
            return arrArg[idxArg + 1];
        }
        return null;
    }

}
