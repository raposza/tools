// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The strip that appears when this machine has no toolchain.
 *
 * <h2>It is not there unless it has something to say</h2>
 *
 * A permanently visible row explaining the state of a machine that is fine is a
 * line of window height spent on nothing. This is hidden by default and the
 * window shows it only when there is a reason.
 *
 * <h2>It offers one act</h2>
 *
 * No version box, no channel choice, no options: acquire what is needed, newest,
 * into the places the vendor's own tools use. Anything more is a decision the
 * reader of this banner cannot yet make, because they have nothing installed to
 * make it about.
 *
 * <h2>The install itself is not here</h2>
 *
 * This panel knows a message, a button and a disabled state. What the button
 * does is the window's, so a long-running network act is never wired to a
 * component's own listener where it would run on the event thread.
 *
 * Author Claude/bentzn
 */
public final class ToolchainBanner extends JPanel {

    private static final long serialVersionUID = 1L;

    /** What it says when there is nothing on the machine. */
    public static final String STR_MESSAGE =
            "No Canton found. Raposza can install the Digital Asset Package Manager "
            + "and the newest Canton it publishes.";

    public static final String STR_BUTTON = "Install";

    public static final String STR_BUTTON_BUSY = "Installing...";

    private final JLabel lblMessage = new JLabel(STR_MESSAGE);

    private final JButton btnInstall = new JButton(STR_BUTTON);


    public ToolchainBanner() {
        super(new BorderLayout(GuiTheme.scale(GuiTheme.N_GAP), 0));
        setOpaque(false);
        setBorder(GuiTheme.borderScaled(0, 0, GuiTheme.N_GAP, 0));

        lblMessage.setForeground(GuiTheme.colMuted());
        add(lblMessage, BorderLayout.CENTER);
        add(btnInstall, BorderLayout.EAST);
        setVisible(false);
    }


    /**
     * @param action what the button does; never null
     */
    public void useAction(Runnable action) {
        if (action == null)
            throw new IllegalArgumentException("an action is required");

        btnInstall.addActionListener(evt -> action.run());
    }


    /**
     * @param flagOffer whether this machine has a reason to see the strip
     */
    public void setOffered(boolean flagOffer) {
        setVisible(flagOffer);
    }


    /**
     * The button is the only thing disabled, and the strip stays up: what it
     * says is still true while the install runs, and hiding it would take the
     * only explanation of what the log is doing.
     *
     * @param flagBusy whether an install is running
     */
    public void setBusy(boolean flagBusy) {
        btnInstall.setEnabled(!flagBusy);
        btnInstall.setText(flagBusy ? STR_BUTTON_BUSY : STR_BUTTON);
    }


    /**
     * @param strLine what to say instead of the standing message
     */
    public void setMessage(String strLine) {
        lblMessage.setText(strLine == null || strLine.isBlank() ? STR_MESSAGE : strLine);
    }


    /**
     * @return whether the button can be pressed
     */
    public boolean isActionEnabled() {
        return btnInstall.isEnabled();
    }

}
