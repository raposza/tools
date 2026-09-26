// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.ApiGeneration;
import com.raposza.api.profile.AccessMode;
import com.raposza.api.profile.HostProfile;
import com.raposza.jwt.ProfileAuth;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * Where a session's target is chosen, and the only place it is chosen.
 *
 * <h2>Why this replaced a combo and a button</h2>
 *
 * The window used to carry a `Participant` combo over the catalogue and an
 * `auth:` button beside it. Two things were wrong with that and only one of
 * them was cosmetic.
 *
 * The combo could only offer CATALOGUE lines, and a catalogue line carries no
 * API generation - so {@link MainWindow#targetFor} refused any build hosting
 * more than one client. A Sandbox reached through `--discovery` worked and
 * everything else did not, which is backwards: the Sandbox is the case that
 * needs least help.
 *
 * The `auth:` button read the PROFILE FILE while a discovered session takes
 * its token from the mint, so the window reported `auth: none` over an
 * authenticated connection. A label that is confidently wrong is worse than no
 * label, and it is why the line went rather than being patched.
 *
 * <h2>Two ways in, and they are not variations of one form</h2>
 *
 * A local Sandbox needs ONE field. The discovery document carries the host,
 * the ports, the version, the generation and the token source, so asking for
 * any of them again would be asking the operator to retype what the Sandbox
 * already published - and to be wrong about it eventually.
 *
 * A standalone ledger has no such document and every field is genuinely
 * unknown. That branch is also the one that gives the catalogue path a
 * generation, by asking for it outright.
 *
 * <h2>OK connects</h2>
 *
 * There is no separate Connect press. A dialog whose OK button only records an
 * intention leaves the operator looking at a window that has changed nothing,
 * and the old strip's Connect button existed only because the combo could not
 * know when a choice was finished.
 *
 * Author Claude/bentzn
 */
public final class ConnectDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /**
     * The Sandbox's own default discovery port.
     *
     * A LITERAL, and DUPLICATED on purpose. The value lives in
     * `RaposzaSettings.nPortDiscovery` in `raposza-runtime`, which the
     * workbench does not depend on and should not start depending on to read
     * one int. It is a pre-filled box the operator can correct, not a
     * constant anything is decided by.
     */
    private static final int N_PORT_DISCOVERY_DEFAULT = 32001;

    /** What the operator chose, or null when nothing was chosen. */
    private transient Choice choice;

    private final JRadioButton radSandbox = new JRadioButton("a local Sandbox", true);

    private final JRadioButton radStandalone = new JRadioButton("a standalone ledger");

    /**
     * Where the Sandbox's discovery endpoint listens.
     *
     * A PORT AND NOT A URL. The endpoint is always `http` on the loopback
     * address at the root path - see `DiscoveryServer.strUrl()` - so every
     * other part of a URL is a constant the operator would be retyping, and
     * one they could get wrong in four different ways.
     */
    private final JTextField fldPortDiscovery = new JTextField(6);

    private final JTextField fldName = new JTextField(16);

    private final JTextField fldHost = new JTextField(16);

    private final JTextField fldPortLedger = new JTextField(6);

    private final JTextField fldPortJson = new JTextField(6);

    private final JCheckBox chkTls = new JCheckBox("TLS");

    /**
     * RD-6, settled 2026-09-23 - he put it back to the agent to decide. A
     * standalone ledger was READ_ONLY with no way round it, so the Workbench
     * could not write to a headless `run-fixture.sh` or `run-localnet.sh`
     * ledger at all. Of the two shapes put to him on 2026-09-15c - the
     * discovery producer moved into the platform and served by the headless
     * launchers, or a write switch on this form - this is the second: one
     * checkbox, OFF by default, so a ledger nobody has marked writable stays
     * read-only exactly as before. A discovery document still grants
     * READ_WRITE by its own stated policy.
     */
    private final JCheckBox chkWrite = new JCheckBox("Allow writes");

    private final JTextField fldAudience = new JTextField(24);

    private final JTextField fldScope = new JTextField(24);

    private final JComboBox<ApiGeneration> cmbGeneration =
            new JComboBox<>(ApiGeneration.values());

    private final JLabel lblProblem = new JLabel(" ");

    private final transient AuthPanel auth;

    private final JPanel pnlSandbox = new JPanel(new GridBagLayout());

    private final JPanel pnlStandalone = new JPanel(new GridBagLayout());


    /**
     * EVERYTHING THE FORM HELD AT THE LAST OK, kept for as long as the
     * Workbench runs - his instruction, 2026-09-23: "Keep the settings in the
     * Connection dialogue while the Workbench is still running." Every Connect
     * used to open a new dialog pre-filled from the catalogue's first profile,
     * so a name, a port, Allow writes and a pasted token all had to be given
     * again. In memory only: nothing here is written to disk, the token least
     * of all.
     */
    private record Remembered(boolean flagStandalone, String strPortDiscovery, String strName,
            String strHost, String strPortLedger, String strPortJson, Object generation,
            String strAudience, String strScope, boolean flagTls, boolean flagWrite,
            String strToken) {
    }

    /** Shared by every tab's dialog; the Workbench is one window. */
    private static Remembered remembered;


    /**
     * What a connection needs, whichever branch produced it.
     *
     * The two are EXCLUSIVE by construction rather than by convention: a
     * discovered session carries its own profile and token source inside the
     * document, so a caller that has one must not also consult the other.
     *
     * @param discovery the Sandbox that was discovered, or null
     * @param profile the participant to connect to; never null
     * @param auth what to present, or {@link ProfileAuth#NONE}
     * @param generation which Ledger API the target speaks, or null when the
     *        discovery document answers it
     */
    public record Choice(Discovery discovery, HostProfile profile, ProfileAuth auth,
            ApiGeneration generation) {
    }


    /**
     * @param wndOwner the window this belongs to, may be null
     * @param authPanel the window's auth panel, embedded here rather than
     *        rebuilt - it already knows how to read a profile's settings file
     *        and how to hold a pasted token
     * @param lstProfile the catalogue, used to pre-fill the standalone form
     *        from the first entry so the common case is a glance rather than
     *        eight fields
     */
    public ConnectDialog(Window wndOwner, AuthPanel authPanel, List<HostProfile> lstProfile) {
        super(wndOwner, "Connect", ModalityType.APPLICATION_MODAL);
        this.auth = authPanel;

        // ASKED, NOT GUESSED. A catalogue line has never carried a generation
        // and that is exactly why `targetFor()` refused a multi-client build.
        cmbGeneration.setRenderer((list, value, numIndex, flagSel, flagFocus) -> new JLabel(
                value == ApiGeneration.V1 ? "Ledger API v1  (Canton 2.x)"
                        : "Ledger API v2  (Canton 3.x)"));
        cmbGeneration.setSelectedItem(ApiGeneration.V2);

        ButtonGroup grp = new ButtonGroup();
        grp.add(radSandbox);
        grp.add(radStandalone);
        radSandbox.addActionListener(ev -> showBranch());
        radStandalone.addActionListener(ev -> showBranch());

        buildSandbox();
        buildStandalone(lstProfile);
        restore();

        setLayout(new BorderLayout());
        add(buildBody(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        showBranch();
        pack();
        setMinimumSize(new Dimension(Math.max(GuiScale.scale(520), getWidth()),
                getHeight()));
        setLocationRelativeTo(wndOwner);
    }


    /**
     * @return what was chosen, or null when the dialog was cancelled
     */
    public Choice choice() {
        return choice;
    }


    private JPanel buildBody() {
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setBorder(GuiScale.border(12, 12, 8, 12));

        GridBagConstraints con = new GridBagConstraints();
        con.insets = new Insets(GuiScale.scale(3), GuiScale.scale(3),
                GuiScale.scale(3), GuiScale.scale(3));
        con.anchor = GridBagConstraints.WEST;
        con.fill = GridBagConstraints.HORIZONTAL;
        con.gridx = 0;
        con.gridy = 0;
        con.weightx = 1;

        pnl.add(new JLabel("Connect to"), con);

        con.gridy = 1;
        pnl.add(radSandbox, con);

        con.gridy = 2;
        pnl.add(pnlSandbox, con);

        con.gridy = 3;
        pnl.add(radStandalone, con);

        con.gridy = 4;
        pnl.add(pnlStandalone, con);

        con.gridy = 5;
        lblProblem.setForeground(java.awt.Color.RED);
        pnl.add(lblProblem, con);

        return pnl;
    }


    private void buildSandbox() {
        pnlSandbox.setBorder(GuiScale.border(0, 24, 4, 0));

        GridBagConstraints con = new GridBagConstraints();
        con.insets = new Insets(GuiScale.scale(2), GuiScale.scale(2),
                GuiScale.scale(2), GuiScale.scale(2));
        con.anchor = GridBagConstraints.WEST;
        con.fill = GridBagConstraints.HORIZONTAL;

        con.gridx = 0;
        con.gridy = 0;
        pnlSandbox.add(new JLabel("Discovery port"), con);

        con.gridx = 1;
        con.weightx = 1;
        fldPortDiscovery.setText(Integer.toString(N_PORT_DISCOVERY_DEFAULT));
        fldPortDiscovery.setToolTipText("where the Sandbox's discovery endpoint listens."
                + " It carries the host, the ports, the version and the token source");
        pnlSandbox.add(fldPortDiscovery, con);
    }


    /**
     * @param lstProfile the catalogue, whose first entry pre-fills the form
     */
    private void buildStandalone(List<HostProfile> lstProfile) {
        pnlStandalone.setBorder(GuiScale.border(0, 24, 4, 0));

        HostProfile profileFirst = lstProfile == null || lstProfile.isEmpty()
                ? null : lstProfile.get(0);
        if (profileFirst != null) {
            fldName.setText(profileFirst.nameDisplay());
            fldHost.setText(profileFirst.nameHost());
            fldPortLedger.setText(Integer.toString(profileFirst.portLedger()));
            fldPortJson.setText(Integer.toString(profileFirst.portJson()));
            chkTls.setSelected(profileFirst.isTls());
            chkWrite.setSelected(profileFirst.canSubmit());
            fldAudience.setText(profileFirst.strAudience() == null
                    ? "" : profileFirst.strAudience());
            fldScope.setText(profileFirst.strScope() == null ? "" : profileFirst.strScope());
        }

        GridBagConstraints con = new GridBagConstraints();
        con.insets = new Insets(GuiScale.scale(2), GuiScale.scale(2),
                GuiScale.scale(2), GuiScale.scale(2));
        con.anchor = GridBagConstraints.WEST;
        con.fill = GridBagConstraints.HORIZONTAL;

        addRow(pnlStandalone, con, 0, "Name", fldName);
        addRow(pnlStandalone, con, 1, "Host", fldHost);
        addRow(pnlStandalone, con, 2, "Ledger API port", fldPortLedger);
        addRow(pnlStandalone, con, 3, "JSON API port", fldPortJson);
        addRow(pnlStandalone, con, 4, "Generation", cmbGeneration);
        addRow(pnlStandalone, con, 5, "Audience", fldAudience);
        addRow(pnlStandalone, con, 6, "Scope", fldScope);
        addRow(pnlStandalone, con, 7, "Credential", auth);

        con.gridx = 1;
        con.gridy = 8;
        pnlStandalone.add(chkTls, con);

        con.gridy = 9;
        chkWrite.setToolTipText("lets CaQL and the examples write to this ledger;"
                + " off, the tab only reads");
        pnlStandalone.add(chkWrite, con);
    }


    /**
     * @param pnl where the row goes
     * @param con the constraints, reused
     * @param nRow which row
     * @param strLabel what to call the field
     * @param comp the field itself
     */
    private static void addRow(JPanel pnl, GridBagConstraints con, int nRow, String strLabel,
            java.awt.Component comp) {
        con.gridx = 0;
        con.gridy = nRow;
        con.weightx = 0;
        pnl.add(new JLabel(strLabel), con);

        con.gridx = 1;
        con.weightx = 1;
        pnl.add(comp, con);
    }


    private JPanel buildButtons() {
        JPanel pnl = new JPanel(new FlowLayout(FlowLayout.RIGHT, GuiScale.scale(8),
                GuiScale.scale(8)));

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(ev -> {
            this.choice = null;
            dispose();
        });

        JButton btnOk = new JButton("OK");
        btnOk.addActionListener(ev -> accept());
        getRootPane().setDefaultButton(btnOk);

        pnl.add(btnCancel);
        pnl.add(btnOk);
        return pnl;
    }


    /** Puts the last OK's form back, over the catalogue's pre-fill. */
    private void restore() {
        Remembered rem = remembered;
        if (rem == null)
            return;
        radStandalone.setSelected(rem.flagStandalone());
        radSandbox.setSelected(!rem.flagStandalone());
        fldPortDiscovery.setText(rem.strPortDiscovery());
        fldName.setText(rem.strName());
        fldHost.setText(rem.strHost());
        fldPortLedger.setText(rem.strPortLedger());
        fldPortJson.setText(rem.strPortJson());
        cmbGeneration.setSelectedItem(rem.generation());
        fldAudience.setText(rem.strAudience());
        fldScope.setText(rem.strScope());
        chkTls.setSelected(rem.flagTls());
        chkWrite.setSelected(rem.flagWrite());
        if (rem.flagStandalone() && auth.strTokenPasted() == null)
            auth.useTokenPasted(rem.strToken());
    }


    /** Keeps what the form holds now, for the next dialog. */
    private void remember() {
        remembered = new Remembered(radStandalone.isSelected(), fldPortDiscovery.getText(),
                fldName.getText(), fldHost.getText(), fldPortLedger.getText(),
                fldPortJson.getText(), cmbGeneration.getSelectedItem(), fldAudience.getText(),
                fldScope.getText(), chkTls.isSelected(), chkWrite.isSelected(),
                auth.strTokenPasted());
    }


    private void showBranch() {
        boolean flagSandbox = radSandbox.isSelected();
        setEnabledDeep(pnlSandbox, flagSandbox);
        setEnabledDeep(pnlStandalone, !flagSandbox);
    }


    /**
     * @param pnl the branch
     * @param flagOn whether it is the selected one
     */
    private static void setEnabledDeep(JPanel pnl, boolean flagOn) {
        pnl.setEnabled(flagOn);
        for (java.awt.Component comp : pnl.getComponents()) {
            comp.setEnabled(flagOn);
        }
    }


    /**
     * Builds the choice, and REFUSES rather than closing when it cannot.
     *
     * The discovery fetch happens here rather than after the dialog closes, so
     * that a URL naming nothing is reported next to the field that holds it.
     * Closing first and failing in the window behind would make the operator
     * reopen this to correct one character.
     */
    private void accept() {
        lblProblem.setText(" ");
        try {
            this.choice = radSandbox.isSelected() ? choiceSandbox() : choiceStandalone();
            remember();
            dispose();
        }
        catch (RuntimeException ex) {
            this.choice = null;
            lblProblem.setText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            pack();
        }
    }


    /**
     * @return the choice a discovery URL produces
     */
    private Choice choiceSandbox() {
        String strUrl = "http://127.0.0.1:"
                + nPortOf(fldPortDiscovery.getText(), "Discovery port") + "/";

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            Discovery discovery = Discovery.fetch(strUrl);
            return new Choice(discovery, discovery.profile(), ProfileAuth.NONE, null);
        }
        finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }


    /**
     * @return the choice the standalone form produces
     */
    private Choice choiceStandalone() {
        String strName = fldName.getText().trim();
        if (strName.isEmpty())
            throw new IllegalArgumentException("a name is needed");

        String strHost = fldHost.getText().trim();
        if (strHost.isEmpty())
            throw new IllegalArgumentException("a host is needed");

        HostProfile profile = new HostProfile(strName,
                chkTls.isSelected() ? "https" : "http", strHost,
                nPortOf(fldPortLedger.getText(), "Ledger API port"),
                nPortOf(fldPortJson.getText(), "JSON API port"),
                strOrNull(fldScope.getText()), strOrNull(fldAudience.getText()),
                chkWrite.isSelected() ? AccessMode.READ_WRITE : AccessMode.READ_ONLY, "#2e7d32");

        // THE PANEL IS TOLD THE PROFILE BEFORE IT IS ASKED. It reads the
        // settings file keyed by the display name, and a panel still holding
        // the previous target's settings would hand this connection a token
        // issued for a different host.
        //
        // BUT THE TOKEN PASTED IN THIS DIALOG IS KEPT. `setProfile` drops the
        // session token, and it ran HERE, at OK - after the paste - so a token
        // pasted for this very connection was thrown away and the participant
        // answered UNAUTHENTICATED "No token was sent". It worked only when the
        // token had also been saved to the profile's file. His report of
        // 2026-09-23: "Sometimes it takes, sometimes it doesn't."
        String strTokenPasted = auth.strTokenPasted();
        auth.setProfile(profile);
        auth.useTokenPasted(strTokenPasted);
        return new Choice(null, profile, auth.effective(),
                (ApiGeneration) cmbGeneration.getSelectedItem());
    }


    /**
     * @param strValue what was typed
     * @param strWhat what to call it in a refusal
     * @return the port, or 0 when the box was left empty
     */
    private static int nPortOf(String strValue, String strWhat) {
        String strTrim = strValue == null ? "" : strValue.trim();
        if (strTrim.isEmpty())
            return 0;

        try {
            int nPort = Integer.parseInt(strTrim);
            if (nPort < 0 || nPort > 65535)
                throw new IllegalArgumentException(strWhat + " is not a port: " + strTrim);

            return nPort;
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(strWhat + " is not a number: " + strTrim, ex);
        }
    }


    /**
     * @param strValue what was typed
     * @return it trimmed, or null when it was blank
     */
    private static String strOrNull(String strValue) {
        String strTrim = strValue == null ? "" : strValue.trim();
        return strTrim.isEmpty() ? null : strTrim;
    }

}
