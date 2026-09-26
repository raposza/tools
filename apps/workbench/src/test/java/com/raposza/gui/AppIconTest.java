// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * The icon must reach the classpath, and it must not be blank.
 *
 * <h2>What this test used to assert, and why it does not any more</h2>
 *
 * It used to look for one specific colour in the rendered pixels. That was the
 * right assertion while the PNG was RASTERISED during the build: Batik's CSS
 * parser discarded every declaration following a vendor-prefixed one, so the
 * letter lost its fill, nothing failed, and every other signal said green. A
 * renderer that silently drops half a style attribute needs an assertion about
 * the pixels.
 *
 * The PNG is exported at build time from src/main/svg/icon.svg, whose text is
 * converted to paths, so the vendor-prefixed style that cost the letter its
 * fill is gone from the source. What survives is the risk any build output
 * carries: missing from the jar, or exported empty. Both are checked below,
 * neither couples this file to the artwork, and the last test keeps the
 * master free of text.
 *
 * That coupling is the reason for the change. The old constant had to be edited
 * whenever the drawing changed, and the first time it did, this test failed for
 * a correct icon - which is how a test that was protecting something real ends
 * up deleted in irritation.
 *
 * Author Claude/bentzn
 * Amended 2026-08-08
 */
class AppIconTest {

    /** Below this the export produced an empty or near-empty image. */
    private static final int CNT_OPAQUE_MIN = 1000;


    @Test
    void iconIsOnTheClasspathAndLoads() {
        List<Image> lstIcon = AppIcon.load();
        assertFalse(lstIcon.isEmpty(),
                "no " + AppIcon.PATH_ICON + " on the classpath - the resource is not packaged");
        assertTrue(lstIcon.get(0).getWidth(null) >= 256, "committed icon is smaller than 256 px");
        assertTrue(lstIcon.size() > 1, "only one size offered");
    }


    /**
     * A PNG of the right dimensions that is entirely transparent loads, scales
     * and reports its size exactly like a good one. Nothing else here would
     * notice, and a window would open with no icon at all.
     */
    @Test
    void theIconIsNotBlank() throws Exception {
        BufferedImage img;
        try (InputStream streamIn = AppIconTest.class.getResourceAsStream(AppIcon.PATH_ICON)) {
            assertFalse(streamIn == null, "no " + AppIcon.PATH_ICON + " on the classpath");
            img = ImageIO.read(streamIn);
        }

        assertFalse(img == null, AppIcon.PATH_ICON + " is not a readable image");
        assertTrue(img.getWidth() == img.getHeight(),
                "the icon is not square: " + img.getWidth() + "x" + img.getHeight());

        int cntOpaque = 0;
        for (int numY = 0; numY < img.getHeight(); numY++) {
            for (int numX = 0; numX < img.getWidth(); numX++) {
                if (((img.getRGB(numX, numY) >>> 24) & 0xff) > 200)
                    cntOpaque++;
            }
        }

        assertTrue(cntOpaque > CNT_OPAQUE_MIN, "the icon is blank or nearly so - only " + cntOpaque
                + " opaque pixels. The export produced an empty image.");
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
