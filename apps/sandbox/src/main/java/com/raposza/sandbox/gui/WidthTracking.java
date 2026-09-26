// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * A panel inside a scroll pane that is as wide as the viewport and no wider.
 *
 * A {@link javax.swing.JScrollPane} lays its view out at the view's PREFERRED
 * width and scrolls horizontally past whatever does not fit. For a form that is
 * the wrong behaviour twice over: the fields are cut off rather than narrowed,
 * and the operator gets a horizontal scrollbar instead of the layout doing what
 * the layout manager was configured to do.
 *
 * Tracking the viewport width makes the width available to the form, so
 * `weightx` and `fill` mean what they say and the fields narrow with the
 * divider. Height is tracked only while the content is SHORTER than the
 * viewport, so a short card fills the space and a long one still scrolls.
 *
 * Author Claude/bentzn
 */
final class WidthTracking extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    private static final int N_UNIT = 16;

    private static final int N_BLOCK = 96;


    /**
     * @param inner what goes in it; never null
     */
    WidthTracking(JComponent inner) {
        super(new BorderLayout());
        if (inner == null)
            throw new IllegalArgumentException("a component is required");
        setOpaque(false);
        add(inner, BorderLayout.CENTER);
    }


    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }


    @Override
    public int getScrollableUnitIncrement(Rectangle rect, int nOrientation, int nDirection) {
        return GuiTheme.scale(N_UNIT);
    }


    @Override
    public int getScrollableBlockIncrement(Rectangle rect, int nOrientation, int nDirection) {
        return nOrientation == SwingConstants.VERTICAL ? rect.height : GuiTheme.scale(N_BLOCK);
    }


    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }


    @Override
    public boolean getScrollableTracksViewportHeight() {
        // TALL ENOUGH TO FILL, and no taller. Returning false laid the
        // card out at its preferred height and left the rest of the
        // viewport empty; returning true unconditionally would stop the
        // pane scrolling once the content outgrew the window.
        return getParent() instanceof javax.swing.JViewport port
                && port.getHeight() > getPreferredSize().height;
    }
}
