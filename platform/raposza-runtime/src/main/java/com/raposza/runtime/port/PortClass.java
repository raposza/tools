// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.port;

/**
 * THE THREE THOUSANDS EVERY DEFAULT PORT LIES IN, and the thousand says what
 * kind of port it is.
 *
 * <pre>
 *   30000 - 30999   NODE    ledger, admin, JSON, validator, health, sequencer,
 *                           mediator, scan, the sv app, PQS
 *   31000 - 31999   UI      the web UIs
 *   32000 - 32767   ADMIN   what the window itself serves or starts - discovery,
 *                           the mint, PostgreSQL, the CaQL harness
 * </pre>
 *
 * The operator's decision of 2026-09-23. Reading a port is then a one-glance
 * operation: `30021` is a node, `31010` a web UI, `32101` infrastructure the
 * window owns.
 *
 * <h2>Why ADMIN stops at 32767</h2>
 *
 * 32768 is where the kernel's ephemeral range begins -
 * `net.ipv4.ip_local_port_range` is 32768-60999 on the workstation - so a port
 * inside it can already be held by a transient client socket when a bind is
 * attempted. `RaposzaSettings` states the measurement.
 *
 * <h2>A block may not leave its own thousand</h2>
 *
 * A block has one settable number, its first port, and every other port in it
 * is arithmetic. {@link #nFirstMax(int)} is the highest first port that keeps
 * the whole block inside the class, which is what makes the one-glance reading
 * hold for a first port somebody moved.
 *
 * Author Claude/bentzn
 */
public enum PortClass {

    NODE("node", 30000, 30999),

    UI("web UI", 31000, 31999),

    ADMIN("administrative", 32000, 32767);

    private final String strName;

    private final int nLow;

    private final int nHigh;


    PortClass(String strName, int nLow, int nHigh) {
        this.strName = strName;
        this.nLow = nLow;
        this.nHigh = nHigh;
    }


    /**
     * @return the lowest port of the class
     */
    public int nLow() {
        return nLow;
    }


    /**
     * @return the highest port of the class
     */
    public int nHigh() {
        return nHigh;
    }


    /**
     * @param nPort a port
     * @return whether it lies in this class
     */
    public boolean contains(int nPort) {
        return nPort >= nLow && nPort <= nHigh;
    }


    /**
     * @param nSpan how far above its first port a block's last port sits
     * @return the highest first port that keeps such a block in this class
     */
    public int nFirstMax(int nSpan) {
        if (nSpan < 0 || nSpan > nHigh - nLow)
            throw new IllegalArgumentException("a block spanning " + nSpan
                    + " does not fit the " + strName + " ports " + nLow + "-" + nHigh);
        return nHigh - nSpan;
    }


    /**
     * @param nPort a port that must lie in this class
     * @param strWhat what it is, for the message
     * @return the port
     * @throws IllegalArgumentException when it does not
     */
    public int require(int nPort, String strWhat) {
        if (!contains(nPort)) {
            throw new IllegalArgumentException(strWhat + " is outside the " + strName
                    + " ports " + nLow + "-" + nHigh + ": " + nPort);
        }
        return nPort;
    }


    /**
     * @param nPort the first port of a block that must lie in this class
     * @param nSpan how far above it the block's last port sits
     * @param strWhat what it is, for the message
     * @return the port
     * @throws IllegalArgumentException when the block would leave the class
     */
    public int requireFirst(int nPort, int nSpan, String strWhat) {
        int nMax = nFirstMax(nSpan);
        if (nPort < nLow || nPort > nMax) {
            throw new IllegalArgumentException(strWhat + " must be " + nLow + "-" + nMax
                    + " so the block stays inside the " + strName + " ports " + nLow + "-"
                    + nHigh + ": " + nPort);
        }
        return nPort;
    }
}
