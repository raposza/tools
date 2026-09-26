// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/**
 * One lamp's coloured circle.
 *
 * IT WAS NESTED INSIDE `LampBar` until a second bar existed. A LocalNet stack
 * names four components of its own and its bar is built from a list rather
 * than from four fields, so the two bars differ in everything except this -
 * and a second painter would be a second answer to what a lamp looks like.
 *
 * Author Claude/bentzn
 */
final class Dot extends JComponent {

    private static final long serialVersionUID = 1L;

    private static final int N_DOT = 12;

    private Color colFill = GuiTheme.COL_LAMP_OFF;


    Dot() {
        int nSize = GuiTheme.scale(N_DOT);
        setPreferredSize(new Dimension(nSize, nSize));
        setMinimumSize(new Dimension(nSize, nSize));
    }


    void setColour(Color colNew) {
        if (colNew.equals(colFill))
            return;
        colFill = colNew;
        repaint();
    }


    @Override
    protected void paintComponent(Graphics gfx) {
        Graphics2D gfx2 = (Graphics2D) gfx.create();
        try {
            gfx2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            gfx2.setColor(colFill);
            gfx2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
        }
        finally {
            gfx2.dispose();
        }
    }

}
