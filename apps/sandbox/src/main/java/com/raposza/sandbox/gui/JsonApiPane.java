// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.app.ReadyReport;
import com.raposza.sandbox.app.SandboxService;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The HTTP JSON API: which port it is on, whether anything answers there, and
 * what the server printed.
 *
 * <h2>Both generations serve, and by different means</h2>
 *
 * On 3.x it is the participant's own HTTP Ledger API, started with
 * `--json-api-port`, so there is no second process and the log below stays
 * empty - that output is the participant's. On 2.x it is a SEPARATE process
 * out of the DAML SDK, and the log below is its own.
 *
 * <h2>The probe is a TCP connect and nothing more</h2>
 *
 * It opens a socket and closes it. Whether something is listening is a fact
 * this window can establish on any version without knowing an endpoint path;
 * the v2 request paths are not banked in the inventory, and a guessed path
 * returning 404 would be indistinguishable from a healthy server.
 *
 * Author Claude/bentzn
 */
public final class JsonApiPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** How long a connect may take before the port counts as closed. */
    private static final int N_MS_CONNECT = 2000;

    private static final String STR_HOST = "localhost";

    private final Supplier<SandboxService> supService;

    private final JLabel lblState = new JLabel(" ");

    private final JLabel lblUrl = new JLabel(" ");

    private final JLabel lblProbe = new JLabel(" ");

    private final JButton btnCheck = new JButton("Check");

    private final LogPane log = new LogPane();


    /**
     * @param supServiceNew where the running service comes from; asked each
     *        time, because this pane outlives every stack the window starts
     */
    public JsonApiPane(Supplier<SandboxService> supServiceNew) {
        super(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        setOpaque(false);
        this.supService = supServiceNew;

        GuiTheme.mono(lblUrl);
        lblProbe.setForeground(GuiTheme.colMuted());
        btnCheck.addActionListener(evt -> check());

        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(16),
                GuiTheme.scale(4)));
        pnlTop.setOpaque(false);
        pnlTop.add(lblState);
        pnlTop.add(lblUrl);
        pnlTop.add(btnCheck);
        pnlTop.add(lblProbe);

        add(pnlTop, BorderLayout.NORTH);
        add(log, BorderLayout.CENTER);
        refresh();
    }


    /**
     * @return the pane the JSON API process writes into
     */
    public LogPane log() {
        return log;
    }


    /**
     * Re-reads the service. Cheap and does no I/O, so the window's lamp timer
     * calls it; the socket is only opened by {@link #check()}.
     */
    public void refresh() {
        SandboxService service = supService.get();
        ReadyReport report = service == null ? null : service.report();
        String strPort = report == null ? null : report.value(ReadyReport.KEY_JSON_API);

        if (strPort == null) {
            lblState.setText("no stack");
            lblUrl.setText(" ");
            btnCheck.setEnabled(false);
            return;
        }

        SandboxService.Health health = service.healthJsonApi();
        lblState.setText(health.name());
        lblUrl.setText("http://" + STR_HOST + ":" + strPort);
        btnCheck.setEnabled(health != SandboxService.Health.OFF
                && health != SandboxService.Health.PENDING);
    }


    private void check() {
        SandboxService service = supService.get();
        ReadyReport report = service == null ? null : service.report();
        String strPort = report == null ? null : report.value(ReadyReport.KEY_JSON_API);
        if (strPort == null)
            return;

        int nPort;
        try {
            nPort = Integer.parseInt(strPort.trim());
        }
        catch (NumberFormatException ex) {
            lblProbe.setText("the report's port is not a number: " + strPort);
            return;
        }

        btnCheck.setEnabled(false);
        lblProbe.setText("connecting ...");

        Thread threadProbe = new Thread(() -> {
            long nStart = System.currentTimeMillis();
            String strResult;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(STR_HOST, nPort), N_MS_CONNECT);
                strResult = "listening, " + (System.currentTimeMillis() - nStart) + " ms";
            }
            catch (Exception ex) {
                strResult = "nothing answered: " + ex.getClass().getSimpleName();
            }
            String strDone = strResult;
            SwingUtilities.invokeLater(() -> {
                lblProbe.setText(strDone);
                btnCheck.setEnabled(true);
            });
        }, "sandbox-gui-jsonapi-probe");
        threadProbe.setDaemon(true);
        threadProbe.start();
    }

}
