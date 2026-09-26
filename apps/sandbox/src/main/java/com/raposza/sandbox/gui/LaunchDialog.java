// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * What a component was actually started with, in a window that can be left open
 * beside the log.
 *
 * <h2>Why a dialog rather than another tab</h2>
 *
 * The command and the configuration are read at ONE moment - when something has
 * gone wrong and the question is what this process was told - and they are read
 * beside the output that raised the question. A tab would be a permanent
 * fixture answering an occasional question, and would push the logs one click
 * further away for the rest of the session.
 *
 * MODELESS on purpose. A modal dialog cannot be held beside the pane it is
 * being compared against, which is the only thing anybody does with it.
 *
 * Author Claude/bentzn
 */
public final class LaunchDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final int N_ROWS = 28;

    private static final int N_COLUMNS = 110;


    private LaunchDialog(Window owner, String strTitle, String strBody) {
        super(owner, strTitle, ModalityType.MODELESS);

        JTextArea area = new JTextArea(strBody, N_ROWS, N_COLUMNS);
        area.setEditable(false);
        area.setLineWrap(false);
        GuiTheme.mono(area);
        area.setBorder(GuiTheme.borderScaled(8, 8, 8, 8));
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(GuiTheme.colCardBorder(), 1, true));

        JButton btnCopy = new JButton("Copy");
        btnCopy.addActionListener(evt -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(area.getText()), null));

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(evt -> dispose());

        JPanel pnlBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, GuiTheme.scale(8),
                GuiTheme.scale(4)));
        pnlBar.setOpaque(false);
        pnlBar.add(btnCopy);
        pnlBar.add(btnClose);

        JPanel pnlPage = new JPanel(new BorderLayout(0, GuiTheme.scale(GuiTheme.N_GAP)));
        pnlPage.setBorder(GuiTheme.borderScaled(GuiTheme.N_PAD_CARD, GuiTheme.N_PAD_CARD,
                GuiTheme.N_PAD_CARD, GuiTheme.N_PAD_CARD));
        pnlPage.add(scroll, BorderLayout.CENTER);
        pnlPage.add(pnlBar, BorderLayout.SOUTH);

        setContentPane(pnlPage);
        pack();
    }


    /**
     * @param parent anything in the window this belongs to, or null
     * @param strTitle the dialog's own title
     * @param strBody what to show; monospaced and never wrapped, because a
     *        command line broken at the window edge is a command line nobody
     *        can copy
     */
    public static void show(Component parent, String strTitle, String strBody) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        LaunchDialog dialog = new LaunchDialog(owner, strTitle, strBody);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

}
