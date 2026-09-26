// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.topology;

import com.raposza.canton.dar.DarCatalog;
import com.raposza.canton.topology.SandboxPorts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What to launch. Everything the 3.x launcher needs and nothing it does not.
 *
 * There is no topology file here, and that is the finding rather than an
 * omission: the Canton jar ships `sandbox/sandbox.conf` and the `sandbox`
 * subcommand bootstraps the synchronizer from it. A hand-written participant,
 * sequencer and mediator, and the console script that ties them together, are
 * work this project does not have to do for 3.x. Overlays come later, for
 * storage and auth, and they overlay that file rather than replace it.
 *
 * @param ports the six ports the stack listens on
 * @param lstFileDar DARs to upload at start-up, in order; may be empty
 * @param flagStaticTime whether time advances only when asked through the time
 *        service
 * @param flagDev whether to run the development version of the protocol
 * @param nHeapMb the JVM heap for the Canton process, or 0 to leave the JVM
 *        default alone
 *
 * Author Claude/bentzn
 */
public record SandboxSpec(SandboxPorts ports, List<Path> lstFileDar, boolean flagStaticTime,
        boolean flagDev, int nHeapMb) {

    public SandboxSpec {
        if (ports == null)
            throw new IllegalArgumentException("ports are required");
        lstFileDar = lstFileDar == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(lstFileDar));
        if (nHeapMb < 0)
            throw new IllegalArgumentException("heap must not be negative: " + nHeapMb);
    }


    public static SandboxSpec ofDefaults() {
        return new SandboxSpec(SandboxPorts.ofDefaults(), List.of(), false, false, 0);
    }


    public SandboxSpec withPorts(SandboxPorts portsNew) {
        return new SandboxSpec(portsNew, lstFileDar, flagStaticTime, flagDev, nHeapMb);
    }


    public SandboxSpec withDars(List<Path> lstFileDarNew) {
        return new SandboxSpec(ports, lstFileDarNew, flagStaticTime, flagDev, nHeapMb);
    }


    /**
     * Every DAR in a directory, in the order the catalogue puts them.
     *
     * These reach the participant through `--dar` on the `sandbox` subcommand,
     * which {@link com.raposza.sandbox.process.CantonSandboxProcess}
     * already renders and which is measured on 3.5.11 - so start-time upload on
     * 3.x needs no console call at all. `dars.upload` on the console is the
     * AFTER-start path and a different capability.
     *
     * @param dirDars a directory of `*.dar` files
     * @return a spec that uploads them
     * @throws com.raposza.canton.dar.DarException when the directory
     *         is absent, unreadable, or stages one package twice
     */
    public SandboxSpec withDarsFrom(Path dirDars) {
        return withDars(DarCatalog.scan(dirDars).lstFiles());
    }


    public SandboxSpec withHeapMb(int nHeapMbNew) {
        return new SandboxSpec(ports, lstFileDar, flagStaticTime, flagDev, nHeapMbNew);
    }
}
