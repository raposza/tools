// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.formdev.flatlaf.FlatLaf;
import com.raposza.design.Tokens;
import com.raposza.design.UiFonts;

import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.UIManager;

/**
 * The Raposza design package applied to the Workbench - D-848, `todo.md` DS-8.
 * The same calls the Sandbox's `GuiTheme.install` makes, so the two windows
 * share one font, one accent and one set of arcs.
 *
 * {@link #install()} runs BEFORE the look and feel instance exists: FlatLaf
 * reads the accent and the preferred family when it is set up, and a value put
 * afterwards leaves a window that looks finished and is simply wrong.
 *
 * Author Claude/bentzn
 */
public final class GuiDesign {

    /** Whether the design package's faces reached the graphics environment. */
    private static boolean flagFontsInstalled;


    private GuiDesign() {
    }


    /**
     * Inter and Hack from the jar, the accent and the arcs from the tokens.
     * Call once, before the look and feel is set up.
     */
    public static void install() {
        // A broken jar is logged and the desktop font stands; it is not a
        // reason to lose the look and feel.
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

        FlatLaf.setPreferredFontFamily(UiFonts.STR_FAMILY_UI);
    }


    /**
     * @return Hack, or the logical MONOSPACED if the design fonts did not install
     */
    public static String strFamilyMono() {
        return flagFontsInstalled ? UiFonts.STR_FAMILY_MONO : Font.MONOSPACED;
    }


    /**
     * @param nSize a size already scaled - one taken off a component's font
     * @return the monospaced font at that size
     */
    public static Font fontMonoAt(int nSize) {
        return new Font(strFamilyMono(), Font.PLAIN, nSize);
    }

}
