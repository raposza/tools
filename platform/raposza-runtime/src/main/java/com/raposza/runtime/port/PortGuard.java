// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.port;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether a port is free, asked before anything tries to bind it.
 *
 * The failure this prevents is not a crash. A stray PostgreSQL left over from
 * a previous run is silently adopted by whatever connects next, and the stack
 * then runs against a database nobody described - green, wrong, and expensive
 * to notice. Canton fails louder, but the diagnosis still costs more than the
 * check.
 *
 * SO_REUSEADDR IS ON, and that is the strict reading of the question rather
 * than a relaxation of it.
 *
 * A bind carrying SO_REUSEADDR still fails
 * against a socket in LISTEN state, which is the adoption this class exists to
 * catch; what it stops failing against is TIME_WAIT, which is not a server and
 * cannot be adopted. With it off, the check refused a port for the sixty
 * seconds the kernel holds the connections a stopped PostgreSQL left behind -
 * a port every server the sandbox starts would have bound without complaint,
 * because they all set the option themselves. So the old setting did not ask a
 * stricter question, it asked a different one, and the answer it gave was
 * wrong for a minute after every stop.
 *
 * Author Claude/bentzn
 */
public final class PortGuard {

    private PortGuard() {
    }


    /**
     * @param nPort the port to test
     * @return whether a server could bind it on the loopback interface now
     */
    public static boolean isFree(int nPort) {
        if (nPort < 1 || nPort > 65535)
            return false;

        try (ServerSocket socket = new ServerSocket()) {
            // TRUE. See the class comment: a LISTENING socket still refuses
            // the bind, so nothing about adoption changes; TIME_WAIT stops
            // being reported as a server that is not there.
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), nPort), 1);
            return true;
        }
        catch (IOException ex) {
            return false;
        }
    }


    /**
     * @param mapPort what each port is for, keyed by a name for the message
     * @return the names of the ports that are occupied, in the order given
     */
    public static List<String> occupied(Map<String, Integer> mapPort) {
        List<String> lstBusy = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : mapPort.entrySet()) {
            if (!isFree(entry.getValue()))
                lstBusy.add(entry.getKey() + " (" + entry.getValue() + ")");
        }
        return lstBusy;
    }


    /**
     * @param mapPort what each port is for, keyed by a name for the message
     * @throws IllegalStateException naming every occupied port, so one run
     *         reports all of them rather than one per attempt
     */
    public static void requireFree(Map<String, Integer> mapPort) {
        List<String> lstBusy = occupied(mapPort);
        if (!lstBusy.isEmpty())
            throw new IllegalStateException("already in use: " + String.join(", ", lstBusy));
    }


    /**
     * @param nPort the number to check
     * @param strWhat what it is for, so a rejection names it
     * @throws IllegalArgumentException when it is not a port number
     */
    public static void requireInRange(int nPort, String strWhat) {
        if (nPort < 1 || nPort > N_PORT_MAX)
            throw new IllegalArgumentException("port out of range for " + strWhat + ": " + nPort);
    }


    /**
     * One above the highest number given.
     *
     * ONE ABOVE THE HIGHEST rather than a fixed offset from the first. A layout
     * change that moved a node would otherwise put the derived number on top of
     * it, and the failure would arrive as a process that will not start with
     * nothing saying which port it wanted.
     *
     * @param arrPort the block to sit above; at least one
     * @return the next number after the highest of them
     * @throws IllegalArgumentException when no port was given
     */
    public static int nAbove(int... arrPort) {
        if (arrPort == null || arrPort.length == 0)
            throw new IllegalArgumentException("a block needs at least one port");
        int nHighest = arrPort[0];
        for (int idx = 1; idx < arrPort.length; idx++) {
            if (arrPort[idx] > nHighest)
                nHighest = arrPort[idx];
        }
        return nHighest + 1;
    }


    private static final int N_PORT_MAX = 65535;

}
