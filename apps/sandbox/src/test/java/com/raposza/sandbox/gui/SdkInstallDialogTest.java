// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.SdkChannel;
import com.raposza.canton.install.SdkOffer;
import com.raposza.canton.install.VersionId;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flow, with nothing fetched and no toolchain run.
 *
 * <h2>Skipped where there is no toolkit</h2>
 *
 * Constructing a Swing component needs one, and this project runs headless
 * builds. The assertions are about state rather than pixels, so the skip costs
 * nothing where it applies.
 *
 * Author Claude/bentzn
 */
class SdkInstallDialogTest {

    private static final VersionId VERSION_OLD = VersionId.parse("2.8.12");

    private static final VersionId VERSION_NEW = VersionId.parse("2.10.6");

    private static final VersionId VERSION_DPM = VersionId.parse("3.5.7");

    /** How long a thread is given before an assertion gives up. */
    private static final int N_TRY = 100;

    private static final int N_MS_STEP = 50;


    @BeforeAll
    static void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "no display");
    }


    /** Both channels are listed; only what is missing can be pressed. */
    @Test
    void onlyWhatIsMissingGetsAButton() throws Exception {
        SdkInstallDialog dialog = new SdkInstallDialog(null,
                () -> List.of(new SdkOffer(VERSION_OLD, SdkChannel.ASSISTANT, true),
                        new SdkOffer(VERSION_NEW, SdkChannel.ASSISTANT, false),
                        new SdkOffer(VERSION_DPM, SdkChannel.DPM, false)),
                (offer, lineProgress) -> {
                });

        waitFor(dialog, () -> dialog.lstOffered().size() == 2);

        // NEWEST FIRST - the proposal of 2026-09-25.
        assertEquals(List.of(VERSION_DPM, VERSION_NEW), dialog.lstOffered());
        assertNull(dialog.buttonFor(VERSION_OLD));
    }


    @Test
    void aFinishedInstallReportsItsVersionAndCloses() throws Exception {
        SdkInstallDialog dialog = new SdkInstallDialog(null,
                () -> List.of(new SdkOffer(VERSION_DPM, SdkChannel.DPM, false)),
                (offer, lineProgress) -> lineProgress.accept("42% of 747.0 MiB"));

        waitFor(dialog, () -> dialog.buttonFor(VERSION_DPM) != null);
        dialog.buttonFor(VERSION_DPM).doClick();
        waitFor(dialog, () -> dialog.versionInstalled() != null);

        assertEquals(VERSION_DPM, dialog.versionInstalled());
    }


    /**
     * A FAILURE IS NOT A CLOSE. The version that failed is still offered and
     * the footer says why.
     */
    @Test
    void aFailedInstallKeepsTheDialogUp() throws Exception {
        SdkInstallDialog dialog = new SdkInstallDialog(null,
                () -> List.of(new SdkOffer(VERSION_NEW, SdkChannel.ASSISTANT, false)),
                (offer, lineProgress) -> {
                    throw new IOException("no such asset");
                });

        waitFor(dialog, () -> dialog.buttonFor(VERSION_NEW) != null);
        dialog.buttonFor(VERSION_NEW).doClick();
        waitFor(dialog, () -> dialog.strStatus().startsWith(SdkInstallDialog.STR_FAILED));

        assertTrue(dialog.strStatus().contains("no such asset"), dialog.strStatus());
        assertNull(dialog.versionInstalled());
        assertTrue(dialog.buttonFor(VERSION_NEW).isEnabled());
    }


    /** A toolchain that could not be asked says so rather than showing zero. */
    @Test
    void aFailedCatalogueSaysSo() throws Exception {
        SdkInstallDialog dialog = new SdkInstallDialog(null, () -> {
            throw new IOException("dpm exited 1");
        }, (offer, lineProgress) -> {
        });

        waitFor(dialog, () -> dialog.strStatus().startsWith(SdkInstallDialog.STR_UNREAD));

        assertTrue(dialog.strStatus().contains("dpm exited 1"), dialog.strStatus());
        assertTrue(dialog.lstOffered().isEmpty());
    }


    /** A platform neither channel publishes for gets a sentence, not an empty box. */
    @Test
    void anEmptyCatalogueSaysSo() throws Exception {
        SdkInstallDialog dialog = new SdkInstallDialog(null, List::of,
                (offer, lineProgress) -> {
                });

        waitFor(dialog, () -> SdkInstallDialog.STR_NONE.equals(dialog.strStatus()));

        assertTrue(dialog.lstOffered().isEmpty());
    }


    /** T-3: a dpm row names its Canton, or says when it will. */
    @Test
    void aDpmRowSaysWhichCantonItBrings() {
        SdkOffer offerDpm = new SdkOffer(VERSION_DPM, SdkChannel.DPM, false);

        assertEquals("3.5.18", SdkInstallDialog.strCanton(offerDpm, "3.5.18"));
        assertEquals(SdkInstallDialog.STR_CANTON_LATER, SdkInstallDialog.strCanton(offerDpm, null));
        assertEquals("", SdkInstallDialog.strCanton(
                new SdkOffer(VERSION_NEW, SdkChannel.ASSISTANT, false), "2.10.6"));
    }


    /** His instruction: a long version is cut with "...", never scrolled to. */
    @Test
    void aLongCantonIsCut() {
        SdkOffer offerDpm = new SdkOffer(VERSION_DPM, SdkChannel.DPM, true);

        String strShown = SdkInstallDialog.strCanton(offerDpm,
                "3.5.14-snapshot.20260819.19183.0.va7a6d3ae");

        assertEquals("3.5.14-snap...", strShown);
        assertEquals("3.5.18", SdkInstallDialog.strShort("3.5.18"));
    }


    /**
     * @param dialog the dialog to pump
     * @param cond what is being waited for
     */
    private static void waitFor(SdkInstallDialog dialog, BooleanSupplier cond) throws Exception {
        for (int cntTry = 0; cntTry < N_TRY; cntTry++) {
            SwingUtilities.invokeAndWait(() -> {
            });
            if (cond.getAsBoolean())
                return;
            Thread.sleep(N_MS_STEP);
        }
        throw new IllegalStateException("the dialog never got there; it says "
                + dialog.strStatus());
    }

}
