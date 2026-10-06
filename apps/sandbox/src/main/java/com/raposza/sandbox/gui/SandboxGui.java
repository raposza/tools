// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.app.DiscoveryDoc;
import com.raposza.sandbox.app.SandboxApp;
import com.raposza.sandbox.app.SandboxOptions;

import java.awt.GraphicsEnvironment;
import java.net.http.HttpClient;

import javax.swing.SwingUtilities;

/**
 * The window entry point, reached from any line without `--cli`.
 *
 * Not a second `main`. `SandboxApp` branches here before it parses anything, so
 * the launcher jar keeps ONE manifest and one entry point - two would be two
 * things to keep in step, and the one used less would be the one that breaks.
 * That is the same argument `WorkbenchApp` records for its headless branch,
 * pointing the other way.
 *
 * <h2>Order, and both parts of it are load-bearing</h2>
 *
 * {@link LogTee#install} runs FIRST, before the look and feel and before any
 * component: slf4j-simple resolves `System.err` once, when its first logger is
 * created, so a tee installed later shows nothing the stack logs.
 *
 * The scaling properties are NOT set. `sun.java2d.uiScale` is read once when
 * the graphics environment initialises and cannot be changed afterwards, which
 * is why `WorkbenchApp` sets it in its first statement - but it sets it to 2
 * unconditionally, which is a 4k assumption on every machine including the
 * laptop, and is a standing defect rather than a pattern to copy. Anything
 * passed as `-Dsun.java2d.uiScale` on the command line still works, because
 * nothing here overwrites it.
 *
 *
 * Author Claude/bentzn
 * Updated 2026-08-21T20:30:00Z
 */
public final class SandboxGui {

    private SandboxGui() {
    }


    /**
     * @param arrArg the command line with `--cli` already removed; it pre-fills
     *        the form and is parsed by the same parser the terminal uses
     */
    public static void open(String[] arrArg) {
        LogTee.install();

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("the window needs a display, and this JVM is headless;"
                    + " --cli starts the stack in the terminal instead");
            System.exit(SandboxApp.N_EXIT_USAGE);
            return;
        }

        SandboxOptions options;
        try {
            options = SandboxOptions.parse(arrArg);
        }
        catch (RuntimeException ex) {
            System.err.println(ex.getMessage());
            System.err.println();
            System.err.print(SandboxOptions.usage());
            System.exit(SandboxApp.N_EXIT_USAGE);
            return;
        }

        // THE HTTP CLIENT, WARMED WHILE THE LOOK AND FEEL INSTALLS AND THE
        // OPERATOR READS THE TOPOLOGY QUESTION. Measured 2026-10-03: opening the
        // window held the event thread 1093 ms, sampled in HttpClientImpl.<init>
        // under SSLContext.getDefault. The first client built in the JVM loads
        // the trust store and the client classes, and the window's panes build
        // theirs as fields, on the event thread. Here it costs nobody a frame.
        Thread threadWarm = new Thread(SandboxGui::warmHttp, "sandbox-http-warm");
        threadWarm.setDaemon(true);
        threadWarm.start();
        // AND WHAT THE WINDOW READS AT OPEN, for the same reason - A-63 (b).
        OpenPrefetch.start();

        SandboxOptions optionsForm = options;
        SwingUtilities.invokeLater(() -> {
            try {
                GuiTheme.install();
            }
            catch (RuntimeException ex) {
                // The stock look and feel is not a reason to refuse to start.
            }
            String strTopology = TopologyDialog.strChoose(null);
            if (strTopology == null) {
                // CLOSED IS A REFUSAL, not a default. There is no topology to
                // fall back to: the two build different windows.
                System.exit(0);
                return;
            }
            try {
                SandboxWindow.open(optionsForm, strTopology);
            }
            catch (RuntimeException ex) {
                Modals.error(null, String.valueOf(ex.getMessage()));
            }
        });
    }


    /**
     * Builds and closes one client, so the next is built from warm state. A
     * failure is not reported here: the pane that needs a client reports the
     * same failure where it is used.
     */
    private static void warmHttp() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            client.version();
        }
        catch (RuntimeException ex) {
            // reported where a client is used
        }
    }

}
