// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import java.util.List;

/**
 * WHICH installed Canton lends its Daml-LF reader - one rule, for the Sandbox's
 * `GET /dars` and for the Workbench's package reads.
 *
 * <h2>Why one rule</h2>
 *
 * His decision of 2026-10-07: the Workbench reads what the participant
 * accepts, the way the Sandbox already did. The newest stable reader on Maven
 * Central refuses LF 2.3 (D-905) and a Canton 3.5 participant accepts it, so
 * both applications borrow the reader of a Canton installed on the machine.
 * Were the two to choose differently, they could disagree about what a
 * package means.
 *
 * Measured: the readers of every open-source Canton from 3.5.1 to 3.5.19
 * decode LF 1.15, 2.2 and 2.3. Enterprise is not measured and is not used.
 *
 * This class CHOOSES; it loads nothing. Loading is `LfReaderLoader`'s, in
 * raposza-lf, which this module does not depend on.
 *
 * Author Claude/bentzn
 */
public final class LfReaderSource {

    /** The oldest Canton whose reader is measured to decode LF 2.3. */
    public static final VersionId VERSION_MIN = VersionId.of(3, 5, 1);


    private LfReaderSource() {
    }


    /**
     * @param lstInstall the installed Cantons, in any order; may be null
     * @return the newest open-source one at or after {@link #VERSION_MIN}
     *         with a runtime jar, or null
     */
    public static CantonInstallation instReader(List<CantonInstallation> lstInstall) {
        CantonInstallation instBest = null;
        for (CantonInstallation inst : lstInstall == null ? List.<CantonInstallation>of() : lstInstall) {
            if (!inst.hasRuntime() || inst.edition() != Edition.OPEN_SOURCE
                    || inst.version().compareTo(VERSION_MIN) < 0)
                continue;
            if (instBest == null || inst.version().compareTo(instBest.version()) > 0)
                instBest = inst;
        }
        return instBest;
    }


    /**
     * @param inst the installation whose reader is used
     * @return what the reader is, as a document or a log line names it - e.g.
     *         `canton-open-source 3.5.19`
     */
    public static String strReader(CantonInstallation inst) {
        return "canton-open-source " + inst.version();
    }

}
