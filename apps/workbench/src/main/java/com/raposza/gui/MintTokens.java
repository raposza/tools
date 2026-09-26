// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.LedgerException;
import com.raposza.api.TokenSource_i;
import com.raposza.api.model.UserInfo;
import com.raposza.jwt.StaticTokenSource;

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
 * The Sandbox's own mint, asked for a token per user.
 *
 * <h2>The `sub` parameter is the whole trick, and it is MEASURED</h2>
 *
 * The discovery document publishes `mint.token` as a url the consumer can GET
 * a token from, and `JwtMintProcess.strMintQuery` builds it as
 *
 * <pre>
 * /mint.txt?sub=&lt;user&gt;&amp;ttlSeconds=&lt;n&gt;[&amp;aud=..|&amp;scope=..][&amp;alg=..][&amp;secret=..]
 * </pre>
 *
 * Every parameter except `sub` describes the PARTICIPANT - its lifetime floor,
 * its audience or scope, its signing algorithm - and is identical whoever is
 * asking. So a token for another user is the published url with one parameter
 * replaced, and nothing about the auth configuration has to be re-derived here.
 *
 * THE URL IS REBUILT, NOT STRING-REPLACED. `sub=alice` appearing inside an
 * audience value would make a textual substitution corrupt a different
 * parameter, and the failure would arrive as a refused token with no clue in
 * it.
 *
 * <h2>ONE TOKEN PER NAMED USER, AND NO BREADTH ENTRY</h2>
 *
 * A `*` entry meaning WHATEVER USER IS NECESSARY led this list until
 * 2026-09-09 and is gone - operator instruction. It resolved to the user
 * holding the most of the hosted parties, which was a second answer to a
 * question the picker already asks, and a submission made under it carried no
 * `user_id` and was refused PERMISSION_DENIED with the reason redacted. Every
 * entry here is now a user the participant named, and `sub` carries it.
 *
 * <h2>What this is NOT</h2>
 *
 * It is not an OAuth client. There is no grant, no client id and no refresh:
 * the Sandbox's mint hands out a signed token to anyone who can reach it,
 * because it exists to make a local stack usable rather than to protect it.
 * A real provider is a different implementation of {@link UserTokens_i} and
 * this class should not grow towards one.
 *
 * Author Claude/bentzn
 */
public final class MintTokens implements UserTokens_i {

    /** The query parameter naming the user a token is minted for. */
    private static final String STR_PARAM_SUB = "sub";

    private static final Duration TIMEOUT_HTTP = Duration.ofSeconds(10);

    private final String strUrlTemplate;

    private final transient List<String> lstUser;


    /**
     * @param strUrlTemplateNew the document's `mint.token` url, for any user
     * @param lstInfoNew what the participant reported about its users
     */
    public MintTokens(String strUrlTemplateNew, List<UserInfo> lstInfoNew) {
        this.strUrlTemplate = strUrlTemplateNew;
        this.lstUser = List.copyOf(lstIdOf(lstInfoNew));
    }


    /**
     * @param lstUserNew what the participant reported
     * @return the ids, sorted, for a combo
     */
    public static List<String> lstIdOf(List<UserInfo> lstUserNew) {
        List<String> lstOut = new ArrayList<>();
        if (lstUserNew != null) {
            for (UserInfo user : lstUserNew) {
                lstOut.add(user.idUser());
            }
        }
        lstOut.sort(String::compareTo);
        return lstOut;
    }


    @Override
    public boolean canChoose() {
        return true;
    }


    @Override
    public List<String> lstUser() {
        return lstUser;
    }


    @Override
    public TokenSource_i sourceFor(String idUser) {
        if (idUser == null || idUser.isBlank())
            throw new LedgerException("no user was named, so no token can be minted");

        return new StaticTokenSource(strFetched(strUrlFor(idUser)));
    }


    @Override
    public String describe() {
        return "minted by the Sandbox";
    }


    /**
     * @param idUser who the token is for
     * @return the published url with its `sub` replaced
     */
    String strUrlFor(String idUser) {
        URI uri = URI.create(strUrlTemplate);
        String strQuery = uri.getRawQuery();
        StringBuilder sbQuery = new StringBuilder();
        boolean flagFound = false;

        if (strQuery != null && !strQuery.isEmpty()) {
            for (String strPair : strQuery.split("&")) {
                if (strPair.isEmpty())
                    continue;
                int idxEq = strPair.indexOf('=');
                String strName = idxEq < 0 ? strPair : strPair.substring(0, idxEq);
                if (sbQuery.length() > 0)
                    sbQuery.append('&');
                if (STR_PARAM_SUB.equals(strName)) {
                    flagFound = true;
                    sbQuery.append(STR_PARAM_SUB).append('=').append(enc(idUser));
                }
                else {
                    sbQuery.append(strPair);
                }
            }
        }
        if (!flagFound) {
            if (sbQuery.length() > 0)
                sbQuery.append('&');
            sbQuery.append(STR_PARAM_SUB).append('=').append(enc(idUser));
        }

        StringBuilder sbUrl = new StringBuilder();
        sbUrl.append(uri.getScheme()).append("://").append(uri.getRawAuthority());
        sbUrl.append(uri.getRawPath() == null ? "" : uri.getRawPath());
        sbUrl.append('?').append(sbQuery);
        return sbUrl.toString();
    }


    /**
     * @param strValue what to put in a query string
     * @return it, percent-encoded
     */
    private static String enc(String strValue) {
        return URLEncoder.encode(strValue, StandardCharsets.UTF_8);
    }


    /**
     * @param strUrl where to ask
     * @return the bare token
     */
    private static String strFetched(String strUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(strUrl))
                .timeout(TIMEOUT_HTTP).GET().build();

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT_HTTP).build()) {

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LedgerException(strUrl + " answered " + response.statusCode()
                        + ", so no token was minted");
            }
            String strToken = response.body().trim();
            if (strToken.isBlank())
                throw new LedgerException(strUrl + " returned an empty token");

            return strToken;
        }
        catch (IOException ex) {
            throw new LedgerException("the mint at " + strUrl + " could not be reached: "
                    + ex.getMessage(), ex);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LedgerException("interrupted while minting a token", ex);
        }
    }

}
