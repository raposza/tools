// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Whether a LocalNet is already holding the ports, asked before anything tries
 * to take them.
 *
 * WHY THIS IS A SEPARATE ANSWER FROM PortGuard. PortGuard asks whether ONE port
 * can be bound, at the moment the thing that wants it is about to bind. By then
 * the runner has already deleted the run directory under --fresh, which on
 * 2026-08-26 destroyed the pgdata of a stack that was still running. This asks
 * the whole question first, cheaply, from outside: is anybody answering on any
 * of the ports this topology needs.
 *
 * A CONNECT, NOT A BIND. The occupant may be another user's process or a
 * container; what matters is that something answers, not whether this process
 * could bind.
 *
 * Author Claude/bentzn
 */
public final class LocalNetStatus {

    private static final int N_TIMEOUT_MS = 700;

    private LocalNetStatus() {
    }


    /**
     * usage: LocalNetStatus [host]
     *
     * Exits 0 when the ports are FREE and 1 when something is running, so it
     * reads as a precondition rather than as a question.
     */
    public static void main(String[] args) {
        String strHost = args.length > 0 ? args[0] : "127.0.0.1";
        Map<String, Integer> mapOpen = mapOpen(strHost);

        if (mapOpen.isEmpty()) {
            System.out.println("=== NOTHING RUNNING on " + strHost
                    + " - every LocalNet port is free");
            System.exit(0);
        }

        System.out.println("=== SOMETHING IS RUNNING on " + strHost);
        for (Map.Entry<String, Integer> entry : mapOpen.entrySet()) {
            System.out.println("  open  " + entry.getValue() + "  " + entry.getKey());
        }
        printHowToStop();
        System.exit(1);
    }


    /**
     * @param strHost what to dial
     * @return every LocalNet port that answers, label to port, empty when none
     */
    public static Map<String, Integer> mapOpen(String strHost) {
        return mapOpen(strHost, LocalNetPorts.ofDefaults());
    }


    /**
     * @param strHost what to dial
     * @param ports the numbering to look for
     * @return every LocalNet port that answers, label to port, empty when none
     */
    public static Map<String, Integer> mapOpen(String strHost, LocalNetPorts ports) {
        Map<String, Integer> mapAll = new LinkedHashMap<>();
        mapAll.put("postgresql", ports.nPortPostgres());
        mapAll.putAll(ports.mapPortRequired());

        Map<String, Integer> mapOpen = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : mapAll.entrySet()) {
            if (open(strHost, entry.getValue()))
                mapOpen.put(entry.getKey(), entry.getValue());
        }
        return mapOpen;
    }


    /**
     * The clean stop is the terminal that started it, because the runner parks
     * on a shutdown hook and SIGTERM is what runs it. Everything below that is
     * a fallback for a terminal that is gone.
     */
    public static void printHowToStop() {
        System.out.println("--- how to stop it");
        System.out.println("  1. Ctrl-C in the terminal running run-localnet.sh - the");
        System.out.println("     shutdown hook stops Canton, Splice and PostgreSQL in order");
        System.out.println("  2. that terminal gone:");
        System.out.println("     pkill -TERM -f com.raposza.runtime.localnet.LocalNetRunner");
        System.out.println("  3. still held afterwards - find who owns the port:");
        System.out.println("     ss -ltnp | grep -E ':(32101|4901|3901|2901|5008|5012|5014)'");
    }


    private static boolean open(String strHost, int nPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(strHost, nPort), N_TIMEOUT_MS);
            return true;
        }
        catch (IOException ex) {
            return false;
        }
    }
}
