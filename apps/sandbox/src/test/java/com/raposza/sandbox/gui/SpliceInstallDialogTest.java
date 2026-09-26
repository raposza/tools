// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Splice install flow with nothing fetched. Skipped headless, as
 * {@link SdkInstallDialogTest} is.
 *
 * Author Claude/bentzn
 */
class SpliceInstallDialogTest {

    private static final int N_TRY = 100;

    private static final int N_MS_STEP = 50;


    @BeforeAll
    static void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "no display");
    }


    private static SpliceInstallDialog dialog(Set<String> setInstalled, SpliceInstallDialog.Sizer sizer) {
        return new SpliceInstallDialog(null, setInstalled, () -> List.of("0.8.1", "0.8.0", "0.7.4"),
                sizer, (strVersion, lineProgress) -> lineProgress.accept("10 of 800 MB"),
                (fileArchive, lineProgress) -> "0.7.4");
    }


    /** THE SIZE BEFORE THE CLICK: a button comes up only once its size is shown. */
    @Test
    void aRowIsPressableOnlyOnceItsSizeIsShown() throws Exception {
        SpliceInstallDialog dialog = dialog(Set.of("0.8.0"), strVersion -> {
            if ("0.7.4".equals(strVersion))
                throw new IOException("404");
            return 800L * 1024L * 1024L;
        });

        waitFor(() -> "size not available".equals(onEdt(() -> dialog.strSizeShown("0.7.4"))));

        assertEquals(List.of("0.8.1", "0.7.4"), onEdt(dialog::lstOffered));
        assertNull(onEdt(() -> dialog.buttonFor("0.8.0")));
        assertEquals("800 MB", onEdt(() -> dialog.strSizeShown("0.8.1")));
        assertTrue(onEdt(() -> dialog.buttonFor("0.8.1").isEnabled()));
        assertFalse(onEdt(() -> dialog.buttonFor("0.7.4").isEnabled()));
        dialog.dispose();
    }


    @Test
    void aDownloadReportsItsVersionAndCloses() throws Exception {
        SpliceInstallDialog dialog = dialog(Set.of(), strVersion -> 1L);

        waitFor(() -> Boolean.TRUE.equals(onEdt(() -> dialog.buttonFor("0.8.1") != null
                && dialog.buttonFor("0.8.1").isEnabled())));
        SwingUtilities.invokeAndWait(() -> dialog.buttonFor("0.8.1").doClick());
        waitFor(() -> dialog.strInstalled() != null);

        assertEquals("0.8.1", dialog.strInstalled());
    }


    @Test
    void anArchiveFromDiskReportsTheVersionItInstalled() throws Exception {
        SpliceInstallDialog dialog = dialog(Set.of(), strVersion -> 1L);

        SwingUtilities.invokeAndWait(() -> dialog.beginFile(Path.of("0.7.4_splice-node.tar.gz")));
        waitFor(() -> dialog.strInstalled() != null);

        assertEquals("0.7.4", dialog.strInstalled());
    }


    @FunctionalInterface
    private interface Read<T> {

        T get();
    }


    private static <T> T onEdt(Read<T> read) throws Exception {
        Object[] arrOut = new Object[1];
        SwingUtilities.invokeAndWait(() -> arrOut[0] = read.get());
        @SuppressWarnings("unchecked")
        T out = (T) arrOut[0];
        return out;
    }


    private static void waitFor(ThrowingCheck check) throws Exception {
        for (int idx = 0; idx < N_TRY; idx++) {
            if (check.ok())
                return;
            Thread.sleep(N_MS_STEP);
        }
        throw new AssertionError("condition not reached");
    }


    @FunctionalInterface
    private interface ThrowingCheck {

        boolean ok() throws Exception;
    }
}
