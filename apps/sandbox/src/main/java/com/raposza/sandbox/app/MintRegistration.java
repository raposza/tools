// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.runtime.localnet.LocalNetUi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * WHAT THE PROVIDER NEEDS FROM A LocalNetND START, written by the window
 * instead of by hand.
 *
 * <h2>The web UI client and its origins</h2>
 *
 * The four web UIs sign in at the provider as ONE public client,
 * {@link #STR_CLIENT_UI}, and the provider refuses a `redirect_uri` it was not
 * given. The window computes those origins already - one per page with a login
 * box, off the stack's own ports - so it registers them at every start. A
 * moved UI block then re-registers itself rather than breaking sign-in.
 *
 * <h2>The role users</h2>
 *
 * Each page is signed in to by the name its login box wants, which the bundle
 * names per role. Every one takes {@link JwtMintProcess#STR_PASSWORD} - the
 * operator's decision of 2026-09-23 - and a POST of an existing name changes
 * its password, so a user added by hand with another one is brought into line.
 *
 * <h2>Why a request and not the seed setting</h2>
 *
 * `raposza.oidc.users` and `raposza.oidc.clients` are read only when
 * their files do not exist yet, so on any machine that has run the provider
 * once they change nothing. The provider's own write endpoints are the one
 * route that works on every machine, and they are guarded - so each request
 * carries the admin credential the window started the provider with.
 *
 * <h2>Registering a client turns the client checks on</h2>
 *
 * An empty registry checks nothing; the first client registered makes the
 * provider refuse an unknown `client_id` at its token endpoint too. That is the
 * state a hand registration already left, stated here so it is not
 * rediscovered.
 *
 * <h2>And scribe is a client too - raposza/tools#4</h2>
 *
 * On the single participant scribe takes client credentials and mints its own
 * tokens, renewing them before they expire. With the registry checking clients
 * its id - the PQS ledger user - was unknown and the token endpoint answered
 * 401, measured at his console 2026-10-26 against a store holding `conf1`,
 * `conf2`, `conf3`, `localnet-ui` and `rawar`. So the window registers it, as
 * a CONFIDENTIAL client with a secret made for that start, before scribe runs -
 * {@link #strRegisterScribe}. Only when the registry already checks clients:
 * registering into an open one would close it for every other caller.
 *
 * Author Claude/bentzn
 */
public final class MintRegistration {

    /** The OAuth client the four web UIs identify as. */
    public static final String STR_CLIENT_UI = "localnet-ui";

    public static final String STR_PATH_USERS = "/api/ui/users";

    public static final String STR_PATH_CLIENTS = "/api/ui/clients";

    private static final Duration DUR_REQUEST = Duration.ofSeconds(10);

    /**
     * A client the provider can be sent back to, which a machine client never
     * is. The provider refuses a client with no `redirect_uri` at all, and
     * nothing listens here.
     */
    public static final String STR_REDIRECT_NONE = "http://127.0.0.1/no-redirect";

    private static final int N_BYTES_SECRET = 24;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final ObjectMapper MAPPER = new ObjectMapper();


    private MintRegistration() {
    }


    /**
     * @param lstPage every page the stack serves
     * @return the names their login boxes want, each once, in page order
     */
    public static List<String> lstUser(List<LocalNetUi.Page> lstPage) {
        Set<String> setOut = new LinkedHashSet<>();
        for (LocalNetUi.Page page : lstPage) {
            if (page.strLogin() != null && !page.strLogin().isBlank())
                setOut.add(page.strLogin());
        }
        return new ArrayList<>(setOut);
    }


    /**
     * A page with no login box - scan - is not a place anybody is sent back
     * to, so it is not an origin.
     *
     * @param lstPage every page the stack serves
     * @return the origins a sign-in returns to, each once, in page order
     */
    public static List<String> lstRedirect(List<LocalNetUi.Page> lstPage) {
        Set<String> setOut = new LinkedHashSet<>();
        for (LocalNetUi.Page page : lstPage) {
            if (page.strLogin() != null)
                setOut.add(page.strUrl());
        }
        return new ArrayList<>(setOut);
    }


    /**
     * @param strName a user
     * @return the body `/api/ui/users` takes
     */
    public static String strBodyUser(String strName) {
        return "{\"name\":\"" + esc(strName) + "\",\"password\":\""
                + esc(JwtMintProcess.STR_PASSWORD) + "\"}";
    }


    /**
     * A BLANK SECRET IS A PUBLIC CLIENT, which is what a page in a browser is -
     * it has nowhere to keep one.
     *
     * @param lstRedirect the origins
     * @return the body `/api/ui/clients` takes
     */
    public static String strBodyClient(List<String> lstRedirect) {
        return "{\"client_id\":\"" + esc(STR_CLIENT_UI) + "\",\"secret\":\"\","
                + "\"redirect_uris\":\"" + esc(String.join(" ", lstRedirect)) + "\"}";
    }


    /**
     * @return a fresh client secret, URL-safe, for one start
     */
    public static String strSecretNew() {
        byte[] arrBytes = new byte[N_BYTES_SECRET];
        RANDOM.nextBytes(arrBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(arrBytes);
    }


    /**
     * @param idClient the client id - for scribe, the PQS ledger user
     * @param strSecret its secret
     * @return the body `/api/ui/clients` takes for a confidential machine client
     */
    public static String strBodyClientConfidential(String idClient, String strSecret) {
        return "{\"client_id\":\"" + esc(idClient) + "\",\"secret\":\"" + esc(strSecret)
                + "\",\"redirect_uris\":\"" + esc(STR_REDIRECT_NONE) + "\"}";
    }


    /**
     * Registers scribe as a confidential client IF the provider checks clients
     * - see the type comment. An open provider accepts any client and any
     * secret, so the same secret serves either way.
     *
     * @param strUrlBase the provider, no trailing slash
     * @param idClient the client id scribe presents, the PQS ledger user
     * @param strSecret the secret scribe presents
     * @return one line for the window's log, never the secret
     * @throws IOException when the provider refuses or cannot be reached
     * @throws InterruptedException when interrupted while waiting
     */
    public static String strRegisterScribe(String strUrlBase, String idClient, String strSecret)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(DUR_REQUEST).build();
        HttpResponse<String> resList = client.send(HttpRequest.newBuilder(
                URI.create(strUrlBase + STR_PATH_CLIENTS)).timeout(DUR_REQUEST)
                .header("Authorization", strAuthBasic()).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resList.statusCode() / 100 != 2) {
            throw new IOException("GET " + STR_PATH_CLIENTS + " answered "
                    + resList.statusCode() + ": " + resList.body());
        }
        JsonNode nodeList = MAPPER.readTree(resList.body());
        if (!nodeList.path("strict").asBoolean(false)) {
            return "PQS client " + idClient
                    + ": the provider checks no clients, nothing registered";
        }

        post(client, strUrlBase + STR_PATH_CLIENTS,
                strBodyClientConfidential(idClient, strSecret));
        return "PQS client " + idClient + " - confidential, secret made for this start";
    }


    /**
     * @return the Authorization header value for the admin credential
     */
    public static String strAuthBasic() {
        String strPair = JwtMintProcess.STR_ADMIN_USER + ":" + JwtMintProcess.STR_PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(
                strPair.getBytes(StandardCharsets.UTF_8));
    }


    /**
     * Registers the users, then the client.
     *
     * @param strUrlBase the provider, no trailing slash
     * @param lstPage every page the stack serves
     * @return one line per thing registered, for the window's log
     * @throws IOException when the provider refuses or cannot be reached
     * @throws InterruptedException when interrupted while waiting
     */
    public static List<String> lstRegister(String strUrlBase, List<LocalNetUi.Page> lstPage)
            throws IOException, InterruptedException {
        List<String> lstUser = lstUser(lstPage);
        List<String> lstRedirect = lstRedirect(lstPage);
        List<String> lstOut = new ArrayList<>();
        if (lstRedirect.isEmpty())
            return lstOut;

        HttpClient client = HttpClient.newBuilder().connectTimeout(DUR_REQUEST).build();
        for (String strUser : lstUser) {
            post(client, strUrlBase + STR_PATH_USERS, strBodyUser(strUser));
        }
        post(client, strUrlBase + STR_PATH_CLIENTS, strBodyClient(lstRedirect));

        lstOut.add("OIDC users " + String.join(", ", lstUser) + " - password "
                + JwtMintProcess.STR_PASSWORD);
        lstOut.add("OIDC client " + STR_CLIENT_UI + " - " + lstRedirect.size()
                + " origins, " + String.join(" ", lstRedirect));
        lstOut.add("OIDC admin " + JwtMintProcess.STR_ADMIN_USER + " - password "
                + JwtMintProcess.STR_PASSWORD + ", " + strUrlBase + "/ui");
        return lstOut;
    }


    private static void post(HttpClient client, String strUrl, String strBody)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(strUrl))
                .timeout(DUR_REQUEST)
                .header("Content-Type", "application/json")
                .header("Authorization", strAuthBasic())
                .POST(HttpRequest.BodyPublishers.ofString(strBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new IOException("POST " + strUrl + " answered " + res.statusCode() + ": "
                    + res.body());
        }
    }


    /**
     * @param strIn a string for a JSON literal
     * @return it escaped for one
     */
    static String esc(String strIn) {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < strIn.length(); idx++) {
            char ch = strIn.charAt(idx);
            if (ch == '"' || ch == '\\')
                sb.append('\\').append(ch);
            else if (ch < 0x20)
                sb.append(String.format("\\u%04x", (int) ch));
            else
                sb.append(ch);
        }
        return sb.toString();
    }
}
