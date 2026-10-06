// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.gui;

import com.raposza.sandbox.app.JwtMintProcess;
import com.raposza.sandbox.app.ProviderUsers;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * One line of the OpenID Provider's access log, worded for the OIDC tab's
 * Access log - his instruction, 2026-10-04: from where, with what, what kind
 * of request, and whether it failed.
 *
 * <h2>Where the line comes from</h2>
 *
 * The provider is a Spring Boot application on Tomcat, and Tomcat's own
 * access log valve is switched on from the command line the Sandbox starts it
 * with - `JwtMintProcess.lstArgAccessLog` - so the provider's code is not
 * touched. The valve writes {@link JwtMintProcess#STR_ACCESS_PATTERN}: the
 * remote address, the method, the path, the status, the query string, the
 * request's Origin, the response's Access-Control-Allow-Origin and the
 * User-Agent, separated by `|`. The User-Agent is LAST because it is the one
 * field a client writes freely.
 *
 * <h2>CORS - his question, 2026-10-04</h2>
 *
 * A browser refuses a cross-origin answer ITSELF, so the provider never sees
 * the refusal; what it does see is the Origin the page sent and whether its
 * answer carried an Access-Control-Allow-Origin for it. A request from
 * another origin answered without one is marked `CORS NOT ALLOWED`, which is
 * the line a page's "CORS error" corresponds to. A request from the
 * provider's own origin - its own sign-in form posts with one - is not a
 * cross-origin request and gets no mark.
 *
 * <h2>What is NOT shown - his instruction, 2026-10-04</h2>
 *
 * No timing: "15 ms is useless". No request that is not OIDC: the provider's
 * own stylesheets and fonts under `/raposza/`, `/favicon.ico` and any other
 * static file are dropped, nor the window's own read of the user list every
 * 3 s - {@link ProviderUsers#STR_AGENT}. Of the query only `sub` and
 * `client_id` are shown.
 * `/mint.txt` carries the participant's HMAC secret on a symmetric stack, and
 * a log pane is the last place it belongs. The Authorization header is not
 * written at all.
 *
 * Author Claude/bentzn
 */
final class OidcAccess {

    private static final int CNT_FIELD = 8;

    /** The provider's own design resources - `DesignResourcesTest`. */
    private static final String STR_PREFIX_STATIC = "/raposza/";

    /** Files a page loads, which say nothing about who signed in. */
    private static final String[] ARR_EXT_STATIC = { ".css", ".js", ".map", ".png", ".svg",
            ".ico", ".jpg", ".gif", ".woff", ".woff2", ".ttf" };


    private OidcAccess() {
    }


    /**
     * @param strRaw one line as the valve wrote it
     * @param nPortOwn the provider's own port, which tells its own origin
     * @return the line for the pane, or null when it is not one the valve
     *         wrote in {@link JwtMintProcess#STR_ACCESS_PATTERN} or is not an
     *         OIDC request
     */
    static String strLine(String strRaw, int nPortOwn) {
        if (strRaw == null || strRaw.isBlank())
            return null;
        String[] arrField = strRaw.split("\\|", CNT_FIELD);
        if (arrField.length < CNT_FIELD)
            return null;
        String strFrom = arrField[0].trim();
        String strMethod = arrField[1].trim();
        String strPath = arrField[2].trim();
        int nStatus;
        try {
            nStatus = Integer.parseInt(arrField[3].trim());
        }
        catch (NumberFormatException ex) {
            return null;
        }
        if (isStatic(strPath) || ProviderUsers.STR_AGENT.equals(arrField[7].trim()))
            return null;
        String strQuery = arrField[4].trim();
        String strOrigin = strField(arrField[5]);
        String strAllow = strField(arrField[6]);
        String strAgent = strAgentShown(arrField[7].trim());

        StringBuilder bld = new StringBuilder();
        bld.append(strFrom).append("  ");
        if ("OPTIONS".equalsIgnoreCase(strMethod))
            bld.append("CORS preflight for ");
        bld.append(strWhat(strMethod, strPath, strQuery));
        bld.append("  ").append(nStatus < 400 ? "ok" : "FAILED " + nStatus);
        if (!strOrigin.isEmpty() && !isOwnOrigin(strOrigin, nPortOwn)) {
            bld.append("  origin ").append(strOrigin).append("  ");
            if (strAllow.isEmpty())
                bld.append("CORS NOT ALLOWED");
            else if ("*".equals(strAllow) || strAllow.equals(strOrigin))
                bld.append("CORS allowed");
            else
                bld.append("CORS allows ").append(strAllow).append(" only");
        }
        if (!strAgent.isEmpty())
            bld.append("  ").append(strAgent);
        return bld.toString();
    }


    /**
     * @param strPath the path, without the query
     * @return whether it is a file a page loads rather than an OIDC request
     */
    static boolean isStatic(String strPath) {
        if (strPath.startsWith(STR_PREFIX_STATIC) || "/favicon.ico".equals(strPath))
            return true;
        for (String strExt : ARR_EXT_STATIC) {
            if (strPath.endsWith(strExt))
                return true;
        }
        return false;
    }


    /**
     * @param strOrigin an Origin header
     * @param nPortOwn the provider's port
     * @return whether it is the provider's own origin on the loopback address
     */
    static boolean isOwnOrigin(String strOrigin, int nPortOwn) {
        for (String strHost : new String[] { "localhost", "127.0.0.1", "[::1]" }) {
            if (strOrigin.equals("http://" + strHost + ":" + nPortOwn)
                    || strOrigin.equals("https://" + strHost + ":" + nPortOwn))
                return true;
        }
        return false;
    }


    /**
     * @param strRaw a valve field
     * @return it trimmed, empty for the valve's `-`
     */
    private static String strField(String strRaw) {
        String strOut = strRaw.trim();
        return "-".equals(strOut) ? "" : strOut;
    }


    /**
     * @param strMethod GET, POST and the rest
     * @param strPath the path, without the query
     * @param strQuery the query with its `?`, or empty
     * @return what the request was, in a few words
     */
    static String strWhat(String strMethod, String strPath, String strQuery) {
        String strSub = strParam(strQuery, "sub");
        String strClient = strParam(strQuery, "client_id");
        switch (strPath) {
            case "/.well-known/openid-configuration":
            case "/.well-known/oauth-authorization-server":
                return "discovery document";
            case "/oauth2/jwks":
            case "/jwks.json":
                return "key set";
            case "/oauth2/jwks-private":
            case "/jwks-private.json":
                return "PRIVATE key set";
            case "/mint":
            case "/mint.txt":
                return "token minted" + (strSub == null ? "" : " for " + strSub);
            case "/oauth2/token":
            case "/oauth/token":
            case "/token":
                return "token request";
            case "/oauth2/authorize":
                return "sign-in" + (strClient == null ? "" : " for client " + strClient);
            case "/oauth2/userinfo":
                return "user info";
            case "/oauth2/logout":
                return "sign-out";
            case "/api/ui/login":
                return "admin sign-in";
            case "/ui":
            case "/ui/":
                return "admin UI";
            case "/keys":
            case "/keys.txt":
                return "keys listed";
            default:
                break;
        }
        if (strPath.startsWith("/api/ui/"))
            return "admin " + strMethod + " " + strPath.substring("/api/ui/".length());
        if (strPath.startsWith("/admin/"))
            return "admin " + strMethod + " " + strPath.substring("/admin/".length());
        return strMethod + " " + strPath;
    }


    /**
     * @param strQuery the query with its `?`, or empty or `-`
     * @param strName the parameter
     * @return its decoded value, or null when it is absent
     */
    static String strParam(String strQuery, String strName) {
        if (strQuery == null || strQuery.length() < 2 || strQuery.charAt(0) != '?')
            return null;
        for (String strPair : strQuery.substring(1).split("&")) {
            int idxEq = strPair.indexOf('=');
            String strKey = idxEq < 0 ? strPair : strPair.substring(0, idxEq);
            if (!strKey.equals(strName))
                continue;
            String strValue = idxEq < 0 ? "" : strPair.substring(idxEq + 1);
            try {
                return URLDecoder.decode(strValue, StandardCharsets.UTF_8);
            }
            catch (IllegalArgumentException ex) {
                return strValue;
            }
        }
        return null;
    }


    /**
     * @param strAgent the User-Agent header, or `-` when the client sent none
     * @return `browser` for a browser, the product token for anything else,
     *         empty for none
     */
    static String strAgentShown(String strAgent) {
        if (strAgent == null || strAgent.isEmpty() || "-".equals(strAgent))
            return "";
        if (strAgent.startsWith("Mozilla/"))
            return "browser";
        int idxSpace = strAgent.indexOf(' ');
        return idxSpace < 0 ? strAgent : strAgent.substring(0, idxSpace);
    }

}
