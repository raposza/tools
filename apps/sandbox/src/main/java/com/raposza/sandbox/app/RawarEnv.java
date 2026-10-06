// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a RAWAR is told about where it runs: `_env.json` under its mount, and
 * the participant `_ledger/` forwards to. `rawar.md` section 4.
 *
 * <h2>Read off the discovery snapshot, never off the window</h2>
 *
 * The window already builds a {@link DiscoveryDoc} on the event thread every
 * tick, and it already says which provider signs people in, which audience a
 * participant checks and which port its JSON Ledger API is on. Reading the
 * same snapshot means a RAWAR is told exactly what the Workbench is told, and
 * nothing here touches a Swing component from the server's thread.
 *
 * <h2>Which participant</h2>
 *
 * The `app-provider` node: the Sandbox's one participant carries that role,
 * and on LocalNetND it is the application side - `rawar.md` section 5. A RAWAR
 * that needs two participants is out of scope for this version.
 *
 * <h2>The issuer is never proxied</h2>
 *
 * Raposza OIDC compares the issuer literally against what the client reached -
 * RFC 8414 section 3.3, `AGENTS.md` section 2 - so a page is given the
 * provider's own url and talks to it directly. The provider answers every
 * origin, without credentials - `CorsConfig` in Raposza OIDC.
 *
 * Author Claude/bentzn
 */
public final class RawarEnv {

    /** The one client id every RAWAR page presents - `rawar.md` section 4. */
    public static final String STR_CLIENT_ID = "rawar";

    /** The format of `_env.json`, for the day it changes. */
    public static final int N_FORMAT = 1;

    /** Where a page reaches the ledger, relative to its own mount. */
    public static final String STR_LEDGER_BASE = "./_ledger/";

    /** The Sandbox's host kind on its one-participant topology. */
    public static final String STR_HOST_SANDBOX = "sandbox";

    /** And on LocalNetND. */
    public static final String STR_HOST_LOCALNET = "sandbox-localnetnd";

    /** What a node verifies when it verifies nothing. */
    public static final String STR_AUTH_NONE = "none";

    /** Where the JSON Ledger API is dialled when the report names no host. */
    static final String STR_HOST_LOOPBACK = "127.0.0.1";


    private RawarEnv() {
    }


    /**
     * The node `_ledger/` forwards to.
     *
     * @param doc the snapshot, or null
     * @return the app-provider node with a JSON Ledger API port, or null when
     *         no stack is serving one
     */
    public static DiscoveryNode node(DiscoveryDoc doc) {
        if (doc == null)
            return null;
        for (DiscoveryNode node : doc.lstNode()) {
            if (DiscoveryNode.STR_ROLE_APP_PROVIDER.equals(node.strRole()) && node.report() != null
                    && node.report().value(ReadyReport.KEY_JSON_API) != null)
                return node;
        }
        return null;
    }


    /**
     * @param doc the snapshot, or null
     * @return the JSON Ledger API's base url without a trailing slash, or null
     *         when no stack is serving one
     */
    public static String strUrlLedger(DiscoveryDoc doc) {
        DiscoveryNode node = node(doc);
        if (node == null)
            return null;
        String strHost = node.report().value(ReadyReport.KEY_HOST);
        if (strHost == null || strHost.isBlank())
            strHost = STR_HOST_LOOPBACK;
        return "http://" + strHost + ":" + node.report().value(ReadyReport.KEY_JSON_API);
    }


    /**
     * The sign-in environment one mount is served.
     *
     * @param doc the snapshot, or null
     * @param strOrigin the origin the browser reached - `http://127.0.0.1:31100`
     *        - so the redirect url is the one it will come back to
     * @param strMount the mount, with both slashes
     * @return `_env.json`, as an ordered map
     */
    public static Map<String, Object> mapEnv(DiscoveryDoc doc, String strOrigin, String strMount) {
        Map<String, Object> mapOut = new LinkedHashMap<>();
        mapOut.put("env", Integer.valueOf(N_FORMAT));
        mapOut.put("host", doc != null && DiscoveryDoc.STR_TOPOLOGY_LOCALNET.equals(doc.strTopology())
                ? STR_HOST_LOCALNET : STR_HOST_SANDBOX);
        mapOut.put("mount", strMount);
        DiscoveryNode node = node(doc);
        mapOut.put("running", Boolean.valueOf(node != null));
        mapOut.put("ledgerBase", STR_LEDGER_BASE);

        AuthSettings auth = node == null ? null : node.auth();
        boolean flagAuth = auth != null && auth.mode().flagTargets();
        mapOut.put("auth", flagAuth ? auth.mode().strType() : STR_AUTH_NONE);
        if (!flagAuth)
            return mapOut;

        Map<String, Object> mapProvider = doc.mapProvider();
        Object objIssuer = mapProvider == null ? null : mapProvider.get("issuer");
        if (objIssuer != null)
            mapOut.put("issuer", String.valueOf(objIssuer));
        mapOut.put("clientId", STR_CLIENT_ID);
        mapOut.put("redirectUri", strOrigin + strMount);
        Map<String, Object> mapOidc = node.mapOidc();
        if (mapOidc != null && mapOidc.get("scope") != null)
            mapOut.put("scope", String.valueOf(mapOidc.get("scope")));
        else if (mapOidc != null && mapOidc.get("audience") != null)
            mapOut.put("audience", String.valueOf(mapOidc.get("audience")));
        return mapOut;
    }


    /**
     * The redirect urls a client registration must carry for these mounts:
     * both spellings of the loopback, because the comparison is exact - Core
     * 3.1.2.1, `OidcClient.flagRedirect` in Raposza OIDC - and a developer
     * types either.
     *
     * @param nPort the RAWAR server's port
     * @param lstMount every mount served
     * @return the urls, in mount order
     */
    public static List<String> lstRedirect(int nPort, List<String> lstMount) {
        List<String> lstOut = new ArrayList<>();
        for (String strMount : lstMount) {
            lstOut.add("http://127.0.0.1:" + nPort + strMount);
            lstOut.add("http://localhost:" + nPort + strMount);
        }
        return lstOut;
    }
}
