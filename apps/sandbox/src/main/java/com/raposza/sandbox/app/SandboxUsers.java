// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Who can sign in on the single-participant topology: every ledger user the
 * participant has, written to the window's own provider with the password
 * LocalNetND's users get - {@link JwtMintProcess#STR_PASSWORD}, D-818.
 *
 * <h2>Why this exists</h2>
 *
 * LocalNetND's start registers its role users at the provider -
 * {@link MintRegistration} - and the single-participant start registered
 * nobody, so a RAWAR page reached the provider's login box with no name to
 * type. Measured 2026-10-02.
 *
 * <h2>The names come off the ledger</h2>
 *
 * `GET /v2/users`, as `participant_admin` - the same source and the same reason
 * `JwtPane` gives for its user list: it is the only one that includes users a
 * Daml script created after the start. A token's `sub` must be a ledger user on
 * the participant or it authenticates and authorises nothing, so a name that
 * is not on the ledger is not offered.
 *
 * <h2>Registering a user turns nothing on</h2>
 *
 * The provider's client checks start with its first CLIENT, not its first
 * user - `OidcClients` in Raposza OIDC - so this changes nothing for any other
 * caller. A POST of an existing name sets its password, which brings a user
 * added by hand into line, the rule `MintRegistration` already follows.
 *
 * Author Claude/bentzn
 */
public final class SandboxUsers {

    private static final Duration DUR_REQUEST = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** No ledger lists more users than this on a developer's participant. */
    private static final int N_PAGES_MAX = 50;


    private SandboxUsers() {
    }


    /**
     * One page of `GET /v2/users`.
     *
     * @param lstUser the user ids on it, in order
     * @param strNext the token for the next page, or empty
     */
    public record Page(List<String> lstUser, String strNext) {
    }


    /**
     * The documented response is `{"users":[{"id":...}],"nextPageToken":...}`.
     *
     * @param strBody what the JSON Ledger API returned
     * @return the ids and the next page token
     * @throws IOException when it is not that shape
     */
    public static Page pageOf(String strBody) throws IOException {
        JsonNode nodeRoot = MAPPER.readTree(strBody);
        List<String> lstOut = new ArrayList<>();
        for (JsonNode nodeUser : nodeRoot.path("users")) {
            String strId = nodeUser.path("id").asText("");
            if (!strId.isEmpty() && !lstOut.contains(strId))
                lstOut.add(strId);
        }
        return new Page(lstOut, nodeRoot.path("nextPageToken").asText(""));
    }


    /**
     * Reads the participant's users and writes each to the provider.
     *
     * @param strUrlProvider the provider, no trailing slash
     * @param strUrlJson the participant's JSON Ledger API, no trailing slash
     * @param strToken a bearer for `participant_admin`
     * @return the users registered, in ledger order
     * @throws IOException when the ledger or the provider refuses
     * @throws InterruptedException when interrupted while waiting
     */
    public static List<String> lstRegister(String strUrlProvider, String strUrlJson,
            String strToken) throws IOException, InterruptedException {
        List<String> lstUser = lstRead(strUrlJson, strToken);
        register(strUrlProvider, lstUser);
        return lstUser;
    }


    /**
     * The participant's users, every page of them.
     *
     * @param strUrlJson the participant's JSON Ledger API, no trailing slash
     * @param strToken a bearer for `participant_admin`
     * @return the user ids, in ledger order
     * @throws IOException when the ledger refuses
     * @throws InterruptedException when interrupted while waiting
     */
    public static List<String> lstRead(String strUrlJson, String strToken)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(DUR_REQUEST).build();
        List<String> lstUser = new ArrayList<>();
        String strNext = "";
        for (int idxPage = 0; idxPage < N_PAGES_MAX; idxPage++) {
            String strUrl = strUrlJson + "/v2/users" + (strNext.isEmpty() ? ""
                    : "?pageToken=" + URLEncoder.encode(strNext, StandardCharsets.UTF_8));
            HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(strUrl))
                    .timeout(DUR_REQUEST).header("Authorization", "Bearer " + strToken).GET()
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2)
                throw new IOException("GET " + strUrl + " answered " + res.statusCode() + ": "
                        + res.body());
            Page page = pageOf(res.body());
            for (String strUser : page.lstUser()) {
                if (!lstUser.contains(strUser))
                    lstUser.add(strUser);
            }
            strNext = page.strNext();
            if (strNext.isEmpty())
                break;
        }
        return lstUser;
    }


    /**
     * Writes each user to the provider with {@link JwtMintProcess#STR_PASSWORD}.
     *
     * @param strUrlProvider the provider, no trailing slash
     * @param lstUser the users to write
     * @throws IOException when the provider refuses one
     * @throws InterruptedException when interrupted while waiting
     */
    public static void register(String strUrlProvider, List<String> lstUser)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(DUR_REQUEST).build();
        for (String strUser : lstUser) {
            String strUrl = strUrlProvider + MintRegistration.STR_PATH_USERS;
            HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(strUrl))
                    .timeout(DUR_REQUEST)
                    .header("Content-Type", "application/json")
                    .header("Authorization", MintRegistration.strAuthBasic())
                    .POST(HttpRequest.BodyPublishers.ofString(MintRegistration.strBodyUser(strUser),
                            StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2)
                throw new IOException("POST " + strUrl + " for " + strUser + " answered "
                        + res.statusCode() + ": " + res.body());
        }
    }
}
