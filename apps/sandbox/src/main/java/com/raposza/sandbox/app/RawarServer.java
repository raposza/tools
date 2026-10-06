// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raposza.rawar.RawarTree;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The RAWAR server: the web pages a developer writes against this Sandbox,
 * served while the window is open. `rawar.md` sections 4 and 5.
 *
 * <h2>What it answers, and nothing else</h2>
 *
 * Under every mount - {@link RawarSites} - three things:
 *
 * <ul>
 *   <li>`_env.json`, generated from the discovery snapshot - {@link RawarEnv};</li>
 *   <li>`_ledger/...`, forwarded to this stack's JSON Ledger API with the path
 *   below `_ledger` unchanged, on the page's own origin so it needs no address
 *   and meets no CORS;</li>
 *   <li>every other path, a file of the RAWAR's own directory.</li>
 * </ul>
 *
 * NO DEFAULT RESOURCES - his decision. A path under no mount is a plain 404
 * that names the mounts there are, which is a diagnosis and not a page.
 *
 * <h2>It belongs to the WINDOW, like the discovery endpoint</h2>
 *
 * It binds when the window opens and stays up while stacks come and go, so a
 * developer can edit pages with nothing running; `_ledger/` then answers 503
 * and `_env.json` says `running: false`.
 *
 * <h2>LOOPBACK</h2>
 *
 * `_ledger/` is a ledger API with the page's token in front of it. Putting that
 * on a network interface is a decision for a host that means it - BaseNet,
 * through its ingress - and not for a developer's window.
 *
 * <h2>Read from disk on every request</h2>
 *
 * A page edited is served on the next reload, and a directory added or a mount
 * changed in `rawar.json` likewise. A RAWAR is a handful of small files and a
 * developer's browser is the only client, so a cache would buy nothing and cost
 * the one property this exists for.
 *
 * <h2>Every request is told - his instruction, 2026-10-04</h2>
 *
 * "We need a log pane on 'Web' tab as well to show all access." Each request
 * ends with one {@link Access} to the consumer {@link #useAccess} set, on the
 * server's own thread: the method, the path WITHOUT its query, what answered
 * it and the status. The headers - the page's bearer among them - are not.
 *
 * Author Claude/bentzn
 */
public final class RawarServer {

    /** The status row's key, beside `port.discovery`. */
    public static final String STR_KEY_STATUS = "rawar";

    static final String STR_THREAD = "raposza-rawar";

    static final String STR_PATH_LEDGER = RawarTree.STR_RESERVED_LEDGER + "/";

    private static final int N_THREADS = 8;

    private static final int N_BACKLOG = 0;

    /** None - {@code DiscoveryServer}'s, measured 2026-10-03: stop(1) held the close a second. */
    private static final int N_SECONDS_STOP = 0;

    private static final Duration DUR_CONNECT = Duration.ofSeconds(5);

    private static final Duration DUR_REQUEST = Duration.ofSeconds(60);

    /**
     * Hop-by-hop headers and the ones the JDK client and server set themselves
     * - the list `LocalNetWeb` measured, for the same reason.
     */
    private static final Set<String> SET_HEADER_SKIP = Set.of("connection", "content-length",
            "host", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "proxy-connection", "te", "trailer", "transfer-encoding", "upgrade", "expect");

    private static final Map<String, String> MAP_MIME = mapMime();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private transient HttpServer server;

    private transient ExecutorService exec;

    private transient HttpClient client;

    private transient Supplier<DiscoveryDoc> supDoc;

    private Path dirRoot;

    private int nPortBound;

    /** Told of every request; kept across start and stop, it is the window's. */
    private transient volatile Consumer<Access> sinkAccess;


    /**
     * One request, as the Web tab shows it.
     *
     * @param strMethod GET, POST and the rest
     * @param strPath the path, without the query
     * @param strKind what answered: `file`, `env`, `ledger`, `redirect` or
     *        `none`
     * @param nStatus the status sent, -1 when none was
     */
    public record Access(String strMethod, String strPath, String strKind, int nStatus) {
    }


    /**
     * @param sinkNew told of every request from now on, or null for none
     */
    public void useAccess(Consumer<Access> sinkNew) {
        this.sinkAccess = sinkNew;
    }


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
     * @return the directory served, or null when nothing is bound
     */
    public Path dirRoot() {
        return dirRoot;
    }


    /**
     * Binds, replacing whatever was bound before - the old one goes first,
     * unconditionally, for the reason `DiscoveryServer.start` gives.
     *
     * @param nPortWanted the port, or 0 for an ephemeral one
     * @param dirRootNew the directory holding one directory per RAWAR
     * @param supDocNew asked for the discovery snapshot on every request
     * @throws IOException when the port cannot be bound
     */
    public void start(int nPortWanted, Path dirRootNew, Supplier<DiscoveryDoc> supDocNew)
            throws IOException {
        if (supDocNew == null || dirRootNew == null)
            throw new IllegalArgumentException("a directory and a document supplier are required");
        stop();

        HttpServer serverNew = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), nPortWanted), N_BACKLOG);
        AtomicInteger cntThread = new AtomicInteger();
        ExecutorService execNew = Executors.newFixedThreadPool(N_THREADS, runnable -> {
            Thread thread = new Thread(runnable, STR_THREAD + "-" + cntThread.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        serverNew.createContext("/", this::handle);
        serverNew.setExecutor(execNew);

        this.client = HttpClient.newBuilder().connectTimeout(DUR_CONNECT)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.supDoc = supDocNew;
        this.dirRoot = dirRootNew;
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
        this.client = null;
        this.dirRoot = null;
        this.nPortBound = 0;
        if (serverHere != null)
            serverHere.stop(N_SECONDS_STOP);
        if (execHere != null)
            execHere.shutdownNow();
    }


    /**
     * @return what is served right now, read off the disk
     */
    public RawarSites.Scan scan() {
        return RawarSites.scan(dirRoot);
    }


    /**
     * The status row: where the pages are and which mounts there are.
     *
     * @param nPortWanted the port the settings name, for the unbound case
     * @return one line
     */
    public String strStatus(int nPortWanted) {
        if (!isRunning())
            return "NOT BOUND - port " + nPortWanted + " is in use, change the RAWAR port on the"
                    + " Settings tab";
        RawarSites.Scan scanNow = scan();
        String strUrl = "http://127.0.0.1:" + nPortBound;
        if (scanNow.lstSite().isEmpty())
            return strUrl + " - no RAWAR in " + dirRoot;
        return strUrl + " - " + String.join(" ", scanNow.lstMount())
                + (scanNow.lstProblem().isEmpty() ? ""
                        : " - " + scanNow.lstProblem().size() + " not served");
    }


    private void handle(HttpExchange exchange) throws IOException {
        String strPath = exchange.getRequestURI().getPath();
        if (strPath == null || strPath.isEmpty())
            strPath = "/";
        String strKind = "none";
        try {
            RawarSites.Scan scanNow = RawarSites.scan(dirRoot);
            // THE SLASH FIRST, and it is not cosmetic. A page at `/usdcx`
            // linking `./css/a.css` resolves to `/css/a.css`; every relative
            // path in the RAWAR depends on the mount ending in one. Before the
            // match, because `/usdcx` also lies under a RAWAR mounted at `/`.
            RawarSites.Site siteSlash = scanNow.siteBelowSlash(strPath);
            if (siteSlash != null) {
                strKind = "redirect";
                exchange.getResponseHeaders().set("Location", siteSlash.strMount());
                sendText(exchange, 301, "moved to " + siteSlash.strMount());
                return;
            }
            RawarSites.Site site = scanNow.site(strPath);
            if (site == null) {
                sendText(exchange, 404, "no RAWAR at " + strPath + " - mounted: "
                        + (scanNow.lstSite().isEmpty() ? "none, in " + dirRoot
                                : String.join(" ", scanNow.lstMount())));
                return;
            }

            String strRel = strPath.substring(site.strMount().length());
            if (strRel.equals(RawarTree.STR_RESERVED_ENV)) {
                strKind = "env";
                serveEnv(exchange, site);
                return;
            }
            if (strRel.equals(RawarTree.STR_RESERVED_LEDGER) || strRel.startsWith(STR_PATH_LEDGER)) {
                strKind = "ledger";
                forward(exchange, strRel.length() <= RawarTree.STR_RESERVED_LEDGER.length() ? "/"
                        : strRel.substring(RawarTree.STR_RESERVED_LEDGER.length()));
                return;
            }
            strKind = "file";
            serveFile(exchange, site, strRel);
        }
        catch (RuntimeException ex) {
            sendText(exchange, 500, "the RAWAR server failed: " + ex);
        }
        finally {
            exchange.close();
            tell(new Access(exchange.getRequestMethod(), strPath, strKind,
                    exchange.getResponseCode()));
        }
    }


    /** A sink that throws must not take the page with it. */
    private void tell(Access access) {
        Consumer<Access> sinkHere = sinkAccess;
        if (sinkHere == null)
            return;
        try {
            sinkHere.accept(access);
        }
        catch (RuntimeException ex) {
            // Nothing to do: the request is answered, the line is lost.
        }
    }


    private void serveEnv(HttpExchange exchange, RawarSites.Site site) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "only GET");
            return;
        }
        Supplier<DiscoveryDoc> supHere = supDoc;
        DiscoveryDoc doc = supHere == null ? null : supHere.get();
        Map<String, Object> mapEnv = RawarEnv.mapEnv(doc, strOrigin(exchange), site.strMount());
        byte[] arrBody;
        try {
            arrBody = (MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(mapEnv) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("_env.json did not serialise", ex);
        }
        // NEVER CACHED. It changes when a stack starts or stops, and a page
        // holding a stale one signs in against a provider that has moved.
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        send(exchange, 200, "application/json; charset=utf-8", arrBody);
    }


    /**
     * The path below `_ledger`, forwarded unchanged with its query, method,
     * body and headers - the bearer among them, which is the page's and is
     * never looked at here.
     */
    private void forward(HttpExchange exchange, String strPathUp) throws IOException {
        Supplier<DiscoveryDoc> supHere = supDoc;
        String strBase = RawarEnv.strUrlLedger(supHere == null ? null : supHere.get());
        if (strBase == null) {
            sendText(exchange, 503, "no stack is running - start one and reload");
            return;
        }
        String strQuery = exchange.getRequestURI().getRawQuery();
        URI uriUp = URI.create(strBase + strPathUp + (strQuery == null ? "" : "?" + strQuery));

        byte[] arrBody = exchange.getRequestBody().readAllBytes();
        HttpRequest.Builder bld = HttpRequest.newBuilder(uriUp).timeout(DUR_REQUEST)
                .method(exchange.getRequestMethod(), arrBody.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(arrBody));
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
            sendText(exchange, 502, "interrupted forwarding to " + uriUp);
            return;
        }
        catch (IOException ex) {
            sendText(exchange, 502, "the ledger at " + uriUp + " did not answer: " + ex.getMessage());
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
        exchange.sendResponseHeaders(response.statusCode(), arrOut.length == 0 ? -1 : arrOut.length);
        if (arrOut.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(arrOut);
            }
        }
    }


    /**
     * A file of the RAWAR's own. A directory gives its `index.html`; nothing
     * else is guessed - no single-page fallback, because a RAWAR's pages link
     * each other by relative path and a missing one should say so.
     */
    private void serveFile(HttpExchange exchange, RawarSites.Site site, String strRel)
            throws IOException {
        String strMethod = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(strMethod) && !"HEAD".equalsIgnoreCase(strMethod)) {
            sendText(exchange, 405, "pages are GET only");
            return;
        }
        Path dirSite = site.dirRoot().toAbsolutePath().normalize();
        Path fileWanted = dirSite.resolve(strRel).normalize();
        if (!fileWanted.startsWith(dirSite)) {
            sendText(exchange, 403, "outside the RAWAR");
            return;
        }
        // HIDDEN ENTRIES ARE NOT PART OF A RAWAR - `RawarTree` - so they are
        // not served either: a `.git` beside the pages is not a page.
        Path rel = dirSite.relativize(fileWanted);
        if (!rel.toString().isEmpty() && isHidden(rel)) {
            sendText(exchange, 404, "not found: " + strRel);
            return;
        }
        if (Files.isDirectory(fileWanted))
            fileWanted = fileWanted.resolve(RawarTree.STR_INDEX);
        if (!Files.isRegularFile(fileWanted)) {
            sendText(exchange, 404, "not found in " + site.strName() + ": " + strRel);
            return;
        }
        // NEVER CACHED. The developer's loop is edit and reload, and a cached
        // script makes a fix look as though it did not work.
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        byte[] arrBody = Files.readAllBytes(fileWanted);
        if ("HEAD".equalsIgnoreCase(strMethod)) {
            exchange.getResponseHeaders().set("Content-Type", strMime(fileWanted));
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        send(exchange, 200, strMime(fileWanted), arrBody);
    }


    private static boolean isHidden(Path rel) {
        for (Path part : rel) {
            if (part.toString().startsWith("."))
                return true;
        }
        return false;
    }


    /**
     * @return the origin the browser reached, from its Host header - so the
     *         redirect url is the spelling it will come back to
     */
    private String strOrigin(HttpExchange exchange) {
        String strHost = exchange.getRequestHeaders().getFirst("Host");
        if (strHost == null || strHost.isBlank())
            strHost = "127.0.0.1:" + nPortBound;
        return "http://" + strHost.trim();
    }


    private static String strMime(Path file) {
        String strName = file.getFileName().toString();
        int nDot = strName.lastIndexOf('.');
        String strExt = nDot < 0 ? "" : strName.substring(nDot + 1).toLowerCase(Locale.ROOT);
        return MAP_MIME.getOrDefault(strExt, "application/octet-stream");
    }


    private static void sendText(HttpExchange exchange, int nStatus, String strText)
            throws IOException {
        send(exchange, nStatus, "text/plain; charset=utf-8",
                (strText + "\n").getBytes(StandardCharsets.UTF_8));
    }


    private static void send(HttpExchange exchange, int nStatus, String strType, byte[] arrBody)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", strType);
        exchange.sendResponseHeaders(nStatus, arrBody.length == 0 ? -1 : arrBody.length);
        if (arrBody.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(arrBody);
            }
        }
    }


    /** The table `LocalNetWeb` serves the Splice bundles with. */
    private static Map<String, String> mapMime() {
        Map<String, String> mapMime = new LinkedHashMap<>();
        mapMime.put("html", "text/html; charset=utf-8");
        mapMime.put("htm", "text/html; charset=utf-8");
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
