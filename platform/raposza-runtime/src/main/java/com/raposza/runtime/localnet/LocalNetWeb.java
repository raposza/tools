// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * LocalNet's web pages, served without nginx and without a container.
 *
 * WHY NOT NGINX. The four UI images are static bundles behind a proxy, and
 * conf/nginx does four easy things - serve those bundles, route by Host, forward
 * five path prefixes, add CORS headers - plus one hard one, the `grpc_pass`
 * vhost. No UI dials the hard one. What is left is a static file handler and an
 * HTTP/1.1 reverse proxy, so it is done here, in the process that already owns
 * Canton, Splice and PostgreSQL: the pages come up and go down with the stack,
 * with nothing extra to install and nothing that can outlive a Ctrl-C.
 *
 * THE PROXY IS NOT A CONVENIENCE. The wallet calls its validator from the
 * browser. Pointed straight at &lt;prefix&gt;903 that is a cross-origin call to a
 * backend that sends no CORS headers of its own, so the same-origin forward is
 * what makes the page work at all - which is what nginx was doing here.
 *
 * HTTP/1.1 ONLY, deliberately. com.sun.net.httpserver speaks no HTTP/2 and
 * terminates no TLS. On loopback, serving assets and JSON, neither is missed;
 * anything needing gRPC dials the participant's own port directly.
 *
 * Author Claude/bentzn
 */
public final class LocalNetWeb implements AutoCloseable {

    private static final int N_THREADS = 24;

    private static final int N_BACKLOG = 32;

    private static final Duration TIMEOUT_UP = Duration.ofSeconds(30);

    /**
     * WHERE THE REQUEST LOG ROTATES - `todo.md` L-12, 10 MB. The SV UI polls
     * three endpoints at about 1 Hz, some three lines a second per open tab
     * (D-584), so a window left open for a day writes tens of MB. At this size
     * the file is moved to `web.log.1`, replacing the previous one, and a new
     * one is begun: at most two files, 20 MB, whatever the uptime.
     */
    public static final long N_BYTES_LOG_MAX = 10_000_000L;

    /** What the rotated file is called beside the live one. */
    public static final String STR_SUFFIX_ROTATED = ".1";

    /**
     * Hop-by-hop headers, plus the ones the JDK client and server insist on
     * setting themselves. Copying any of these through is either rejected
     * outright or produces a response whose framing contradicts its body.
     */
    private static final Set<String> SET_HEADER_SKIP = Set.of("connection", "content-length",
            "host", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "proxy-connection", "te", "trailer", "transfer-encoding", "upgrade", "expect");

    private static final Map<String, String> MAP_MIME = mapMime();

    private final String strHost;
    private final Path dirWebUis;
    private final List<LocalNetUi.Site> lstSite;
    private final Path fileLog;
    private final List<HttpServer> lstServer = new ArrayList<>();

    private ExecutorService exec;
    private HttpClient client;
    private volatile Writer writerLog;

    /** What has been written to the live file, counted from its size at open. */
    private long nBytesLog;

    /** Where it rotates; {@link #N_BYTES_LOG_MAX} except under test. */
    private final long nBytesLogMax;

    /**
     * @param strHost the interface the pages bind to
     * @param dirWebUis the bundle's web-uis directory
     * @param lstSite what LocalNetUi built
     * @param fileLog one line per request lands here, or null for no log
     */
    public LocalNetWeb(String strHost, Path dirWebUis, List<LocalNetUi.Site> lstSite,
            Path fileLog) {
        this(strHost, dirWebUis, lstSite, fileLog, N_BYTES_LOG_MAX);
    }


    /**
     * @param strHost the interface the pages bind to
     * @param dirWebUis the bundle's web-uis directory
     * @param lstSite what LocalNetUi built
     * @param fileLog one line per request lands here, or null for no log
     * @param nBytesLogMaxNew where the log rotates - a test's small number
     */
    LocalNetWeb(String strHost, Path dirWebUis, List<LocalNetUi.Site> lstSite, Path fileLog,
            long nBytesLogMaxNew) {
        this.nBytesLogMax = nBytesLogMaxNew;
        this.strHost = strHost;
        this.dirWebUis = dirWebUis.toAbsolutePath().normalize();
        this.lstSite = List.copyOf(lstSite);
        this.fileLog = fileLog == null ? null : fileLog.toAbsolutePath().normalize();
    }


    /**
     * @return where the request log is written, or null
     */
    public Path fileLog() {
        return fileLog;
    }


    /**
     * A site declared on port 0 binds an ephemeral one, which is the only way a
     * test can run without fighting a real LocalNet for 2000.
     *
     * @return the ports actually bound, in site order; empty before start
     */
    public List<Integer> lstPortBound() {
        List<Integer> lstPort = new ArrayList<>();
        for (HttpServer server : lstServer) {
            lstPort.add(Integer.valueOf(server.getAddress().getPort()));
        }
        return lstPort;
    }


    /**
     * @return the bundles named by the sites that are not on disk; a site whose
     *         bundle is missing still serves its proxied prefixes
     */
    public List<String> lstBundleMissing() {
        List<String> lstMissing = new ArrayList<>();
        for (LocalNetUi.Site site : lstSite) {
            for (LocalNetUi.Vhost vhost : site.lstVhost()) {
                if (vhost.strApp() == null)
                    continue;
                if (!Files.isDirectory(dirWebUis.resolve(vhost.strApp()))
                        && !lstMissing.contains(vhost.strApp()))
                    lstMissing.add(vhost.strApp());
            }
        }
        return lstMissing;
    }


    /**
     * A vhost nobody can resolve is a page nobody can open, and the failure
     * arrives in the browser as a DNS error with nothing in any log here.
     *
     * @return the hostnames that do not resolve on this machine
     */
    public static List<String> lstHostUnresolved() {
        List<String> lstBad = new ArrayList<>();
        for (String strName : LocalNetUi.lstHostName()) {
            try {
                InetAddress.getByName(strName);
            }
            catch (UnknownHostException ex) {
                lstBad.add(strName);
            }
        }
        return lstBad;
    }


    /**
     * One page, as measured through the socket it is served on.
     *
     * @param strUrl what to type into a browser
     * @param strTitle what the page calls itself
     * @param flagOk whether the bundle, its configuration and its backends all
     *        answered
     * @param strNote the measurement itself, one line
     */
    public record Check(String strUrl, String strTitle, boolean flagOk, String strNote) {
    }


    /**
     * WHAT THIS MEASURES AND WHAT IT CANNOT. The vhost travels in a Host header
     * over a socket opened to the bound interface, so no name is resolved
     * anywhere - which is exactly the failure that kept a page from being
     * opened on Windows, where the OS resolver knows nothing under .localhost
     * and the browser never asks it anyway. What comes back is whether the
     * bundle is on disk, whether /config.js is generated, and whether each
     * proxied prefix reaches a backend at all. It does not run the bundle, so
     * "renders" remains a claim only a browser settles.
     *
     * @return one entry per page, in {@link LocalNetUi#lstPage} order; empty
     *         before start
     */
    public List<Check> lstCheck() {
        List<Check> lstCheck = new ArrayList<>();
        List<Integer> lstPort = lstPortBound();
        for (int cntSite = 0; cntSite < lstSite.size() && cntSite < lstPort.size(); cntSite++) {
            LocalNetUi.Site site = lstSite.get(cntSite);
            for (LocalNetUi.Vhost vhost : site.lstVhost()) {
                // THE ALIASES ARE NOT PAGES: one check per page, in page order.
                if (vhost.strApp() == null || vhost.flagAlias())
                    continue;
                lstCheck.add(check(site, vhost, lstPort.get(cntSite).intValue()));
            }
        }
        return lstCheck;
    }


    /**
     * A BACKEND THAT CANNOT BE DIALLED IS A BROKEN PAGE, and that is a 502 from
     * the proxy or nothing at all. Any other status is the backend answering,
     * and what it says to a bare prefix is its own business - the validator
     * returns 404 there and is perfectly healthy.
     *
     * @param site the site this vhost belongs to
     * @param vhost the vhost to measure
     * @param nPort the port actually bound, which is not site.nPort() in a test
     * @return what it answered
     */
    private Check check(LocalNetUi.Site site, LocalNetUi.Vhost vhost, int nPort) {
        StringBuilder bldNote = new StringBuilder();
        boolean flagOk = true;

        Reply replyIndex = get(nPort, vhost.strHost(), "/");
        bldNote.append("index ").append(replyIndex.strStatus()).append(' ')
                .append(replyIndex.nBytes()).append('B');
        if (replyIndex.nStatus() != 200 || replyIndex.nBytes() == 0)
            flagOk = false;

        if (vhost.strConfigJs() != null) {
            Reply replyConfig = get(nPort, vhost.strHost(), "/config.js");
            bldNote.append("   config.js ").append(replyConfig.strStatus());
            if (replyConfig.nStatus() != 200)
                flagOk = false;
        }

        for (LocalNetUi.Route route : vhost.lstRoute()) {
            Reply replyRoute = get(nPort, vhost.strHost(), route.strPrefix());
            bldNote.append("   ").append(route.strPrefix()).append(' ')
                    .append(replyRoute.strStatus());
            if (replyRoute.nStatus() == 0 || replyRoute.nStatus() == 502)
                flagOk = false;
        }
        return new Check("http://" + vhost.strHost() + ":" + site.nPort(),
                LocalNetUi.strTitle(vhost.strApp(), site.strRole()), flagOk,
                bldNote.toString());
    }


    /**
     * @param nStatus the HTTP status, or 0 when nothing answered
     * @param nBytes how many bytes of body came back
     * @param strStatus what to print - the status, or why there was none
     */
    private record Reply(int nStatus, int nBytes, String strStatus) {
    }


    /**
     * RAW SOCKET, NOT HttpClient. java.net.http refuses to set Host without a
     * JVM-wide system property, and the Host header is the entire point of this
     * request.
     *
     * @param nPort the bound UI port
     * @param strName the vhost to ask for
     * @param strPath the path to request
     * @return what came back
     */
    private Reply get(int nPort, String strName, String strPath) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(strHost, nPort), 2000);
            socket.setSoTimeout(10000);
            byte[] arrReq = ("GET " + strPath + " HTTP/1.1\r\nHost: " + strName
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            OutputStream stream = socket.getOutputStream();
            stream.write(arrReq);
            stream.flush();
            return reply(socket.getInputStream().readAllBytes());
        }
        catch (IOException ex) {
            return new Reply(0, 0, "UNREACHABLE " + ex.getClass().getSimpleName());
        }
    }


    /**
     * @param arrAll the whole response as it came off the wire
     * @return its status and body length
     */
    private static Reply reply(byte[] arrAll) {
        String strHead = new String(arrAll, 0, Math.min(arrAll.length, 1024),
                StandardCharsets.ISO_8859_1);
        int nEol = strHead.indexOf("\r\n");
        int nStatus = 0;
        if (nEol > 0) {
            String[] arrPart = strHead.substring(0, nEol).split(" ");
            if (arrPart.length >= 2) {
                try {
                    nStatus = Integer.parseInt(arrPart[1]);
                }
                catch (NumberFormatException ex) {
                    nStatus = 0;
                }
            }
        }
        int nBody = idxBody(arrAll);
        return new Reply(nStatus, nBody < 0 ? 0 : arrAll.length - nBody,
                nStatus == 0 ? "NO STATUS LINE" : Integer.toString(nStatus));
    }


    /**
     * @param arr the whole response
     * @return where the body starts, or -1 when the headers never ended
     */
    private static int idxBody(byte[] arr) {
        for (int cntByte = 0; cntByte + 3 < arr.length; cntByte++) {
            if (arr[cntByte] == '\r' && arr[cntByte + 1] == '\n' && arr[cntByte + 2] == '\r'
                    && arr[cntByte + 3] == '\n')
                return cntByte + 4;
        }
        return -1;
    }


    /**
     * THE UNRESOLVED NAMES ARE NOT NORMALLY A BLOCKER, and saying so flatly
     * cost W-7 a week of pages nobody opened. lstHostUnresolved asks the OS
     * resolver, which is the one caller a browser is not: Chrome, Edge and
     * Firefox hardcode every *.localhost name to 127.0.0.1 per RFC 6761 and
     * never issue the query. On Windows the OS resolver refuses them all, on
     * every run, and the page opens anyway.
     *
     * @param strFileHosts the platform's hosts file
     * @return what to tell the operator, empty when every name resolves
     */
    public static List<String> lstHostAdvice(String strFileHosts) {
        List<String> lstBad = lstHostUnresolved();
        if (lstBad.isEmpty())
            return List.of();
        return List.of(
                "  the OS resolver does not know " + String.join(", ", lstBad),
                "  Chrome, Edge and Firefox map *.localhost to 127.0.0.1 themselves and never",
                "  ask it, so OPEN THE PAGE FIRST - the measurement above needed no name either",
                "  only if the browser itself reports a DNS error, add to " + strFileHosts + ":",
                "  127.0.0.1  " + String.join(" ", lstBad));
    }


    public void start() {
        AtomicInteger cntThread = new AtomicInteger();
        exec = Executors.newFixedThreadPool(N_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "localnet-web-" + cntThread.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        openLog();
        client = HttpClient.newBuilder().connectTimeout(TIMEOUT_UP)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).build();

        try {
            for (LocalNetUi.Site site : lstSite) {
                HttpServer server = HttpServer
                        .create(new InetSocketAddress(strHost, site.nPort()), N_BACKLOG);
                server.createContext("/", exchange -> handle(exchange, site));
                server.setExecutor(exec);
                server.start();
                lstServer.add(server);
            }
        }
        catch (IOException ex) {
            stop();
            throw new UncheckedIOException("could not bind a UI port", ex);
        }
    }


    public void stop() {
        for (HttpServer server : lstServer) {
            server.stop(0);
        }
        lstServer.clear();
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
        client = null;
        closeLog();
    }


    @Override
    public void close() {
        stop();
    }


    /**
     * @param site the site whose port this exchange arrived on
     */
    private void handle(HttpExchange exchange, LocalNetUi.Site site) {
        long nStart = System.nanoTime();
        String strName = strHostName(exchange);
        String strPath = exchange.getRequestURI().getPath();
        String strKind = "none";
        try {
            LocalNetUi.Vhost vhost = vhost(site, strName);
            if (vhost.flagCors())
                addCors(exchange);
            if (vhost.flagCors() && "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                strKind = "cors";
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (vhost.strConfigJs() != null && "/config.js".equals(strPath)) {
                strKind = "config";
                send(exchange, 200, "text/javascript; charset=utf-8",
                        vhost.strConfigJs().getBytes(StandardCharsets.UTF_8));
                return;
            }

            LocalNetUi.Route route = route(vhost, strPath);
            if (route != null) {
                strKind = "proxy " + route.strUpstream();
                proxy(exchange, route);
                return;
            }
            if (vhost.strApp() == null) {
                send(exchange, 404, "text/plain; charset=utf-8",
                        ("no route for " + strPath + "\n").getBytes(StandardCharsets.UTF_8));
                return;
            }
            strKind = "static " + vhost.strApp();
            serveStatic(exchange, vhost.strApp(), strPath);
        }
        catch (IOException | RuntimeException ex) {
            fail(exchange, ex);
        }
        finally {
            log(exchange, strName, strPath, strKind, nStart);
            exchange.close();
        }
    }


    /**
     * ONE LINE PER REQUEST, and the reason it exists: a page that half-works
     * looks identical in the browser whether a call 502'd, 404'd into the SPA
     * fallback as HTML, or was answered correctly with an empty body. Without
     * this the only way to tell was curl against both the proxy and the backend,
     * which cost a session on 2026-08-26.
     *
     * The kind column is what nginx's access log cannot say: which of the four
     * paths through this server the request took, and to which upstream.
     */
    private void log(HttpExchange exchange, String strName, String strPath, String strKind,
            long nStart) {
        Writer writer = writerLog;
        if (writer == null)
            return;
        long nMs = (System.nanoTime() - nStart) / 1000000L;
        String strLine = Instant.now() + "  " + exchange.getRequestMethod() + "  " + strName
                + "  " + strPath + "  " + strKind + "  " + exchange.getResponseCode() + "  "
                + nMs + "ms" + System.lineSeparator();
        synchronized (this) {
            Writer writerNow = writerLog;
            if (writerNow == null)
                return;
            try {
                writerNow.write(strLine);
                writerNow.flush();
                nBytesLog += strLine.getBytes(StandardCharsets.UTF_8).length;
                if (nBytesLog >= nBytesLogMax)
                    rotateLog();
            }
            catch (IOException ex) {
                // A log that cannot be written must not take the page with it.
                writerLog = null;
            }
        }
    }


    /**
     * `web.log` BECOMES `web.log.1` AND A NEW ONE IS BEGUN - `todo.md` L-12.
     * The window's tail reads the path again on every poll and restarts at 0
     * when the file is shorter than where it was, so it follows the new file
     * without being told.
     *
     * Called holding this object's monitor, with the writer open.
     *
     * @throws IOException when the move fails; the caller drops the log
     */
    private void rotateLog() throws IOException {
        Writer writer = writerLog;
        writerLog = null;
        if (writer != null)
            writer.close();
        Files.move(fileLog, fileLog.resolveSibling(fileLog.getFileName() + STR_SUFFIX_ROTATED),
                StandardCopyOption.REPLACE_EXISTING);
        openLog();
    }


    /**
     * Flushed per line on purpose: the process is normally killed with Ctrl-C,
     * and a buffer that loses the last requests loses exactly the ones being
     * investigated.
     */
    private void openLog() {
        if (fileLog == null)
            return;
        try {
            if (fileLog.getParent() != null)
                Files.createDirectories(fileLog.getParent());
            writerLog = Files.newBufferedWriter(fileLog, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
            nBytesLog = Files.size(fileLog);
        }
        catch (IOException ex) {
            System.out.println("--- web ui: no request log at " + fileLog + ": "
                    + ex.getMessage());
            writerLog = null;
        }
    }


    private synchronized void closeLog() {
        Writer writer = writerLog;
        writerLog = null;
        if (writer == null)
            return;
        try {
            writer.close();
        }
        catch (IOException ex) {
            // nothing left to do with it
        }
    }


    /**
     * nginx sends an unmatched Host to the first server block on the port. This
     * sends it to the first vhost too - which LocalNetUi orders so that it is
     * the page the operator most likely wanted.
     *
     * @return the vhost that answers, never null
     */
    private LocalNetUi.Vhost vhost(LocalNetUi.Site site, String strName) {
        for (LocalNetUi.Vhost vhost : site.lstVhost()) {
            if (vhost.strHost().equals(strName))
                return vhost;
        }
        return site.lstVhost().get(0);
    }


    /**
     * @return the Host header without its port, lowercased, or an empty string
     */
    private String strHostName(HttpExchange exchange) {
        String strHeader = exchange.getRequestHeaders().getFirst("Host");
        if (strHeader == null)
            return "";
        String strName = strHeader.trim().toLowerCase(Locale.ROOT);
        int nColon = strName.lastIndexOf(':');
        return nColon > 0 ? strName.substring(0, nColon) : strName;
    }


    /**
     * Longest prefix wins, which is how nginx picks between two `location`
     * blocks that both match.
     *
     * @return the route for this path, or null when none matches
     */
    private LocalNetUi.Route route(LocalNetUi.Vhost vhost, String strPath) {
        LocalNetUi.Route routeBest = null;
        for (LocalNetUi.Route route : vhost.lstRoute()) {
            if (!strPath.equals(route.strPrefix()) && !strPath.startsWith(route.strPrefix() + "/")
                    && !"/".equals(route.strPrefix()))
                continue;
            if (routeBest == null
                    || route.strPrefix().length() > routeBest.strPrefix().length())
                routeBest = route;
        }
        return routeBest;
    }


    /**
     * The path is forwarded unchanged. Upstream's `rewrite ^\\/(.*) /$1 break`
     * is the identity rewrite, and with `break` in force nginx passes the whole
     * rewritten URI, so the container was doing the same thing in four lines.
     */
    private void proxy(HttpExchange exchange, LocalNetUi.Route route) throws IOException {
        URI uriIn = exchange.getRequestURI();
        String strQuery = uriIn.getRawQuery();
        URI uriUp = URI.create(route.strUpstream() + uriIn.getRawPath()
                + (strQuery == null ? "" : "?" + strQuery));

        byte[] arrBody = exchange.getRequestBody().readAllBytes();
        HttpRequest.BodyPublisher publisher = arrBody.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(arrBody);
        HttpRequest.Builder bld = HttpRequest.newBuilder(uriUp)
                .method(exchange.getRequestMethod(), publisher);
        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
            if (SET_HEADER_SKIP.contains(entry.getKey().toLowerCase(Locale.ROOT)))
                continue;
            for (String strValue : entry.getValue()) {
                bld.header(entry.getKey(), strValue);
            }
        }

        HttpResponse<byte[]> response;
        try {
            response = client.send(bld.build(), HttpResponse.BodyHandlers.ofByteArray());
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted forwarding to " + uriUp, ex);
        }
        catch (IOException ex) {
            send(exchange, 502, "text/plain; charset=utf-8",
                    ("upstream " + uriUp + ": " + ex.getMessage() + "\n")
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }

        for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
            if (SET_HEADER_SKIP.contains(entry.getKey().toLowerCase(Locale.ROOT)))
                continue;
            for (String strValue : entry.getValue()) {
                exchange.getResponseHeaders().add(entry.getKey(), strValue);
            }
        }
        byte[] arrOut = response.body();
        exchange.sendResponseHeaders(response.statusCode(), arrOut.length == 0 ? -1
                : arrOut.length);
        if (arrOut.length > 0) {
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(arrOut);
            }
        }
    }


    /**
     * SPA FALLBACK IS DELIBERATE. These bundles are single-page apps with
     * client-side routing, so a deep link is a path with no file behind it. A
     * request whose last segment carries no dot therefore gets index.html;
     * anything that looks like an asset gets an honest 404.
     */
    private void serveStatic(HttpExchange exchange, String strApp, String strPath)
            throws IOException {
        Path dirRoot = dirWebUis.resolve(strApp).normalize();
        String strRel = strPath.startsWith("/") ? strPath.substring(1) : strPath;
        Path fileWanted = dirRoot.resolve(strRel).normalize();

        if (!fileWanted.startsWith(dirRoot)) {
            send(exchange, 403, "text/plain; charset=utf-8",
                    "no\n".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (Files.isDirectory(fileWanted))
            fileWanted = fileWanted.resolve("index.html");
        if (!Files.isRegularFile(fileWanted)) {
            String strLast = fileWanted.getFileName().toString();
            if (strLast.indexOf('.') >= 0) {
                send(exchange, 404, "text/plain; charset=utf-8",
                        ("not found: " + strPath + "\n").getBytes(StandardCharsets.UTF_8));
                return;
            }
            fileWanted = dirRoot.resolve("index.html");
        }
        if (!Files.isRegularFile(fileWanted)) {
            send(exchange, 404, "text/plain; charset=utf-8",
                    ("no bundle at " + dirRoot + "\n").getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(exchange, 200, strMime(fileWanted), Files.readAllBytes(fileWanted));
    }


    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods",
                "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
    }


    private void send(HttpExchange exchange, int nStatus, String strType, byte[] arrBody)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", strType);
        exchange.sendResponseHeaders(nStatus, arrBody.length == 0 ? -1 : arrBody.length);
        if (arrBody.length == 0)
            return;
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(arrBody);
        }
    }


    /**
     * A handler that throws leaves the connection open and the browser waiting,
     * so every failure becomes a 500 with the reason in the body.
     */
    private void fail(HttpExchange exchange, Exception ex) {
        try {
            send(exchange, 500, "text/plain; charset=utf-8",
                    (ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n")
                            .getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException exSend) {
            exchange.close();
        }
    }


    private static String strMime(Path file) {
        String strName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int nDot = strName.lastIndexOf('.');
        String strExt = nDot < 0 ? "" : strName.substring(nDot + 1);
        return MAP_MIME.getOrDefault(strExt, "application/octet-stream");
    }


    private static Map<String, String> mapMime() {
        Map<String, String> mapMime = new LinkedHashMap<>();
        mapMime.put("html", "text/html; charset=utf-8");
        mapMime.put("js", "text/javascript; charset=utf-8");
        mapMime.put("mjs", "text/javascript; charset=utf-8");
        mapMime.put("css", "text/css; charset=utf-8");
        mapMime.put("json", "application/json; charset=utf-8");
        mapMime.put("map", "application/json; charset=utf-8");
        mapMime.put("txt", "text/plain; charset=utf-8");
        mapMime.put("svg", "image/svg+xml");
        mapMime.put("png", "image/png");
        mapMime.put("jpg", "image/jpeg");
        mapMime.put("jpeg", "image/jpeg");
        mapMime.put("gif", "image/gif");
        mapMime.put("ico", "image/x-icon");
        mapMime.put("webp", "image/webp");
        mapMime.put("woff", "font/woff");
        mapMime.put("woff2", "font/woff2");
        mapMime.put("ttf", "font/ttf");
        mapMime.put("otf", "font/otf");
        mapMime.put("wasm", "application/wasm");
        return mapMime;
    }
}
