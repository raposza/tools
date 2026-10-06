// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.runtime.localnet.LocalNetUi;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * WHO CAN SIGN IN: the Users tab under OIDC - his instruction, 2026-10-02,
 * "create a tab 'Users' under OIDC. With username, node and password."
 *
 * <h2>Where the rows come from: the provider - his instruction, 2026-10-04</h2>
 *
 * "The OIDC Users tab should obviously list all users that can work with that
 * server." Every row is a user the provider holds, read from it -
 * `ProviderUsers`. The window registers some of them itself: on LocalNetND the
 * role users every start registers - `MintRegistration`; on the single
 * participant every ledger user the participant has, at each start, after the
 * Aviation fixture and every 3 s - `SandboxUsers`. For those the window knows
 * the node and the password. A user added at the provider's own UI - the USDCx
 * demo's - is listed too, with neither: the provider does not know nodes and
 * never returns a password.
 *
 * <h2>No Refresh button - his instruction, 2026-10-04</h2>
 *
 * The window reads the server again every 3 s. A read that finds what is
 * already shown changes nothing on the tab, so a selected row stays selected.
 *
 * <h2>The password is shown plainly, and only where it is true</h2>
 *
 * `123456` - D-818 - is what the window writes to ITS OWN provider. On an
 * external provider, or with auth off, the window wrote nobody, and the state
 * line says so rather than the table showing a password nobody set. Neither
 * goes into the discovery document or snapshot metadata.
 *
 * Author Claude/bentzn
 */
public final class UsersPane extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] ARR_COLUMN = { "Username", "Node", "Password" };

    /**
     * One row.
     *
     * @param strUser the name typed at the provider's login box
     * @param strNode the node it is a ledger user on
     * @param strPassword its password
     */
    public record Row(String strUser, String strNode, String strPassword) {
    }


    private final DefaultTableModel model = new DefaultTableModel(ARR_COLUMN, 0) {

        private static final long serialVersionUID = 1L;


        @Override
        public boolean isCellEditable(int idxRow, int idxCol) {
            return false;
        }
    };

    private final transient JTable table = new JTable(model);

    private final JLabel lblState = new JLabel(" ");

    /** What the table shows now, so a read that found the same redraws nothing. */
    private transient List<Row> lstRowShown;


    public UsersPane() {
        super(new BorderLayout());
        setOpaque(false);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(GuiTheme.scale(22));

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, GuiTheme.scale(8), 0));
        pnlButtons.setOpaque(false);
        lblState.setForeground(GuiTheme.colMuted());
        pnlButtons.add(lblState);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        JPanel pnlInner = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlInner.setOpaque(false);
        pnlInner.add(pnlButtons, BorderLayout.NORTH);
        pnlInner.add(scroll, BorderLayout.CENTER);

        add(GuiTheme.card("OIDC users", pnlInner), BorderLayout.CENTER);
        show(List.of(), "nothing is running");
    }


    /**
     * LocalNetND's role users: one per name its login boxes want, with the
     * node it belongs to. Built without Swing so it can be asserted.
     *
     * @param lstPage what the runner serves
     * @param strPassword what the window's provider holds for every user
     * @return one row per name, in page order
     */
    static List<Row> lstRowLocalNet(List<LocalNetUi.Page> lstPage, String strPassword) {
        Map<String, Row> mapOut = new LinkedHashMap<>();
        for (LocalNetUi.Page page : lstPage) {
            if (page.strLogin() == null || mapOut.containsKey(page.strLogin()))
                continue;
            mapOut.put(page.strLogin(), new Row(page.strLogin(), page.strRole(), strPassword));
        }
        return new ArrayList<>(mapOut.values());
    }


    /** What the Password column says when the server's file could not be read. */
    static final String STR_PASSWORD_UNREAD = "not readable";

    /** What the Node column says for a user the window did not register. */
    static final String STR_NODE_UNKNOWN = "-";


    /**
     * Every user the OIDC server holds, in its order, with the password its
     * own file holds and the node the window knows for the ones it registered.
     *
     * @param lstName the server's users
     * @param mapPassword name to password, out of the server's `users.json`;
     *        empty when it could not be read
     * @param lstKnown the rows for the users this window registered
     * @return one row per server user; a registered user the server does not
     *         hold is not listed - it cannot sign in
     */
    static List<Row> lstRowProvider(List<String> lstName, Map<String, String> mapPassword,
            List<Row> lstKnown) {
        Map<String, Row> mapKnown = new LinkedHashMap<>();
        for (Row row : lstKnown) {
            mapKnown.putIfAbsent(row.strUser(), row);
        }
        List<Row> lstOut = new ArrayList<>();
        for (String strName : lstName) {
            Row rowKnown = mapKnown.get(strName);
            String strNode = rowKnown != null ? rowKnown.strNode() : STR_NODE_UNKNOWN;
            String strPassword = mapPassword.getOrDefault(strName, STR_PASSWORD_UNREAD);
            lstOut.add(new Row(strName, strNode, strPassword));
        }
        return lstOut;
    }


    /**
     * The single participant's ledger users.
     *
     * @param lstUser the users registered, in ledger order
     * @param strNode the participant
     * @param strPassword what they were registered with
     * @return one row each
     */
    static List<Row> lstRowSandbox(List<String> lstUser, String strNode, String strPassword) {
        List<Row> lstOut = new ArrayList<>();
        for (String strUser : lstUser) {
            lstOut.add(new Row(strUser, strNode, strPassword));
        }
        return lstOut;
    }


    /**
     * @param lstRow the rows
     * @param strState what the state label says, or null for nothing
     */
    public void show(List<Row> lstRow, String strState) {
        state(strState);
        if (lstRow.equals(lstRowShown))
            return;
        lstRowShown = List.copyOf(lstRow);
        model.setRowCount(0);
        for (Row row : lstRow) {
            model.addRow(new Object[] { row.strUser(), row.strNode(), row.strPassword() });
        }
    }


    /**
     * @param strState what the state label says
     */
    public void state(String strState) {
        String strText = strState == null || strState.isBlank() ? " " : strState;
        if (!strText.equals(lblState.getText()))
            lblState.setText(strText);
    }
}
