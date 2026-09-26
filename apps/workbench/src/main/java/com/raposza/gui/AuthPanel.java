// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.profile.HostProfile;
import com.raposza.jwt.AuthMode;
import com.raposza.jwt.ProfileAuth;
import com.raposza.jwt.ProfileAuthStore;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Where the token for the selected profile comes from, and how to paste one.
 *
 * Three states, and the button always says which: no auth, settings from the
 * profile's file, or a token pasted in this session. A pasted token wins over
 * the file for the session and is not written anywhere unless the operator
 * ticks the box.
 *
 * A settings file that exists but does not parse is NOT downgraded to "no
 * auth". The button turns into a warning and connecting is still possible,
 * because reaching a sandbox is still useful - but the operator is told, rather
 * than left to wonder why a participant that was configured for a token is
 * refusing them.
 *
 * The token itself never appears on the button, in a tooltip or in a message.
 * StaticTokenSource.describe() summarises a token by its claims and that is the
 * most that is ever shown.
 *
 * Author Claude/bentzn
 */
public final class AuthPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JButton btnAuth = new JButton("auth: none");

    private transient Path dirHome = Path.of(System.getProperty("user.home"));
    private transient HostProfile profile;
    private transient ProfileAuth authFile = ProfileAuth.NONE;
    private transient String strTokenSession;
    private transient String strProblem;


    public AuthPanel() {
        super(new BorderLayout());
        btnAuth.setToolTipText("no authorization header is sent");
        btnAuth.addActionListener(ev -> openDialog());
        add(btnAuth, BorderLayout.CENTER);
    }


    /**
     * @param dirHomeNew the home directory holding ~/.raposza/profiles
     */
    public void setHome(Path dirHomeNew) {
        this.dirHome = dirHomeNew;
    }


    /**
     * Reads the settings file for a profile. Any pasted token is dropped: it
     * belonged to the participant that was selected when it was pasted, and
     * carrying it to another one is how a token reaches a host it was never
     * issued for.
     *
     * @param profileNew the newly selected profile, may be null
     */
    public void setProfile(HostProfile profileNew) {
        this.profile = profileNew;
        this.strTokenSession = null;
        this.strProblem = null;
        this.authFile = ProfileAuth.NONE;

        if (profileNew != null) {
            try {
                authFile = ProfileAuthStore
                        .load(ProfileAuthStore.fileFor(dirHome, profileNew.nameDisplay()));
            }
            catch (RuntimeException ex) {
                strProblem = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
        }
        refresh();
    }


    /**
     * @return the token pasted into this panel this session, or null
     */
    public String strTokenPasted() {
        return strTokenSession;
    }


    /**
     * Puts a pasted token back after {@link #setProfile} has dropped it - the
     * Connect dialog's case, where the token was pasted for the connection
     * being made and the profile is only named when OK is pressed.
     *
     * @param strToken the token, or null to leave the panel as it is
     */
    public void useTokenPasted(String strToken) {
        if (strToken == null || strToken.isBlank())
            return;
        strTokenSession = strToken.trim();
        refresh();
    }


    /**
     * @return the settings a connection should use: the pasted token when there
     *         is one, the file's settings otherwise, NONE when there is neither
     */
    public ProfileAuth effective() {
        if (strTokenSession == null)
            return authFile;

        return new ProfileAuth(AuthMode.TOKEN, strTokenSession, null, null, authFile.strSubject(),
                authFile.strScope(), authFile.strAudience(), authFile.lstActAs(),
                authFile.lstReadAs(), authFile.flagAdmin(), authFile.idApplication(),
                authFile.idLedger(), authFile.idParticipant(), authFile.ttl());
    }


    /** @return why the settings file could not be read, null when it could */
    public String strProblem() {
        return strProblem;
    }


    private void openDialog() {
        JTextArea areaToken = new JTextArea(6, 46);
        areaToken.setLineWrap(true);
        areaToken.setWrapStyleWord(false);

        JCheckBox chkSave = new JCheckBox("save to this profile's settings file");
        chkSave.setToolTipText(profile == null ? "" : ProfileAuthStore
                .fileFor(dirHome, profile.nameDisplay()).toString());

        JPanel pnl = new JPanel(new BorderLayout(0, GuiScale.scale(6)));
        pnl.setBorder(GuiScale.border(4, 4, 4, 4));
        pnl.add(new JLabel(describeCurrent()), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(areaToken);
        scroll.setPreferredSize(GuiScale.dim(560, 130));
        pnl.add(scroll, BorderLayout.CENTER);
        pnl.add(chkSave, BorderLayout.SOUTH);

        // Two clears, deliberately separate. The pasted one is this session and
        // costs nothing to redo; the stored one is a file on disk and is the
        // only one that outlives the window.
        String[] arrOption = { "Apply", "Clear pasted token", "Delete stored token", "Cancel" };
        int numChoice = JOptionPane.showOptionDialog(this, pnl, "Token for this participant",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, arrOption,
                arrOption[0]);

        if (numChoice == 1) {
            strTokenSession = null;
            refresh();
            return;
        }
        if (numChoice == 2) {
            clearStored();
            return;
        }
        if (numChoice != 0)
            return;

        String strToken = areaToken.getText() == null ? "" : areaToken.getText().trim();
        if (strToken.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nothing pasted; the token was not changed.",
                    "workbench", JOptionPane.WARNING_MESSAGE);
            return;
        }

        strTokenSession = strToken;

        if (chkSave.isSelected() && profile != null)
            save(strToken);

        refresh();
    }


    /**
     * Deletes the token stored for this profile, after naming the file.
     *
     * Confirmed because it is not undoable and the file may hold a token that
     * was minted somewhere the operator no longer has access to. The path is in
     * the question rather than in a tooltip: "delete the stored token" is not
     * an answerable question until you know which file it means.
     *
     * The session token is NOT touched. It was pasted deliberately and after
     * this, and dropping it here would disconnect a working window as a side
     * effect of tidying a file.
     */
    private void clearStored() {
        if (profile == null)
            return;

        Path fileSettings = ProfileAuthStore.fileFor(dirHome, profile.nameDisplay());
        int numAnswer = JOptionPane.showConfirmDialog(this,
                "Delete the stored token for '" + profile.nameDisplay() + "'?\n\n"
                        + fileSettings + "\n\nOther settings in the file are kept. This cannot"
                        + " be undone, and a token\nthat has been on disk should be revoked at"
                        + " the issuer as well.",
                "workbench", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (numAnswer != JOptionPane.YES_OPTION)
            return;

        try {
            String strOutcome = ProfileAuthStore.clearToken(fileSettings);
            authFile = ProfileAuthStore.load(fileSettings);
            strProblem = null;
            JOptionPane.showMessageDialog(this, strOutcome, "workbench",
                    JOptionPane.INFORMATION_MESSAGE);
        }
        catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not clear the stored token.\n\n" + ex.getMessage(), "workbench",
                    JOptionPane.ERROR_MESSAGE);
        }
        refresh();
    }


    /**
     * Writing a credential is reported either way. A silent success leaves the
     * operator unsure whether the token will still be there tomorrow, and a
     * silent permission failure leaves it world readable.
     */
    private void save(String strToken) {
        Path fileSettings = ProfileAuthStore.fileFor(dirHome, profile.nameDisplay());
        try {
            ProfileAuthStore.saveToken(fileSettings, strToken);
            String strPerm = ProfileAuthStore.restrict(fileSettings);
            String strMsg = "Saved to\n" + fileSettings
                    + (strPerm == null ? "\n\nPermissions set to owner-only."
                            : "\n\nWARNING: could not set owner-only permissions: " + strPerm);
            JOptionPane.showMessageDialog(this, strMsg, "workbench",
                    JOptionPane.INFORMATION_MESSAGE);
            authFile = ProfileAuthStore.load(fileSettings);
        }
        catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the token.\n\n" + ex.getMessage(),
                    "workbench", JOptionPane.ERROR_MESSAGE);
        }
    }


    private String describeCurrent() {
        if (strProblem != null)
            return "Settings file problem: " + strProblem;
        if (strTokenSession != null)
            return "A token is pasted for this session. Paste another to replace it.";

        switch (authFile.mode()) {
            case TOKEN:
                return "This profile has a stored token. Pasting one overrides it for this session.";
            case JWKS:
                return "This profile mints its own tokens from a key file.";
            default:
                return "This profile sends no token. Paste one to authenticate.";
        }
    }


    private void refresh() {
        if (strProblem != null) {
            btnAuth.setText("auth: SETTINGS ERROR");
            btnAuth.setToolTipText(strProblem);
            return;
        }
        if (strTokenSession != null) {
            btnAuth.setText("auth: pasted \u25be");
            btnAuth.setToolTipText("a token pasted in this session, not saved unless you asked");
            return;
        }

        switch (authFile.mode()) {
            case TOKEN:
                btnAuth.setText("auth: stored \u25be");
                btnAuth.setToolTipText("token from this profile's settings file");
                break;
            case JWKS:
                btnAuth.setText("auth: minted \u25be");
                btnAuth.setToolTipText("minted from "
                        + (authFile.fileJwks() == null ? "?" : authFile.fileJwks().getFileName()));
                break;
            default:
                btnAuth.setText("auth: none \u25be");
                btnAuth.setToolTipText("no authorization header is sent");
                break;
        }
    }

}
