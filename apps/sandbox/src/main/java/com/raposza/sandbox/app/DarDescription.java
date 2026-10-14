// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.raposza.canton.dar.DarDescriber;
import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.LfReaderSource;
import com.raposza.canton.install.VersionId;
import com.raposza.lf.LfReaderLoader;
import com.raposza.spi.LfDecoder_i;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What `GET /dars` answers: the DARs the discovery document lists, described
 * by `DarDescriber` with the Daml-LF reader of an installed Canton.
 *
 * <h2>The reader comes from the machine, not from the Sandbox</h2>
 *
 * The Sandbox carries no Daml-LF reader of its own. The newest installed
 * OPEN-SOURCE Canton of {@link #VERSION_MIN} or later lends its own, loaded by
 * `LfReaderLoader` in a classloader of its own - measured: the readers of
 * every open-source Canton from 3.5.1 to 3.5.19 decode LF 1.15, 2.2 and 2.3.
 * Enterprise is not measured and is not used. With none installed the request
 * is refused with the reason, and nothing else on the endpoint changes.
 *
 * WHICH Canton is `LfReaderSource`'s rule, shared with the Workbench since
 * 2026-10-07, so the two applications read a package the same way.
 *
 * <h2>Loaded once per jar</h2>
 *
 * Opening the Canton jar is the expensive part, so the decoder is kept until
 * the newest installation changes.
 *
 * Author Claude/bentzn
 */
public final class DarDescription {

    /** The oldest Canton whose reader is measured to decode LF 2.3. */
    static final VersionId VERSION_MIN = LfReaderSource.VERSION_MIN;

    private static final Object LOCK = new Object();

    private static Path fileLoaded;

    private static LfDecoder_i decoderLoaded;


    private DarDescription() {
    }


    /**
     * @param strTopology `sandbox` or `localnet`
     * @param lstDar the DARs, as the discovery document lists them
     * @return the description, as JSON
     * @throws IllegalStateException when no usable Canton is installed
     */
    public static String strJson(String strTopology, List<DiscoveryDar> lstDar) {
        CantonInstallation inst = instReader(SandboxService.lstCanton());
        if (inst == null) {
            throw new IllegalStateException("no open-source Canton " + VERSION_MIN
                    + " or newer is installed, and its jar carries the Daml-LF reader the"
                    + " description needs - install one from the Sandbox's SDK dialog");
        }

        List<DarDescriber.Input> lstInput = new ArrayList<>();
        for (DiscoveryDar dar : lstDar == null ? List.<DiscoveryDar>of() : lstDar) {
            lstInput.add(new DarDescriber.Input(dar.fileDar(), dar.strOrigin(), dar.strStatus()));
        }
        String strReader = LfReaderSource.strReader(inst);
        ObjectNode node = new DarDescriber(decoderFor(inst.fileRuntime()), strReader)
                .describe(strTopology, lstInput);
        return DarDescriber.strJson(node);
    }


    /**
     * @param lstInstall the installed Cantons, in any order
     * @return the one {@link LfReaderSource#instReader} chooses, or null
     */
    static CantonInstallation instReader(List<CantonInstallation> lstInstall) {
        return LfReaderSource.instReader(lstInstall);
    }


    private static LfDecoder_i decoderFor(Path fileJar) {
        synchronized (LOCK) {
            if (decoderLoaded == null || !fileJar.equals(fileLoaded)) {
                decoderLoaded = LfReaderLoader.decoderFrom(List.of(fileJar));
                fileLoaded = fileJar;
            }
            return decoderLoaded;
        }
    }

}
