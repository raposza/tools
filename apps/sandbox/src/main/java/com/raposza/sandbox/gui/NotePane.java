// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * A tab that exists and has nothing in it yet, saying which.
 *
 * A blank panel and an unbuilt one look identical, and the window has already
 * carried a pane that was believed finished for a day because nobody opened
 * it. A tab that states what it is waiting for cannot be mistaken for one that
 * is working.
 *
 * Author Claude/bentzn
 */
public final class NotePane extends JPanel {

    private static final long serialVersionUID = 1L;


    /**
     * @param strText what this tab is for and what it is waiting for
     */
    public NotePane(String strText) {
        super(new BorderLayout());
        setOpaque(false);

        JTextArea areaNote = new JTextArea(strText);
        areaNote.setEditable(false);
        areaNote.setLineWrap(true);
        areaNote.setWrapStyleWord(true);
        areaNote.setOpaque(false);
        areaNote.setBorder(GuiTheme.borderScaled(4, 4, 4, 4));
        areaNote.setForeground(GuiTheme.colMuted());

        JScrollPane scroll = new JScrollPane(areaNote);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        add(scroll, BorderLayout.CENTER);
    }

}
