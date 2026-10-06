// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The OAuth client every RAWAR page signs in as - {@link RawarEnv#STR_CLIENT_ID}
 * - registered at the window's own provider with the mounts' urls.
 *
 * <h2>ONLY WHEN THE PROVIDER IS ALREADY CHECKING CLIENTS</h2>
 *
 * Raposza OIDC with no client registered checks nothing, and registering the
 * FIRST client turns the checks on for every client - `MintRegistration`, and
 * `OidcClients` in Raposza OIDC. A Sandbox that has never run LocalNetND has
 * an empty registry, and a RAWAR registration there would start refusing the
 * `client_id` every existing mint call presents. So the registry is asked
 * first: open, and a page signs in as anything and nothing is written;
 * strict - LocalNetND has registered `localnet-ui` - and `rawar` is written
 * with exactly the urls served now, which replaces what it had.
 *
 * <h2>The embedded provider only</h2>
 *
 * An external one is somebody else's, and its admin surface - if it has one -
 * is not this window's to write to. The caller says so instead.
 *
 * Author Claude/bentzn
 */
public final class RawarClient {

    private static final Duration DUR_REQUEST = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper();


    private RawarClient() {
    }


    /**
     * @param lstRedirect the urls a sign-in may return to
     * @return the body `/api/ui/clients` takes - a public client, no secret
     */
    public static String strBodyClient(List<String> lstRedirect) {
        try {
            return MAPPER.writeValueAsString(Map.of("client_id", RawarEnv.STR_CLIENT_ID,
                    "secret", "", "redirect_uris", String.join(" ", lstRedirect)));
        }
        catch (IOException ex) {
            throw new IllegalStateException("a three-string map cannot fail to serialise", ex);
        }
    }


    /**
     * Registers the client if the provider is checking clients.
     *
     * @param strUrlBase the provider, no trailing slash
     * @param lstRedirect the urls a sign-in may return to; empty registers nothing
     * @return one line for the window's log
     * @throws IOException when the provider refuses or cannot be reached
     * @throws InterruptedException when interrupted while waiting
     */
    public static String strRegister(String strUrlBase, List<String> lstRedirect)
            throws IOException, InterruptedException {
        if (lstRedirect.isEmpty())
            return "RAWAR client: no RAWAR served, nothing registered";
        HttpClient client = HttpClient.newBuilder().connectTimeout(DUR_REQUEST).build();
        HttpResponse<String> resList = client.send(HttpRequest.newBuilder(
                URI.create(strUrlBase + MintRegistration.STR_PATH_CLIENTS)).timeout(DUR_REQUEST)
                .header("Authorization", MintRegistration.strAuthBasic()).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resList.statusCode() / 100 != 2)
            throw new IOException("GET " + MintRegistration.STR_PATH_CLIENTS + " answered "
                    + resList.statusCode() + ": " + resList.body());
        JsonNode nodeList = MAPPER.readTree(resList.body());
        if (!nodeList.path("strict").asBoolean(false))
            return "RAWAR client: the provider checks no clients, nothing registered";

        HttpResponse<String> resPut = client.send(HttpRequest.newBuilder(
                URI.create(strUrlBase + MintRegistration.STR_PATH_CLIENTS)).timeout(DUR_REQUEST)
                .header("Content-Type", "application/json")
                .header("Authorization", MintRegistration.strAuthBasic())
                .POST(HttpRequest.BodyPublishers.ofString(strBodyClient(lstRedirect),
                        StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resPut.statusCode() / 100 != 2)
            throw new IOException("POST " + MintRegistration.STR_PATH_CLIENTS + " answered "
                    + resPut.statusCode() + ": " + resPut.body());
        return "RAWAR client " + RawarEnv.STR_CLIENT_ID + " - " + lstRedirect.size() + " urls, "
                + String.join(" ", lstRedirect);
    }
}
