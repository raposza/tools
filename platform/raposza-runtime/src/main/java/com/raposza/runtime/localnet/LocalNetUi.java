// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The web layer expressed as data: which virtual host answers on which port,
 * which static bundle it serves, and where its API prefixes are forwarded.
 *
 * READ OUT OF conf/nginx, NOT DESIGNED. Every vhost, every location prefix and
 * every upstream below is the same one `app-provider.conf`, `app-user.conf` and
 * `sv.conf` declare; only the upstreams move, because a container name resolves
 * to nothing outside a Docker network and the backends are on loopback here.
 *
 * WHAT IS NOT REPRODUCED, and why. `grpc-ledger-api.localhost` is a
 * `grpc_pass` vhost: HTTP/2 with trailers and streaming passthrough, which the
 * JDK's HTTP server cannot serve at any price. Nothing in any of the four UI
 * bundles dials it, and the participant's gRPC Ledger API is already bound on
 * the host at &lt;prefix&gt;901, so the vhost was an in-network convenience and its
 * absence costs a native run nothing.
 *
 * TWO DELIBERATE ADDITIONS. An unmatched Host reaches the role's wallet rather
 * than nginx's first-declared server block, because a 404 from a bare
 * `127.0.0.1` is a worse answer than the page the operator wanted. And every
 * wallet vhost carries `/api/scan` to the scan backend, so the wallet's scan
 * calls are same-origin; upstream leaves them cross-origin against a backend
 * whose CORS behaviour this project has not measured.
 *
 * THE CONFIG IS GENERATED, NOT TEMPLATED. `index.html` loads `/config.js` as a
 * plain script - the shipped file says in its own comment that it is meant to
 * be replaced by the operator - so the server answers that one path itself and
 * the bundle on disk is never written to. The shape of the object is the shape
 * of the shipped files; the values are this run's.
 *
 * Author Claude/bentzn
 */
public final class LocalNetUi {

    /** From env/common.env, SV_UI_PORT / APP_PROVIDER_UI_PORT / APP_USER_UI_PORT. */
    public static final int N_PORT_UI_SV = 4000;

    public static final int N_PORT_UI_APP_PROVIDER = 3000;

    public static final int N_PORT_UI_APP_USER = 2000;

    /** What the UIs sign browser tokens with, per the shipped config.js. */
    public static final String STR_ALGORITHM = "hs-256-unsafe";

    /** What the shipped config.js calls the provider path, in its own comment. */
    public static final String STR_ALGORITHM_RS256 = "rs-256";

    public static final String STR_HOST_WALLET = "wallet.localhost";

    public static final String STR_HOST_ANS = "ans.localhost";

    public static final String STR_HOST_SV = "sv.localhost";

    public static final String STR_HOST_SCAN = "scan.localhost";

    public static final String STR_HOST_CANTON = "canton.localhost";

    public static final String STR_HOST_JSON = "json-ledger-api.localhost";

    /** Verbatim from the shipped bundles, where all five agree. */
    private static final String STR_INSTANCE_NAMES = """
              spliceInstanceNames: {
                networkName: 'Splice',
                networkFaviconUrl: 'https://www.hyperledger.org/hubfs/hyperledgerfavicon.png',
                amuletName: 'Amulet',
                amuletNameAcronym: 'AMT',
                nameServiceName: 'Amulet Name Service',
                nameServiceNameAcronym: 'ANS',
              },
            """;

    private LocalNetUi() {
    }


    /**
     * @param strPrefix the path prefix that selects this route
     * @param strUpstream scheme, host and port the request is forwarded to
     */
    public record Route(String strPrefix, String strUpstream) {
    }


    /**
     * @param strHost the Host header that selects this vhost
     * @param strApp the bundle under web-uis/ it serves, or null when it only
     *        proxies
     * @param strConfigJs what /config.js answers, or null with no app
     * @param lstRoute proxied prefixes, longest match wins
     * @param flagCors whether the permissive headers from
     *        conf/nginx/swagger-ui/ are added
     */
    public record Vhost(String strHost, String strApp, String strConfigJs, List<Route> lstRoute,
            boolean flagCors) {
    }


    /**
     * @param strRole sv, app-provider or app-user
     * @param nPort the port this role's vhosts listen on
     * @param strLogin the name to type at the login box of this role's pages
     * @param lstVhost its vhosts; the FIRST is what an unmatched Host reaches
     */
    public record Site(String strRole, int nPort, String strLogin, List<Vhost> lstVhost) {
    }


    /**
     * @param strUrl what to open
     * @param strTitle the page's own name, as it calls itself
     * @param strLogin the name to type at its login box, or null when it has
     *        none
     */
    public record Page(String strUrl, String strTitle, String strLogin) {
    }


    /**
     * @return every hostname a browser has to be able to resolve
     */
    public static List<String> lstHostName() {
        return List.of(STR_HOST_WALLET, STR_HOST_ANS, STR_HOST_SV, STR_HOST_SCAN,
                STR_HOST_CANTON, STR_HOST_JSON);
    }


    /**
     * @param strHost what the backends are dialled on
     * @param mapPortUi role to UI port
     * @param mapAudience role to the audience its participant demands
     * @return the three sites, sv first
     */
    public static List<Site> lstSite(String strHost, Map<String, Integer> mapPortUi,
            Map<String, String> mapAudience, Map<String, String> mapLogin) {
        return lstSite(strHost, LocalNetPorts.ofBundle(), mapPortUi, mapAudience, mapLogin);
    }


    /**
     * @param strHost what the backends are dialled on
     * @param ports the numbering the backends are bound on
     * @param mapPortUi role to UI port
     * @param mapAudience role to the audience its participant demands
     * @param mapLogin role to the name typed at its login box
     * @return the three sites, sv first
     */
    public static List<Site> lstSite(String strHost, LocalNetPorts ports,
            Map<String, Integer> mapPortUi, Map<String, String> mapAudience,
            Map<String, String> mapLogin) {
        return lstSite(strHost, ports, mapPortUi, mapAudience, mapLogin,
                LocalNetAuth.ofUnsafe());
    }


    /**
     * The same three sites, on an auth the caller chose.
     *
     * @param strHost what the backends are dialled on
     * @param ports the numbering the backends are bound on
     * @param mapPortUi role to UI port
     * @param mapAudience role to the audience its participant demands, which
     *        is what a browser-minted HS256 token has to carry
     * @param mapLogin role to the name typed at its login box
     * @param auth what the stack verifies; when it carries a provider the four
     *        UIs sign in there instead of minting their own tokens
     * @return the three sites, sv first
     */
    public static List<Site> lstSite(String strHost, LocalNetPorts ports,
            Map<String, Integer> mapPortUi, Map<String, String> mapAudience,
            Map<String, String> mapLogin, LocalNetAuth auth) {
        List<Site> lstSite = new ArrayList<>();
        lstSite.add(siteSv(strHost, ports, nPort(mapPortUi, "sv", ports.nPortUi("sv")),
                strAudience(mapAudience, "sv"), strLogin(mapLogin, "sv"), auth));
        lstSite.add(siteValidator(strHost, ports, "app-provider",
                nPort(mapPortUi, "app-provider", ports.nPortUi("app-provider")),
                strAudience(mapAudience, "app-provider"),
                strLogin(mapLogin, "app-provider"), auth));
        lstSite.add(siteValidator(strHost, ports, "app-user",
                nPort(mapPortUi, "app-user", ports.nPortUi("app-user")),
                strAudience(mapAudience, "app-user"),
                strLogin(mapLogin, "app-user"), auth));
        return lstSite;
    }


    /**
     * The sv port carries four vhosts in conf/nginx/sv.conf. Its default server
     * block serves /usr/share/nginx/sv-html, which exists in no image this runs
     * without, so the SV UI takes the unmatched Host instead.
     *
     * @param strHost what the backends are dialled on
     * @param nPort the UI port
     * @param strAudience what the sv participant demands
     * @param strLogin the name to type at its login box
     * @return the sv site
     */
    private static Site siteSv(String strHost, LocalNetPorts ports, int nPort,
            String strAudience, String strLogin, LocalNetAuth auth) {
        String strUpSv = strUp(strHost, ports.nPortSvApp());
        String strUpScan = strUp(strHost, ports.nPortScan());
        String strUpValidator = strUp(strHost, ports.nPortValidator("sv"));
        String strUpJson = strUp(strHost, ports.nPortJson("sv"));

        List<Vhost> lstVhost = new ArrayList<>();
        lstVhost.add(new Vhost(STR_HOST_SV, "sv",
                strConfigSv(strOrigin(STR_HOST_SV, nPort), strAudience, auth),
                List.of(new Route("/api/sv", strUpSv)), false));
        lstVhost.add(new Vhost(STR_HOST_SCAN, "scan",
                strConfigScan(strOrigin(STR_HOST_SCAN, nPort)),
                List.of(new Route("/api/scan", strUpScan),
                        new Route("/registry", strUpScan)),
                false));
        lstVhost.add(vhostWallet(nPort, strUpValidator, strUpScan, strAudience, auth));
        lstVhost.add(new Vhost(STR_HOST_CANTON, null, null,
                List.of(new Route("/docs/openapi", strUpJson), new Route("/v2", strUpJson)),
                true));
        return new Site("sv", nPort, strLogin, lstVhost);
    }


    /**
     * @param strHost what the backends are dialled on
     * @param strRole app-provider or app-user
     * @param nPort the UI port
     * @param strPrefix that role's port prefix
     * @param strAudience what that role's participant demands
     * @param strLogin the name to type at its login box
     * @return the site for one validator role
     */
    private static Site siteValidator(String strHost, LocalNetPorts ports, String strRole,
            int nPort, String strAudience, String strLogin, LocalNetAuth auth) {
        String strUpValidator = strUp(strHost, ports.nPortValidator(strRole));
        String strUpJson = strUp(strHost, ports.nPortJson(strRole));
        String strUpScan = strUp(strHost, ports.nPortScan());

        List<Vhost> lstVhost = new ArrayList<>();
        lstVhost.add(vhostWallet(nPort, strUpValidator, strUpScan, strAudience, auth));
        lstVhost.add(new Vhost(STR_HOST_ANS, "ans",
                strConfigAns(strOrigin(STR_HOST_ANS, nPort), strOrigin(STR_HOST_WALLET, nPort),
                        strAudience, auth),
                List.of(new Route("/api/validator", strUpValidator)), false));
        lstVhost.add(new Vhost(STR_HOST_CANTON, null, null,
                List.of(new Route("/", strUpJson)), true));
        lstVhost.add(new Vhost(STR_HOST_JSON, null, null,
                List.of(new Route("/", strUpJson)), true));
        return new Site(strRole, nPort, strLogin, lstVhost);
    }


    private static Vhost vhostWallet(int nPort, String strUpValidator, String strUpScan,
            String strAudience, LocalNetAuth auth) {
        return new Vhost(STR_HOST_WALLET, "wallet",
                strConfigWallet(strOrigin(STR_HOST_WALLET, nPort), strAudience, auth),
                List.of(new Route("/api/validator", strUpValidator),
                        new Route("/api/scan", strUpScan)),
                false);
    }


    /**
     * @param strOrigin scheme, host and port the browser loaded the page from
     * @param strAudience what the participant demands
     * @return the wallet's configuration
     */
    public static String strConfigWallet(String strOrigin, String strAudience) {
        return strConfigWallet(strOrigin, strAudience, LocalNetAuth.ofUnsafe());
    }


    /**
     * @param strOrigin scheme, host and port the browser loaded the page from
     * @param strAudience what a browser-minted token has to carry
     * @param auth what the stack verifies
     * @return the wallet's configuration
     */
    public static String strConfigWallet(String strOrigin, String strAudience,
            LocalNetAuth auth) {
        return strHead()
                + strAuth(strAudience, auth)
                + "  services: {\n"
                + "    validator: {\n"
                + "      url: '" + strOrigin + "/api/validator',\n"
                + "    },\n"
                + "    scan: {\n"
                + "      url: '" + strOrigin + "/api/scan',\n"
                + "    },\n"
                + "  },\n"
                + STR_INSTANCE_NAMES
                + "};\n";
    }


    /**
     * @param strOrigin scheme, host and port the browser loaded the page from
     * @param strOriginWallet where payment workflows are forwarded
     * @param strAudience what the participant demands
     * @return the name service's configuration
     */
    public static String strConfigAns(String strOrigin, String strOriginWallet,
            String strAudience) {
        return strConfigAns(strOrigin, strOriginWallet, strAudience, LocalNetAuth.ofUnsafe());
    }


    /**
     * @param strOrigin scheme, host and port the browser loaded the page from
     * @param strOriginWallet where payment workflows are forwarded
     * @param strAudience what a browser-minted token has to carry
     * @param auth what the stack verifies
     * @return the name service's configuration
     */
    public static String strConfigAns(String strOrigin, String strOriginWallet,
            String strAudience, LocalNetAuth auth) {
        return strHead()
                + strAuth(strAudience, auth)
                + "  services: {\n"
                + "    wallet: {\n"
                + "      uiUrl: '" + strOriginWallet + "',\n"
                + "    },\n"
                + "    validator: {\n"
                + "      url: '" + strOrigin + "/api/validator',\n"
                + "    },\n"
                + "  },\n"
                + STR_INSTANCE_NAMES
                + "};\n";
    }


    /**
     * @param strOrigin scheme, host and port the browser loaded the page from
     * @param strAudience what the sv participant demands
     * @return the SV UI's configuration
     */
    public static String strConfigSv(String strOrigin, String strAudience) {
        return strConfigSv(strOrigin, strAudience, LocalNetAuth.ofUnsafe());
    }


    /**
     * @param strOrigin scheme, host and port the browser loaded the page from
     * @param strAudience what a browser-minted token has to carry
     * @param auth what the stack verifies
     * @return the SV UI's configuration
     */
    public static String strConfigSv(String strOrigin, String strAudience,
            LocalNetAuth auth) {
        return strHead()
                + strAuth(strAudience, auth)
                + "  services: {\n"
                + "    sv: {\n"
                + "      url: '" + strOrigin + "/api/sv',\n"
                + "    },\n"
                + "  },\n"
                + STR_INSTANCE_NAMES
                + "};\n";
    }


    /**
     * The shipped scan bundle carries no auth block - scan is read-only and
     * unauthenticated - so none is written here either.
     *
     * @param strOrigin scheme, host and port the browser loaded the page from
     * @return the scan UI's configuration
     */
    public static String strConfigScan(String strOrigin) {
        return strHead()
                + "  services: {\n"
                + "    scan: {\n"
                + "      url: '" + strOrigin + "/api/scan',\n"
                + "    },\n"
                + "  },\n"
                + STR_INSTANCE_NAMES
                + "};\n";
    }


    private static String strHead() {
        return "// Author Claude/bentzn - generated by LocalNetWeb, not read from disk\n"
                + "window.splice_config = {\n";
    }


    /**
     * The browser mints its own HS256 token and the participant verifies it
     * against the literal in conf/canton/&lt;role&gt;/app-auth.conf. Both halves are
     * published constants of a development bundle - LocalNetToken says why that
     * is not a secret being leaked.
     */
    private static String strAuth(String strAudience) {
        return strAuth(strAudience, LocalNetAuth.ofUnsafe());
    }


    /**
     * THE RS-256 KEYS ARE THE BUNDLE'S OWN, not invented: `web-uis/wallet`,
     * `web-uis/ans` and `web-uis/splitwell` each ship this block commented out
     * beside the unsafe one, naming `algorithm`, `authority`, `client_id` and
     * `token_audience`, and pointing at the `oidc-client-ts` settings page.
     * `web-uis/sv` ships no commented block; its UI takes the same keys.
     *
     * THE AUDIENCE IS THE STACK'S, NOT THE ROLE'S. On the browser-minted path
     * each UI signs a token for its own participant, so the per-role
     * `AUTH_&lt;ROLE&gt;_AUDIENCE` is what it must carry. On the provider path the
     * token is issued once and presented to the validator app, which now
     * demands what the participants demand - one value, so one sign-in works
     * everywhere.
     *
     * NO PROXY IS NEEDED FOR THIS. The provider answers `/**` with
     * `Access-Control-Allow-Origin: *` for GET, POST and OPTIONS -
     * `CorsConfig` in `oidc-server`, written for exactly this case - so the
     * page reads the discovery document and the token endpoint straight from
     * the issuer's own origin.
     *
     * @param strAudience what a browser-minted token has to carry
     * @param auth what the stack verifies
     * @return the `auth` block, ending with a newline
     */
    private static String strAuth(String strAudience, LocalNetAuth auth) {
        if (auth.isProvider()) {
            return "  auth: {\n"
                    + "    algorithm: '" + STR_ALGORITHM_RS256 + "',\n"
                    + "    authority: '" + auth.strIssuer() + "',\n"
                    + "    client_id: '" + auth.strClientId() + "',\n"
                    + "    token_audience: '" + auth.strAudience() + "',\n"
                    + "  },\n";
        }
        return "  auth: {\n"
                + "    algorithm: '" + STR_ALGORITHM + "',\n"
                + "    secret: '" + LocalNetToken.STR_SECRET + "',\n"
                + "    token_audience: '" + strAudience + "',\n"
                + "  },\n";
    }


    private static String strOrigin(String strName, int nPort) {
        return "http://" + strName + ":" + nPort;
    }


    private static String strUp(String strHost, int nPort) {
        return "http://" + strHost + ":" + nPort;
    }


    private static int nPortRole(String strPrefix, String strSuffix) {
        return Integer.parseInt(strPrefix + strSuffix);
    }


    private static int nPort(Map<String, Integer> mapPortUi, String strRole, int nFallback) {
        Integer nPort = mapPortUi == null ? null : mapPortUi.get(strRole);
        return nPort == null ? nFallback : nPort.intValue();
    }


    private static String strAudience(Map<String, String> mapAudience, String strRole) {
        String strValue = mapAudience == null ? null : mapAudience.get(strRole);
        return strValue == null || strValue.isBlank() ? LocalNetToken.STR_AUDIENCE : strValue;
    }


    /**
     * The bundle names one wallet user per role - AUTH_&lt;ROLE&gt;_WALLET_ADMIN_USER_NAME,
     * which is what validator-wallet-users.0 is set from - and it happens to be
     * the role's own name on 0.7.4. The role is the fallback for a bundle that
     * says nothing.
     */
    private static String strLogin(Map<String, String> mapLogin, String strRole) {
        String strValue = mapLogin == null ? null : mapLogin.get(strRole);
        return strValue == null || strValue.isBlank() ? strRole : strValue;
    }


    /**
     * WHAT THE PAGE CALLS ITSELF, not what this project calls its bundle
     * directory. "sv sv" told the operator nothing; the heading on the page
     * does, and it is what a second person is told to look for.
     *
     * @param strApp the bundle name
     * @param strRole which role's copy of it this is
     * @return a human title
     */
    public static String strTitle(String strApp, String strRole) {
        if ("sv".equals(strApp))
            return "Super Validator Operations";
        if ("scan".equals(strApp))
            return "Amulet Scan";
        if ("wallet".equals(strApp))
            return "Amulet Wallet - " + strRole;
        if ("ans".equals(strApp))
            return "Amulet Name Service - " + strRole;
        return strApp + " - " + strRole;
    }


    /**
     * Scan is READ-ONLY AND UNAUTHENTICATED - its shipped config.js carries no
     * auth block at all - so it is the one page with no login to report.
     *
     * @param lstSite what {@link #lstSite} built
     * @return every page a browser can open
     */
    public static List<Page> lstPage(List<Site> lstSite) {
        List<Page> lstPage = new ArrayList<>();
        for (Site site : lstSite) {
            for (Vhost vhost : site.lstVhost()) {
                if (vhost.strApp() == null)
                    continue;
                lstPage.add(new Page(strOrigin(vhost.strHost(), site.nPort()),
                        strTitle(vhost.strApp(), site.strRole()),
                        "scan".equals(vhost.strApp()) ? null : site.strLogin()));
            }
        }
        return lstPage;
    }
}
