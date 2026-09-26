// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.formdev.flatlaf.util.UIScale;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

/**
 * Lengths at the current scale, for a window whose sizes are written in
 * pixels.
 *
 * <h2>Why this exists, and what it is NOT</h2>
 *
 * There are two scalings and only one of them reaches a raw pixel. The JRE's
 * system scale - `sun.java2d.uiScale` - is a transform over everything the
 * window paints, so a hardcoded `13` comes out at 26 physical pixels without
 * anything in this class. FlatLaf's USER scale is not a transform: it enlarges
 * the look and feel's own defaults, its fonts and its own painted lengths, and
 * leaves a `new Dimension(340, 400)` and a `new Font(..., 13)` exactly where
 * they were written.
 *
 * Windows hands the JRE a system scale and the distinction never shows.
 * A GTK desktop that carries its scale as a font DPI - LMDE among them - gives
 * the JRE a system scale of 1 and FlatLaf a user scale of 2, so a window built
 * out of raw pixels comes out at HALF the size beside the same desktop's other
 * windows, with monospace text half the height of the labels next to it. That
 * is the defect this closes, and the Sandbox window does not have it because
 * every length in it already goes through {@link UIScale}.
 *
 * <h2>What goes through here and what must not</h2>
 *
 * Every hardcoded pixel: preferred and minimum sizes, layout gaps, insets,
 * empty borders, divider locations, and the point size of a font this code
 * constructs itself - gui_design.md sec. 5.
 *
 * NOT a length already derived from a component's font. `getFont().getSize()`
 * and a `FontMetrics` height come off the look and feel's own font, which
 * FlatLaf has already scaled; scaling one again applies the factor twice.
 *
 * Author Claude/bentzn
 */
public final class GuiScale {

    private GuiScale() {
    }


    /**
     * @param nPx an unscaled pixel count
     * @return the same length at the current scale
     */
    public static int scale(int nPx) {
        return UIScale.scale(nPx);
    }


    /**
     * @param nWidth unscaled
     * @param nHeight unscaled
     * @return the scaled dimension
     */
    public static Dimension dim(int nWidth, int nHeight) {
        return new Dimension(scale(nWidth), scale(nHeight));
    }


    /**
     * The scaled dimension, held inside the desktop's usable area.
     *
     * A forced scale on a small screen otherwise asks for a window larger than
     * the display, which a window manager grants and the operator then cannot
     * reach the bottom of.
     *
     * @param nWidth unscaled
     * @param nHeight unscaled
     * @return the scaled dimension, clamped to `getMaximumWindowBounds()`
     */
    public static Dimension dimClamped(int nWidth, int nHeight) {
        Dimension dimWanted = dim(nWidth, nHeight);
        Rectangle rectMax = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        return new Dimension(Math.min(dimWanted.width, rectMax.width),
                Math.min(dimWanted.height, rectMax.height));
    }


    /**
     * @param nTop unscaled
     * @param nLeft unscaled
     * @param nBottom unscaled
     * @param nRight unscaled
     * @return an empty border at the current scale
     */
    public static Border border(int nTop, int nLeft, int nBottom, int nRight) {
        return BorderFactory.createEmptyBorder(scale(nTop), scale(nLeft), scale(nBottom),
                scale(nRight));
    }


    /**
     * @param nSize the point size as the design writes it, unscaled
     * @return the monospaced font - Hack, D-848 - at that size, scaled
     */
    public static Font fontMono(int nSize) {
        return new Font(GuiDesign.strFamilyMono(), Font.PLAIN, scale(nSize));
    }

}
