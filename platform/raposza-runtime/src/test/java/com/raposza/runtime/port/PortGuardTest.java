// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.port;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Author Claude/bentzn
 */
class PortGuardTest {

    @Test
    void seesAnOccupiedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
            int nPort = socket.getLocalPort();

            assertFalse(PortGuard.isFree(nPort));
            assertTrue(PortGuard.isFree(freePort()));
        }
    }


    @Test
    void namesEveryClashRatherThanTheFirst() throws IOException {
        try (ServerSocket first = new ServerSocket();
                ServerSocket second = new ServerSocket()) {
            first.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
            second.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);

            Map<String, Integer> mapPort = new LinkedHashMap<>();
            mapPort.put("ledger-api", first.getLocalPort());
            mapPort.put("admin-api", second.getLocalPort());
            mapPort.put("json-api", freePort());

            List<String> lstBusy = PortGuard.occupied(mapPort);
            assertEquals(2, lstBusy.size());
            assertTrue(lstBusy.get(0).startsWith("ledger-api"));
            assertTrue(lstBusy.get(1).startsWith("admin-api"));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> PortGuard.requireFree(mapPort));
            assertTrue(ex.getMessage().contains("ledger-api"));
            assertTrue(ex.getMessage().contains("admin-api"));
        }
    }


    @Test
    void aPortOutOfRangeIsNeverFree() {
        assertFalse(PortGuard.isFree(0));
        assertFalse(PortGuard.isFree(70000));
    }


    @Test
    void aPortHoldingONLYTimeWaitIsFree() throws IOException {
        // THE REGRESSION THIS FIX IS. Server closes first, so the accepted
        // connection's TIME_WAIT carries the LISTENING port as its local port
        // - the shape a stopped PostgreSQL leaves - and the port then read as
        // occupied for the kernel's sixty seconds.
        assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"),
                "SO_REUSEADDR does not mean the same thing off Linux");

        int nPort;
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
            nPort = server.getLocalPort();
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), nPort);
                    Socket accepted = server.accept()) {
                accepted.setSoLinger(false, 0);
            }
        }

        assertTrue(PortGuard.isFree(nPort),
                "port " + nPort + " holds no server, only sockets draining");
    }


    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
