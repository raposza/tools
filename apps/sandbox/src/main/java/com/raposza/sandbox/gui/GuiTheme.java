// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import com.raposza.design.Tokens;
import com.raposza.design.UiFonts;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * The project's GUI token table, applied.
 *
 * Three of the four mechanics recorded as corrections to that table are here
 * rather than in the panels, because each of them fails SILENTLY when it is got
 * wrong:
 *
 * <ul>
 * <li>the accent reaches FlatLaf as `@accentColor` through
 * {@link FlatLaf#setGlobalExtraDefaults}, BEFORE the look and feel instance
 * exists. `UIManager.put("Component.accentColor", ...)` is not a FlatLaf key
 * and does nothing at all;</li>
 * <li>a chip is a {@link JPanel} carrying the style with a plain
 * {@link JLabel} inside it. `JLabel` has no `arc` key, and applying one throws
 * inside the style parser, which abandons the REST of the string - so the label
 * loses its colours too and looks merely unstyled rather than broken;</li>
 * <li>`Table.rowHeight` is set per component through {@link UIScale#scale},
 * never through `UIManager`: values consumed by Swing core are not scaled by
 * FlatLaf when they arrive as a plain Integer.</li>
 * </ul>
 *
 * The fourth, `TitlePane.menuBarEmbedded`, needs FlatLaf window decorations and
 * is not used here at all.
 *
 * <h2>Scaling, and the defect this does NOT repeat</h2>
 *
 * `WorkbenchApp` sets `sun.java2d.uiScale=2` unconditionally, which hardcodes a
 * 4k assumption onto every machine including the laptop - the standing gap
 * named in section 7 of the guideline. This window sets NEITHER scaling
 * property unless one was already given on the command line, so the JRE's own
 * detection stands and `-Dsun.java2d.uiScale=2` still works for anyone who
 * wants it.
 *
 * Author Claude/bentzn
 */
public final class GuiTheme {

    /** Accent, light theme - the design package's one accent. */
    public static final Color COL_ACCENT = Tokens.COL_ACCENT;

    /** OK / healthy. */
    public static final Color COL_OK = Tokens.COL_OK;

    /** Not a token of the guideline: a failed start needs its own colour. */
    public static final Color COL_BAD = Tokens.COL_BAD;

    /**
     * Stopped. The muted token is `#f2f2f2` in this theme, and a chip filled
     * with it carrying white text is 1.1:1 - a pill that reads as blank. The
     * state a stopped window spends all its time in needs a fill of its own.
     */
    public static final Color COL_STOPPED = Tokens.COL_STOPPED;

    /** Starting or stopping - in flight, neither good nor bad. */
    public static final Color COL_BUSY = Tokens.COL_BUSY;

    /**
     * A lamp that is OFF - not part of this stack at all.
     *
     * LIGHTER than {@link #COL_STOPPED}, which is a CHIP fill and has to carry
     * white text on it. A dot carries no text, and at the chip's value it read
     * as a component that was merely dark rather than one that is not here.
     */
    public static final Color COL_LAMP_OFF = Tokens.COL_LAMP_OFF;

    /**
     * A lamp that is PENDING: this start will bring the component up and its
     * turn has not come. The accent, because it is a statement about the
     * stack's plan rather than about a component's own health.
     */
    public static final Color COL_PENDING = COL_ACCENT;

    public static final int N_PAD_PAGE = Tokens.N_PAD_PAGE;

    public static final int N_PAD_CARD = Tokens.N_PAD_CARD;

    public static final int N_GAP = Tokens.N_GAP;

    public static final int N_ROW_HEIGHT = Tokens.N_ROW_HEIGHT;

    /** Every component carrying the monospaced font, held weakly. */
    private static final List<WeakReference<JComponent>> LST_MONO = new ArrayList<>();

    /** The monospaced font set by {@link #setFontMono}, or null for the default. */
    private static Font fontMono;

    /** Whether the design package's faces reached the graphics environment. */
    private static boolean flagFontsInstalled;


    private GuiTheme() {
    }


    /**
     * Installs the look and feel. Call once, before any component exists.
     */
    public static void install() {
        // Inter and Hack from the design package's jar, BEFORE FlatLaf
        // resolves a font - D-848, todo.md DS-7. A broken jar is logged and
        // the desktop font stands; it is not a reason to lose the theme.
        // THE TEMPORARY DIRECTORY FIRST - observed 2026-09-26 on a clean Windows
        // guest: with TMP naming a directory that did not exist yet, the first
        // start printed "design fonts not installed" and fell back to the
        // desktop font; every later start, with the directory present, did not.
        try {
            java.nio.file.Files.createDirectories(
                    java.nio.file.Path.of(System.getProperty("java.io.tmpdir")));
        }
        catch (java.io.IOException | RuntimeException ex) {
            System.err.println("temporary directory not created: " + ex.getMessage());
        }
        try {
            UiFonts.install();
            flagFontsInstalled = true;
        }
        catch (RuntimeException ex) {
            System.err.println("design fonts not installed: " + ex.getMessage());
        }

        // BEFORE the instance. This is mechanic 1 of the guideline and it is
        // the one with no symptom: a wrong key leaves a window that looks
        // finished and is simply the wrong colour.
        Map<String, String> mapExtra = new LinkedHashMap<>();
        mapExtra.put("@accentColor", Tokens.str("--rz-accent"));
        // THE NEUTRALS - one set for Swing and the web, his instruction of
        // 2026-09-24. FlatLaf derives the rest of its greys from these.
        mapExtra.put("@background", Tokens.str("--rz-bg"));
        mapExtra.put("@componentBackground", Tokens.str("--rz-surface"));
        mapExtra.put("@foreground", Tokens.str("--rz-fg"));
        mapExtra.put("@disabledForeground", Tokens.str("--rz-fg-disabled"));
        mapExtra.put("Component.borderColor", Tokens.str("--rz-border"));
        FlatLaf.setGlobalExtraDefaults(mapExtra);

        UIManager.put("Component.arc", Tokens.N_ARC);
        UIManager.put("Button.arc", Tokens.N_ARC);
        UIManager.put("TextComponent.arc", Tokens.N_ARC_TEXT);
        UIManager.put("CheckBox.arc", Tokens.N_ARC_CHECK);
        UIManager.put("ProgressBar.arc", Tokens.N_ARC_PROGRESS);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.arrowType", "chevron");
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 999);

        // INTER - D-848. A PREFERRED family, not a defaultFont: FlatLaf keeps
        // the size and scale it derives from the desktop font and swaps only
        // the family.
        FlatLaf.setPreferredFontFamily(UiFonts.STR_FAMILY_UI);

        FlatLightLaf.setup();
    }


    /**
     * Gives a component the monospaced font and remembers it, so a later
     * {@link #setFontMono} reaches it. Every monospaced pane goes through here
     * rather than building its own `new Font(Font.MONOSPACED, ...)`.
     *
     * Until a font is set, the component gets Hack at its own size - D-848 -
     * or the logical MONOSPACED if the design fonts did not install.
     *
     * @param comp the component; held weakly
     */
    public static void mono(JComponent comp) {
        LST_MONO.removeIf(ref -> ref.get() == null);
        LST_MONO.add(new WeakReference<>(comp));
        comp.setFont(fontMono != null ? fontMono : fontMonoAt(comp.getFont().getSize()));
    }


    /**
     * @return the monospaced font: the one set, else Hack at the UI font's size
     */
    public static Font fontMonoCurrent() {
        if (fontMono != null)
            return fontMono;
        Font fontUi = UIManager.getFont("defaultFont");
        return fontMonoAt(fontUi == null ? scale(Tokens.N_FONT_SIZE) : fontUi.getSize());
    }


    /**
     * Sets the monospaced font on every component registered through
     * {@link #mono} that is still alive. Event dispatch thread only.
     *
     * @param font the font, or null for Hack at the UI size
     */
    public static void setFontMono(Font font) {
        fontMono = font;
        Font fontNew = fontMonoCurrent();
        LST_MONO.removeIf(ref -> ref.get() == null);
        for (WeakReference<JComponent> ref : LST_MONO) {
            JComponent comp = ref.get();
            if (comp == null)
                continue;
            comp.setFont(fontNew);
            comp.revalidate();
            comp.repaint();
        }
    }


    /**
     * @return the family name for monospaced text in HTML the window renders
     */
    public static String strFamilyMono() {
        return flagFontsInstalled ? UiFonts.STR_FAMILY_MONO : Font.MONOSPACED;
    }


    private static Font fontMonoAt(int nSize) {
        return flagFontsInstalled ? UiFonts.fontMono(nSize) : new Font(Font.MONOSPACED, Font.PLAIN, nSize);
    }


    /**
     * @return the card background - the surface token, as on the web
     */
    public static Color colCard() {
        return Tokens.COL_SURFACE;
    }

    /**
     * @return the card border colour - the token
     */
    public static Color colCardBorder() {
        return Tokens.COL_BORDER;
    }


    /**
     * @return the colour for text that is present but secondary - the token
     */
    public static Color colMuted() {
        return Tokens.COL_FG_MUTED;
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
    public static Dimension dimScaled(int nWidth, int nHeight) {
        return new Dimension(scale(nWidth), scale(nHeight));
    }


    /**
     * @param nTop unscaled
     * @param nLeft unscaled
     * @param nBottom unscaled
     * @param nRight unscaled
     * @return an empty border at the current scale
     */
    public static Border borderScaled(int nTop, int nLeft, int nBottom, int nRight) {
        return BorderFactory.createEmptyBorder(scale(nTop), scale(nLeft), scale(nBottom),
                scale(nRight));
    }


    /**
     * A card: one line of border, rounded, padded on the 16 rhythm.
     *
     * @param strTitle the heading, or null for none
     * @param inner what goes in it
     * @return the card
     */
    public static JPanel card(String strTitle, JComponent inner) {
        JPanel pnlCard = new JPanel(new java.awt.BorderLayout(scale(N_GAP), scale(N_GAP)));
        pnlCard.setBackground(colCard());
        pnlCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(colCardBorder(), 1, true),
                borderScaled(N_PAD_CARD, N_PAD_CARD, N_PAD_CARD, N_PAD_CARD)));
        if (strTitle != null) {
            JLabel lblTitle = new JLabel(strTitle);
            style(lblTitle, "font: bold");
            pnlCard.add(lblTitle, java.awt.BorderLayout.NORTH);
        }
        pnlCard.add(inner, java.awt.BorderLayout.CENTER);
        return pnlCard;
    }


    /**
     * A pill. A panel with the style and a plain label inside it, which is
     * mechanic 2 of the guideline: styling the label directly throws inside the
     * parser and takes the colours with it.
     *
     * @param strText what it says
     * @param colFill the background
     * @return the chip
     */
    public static JPanel chip(String strText, Color colFill) {
        JPanel pnlChip = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
        pnlChip.setBackground(colFill);
        pnlChip.setBorder(borderScaled(3, 10, 3, 10));
        style(pnlChip, "arc: 999");
        JLabel lblText = new JLabel(strText);
        // Chosen from the FILL rather than fixed. White on a light fill is
        // how STOPPED came out blank, and the next colour added would have
        // done it again.
        lblText.setForeground(colTextOn(colFill));
        style(lblText, "font: bold");
        pnlChip.add(lblText);
        return pnlChip;
    }


    /**
     * A style string, applied so that an unknown key cannot take the window
     * with it.
     *
     * FlatLaf throws on a key the component's UI does not define, and the
     * throw abandons the rest of the string - so one wrong word turns a styled
     * component into an unstyled one at best and an exception during layout at
     * worst. Every style here is cosmetic; none of it is worth a window that
     * does not open.
     *
     * @param comp what to style
     * @param strStyle a FlatLaf style string
     */
    public static void style(JComponent comp, String strStyle) {
        try {
            comp.putClientProperty("FlatLaf.style", strStyle);
        }
        catch (RuntimeException ex) {
            // cosmetic only
        }
    }


    /**
     * @param col any colour
     * @return `#rrggbb`, which is what a FlatLaf style string takes
     */
    public static String hex(Color col) {
        return String.format("#%02x%02x%02x", col.getRed(), col.getGreen(), col.getBlue());
    }


    /**
     * @param colFill a background
     * @return black or white, whichever is readable on it. Rec. 709 luma,
     *         because a green and a grey of the same RGB sum are not equally
     *         bright and the eye is the thing being served
     */
    public static Color colTextOn(Color colFill) {
        double dLuma = 0.2126 * colFill.getRed() + 0.7152 * colFill.getGreen()
                + 0.0722 * colFill.getBlue();
        return dLuma > 150.0 ? Tokens.COL_FG : Color.WHITE;
    }

}
