// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * The discovery endpoint, B-8: one GET on one url, answering with what the
 * window last knew.
 *
 * <h2>It belongs to the WINDOW, not to the stack</h2>
 *
 * An endpoint that went down with the participant could not answer "nothing is
 * running", which is the one answer a client most needs and cannot get any
 * other way - a refused connection is indistinguishable from a Sandbox that was
 * never started, a Sandbox on another port, and a firewall. So this binds when
 * the window opens and unbinds when it closes, and reports a STOPPED state in
 * between.
 *
 * <h2>LOOPBACK, and not negotiable</h2>
 *
 * The document carries the participant's JDBC url, its role and its password,
 * because a consumer that has to ask a person for the password has not been
 * handed over to. Those are facts about a machine's own developer sandbox and
 * they do not go on a network interface. {@link InetAddress#getLoopbackAddress}
 * is what is bound and there is no setting that changes it.
 *
 * <h2>Read-only</h2>
 *
 * GET on `/` and nothing else. Anything that could START or STOP a stack is a
 * second way to drive the Sandbox with no authentication in front of it, and
 * the request was to publish what this one is.
 *
 * <h2>A snapshot, not a callback</h2>
 *
 * The supplier hands back a {@link DiscoveryDoc} the window built while it held
 * the event thread. The handler thread renders it and touches no Swing
 * component; a supplier that read fields directly would be reading a JTextField
 * from a thread that has no business doing so.
 *
 * Author Claude/bentzn
 */
public final class DiscoveryServer {

    /** The only path served. Anything else is 404. */
    static final String STR_PATH = "/";

    static final String STR_METHOD = "GET";

    static final String STR_CONTENT_TYPE = "application/json; charset=utf-8";

    static final String STR_THREAD = "raposza-discovery";

    /**
     * The default queue. One consumer polling a document is not a load, and a
     * backlog worth tuning would mean something is wrong at the other end.
     */
    private static final int N_BACKLOG = 0;

    /** How long {@link #stop} lets an in-flight exchange finish. */
    private static final int N_SECONDS_STOP = 1;

    private transient HttpServer server;

    private transient ExecutorService exec;

    private transient Supplier<DiscoveryDoc> supDoc;

    private int nPortBound;


    /**
     * @return the port actually bound, or 0 when nothing is
     */
    public int nPort() {
        return nPortBound;
    }


    public boolean isRunning() {
        return server != null;
    }


    /**
     * Binds, replacing whatever was bound before.
     *
     * THE OLD ONE GOES FIRST, unconditionally. A rebind that kept the previous
     * server alive when the new port was refused would leave the endpoint
     * answering on a port the Settings tab no longer names, which is worse than
     * not answering: a consumer would find it and believe it.
     *
     * @param nPortWanted the port to bind, or 0 for an ephemeral one
     * @param supDocNew asked for the document on every request; never null
     * @throws IOException when the port cannot be bound, which is the conflict
     *         the caller reports
     */
    public void start(int nPortWanted, Supplier<DiscoveryDoc> supDocNew) throws IOException {
        if (supDocNew == null)
            throw new IllegalArgumentException("a document supplier is required");
        stop();

        HttpServer serverNew = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), nPortWanted), N_BACKLOG);
        ExecutorService execNew = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, STR_THREAD);
            // A DAEMON. The window disposes and the JVM exits; a live pool
            // thread here would hold it open with nothing on screen to say why.
            thread.setDaemon(true);
            return thread;
        });
        serverNew.createContext(STR_PATH, this::handle);
        serverNew.setExecutor(execNew);

        this.supDoc = supDocNew;
        this.server = serverNew;
        this.exec = execNew;
        this.nPortBound = serverNew.getAddress().getPort();
        serverNew.start();
    }


    /**
     * Unbinds. Does nothing when nothing is bound.
     */
    public void stop() {
        HttpServer serverHere = server;
        ExecutorService execHere = exec;
        this.server = null;
        this.exec = null;
        this.supDoc = null;
        this.nPortBound = 0;
        if (serverHere != null)
            serverHere.stop(N_SECONDS_STOP);
        if (execHere != null)
            execHere.shutdownNow();
    }


    /**
     * @param nPort the bound port
     * @return the url a consumer connects to
     */
    public static String strUrlOf(int nPort) {
        return "http://127.0.0.1:" + nPort + STR_PATH;
    }


    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!STR_METHOD.equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"only GET\"}\n");
                return;
            }
            if (!STR_PATH.equals(exchange.getRequestURI().getPath())) {
                respond(exchange, 404, "{\"error\":\"only /\"}\n");
                return;
            }

            Supplier<DiscoveryDoc> supHere = supDoc;
            DiscoveryDoc doc = supHere == null ? null : supHere.get();
            if (doc == null) {
                // The window is up and has not built its first snapshot yet.
                // 503 rather than an empty document, because an empty document
                // would be read as a Sandbox that knows nothing about itself.
                respond(exchange, 503, "{\"error\":\"no snapshot yet\"}\n");
                return;
            }
            respond(exchange, 200, doc.strJson());
        }
        catch (RuntimeException ex) {
            // NOTHING THROWN OUT OF HERE. An exception escaping a handler drops
            // the connection with no status at all, and the consumer sees a
            // reset rather than a reason.
            respond(exchange, 500, "{\"error\":\"the document could not be built\"}\n");
        }
        finally {
            exchange.close();
        }
    }


    private static void respond(HttpExchange exchange, int nStatus, String strBody)
            throws IOException {
        byte[] arrBody = strBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", STR_CONTENT_TYPE);
        exchange.sendResponseHeaders(nStatus, arrBody.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(arrBody);
        }
    }
}
