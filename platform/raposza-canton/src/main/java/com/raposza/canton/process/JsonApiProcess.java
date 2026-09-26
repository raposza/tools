// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.process;

import com.raposza.canton.install.CantonInstallation;
import com.raposza.canton.install.InstallSource;
import com.raposza.runtime.process.JvmCommand;
import com.raposza.runtime.process.ManagedProcess;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The HTTP JSON API of the 2.x line: a SEPARATE process, out of the DAML SDK.
 *
 * <h2>Where the jar is, and why it is derived rather than configured</h2>
 *
 * A 2.x Canton on this machine is found by the DAML assistant at
 * `~/.daml/sdk/&lt;version&gt;/canton`, and the SDK it belongs to puts its own jar
 * beside it at `~/.daml/sdk/&lt;version&gt;/daml-sdk/daml-sdk.jar`. That is the same
 * pair the CantonSandboxPqs prototype resolved from one `daml.dir` setting,
 * and it is a property of the assistant's layout rather than of any
 * installation this workspace makes. A DPM-sourced install has no SDK beside
 * it and gets null.
 *
 * <h2>Readiness is a socket, not a line</h2>
 *
 * The prototype waited on a TCP port and this does the same. The wording of
 * the server's own start-up line is a property of the SDK version and is not
 * banked anywhere in the inventory, so matching on it would be a guess that
 * fails silently by never firing; a port that accepts a connection is the same
 * fact in every version.
 *
 * Author Claude/bentzn
 */
public final class JsonApiProcess extends ManagedProcess {

    public static final String STR_NAME = "json-api";

    /** What the prototype gave it, and it has never needed more. */
    public static final int N_HEAP_MB_DEFAULT = 384;

    private static final String STR_REL_SDK_JAR = "daml-sdk/daml-sdk.jar";

    private static final int N_MS_PROBE = 250;

    private final Path fileSdkJar;

    private final Path dirWork;

    private final String strHostLedger;

    private final int nPortLedger;

    private final int nPortHttp;

    private final int nHeapMb;


    /**
     * @param fileSdkJarNew the SDK jar to run; see {@link #fileSdkJarFor}
     * @param dirWorkNew where the process runs
     * @param strHostLedgerNew the participant's host
     * @param nPortLedgerNew the participant's Ledger API port
     * @param nPortHttpNew what to serve HTTP on
     * @param nHeapMbNew the JVM heap, or 0 for the JVM default
     */
    public JsonApiProcess(Path fileSdkJarNew, Path dirWorkNew, String strHostLedgerNew,
            int nPortLedgerNew, int nPortHttpNew, int nHeapMbNew) {
        super(STR_NAME);
        if (fileSdkJarNew == null)
            throw new IllegalArgumentException("the SDK jar is required");
        if (dirWorkNew == null)
            throw new IllegalArgumentException("a work directory is required");
        this.fileSdkJar = fileSdkJarNew;
        this.dirWork = dirWorkNew;
        this.strHostLedger = strHostLedgerNew;
        this.nPortLedger = nPortLedgerNew;
        this.nPortHttp = nPortHttpNew;
        this.nHeapMb = nHeapMbNew;
    }


    /**
     * @param installation the Canton this stack is running
     * @return the SDK jar beside it, or null when there is none - which is
     *         every DPM install and any assistant directory missing its SDK
     */
    public static Path fileSdkJarFor(CantonInstallation installation) {
        if (installation == null || installation.source() != InstallSource.DAML_ASSISTANT)
            return null;

        Path dirVersion = installation.dirHome().getParent();
        if (dirVersion == null)
            return null;

        Path fileJar = dirVersion.resolve(STR_REL_SDK_JAR);
        return Files.isRegularFile(fileJar) ? fileJar : null;
    }


    public int portHttp() {
        return nPortHttp;
    }


    @Override
    protected Path workingDir() {
        return dirWork;
    }


    @Override
    protected List<String> buildCommand() throws IOException {
        if (!Files.isRegularFile(fileSdkJar))
            throw new IOException("no daml-sdk.jar at " + fileSdkJar);

        return JvmCommand.ofJar(fileSdkJar)
                .heapMb(nHeapMb)
                .arg("json-api")
                .arg("--ledger-host", strHostLedger)
                .arg("--ledger-port", nPortLedger)
                .arg("--http-port", nPortHttp)
                // The participant this stack starts has no auth on its Ledger
                // API, so the tokens a caller sends are unsigned. Without this
                // the server refuses them and every request fails at the door.
                .arg("--allow-insecure-tokens")
                .build();
    }


    /**
     * Never. Readiness is the port; see the class comment.
     *
     * @param strLine one line of output
     * @return false, always
     */
    @Override
    protected boolean isReadyLine(String strLine) {
        return false;
    }


    @Override
    protected boolean isReadyOutOfBand() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(strHostLedger, nPortHttp), N_MS_PROBE);
            return true;
        }
        catch (IOException ex) {
            return false;
        }
    }

}
