// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Image;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The icon must reach the classpath, and it must not be empty.
 *
 * The PNG is exported at build time from src/main/svg/icon.svg, whose text is
 * converted to paths. What survives is the risk any build output carries:
 * missing from the jar, or exported empty. Neither assertion couples this file
 * to the artwork; the last one keeps the master free of text.
 *
 * Author Claude/bentzn
 */
class AppIconTest {

    /** Below this the export produced an empty file. */
    private static final int CNT_BYTE_MIN = 2000;


    @Test
    void iconIsOnTheClasspathAndLoads() {
        List<Image> lstIcon = AppIcon.load();
        assertFalse(lstIcon.isEmpty(),
                "no " + AppIcon.PATH_ICON + " on the classpath - the resource is not packaged");
        assertTrue(lstIcon.get(0).getWidth(null) >= 256, "committed icon is smaller than 256 px");
        assertTrue(lstIcon.size() > 1, "only one size offered");
    }


    @Test
    void iconIsNotAnEmptyFile() throws Exception {
        try (InputStream streamIn = AppIconTest.class.getResourceAsStream(AppIcon.PATH_ICON)) {
            assertFalse(streamIn == null, "no " + AppIcon.PATH_ICON + " on the classpath");
            assertTrue(streamIn.readAllBytes().length >= CNT_BYTE_MIN,
                    AppIcon.PATH_ICON + " is suspiciously small - the export produced nothing");
        }
    }


    /**
     * The master must hold no text element. A letter left as text renders in
     * whatever font the build machine happens to have, and the export fails
     * where that font is missing - so the SVG carries paths only.
     */
    @Test
    void theSourceSvgHoldsNoText() throws Exception {
        Path fileSvg = Path.of("src", "main", "svg", "icon.svg");
        assertTrue(Files.isRegularFile(fileSvg), "no " + fileSvg + " in the module");
        String strSvg = Files.readString(fileSvg, StandardCharsets.UTF_8);
        assertFalse(strSvg.contains("<text"), fileSvg + " holds a text element - convert it to paths");
    }

}
