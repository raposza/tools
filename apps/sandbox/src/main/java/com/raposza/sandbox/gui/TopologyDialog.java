// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.app.DiscoveryDoc;

import java.awt.Component;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/**
 * WHICH STACK THIS RUN IS, asked once, before any window exists.
 *
 * <h2>Why a modal and not a control on a tab</h2>
 *
 * Operator instruction, 2026-09-21. The two topologies do not share a pane
 * set - a LocalNet stack has three participants and three databases, so "the
 * participant" and "the database" have no referent in it - and a window that
 * had to rebuild itself when a combo box moved would carry both sets of panes
 * and hide one. Asking first means each window is built for what it holds.
 *
 * <h2>The values are the discovery document's, not new ones</h2>
 *
 * {@link DiscoveryDoc#STR_TOPOLOGY_SANDBOX} and
 * {@link DiscoveryDoc#STR_TOPOLOGY_LOCALNET} are what D-788 publishes as
 * `topology`, and this dialog returns those same strings so the window, the
 * document and the operator are all saying one word.
 *
 * NOT `Modals.idxOption`: that one is WARNING styled and honours the
 * unattended suppression, which answers -1. A choice with no default cannot be
 * suppressed into one.
 *
 * Author Claude/bentzn
 */
final class TopologyDialog {

    private static final String STR_SINGLE = "Single participant";

    private static final String STR_LOCALNET = "LocalNetND";

    private static final String STR_ASK = "What should this Sandbox start?";

    private static final String STR_DETAIL =
            "Single participant - one Canton, one database, the released Sandbox.\n"
            + "LocalNetND - the Splice network natively: sv, app-provider and app-user.";


    private TopologyDialog() {
    }


    /**
     * @param owner the parent component, or null
     * @return one of the DiscoveryDoc STR_TOPOLOGY constants, or null when the
     *         dialog was closed - which is a refusal to start anything
     */
    static String strChoose(Component owner) {
        // COMPONENTS, NOT STRINGS. JOptionPane renders a String option as a
        // button of its own making, which cannot be coloured without reaching
        // into the dialog's component tree afterwards and guessing which
        // buttons are the options. A Component option is rendered as given -
        // and wired to NOTHING, so each one sets the pane's value itself.
        JButton btnSingle = btnOption(STR_SINGLE);
        JButton btnLocalNet = btnOption(STR_LOCALNET);
        Object[] arrOption = { btnSingle, btnLocalNet };
        // NOT showOptionDialog. Passing a null initialValue is NOT enough,
        // measured 2026-09-22: JOptionPane.selectInitialValue() still moves
        // focus to the first option, and the look and feel then draws it as
        // the answer - so the question arrives already answered and a Return
        // pressed out of habit sends it. The override below is the documented
        // hook for exactly that, and clearing the root pane's default button
        // takes away the ring that survives it.
        //
        // THE BUTTONS STAY FOCUSABLE. Making them unfocusable would also
        // unselect them and would take the keyboard away from the dialog
        // altogether, which is a worse answer than the one being fixed.
        PaneUnselected pane = new PaneUnselected(STR_ASK + "\n\n" + STR_DETAIL,
                arrOption);
        // SETTING THE VALUE IS WHAT CLOSES THE DIALOG - `createDialog`
        // installs a listener on it. A Component option gets no such wiring
        // of its own, so without these two the buttons would draw and do
        // nothing.
        btnSingle.addActionListener(evt -> pane.setValue(STR_SINGLE));
        btnLocalNet.addActionListener(evt -> pane.setValue(STR_LOCALNET));
        JDialog dlg = pane.createDialog(owner, "Raposza Sandbox");
        dlg.getRootPane().setDefaultButton(null);
        dlg.setVisible(true);
        dlg.dispose();

        // BY VALUE, not by index. `getValue()` answers with the option
        // object, and with UNINITIALIZED_VALUE or null when the dialog was
        // closed - which is a refusal to start anything.
        Object objChosen = pane.getValue();
        if (STR_SINGLE.equals(objChosen))
            return DiscoveryDoc.STR_TOPOLOGY_SANDBOX;
        if (STR_LOCALNET.equals(objChosen))
            return DiscoveryDoc.STR_TOPOLOGY_LOCALNET;
        return null;
    }


    /**
     * BOTH OPTIONS CARRY THE ACCENT, and neither carries anything else.
     * `gui_design.md` section 3: the accent token is `2563EB`, and
     * {@link GuiTheme#colTextOn} picks black or white off the fill's Rec. 709
     * luma so the label is readable in either theme rather than white on a
     * pale blue.
     *
     * THE SAME COLOUR ON BOTH is the point. These are two equal choices and
     * the dialog deliberately preselects neither; colouring one of them
     * differently would answer the question the focus rules just stopped
     * answering.
     *
     * @param strText the label
     * @return the option button
     */
    private static JButton btnOption(String strText) {
        JButton btn = new JButton(strText);
        btn.setBackground(GuiTheme.COL_ACCENT);
        btn.setForeground(GuiTheme.colTextOn(GuiTheme.COL_ACCENT));
        // THE RING IS THE THING BEING REMOVED everywhere else in this file.
        btn.setFocusPainted(false);
        return btn;
    }


    /** A pane that leaves the focus where it found it - see strChoose. */
    private static final class PaneUnselected extends JOptionPane {

        private static final long serialVersionUID = 1L;


        PaneUnselected(String strMessage, Object[] arrOption) {
            super(strMessage, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                    null, arrOption, null);
            // SO THE PANE CAN HOLD THE FOCUS ITSELF - see below.
            setFocusable(true);
        }


        @Override
        public void selectInitialValue() {
            // THE PANE, NOT THE FIRST OPTION. Doing nothing here is not
            // enough: the focus then lands on the first button anyway and
            // the look and feel rings it. MEASURED 2026-09-22 under Xvfb -
            // with this, both buttons report focusOwner false and
            // isDefaultButton false, and Tab still reaches both.
            requestFocusInWindow();
        }
    }

}
