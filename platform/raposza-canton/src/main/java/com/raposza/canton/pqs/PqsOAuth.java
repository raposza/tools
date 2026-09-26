// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.pqs;

/**
 * The client credentials scribe re-mints its ledger-api token from.
 *
 * <h2>Why this exists at all</h2>
 *
 * `--pipeline-oauth-accesstoken` takes ONE STRING and nothing renews it. Below
 * Canton 3.5.6 the participant caps a token at 240 s, so a
 * stack on that column authenticates for four minutes and then stops, and above
 * it the string outlives nothing and renews never. Given an endpoint instead,
 * scribe asks for a token itself and asks again `preemptexpiry` before each one
 * expires, PT1M by default.
 *
 * <h2>The two constraints, neither of them optional</h2>
 *
 * The client id becomes the token's `sub` and MUST name a ledger-api user that
 * exists, or the run authenticates and then authorises nothing and the failure
 * reads as a permissions bug. The secret is never checked by this project's own
 * mint but must be NON-EMPTY, because an empty one fails on scribe's side of
 * the call, which reads differently from a refusal - so a blank one is filled
 * in here rather than passed on.
 *
 * <h2>Everything the mint needs that OAuth has no field for is in the URL</h2>
 *
 * The shape, the audience and the lifetime are not OAuth parameters, and
 * scribe's `--pipeline-oauth-parameters` is typed only as `map` with no
 * measured syntax. They ride in the endpoint's own query string instead, which
 * Spring binds from exactly as it binds a form body. WHETHER SCRIBE PRESERVES
 * THAT QUERY STRING IS THE ONE UNMEASURED THING HERE: if it does not, the mint
 * sees no shape, the token comes back without the claim the participant checks,
 * and the refusal is a target mismatch rather than a lifetime error - which is
 * distinguishable from every other way this can fail.
 *
 * @param strUrlToken the token endpoint, query string and all
 * @param strClientId the OAuth client id, which is a ledger user id
 * @param strClientSecret the secret, never checked and never empty
 * @param strScope what to ask for, or null to leave scribe's default alone
 *
 * Author Claude/bentzn
 */
public record PqsOAuth(String strUrlToken, String strClientId, String strClientSecret,
        String strScope) {

    /** What a blank secret becomes; it is sent, and it is not examined. */
    public static final String STR_SECRET_PLACEHOLDER = "unused";

    /**
     * The query parameter carrying a symmetric participant's shared secret,
     * masked wherever this record is rendered for a human.
     */
    public static final String STR_PARAM_SECRET = "secret";


    public PqsOAuth {
        if (strUrlToken == null || strUrlToken.isBlank())
            throw new IllegalArgumentException("a token endpoint is required");
        if (strClientId == null || strClientId.isBlank()) {
            throw new IllegalArgumentException("a client id is required: it becomes the token's"
                    + " sub and has to name a ledger user");
        }
        if (strClientSecret == null || strClientSecret.isBlank())
            strClientSecret = STR_SECRET_PLACEHOLDER;
        if (strScope != null && strScope.isBlank())
            strScope = null;
    }


    /**
     * @param strUrlToken the token endpoint
     * @param strClientId the ledger user the token speaks for
     * @return credentials with the placeholder secret and no scope
     */
    public static PqsOAuth of(String strUrlToken, String strClientId) {
        return new PqsOAuth(strUrlToken, strClientId, null, null);
    }


    /**
     * @param strScopeNew what to ask for, or null for scribe's default
     * @return the same credentials asking for that scope
     */
    public PqsOAuth withScope(String strScopeNew) {
        return new PqsOAuth(strUrlToken, strClientId, strClientSecret, strScopeNew);
    }


    /**
     * @return the endpoint with any shared secret in its query masked, which
     *         is the only form that belongs in a log or a pasted command
     */
    public String strUrlTokenSafe() {
        return strMasked(strUrlToken);
    }


    /** Safe for a log line: where it mints and who it speaks as, never a secret. */
    public String describe() {
        return "OAuth, re-minted as " + strClientId + " at " + strUrlTokenSafe()
                + (strScope == null ? "" : ", scope " + strScope);
    }


    /**
     * @param strUrl any URL
     * @return it, with the value of {@link #STR_PARAM_SECRET} replaced
     */
    static String strMasked(String strUrl) {
        int idxQuery = strUrl.indexOf('?');
        if (idxQuery < 0)
            return strUrl;
        StringBuilder sb = new StringBuilder(strUrl.substring(0, idxQuery + 1));
        String[] arrPair = strUrl.substring(idxQuery + 1).split("&");
        for (int idxPair = 0; idxPair < arrPair.length; idxPair++) {
            if (idxPair > 0)
                sb.append('&');
            if (arrPair[idxPair].startsWith(STR_PARAM_SECRET + "="))
                sb.append(STR_PARAM_SECRET).append("=<secret>");
            else
                sb.append(arrPair[idxPair]);
        }
        return sb.toString();
    }


    @Override
    public String toString() {
        return describe();
    }
}
