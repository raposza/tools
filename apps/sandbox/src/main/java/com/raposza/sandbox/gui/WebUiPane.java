// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.localnet.LocalNetUi;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * WHERE A PERSON SIGNS IN - `todo.md` A-40. Every web UI LocalNetND serves,
 * the name to type at its login box and the password, and each participant's
 * JSON Ledger API beside them.
 *
 * <h2>A sub-tab of OIDC, on LocalNetND only</h2>
 *
 * His words, 2026-09-23: "a new tab on Sandbox for web UI endpoints and
 * users/passwords". It sits under the OIDC tab rather than beside it, because
 * the users ARE the provider's users and A-3 is his precedent against adding
 * top-level tabs. Sandbox Simple serves no web UI, so it never has this.
 *
 * <h2>URLs, not ports</h2>
 *
 * The UIs are host-routed virtual hosts - `LocalNetUi` - so a port alone does
 * not reach a page. The rows are the URLs `LocalNetRunner.lstPageWeb` builds,
 * the same list the OIDC registration writes as redirect origins.
 *
 * <h2>The password is shown plainly, and only where it is true</h2>
 *
 * `123456` - D-818 - is what the window writes to ITS OWN provider at every
 * start. On an external provider or on the bundle's HS256 secret the window
 * wrote nothing, so the cell says so rather than showing a password nobody set.
 * Neither this nor the URLs go into the discovery document or snapshot
 * metadata.
 *
 * Author Claude/bentzn
 */
public final class WebUiPane extends JPanel {

    private static final long serialVersionUID = 1L;

    /** What the pane says when nothing is up. */
    private static final String STR_IDLE = "LocalNetND is not running";

    /** What a password cell says when the window registered nobody. */
    static final String STR_NO_PASSWORD = "not set by this window";

    /** What a user cell says for a page with no login box. */
    static final String STR_NO_LOGIN = "-";

    /**
     * THE NAMES ARE *.localhost. Browsers answer those on the loopback address
     * themselves, which is why a Windows machine that resolves no .localhost
     * name still opens every page - `LocalNetWebTest`. A tool that asks the
     * operating system's resolver instead - curl on some systems - may need
     * them in the hosts file.
     */
    static final String STR_HINT = "The *.localhost names are answered by the browser itself."
            + " A tool that asks the system resolver may need them in the hosts file.";

    private static final String[] ARR_COLUMN = { "Page", "URL", "User", "Password" };

    private final DefaultTableModel model = new DefaultTableModel(ARR_COLUMN, 0) {

        private static final long serialVersionUID = 1L;


        @Override
        public boolean isCellEditable(int idxRow, int idxCol) {
            return false;
        }
    };

    private final transient JTable table = new JTable(model);

    private final JButton btnOpen = new JButton("Open");

    private final JButton btnCopy = new JButton("Copy URL");

    private final JLabel lblState = new JLabel(STR_IDLE);


    public WebUiPane() {
        super(new BorderLayout());
        setOpaque(false);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(GuiTheme.scale(22));

        btnOpen.addActionListener(evt -> open());
        btnCopy.addActionListener(evt -> copy());

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlButtons.setOpaque(false);
        pnlButtons.add(btnOpen);
        pnlButtons.add(btnCopy);
        lblState.setForeground(GuiTheme.colMuted());
        pnlButtons.add(lblState);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        JLabel lblHint = new JLabel(STR_HINT);
        lblHint.setForeground(GuiTheme.colMuted());

        JPanel pnlInner = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlInner.setOpaque(false);
        pnlInner.add(pnlButtons, BorderLayout.NORTH);
        pnlInner.add(scroll, BorderLayout.CENTER);
        pnlInner.add(lblHint, BorderLayout.SOUTH);

        add(GuiTheme.card("Web UIs and endpoints", pnlInner), BorderLayout.CENTER);
        reset();
    }


    /**
     * The rows, built without Swing so they can be asserted.
     *
     * @param lstPage what the runner serves, in its order
     * @param mapJson role to its JSON Ledger API url, in display order
     * @param strPassword what the window's provider holds for every user, or
     *        null when the window registered nobody
     * @return one array per row: page, url, user, password
     */
    static List<String[]> lstRow(List<LocalNetUi.Page> lstPage, Map<String, String> mapJson,
            String strPassword) {
        List<String[]> lstOut = new ArrayList<>();
        for (LocalNetUi.Page page : lstPage) {
            boolean flagLogin = page.strLogin() != null;
            lstOut.add(new String[] { page.strTitle(), page.strUrl(),
                    flagLogin ? page.strLogin() : STR_NO_LOGIN,
                    !flagLogin ? STR_NO_LOGIN
                            : strPassword == null ? STR_NO_PASSWORD : strPassword });
        }
        for (Map.Entry<String, String> entry : mapJson.entrySet()) {
            lstOut.add(new String[] { "JSON Ledger API - " + entry.getKey(), entry.getValue(),
                    "bearer token", "OIDC tab" });
        }
        return lstOut;
    }


    /**
     * @param lstPage what the runner serves
     * @param mapJson role to its JSON Ledger API url
     * @param strPassword what the window's provider holds, or null
     */
    public void show(List<LocalNetUi.Page> lstPage, Map<String, String> mapJson,
            String strPassword) {
        model.setRowCount(0);
        for (String[] arrRow : lstRow(lstPage, mapJson, strPassword)) {
            model.addRow(arrRow);
        }
        lblState.setText(lstPage.isEmpty() ? "no web UI is being served" : " ");
        btnOpen.setEnabled(model.getRowCount() > 0);
        btnCopy.setEnabled(model.getRowCount() > 0);
    }


    /**
     * Clears the table, for a stack that has gone down.
     */
    public void reset() {
        model.setRowCount(0);
        lblState.setText(STR_IDLE);
        btnOpen.setEnabled(false);
        btnCopy.setEnabled(false);
    }


    /**
     * @return the selected row's URL, or null
     */
    private String strUrlSelected() {
        int idxRow = table.getSelectedRow();
        if (idxRow < 0)
            return null;
        return String.valueOf(model.getValueAt(table.convertRowIndexToModel(idxRow), 1));
    }


    private void open() {
        String strUrl = strUrlSelected();
        if (strUrl == null) {
            lblState.setText("select a row first");
            return;
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(strUrl));
                lblState.setText(" ");
                return;
            }
        }
        catch (RuntimeException | IOException ex) {
            // no browser, or it refused; the clipboard is the fallback
        }
        copy();
    }


    private void copy() {
        String strUrl = strUrlSelected();
        if (strUrl == null) {
            lblState.setText("select a row first");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(strUrl), null);
        lblState.setText("copied " + strUrl);
    }
}
