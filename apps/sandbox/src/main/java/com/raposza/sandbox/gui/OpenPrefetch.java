// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.runtime.localnet.SpliceInstallations;
import com.raposza.sandbox.app.JwtMintProcess;
import com.raposza.sandbox.app.SandboxService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * WHAT THE WINDOW READS AT OPEN, READ WHILE THE OPERATOR READS THE TOPOLOGY
 * QUESTION - `todo.md` A-63 (b).
 *
 * Measured by the open trace at his console, 2026-10-04, two opens. The
 * steps below are not Swing and held the event thread for no reason but
 * order:
 *
 * <ul>
 * <li>the Jackson classes, initialised by `JwtMintProcess`'s static
 * `ObjectMapper` under the `JwtPane` field - 133 ms, 468 classes;</li>
 * <li>the Canton installations, `SandboxService.rescanCanton` under
 * `SandboxForm` - 77 ms warm; on the first open, with the filesystem cold, the
 * whole `SandboxForm` step was 522 ms, 290 of them waiting;</li>
 * <li>the Splice versions and each one's shown label - `lstVersion` 11 ms and
 * `useSplice` 103 ms, every one of them in `SpliceInstallations.strShown`, a
 * page read and two regular expressions per version.</li>
 * </ul>
 *
 * The question is open for seconds - 3386 ms on the second open - and the
 * event thread is idle in its loop for all of it. This thread does the three
 * in the order the window needs them; the window then WAITS for what it needs,
 * rather than reading it again, so an answer faster than the reads costs no
 * more than the reads did.
 *
 * <h2>Read once, at open</h2>
 *
 * What is handed over was read when the process started, not when the window
 * opened - the time it took to answer one question. Rescan and the Splice
 * install read the disk again, as they did; only the window's FIRST read comes
 * from here.
 *
 * Author Claude/bentzn
 */
final class OpenPrefetch {

    /**
     * @param lstVersion the staged Splice versions, newest first
     * @param mapShown each version as the dropdown shows it
     */
    record Splice(List<String> lstVersion, Map<String, String> mapShown) {
    }


    private static boolean flagStarted;

    private static CompletableFuture<Boolean> futureCanton;

    private static CompletableFuture<Splice> futureSplice;


    private OpenPrefetch() {
    }


    /**
     * Starts the reads on a daemon thread. Once per process.
     */
    static synchronized void start() {
        if (flagStarted)
            return;
        flagStarted = true;
        CompletableFuture<Boolean> futureCantonNew = new CompletableFuture<>();
        CompletableFuture<Splice> futureSpliceNew = new CompletableFuture<>();
        futureCanton = futureCantonNew;
        futureSplice = futureSpliceNew;
        Thread thread = new Thread(() -> run(futureCantonNew, futureSpliceNew),
                "sandbox-open-prefetch");
        thread.setDaemon(true);
        thread.start();
    }


    private static void run(CompletableFuture<Boolean> futureCantonNew,
            CompletableFuture<Splice> futureSpliceNew) {
        // IN THE ORDER THE WINDOW NEEDS THEM: the fields, then the form, then
        // the LocalNetND rows.
        try {
            Class.forName(JwtMintProcess.class.getName(), true,
                    OpenPrefetch.class.getClassLoader());
        }
        catch (ClassNotFoundException | LinkageError ex) {
            // the window initialises it where it is used, and reports there
        }
        try {
            SandboxService.lstCanton();
            futureCantonNew.complete(Boolean.TRUE);
        }
        catch (RuntimeException ex) {
            futureCantonNew.complete(Boolean.FALSE);
        }
        try {
            futureSpliceNew.complete(spliceNow());
        }
        catch (RuntimeException ex) {
            futureSpliceNew.complete(null);
        }
    }


    /**
     * The Canton installations for the window's first read: waits for the
     * prefetch and returns what it cached, or reads now when there was none or
     * it failed. Once; a later call reads the disk.
     *
     * @return every Canton on this machine, as `SandboxService.lstCanton`
     */
    static List<CantonInstallation> lstCanton() {
        Boolean flagCached = joinOr(take(true), () -> null);
        return Boolean.TRUE.equals(flagCached) ? SandboxService.lstCanton()
                : SandboxService.rescanCanton();
    }


    /**
     * The Splice versions for the window's first read, as {@link #lstCanton}.
     *
     * @return never null
     */
    static Splice splice() {
        return joinOr(take(false), OpenPrefetch::spliceNow);
    }


    /**
     * @return the versions and their labels, read now
     */
    static Splice spliceNow() {
        List<String> lstVersion = SpliceInstallations.lstVersion();
        Map<String, String> mapShown = new LinkedHashMap<>();
        for (String strVersion : lstVersion) {
            mapShown.put(strVersion, SpliceInstallations.strShown(strVersion));
        }
        return new Splice(List.copyOf(lstVersion), mapShown);
    }


    @SuppressWarnings("unchecked")
    private static synchronized <T> CompletableFuture<T> take(boolean flagCanton) {
        CompletableFuture<?> future = flagCanton ? futureCanton : futureSplice;
        if (flagCanton)
            futureCanton = null;
        else
            futureSplice = null;
        return (CompletableFuture<T>) future;
    }


    /**
     * @param future what the prefetch produced, or null when none was started
     *        or it was taken
     * @param supplierNow reads now
     * @return the prefetched value, or the supplier's when there is none
     */
    static <T> T joinOr(CompletableFuture<T> future, Supplier<T> supplierNow) {
        T value = future == null ? null : future.join();
        return value != null ? value : supplierNow.get();
    }

}
