// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Fetches one release artefact to a file.
 *
 * <h2>A response that is not 200 is a FAILURE, never a file</h2>
 *
 * The way this fails in the field is a progress bar reaching 100% followed by
 * an extract that reports an unrecognised archive format: the fetch wrote a
 * server error page under the artefact's name and reported success. A download
 * that reports success having written 12 kB of HTML is worse than one that
 * refuses, because the failure then arrives one step later with a message
 * about the wrong subject. So the status is checked before a single byte
 * reaches the destination.
 *
 * <h2>Written beside, then moved</h2>
 *
 * Bytes land in `&lt;name&gt;.part` and the file appears at its real name only
 * on success. A partial file carrying the right name is indistinguishable from
 * a complete one, and on a slow link a partial file is the normal outcome of
 * closing the window.
 *
 * <h2>No read timeout, and that is deliberate</h2>
 *
 * The SDK tarballs are hundreds of megabytes and the guest this is tested on
 * reaches the network over an emulated NIC slow enough for one to take several
 * minutes. A read timeout sized for that is not a timeout; the connect attempt
 * is bounded instead, which is what actually distinguishes an unreachable host
 * from a slow one.
 *
 * Author Claude/bentzn
 */
public final class Download {

    /** How long to wait for the connection itself, not for the body. */
    public static final int N_SECONDS_CONNECT = 30;

    public static final String STR_SUFFIX_PART = ".part";

    private static final int N_BUFFER = 65536;

    private static final Logger log = LoggerFactory.getLogger(Download.class);


    /**
     * Called as bytes arrive, so a window can show progress on a fetch that
     * takes minutes.
     */
    @FunctionalInterface
    public interface Progress {

        /**
         * @param cntBytes how much has been written so far
         * @param cntTotal what the server said the whole body is, or -1 when it
         *        did not say
         */
        void onBytes(long cntBytes, long cntTotal);
    }


    private Download() {
    }


    /**
     * For an endpoint whose whole answer is a short piece of text - the version
     * the vendor considers current, and nothing else. The same status rule
     * applies: a body is only read from a 200.
     *
     * @param strUrl what to read; never null
     * @return the body, trimmed
     * @throws IOException on a connection failure or a status other than 200
     */
    public static String strFetch(String strUrl) throws IOException {
        if (strUrl == null || strUrl.isBlank())
            throw new IllegalArgumentException("a url is required");

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(N_SECONDS_CONNECT))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(strUrl)).GET().build();

        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IOException("read of " + strUrl + " answered HTTP "
                        + response.statusCode());

            return response.body().trim();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reading " + strUrl, ex);
        }
    }


    /**
     * @param strUrl what to fetch; never null
     * @param fileOut where to write it; its directory is created
     * @param progress called as bytes arrive, or null
     * @return how many bytes were written
     * @throws IOException on a connection failure, a status other than 200, or
     *         a body shorter than the length the server declared
     */
    public static long fetch(String strUrl, Path fileOut, Progress progress) throws IOException {
        if (strUrl == null || strUrl.isBlank())
            throw new IllegalArgumentException("a url is required");
        if (fileOut == null)
            throw new IllegalArgumentException("a destination is required");

        Path filePart = fileOut.resolveSibling(fileOut.getFileName() + STR_SUFFIX_PART);
        Path dirParent = fileOut.toAbsolutePath().getParent();
        if (dirParent != null)
            Files.createDirectories(dirParent);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(N_SECONDS_CONNECT))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(strUrl)).GET().build();

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while fetching " + strUrl, ex);
        }

        // BEFORE ANY BYTES ARE WRITTEN.
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("fetch of " + strUrl + " answered HTTP "
                    + response.statusCode());
        }

        long cntTotal = response.headers().firstValueAsLong("content-length").orElse(-1L);
        long cntWritten = 0L;
        try (InputStream in = response.body();
                OutputStream out = Files.newOutputStream(filePart)) {
            byte[] arrBuf = new byte[N_BUFFER];
            int cntRead = in.read(arrBuf);
            while (cntRead >= 0) {
                out.write(arrBuf, 0, cntRead);
                cntWritten += cntRead;
                if (progress != null)
                    progress.onBytes(cntWritten, cntTotal);
                cntRead = in.read(arrBuf);
            }
        }
        catch (IOException ex) {
            Files.deleteIfExists(filePart);
            throw ex;
        }

        // A TRUNCATED BODY IS A FAILED FETCH. One artefact arrived at a tenth
        // of its size over a slow link and would otherwise have failed at
        // extract time, naming the archive rather than the transfer.
        if (cntTotal >= 0 && cntWritten != cntTotal) {
            Files.deleteIfExists(filePart);
            throw new IOException("fetch of " + strUrl + " gave " + cntWritten
                    + " bytes, the server declared " + cntTotal);
        }

        Files.move(filePart, fileOut, StandardCopyOption.REPLACE_EXISTING);
        log.info("fetched {} bytes to {}", cntWritten, fileOut);
        return cntWritten;
    }

}
