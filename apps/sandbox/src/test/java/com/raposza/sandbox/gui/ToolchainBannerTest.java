// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>Skipped where there is no toolkit</h2>
 *
 * Constructing a Swing component needs one, and this project runs headless
 * builds. The assertions are about state rather than pixels, so the skip costs
 * nothing where it applies.
 *
 * Author Claude/bentzn
 */
class ToolchainBannerTest {

    @BeforeAll
    static void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "no display");
    }


    /** A machine that is fine sees nothing. */
    @Test
    void itIsHiddenUntilThereIsAReason() {
        ToolchainBanner banner = new ToolchainBanner();

        assertFalse(banner.isVisible());
        banner.setOffered(true);
        assertTrue(banner.isVisible());
        banner.setOffered(false);
        assertFalse(banner.isVisible());
    }


    /**
     * The strip stays up while the install runs - it is the only explanation
     * of what the log is doing - and only the button goes down.
     */
    @Test
    void busyDisablesTheButtonAndKeepsTheStrip() {
        ToolchainBanner banner = new ToolchainBanner();
        banner.setOffered(true);

        banner.setBusy(true);

        assertTrue(banner.isVisible());
        assertFalse(banner.isActionEnabled());

        banner.setBusy(false);
        assertTrue(banner.isActionEnabled());
    }


    @Test
    void theActionIsTheWindowsRatherThanTheComponentsOwn() {
        ToolchainBanner banner = new ToolchainBanner();
        AtomicInteger cntRun = new AtomicInteger();
        banner.useAction(cntRun::incrementAndGet);

        for (java.awt.Component comp : banner.getComponents()) {
            if (comp instanceof javax.swing.JButton button)
                button.doClick();
        }

        assertTrue(cntRun.get() == 1, "the action ran " + cntRun.get() + " times");
    }

}
