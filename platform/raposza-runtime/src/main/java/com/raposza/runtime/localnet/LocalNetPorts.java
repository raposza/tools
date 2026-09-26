// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import com.raposza.runtime.port.PortClass;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every port a LocalNet stack binds, and the rule that assigns them.
 *
 * <h2>The rule</h2>
 *
 * THREE THOUSANDS, ONE PER KIND OF PORT - {@link PortClass}, the operator's
 * decision of 2026-09-23. The nodes take a block in 30xxx, the web UIs a block
 * in 31xxx, and PostgreSQL is a single administrative port in 32xxx.
 *
 * Each block has ONE SETTABLE NUMBER, its first port, and every other port in
 * it is `first + role index x stride + slot index`, the indexes read from
 * {@link #LST_ROLE}, {@link #LST_SLOT_ROLE} and {@link #LST_SLOT_INFRA}. The
 * node block's default is the SAME 30010 Sandbox Simple takes: a developer
 * never runs Sandbox Simple and LocalNetND at once, so the two products occupy
 * one block rather than two that have to be kept apart.
 *
 * <pre>
 *   node first +  0 ..  5   sv            ledger, admin, json, validator, http-health, grpc-health
 *   node first + 10 .. 15   app-provider  the same six, in the same order
 *   node first + 20 .. 25   app-user      the same six, in the same order
 *   node first + 30 .. 41   infrastructure - sequencer, mediator, app-sequencer,
 *                           app-mediator, scan and the sv app
 *   node first + 42         PQS health - the last slot of the block
 *
 *   ui first   +  0, 10, 20 the web UIs of sv, app-provider and app-user
 * </pre>
 *
 * Ten per role rather than six so a role that gains a port does not renumber
 * the two below it - a renumbering invalidates every saved profile and every
 * registered OIDC origin at once. The web UIs keep the same stride in their own
 * thousand for the same reason. PostgreSQL is NOT in a block and keeps its own
 * number, exactly as it does on the Sandbox.
 *
 * <h2>The ceilings</h2>
 *
 * NO BLOCK LEAVES ITS THOUSAND: the node block's first port is capped at
 * {@link #N_PORT_FIRST_MAX} and the UI block's at {@link #N_PORT_UI_FIRST_MAX}.
 * And nothing here may reach 32768, where the kernel's ephemeral range begins -
 * `RaposzaSettings` states it and gives the reason - so a port drawn from inside
 * it can already be held by an outgoing connection.
 *
 * <h2>Why the bundle's own numbering is still here</h2>
 *
 * {@link #ofBundle()} returns what the shipped bundle asks for - `4901` and its
 * relatives, read out of env/common.env and conf/splice/sv/app.conf. It is what
 * the Windows runner in `private/tools` still uses, and it is the reference the
 * overrides are computed against. It is not the default.
 *
 * Author Claude/bentzn
 */
public record LocalNetPorts(Map<String, Integer> mapPort, int nPortPostgres) {

    /** The lowest port the stack takes, matching the Sandbox's own default. */
    public static final int N_PORT_FIRST_DEFAULT = 30010;

    /** PostgreSQL, outside the block, as on the Sandbox. */
    public static final int N_PORT_POSTGRES_DEFAULT = 32101;

    /** How far apart two roles' blocks sit. */
    public static final int N_STRIDE_ROLE = 10;

    /** Where the shared infrastructure block starts, from the first port. */
    public static final int N_OFFSET_INFRA = 30;

    /**
     * Where PQS's health server sits: the slot directly above the last
     * infrastructure port, and the last slot of the node block.
     */
    public static final int N_OFFSET_PQS_HEALTH = 42;

    /** How far above its first port the node block's last port sits. */
    public static final int N_SPAN_NODE = N_OFFSET_PQS_HEALTH;

    /** The highest node first port that keeps the block inside 30xxx. */
    public static final int N_PORT_FIRST_MAX = PortClass.NODE.nFirstMax(N_SPAN_NODE);

    /** The web UIs' block, in its own thousand. */
    public static final int N_PORT_UI_FIRST_DEFAULT = 31000;

    /** How far apart two roles' web UIs sit - the node stride, for the node reason. */
    public static final int N_STRIDE_UI = 10;

    /** The highest port any of this may take. See the type comment. */
    public static final int N_PORT_CEILING = 32767;

    public static final int N_PORT_FLOOR = 1024;

    /** The roles, in block order: sv first, which is how everything lists them. */
    public static final List<String> LST_ROLE = List.of("sv", "app-provider", "app-user");

    /** How far above its first port the UI block's last port sits. */
    public static final int N_SPAN_UI = (LST_ROLE.size() - 1) * N_STRIDE_UI;

    /** The highest UI first port that keeps the block inside 31xxx. */
    public static final int N_PORT_UI_FIRST_MAX = PortClass.UI.nFirstMax(N_SPAN_UI);

    public static final String STR_KEY_LEDGER = "ledger";

    public static final String STR_KEY_ADMIN = "admin";

    public static final String STR_KEY_JSON = "json";

    public static final String STR_KEY_VALIDATOR = "validator";

    public static final String STR_KEY_HTTP_HEALTH = "http-health";

    public static final String STR_KEY_GRPC_HEALTH = "grpc-health";

    public static final String STR_KEY_UI = "ui";

    /**
     * The six per-role slots, in the order they sit in a role's block. The
     * index in this list IS the offset from the role's own first port, so the
     * layout is stated once and read everywhere.
     */
    public static final List<String> LST_SLOT_ROLE = List.of(STR_KEY_LEDGER, STR_KEY_ADMIN,
            STR_KEY_JSON, STR_KEY_VALIDATOR, STR_KEY_HTTP_HEALTH, STR_KEY_GRPC_HEALTH);

    /**
     * The twelve infrastructure slots, in block order, for the same reason.
     *
     * `sv-app` and `scan-app` are spelled with the suffix because `sv` alone is
     * already a ROLE here, and one map holds both.
     */
    public static final List<String> LST_SLOT_INFRA = List.of(
            "sequencer.public", "sequencer.admin", "sequencer.grpc-health",
            "mediator.admin", "mediator.grpc-health",
            "app-sequencer.public", "app-sequencer.admin", "app-sequencer.grpc-health",
            "app-mediator.admin", "app-mediator.grpc-health",
            "scan-app.admin", "sv-app.admin");


    public LocalNetPorts {
        if (mapPort == null || mapPort.isEmpty())
            throw new IllegalArgumentException("a port map is required");
        for (Map.Entry<String, Integer> entry : mapPort.entrySet()) {
            requirePort(entry.getValue(), entry.getKey());
        }
        requirePort(nPortPostgres, "postgresql");
        mapPort = Collections.unmodifiableMap(new LinkedHashMap<>(mapPort));
    }


    /**
     * THE LOWEST PORT OF THE BLOCK, which on a numbering from
     * {@link #ofFirst} is `sv.ledger` by construction. On {@link #ofBundle}'s
     * numbering nothing is contiguous and this is simply the SV's Ledger API
     * port - which still distinguishes one numbering from another, which is
     * all a snapshot key needs of it.
     *
     * @return the port, or 0 when the map carries no sv ledger entry
     */
    public int nPortFirst() {
        Integer nPort = mapPort.get(LST_ROLE.get(0) + "." + STR_KEY_LEDGER);
        return nPort == null ? 0 : nPort.intValue();
    }


    /**
     * @param nPort the port to check
     * @param strWhat what it is, for the message
     */
    private static void requirePort(int nPort, String strWhat) {
        if (nPort < N_PORT_FLOOR || nPort > N_PORT_CEILING) {
            throw new IllegalArgumentException(strWhat + " is outside " + N_PORT_FLOOR + "-"
                    + N_PORT_CEILING + ": " + nPort);
        }
    }


    /**
     * THE NODE BLOCK AT A GIVEN FIRST PORT, with the web UIs on their default
     * block.
     *
     * @param nPortFirst the lowest port the stack takes
     * @param nPortPostgresNew the embedded cluster, outside the block
     * @return the whole numbering
     * @throws IllegalArgumentException when a port leaves its class
     */
    public static LocalNetPorts ofFirst(int nPortFirst, int nPortPostgresNew) {
        return ofFirst(nPortFirst, N_PORT_UI_FIRST_DEFAULT, nPortPostgresNew);
    }


    /**
     * THE TWO BLOCKS, and the defaults are where LocalNetND now runs.
     *
     * @param nPortFirst the node block's first port
     * @param nPortUiFirst the web UI block's first port
     * @param nPortPostgresNew the embedded cluster, an administrative port
     * @return the whole numbering
     * @throws IllegalArgumentException when a block would leave its thousand or
     *         PostgreSQL is not an administrative port
     */
    public static LocalNetPorts ofFirst(int nPortFirst, int nPortUiFirst, int nPortPostgresNew) {
        PortClass.NODE.requireFirst(nPortFirst, N_SPAN_NODE, "first port");
        PortClass.UI.requireFirst(nPortUiFirst, N_SPAN_UI, "first web UI port");
        PortClass.ADMIN.require(nPortPostgresNew, "PostgreSQL port");

        Map<String, Integer> mapOut = new LinkedHashMap<>();
        for (int idxRole = 0; idxRole < LST_ROLE.size(); idxRole++) {
            int nPortRole = nPortFirst + idxRole * N_STRIDE_ROLE;
            for (int idxSlot = 0; idxSlot < LST_SLOT_ROLE.size(); idxSlot++) {
                mapOut.put(LST_ROLE.get(idxRole) + "." + LST_SLOT_ROLE.get(idxSlot),
                        nPortRole + idxSlot);
            }
        }
        for (int idxInfra = 0; idxInfra < LST_SLOT_INFRA.size(); idxInfra++) {
            mapOut.put(LST_SLOT_INFRA.get(idxInfra), nPortFirst + N_OFFSET_INFRA + idxInfra);
        }
        for (int idxRole = 0; idxRole < LST_ROLE.size(); idxRole++) {
            mapOut.put(LST_ROLE.get(idxRole) + "." + STR_KEY_UI,
                    nPortUiFirst + idxRole * N_STRIDE_UI);
        }
        return new LocalNetPorts(mapOut, nPortPostgresNew);
    }


    /**
     * @return the blocks at {@link #N_PORT_FIRST_DEFAULT} and
     *         {@link #N_PORT_UI_FIRST_DEFAULT}
     */
    public static LocalNetPorts ofDefaults() {
        return ofFirst(N_PORT_FIRST_DEFAULT, N_PORT_POSTGRES_DEFAULT);
    }


    /**
     * WHAT THE SHIPPED BUNDLE ASKS FOR, every number read out of it - the role
     * prefix and suffix from env/common.env, the fixed ones from
     * conf/canton/*&#47;app.conf and conf/splice/sv/app.conf, measured against
     * 0.8.1 on 2026-09-21.
     *
     * The five values in the `_participant` and `_validator_backend` templates
     * - 5001, 5002, 5003, 7000 and 7575 - are NOT here: every one of the three
     * roles overrides all five in its own file, so none of them is ever bound.
     *
     * @return the vendor's numbering
     */
    public static LocalNetPorts ofBundle() {
        Map<String, Integer> mapOut = new LinkedHashMap<>();
        for (String strRole : LST_ROLE) {
            String strPrefix = LocalNetSpec.strPrefix(strRole);
            mapOut.put(strRole + "." + STR_KEY_LEDGER,
                    Integer.parseInt(strPrefix + LocalNetSpec.STR_SUFFIX_LEDGER));
            mapOut.put(strRole + "." + STR_KEY_ADMIN,
                    Integer.parseInt(strPrefix + LocalNetSpec.STR_SUFFIX_ADMIN));
            mapOut.put(strRole + "." + STR_KEY_JSON,
                    Integer.parseInt(strPrefix + LocalNetSpec.STR_SUFFIX_JSON));
            mapOut.put(strRole + "." + STR_KEY_VALIDATOR,
                    Integer.parseInt(strPrefix + LocalNetSpec.STR_SUFFIX_VALIDATOR));
            mapOut.put(strRole + "." + STR_KEY_HTTP_HEALTH,
                    Integer.parseInt(strPrefix + LocalNetSpec.STR_SUFFIX_HTTP_HEALTH));
            mapOut.put(strRole + "." + STR_KEY_GRPC_HEALTH,
                    Integer.parseInt(strPrefix + LocalNetSpec.STR_SUFFIX_GRPC_HEALTH));
        }
        mapOut.put("sequencer.public", LocalNetSpec.N_PORT_SEQUENCER_INTERNAL);
        mapOut.put("sequencer.admin", LocalNetSpec.N_PORT_SEQUENCER_ADMIN);
        mapOut.put("sequencer.grpc-health", 5062);
        mapOut.put("mediator.admin", LocalNetSpec.N_PORT_MEDIATOR_ADMIN);
        mapOut.put("mediator.grpc-health", 5061);
        mapOut.put("app-sequencer.public", LocalNetSpec.N_PORT_APP_SEQUENCER_INTERNAL);
        mapOut.put("app-sequencer.admin", LocalNetSpec.N_PORT_APP_SEQUENCER_ADMIN);
        mapOut.put("app-sequencer.grpc-health", 5072);
        mapOut.put("app-mediator.admin", LocalNetSpec.N_PORT_APP_MEDIATOR_ADMIN);
        mapOut.put("app-mediator.grpc-health", 5071);
        mapOut.put("scan-app.admin", LocalNetSpec.N_PORT_SCAN);
        mapOut.put("sv-app.admin", LocalNetSpec.N_PORT_SV);
        mapOut.put("sv." + STR_KEY_UI, LocalNetUi.N_PORT_UI_SV);
        mapOut.put("app-provider." + STR_KEY_UI, LocalNetUi.N_PORT_UI_APP_PROVIDER);
        mapOut.put("app-user." + STR_KEY_UI, LocalNetUi.N_PORT_UI_APP_USER);
        return new LocalNetPorts(mapOut, LocalNetSpec.N_PORT_PG);
    }


    /**
     * @param strKey one of the keys this record was built with
     * @return that port
     * @throws IllegalArgumentException for a key this numbering does not carry,
     *         which is a programming error rather than a configuration one
     */
    public int nPortOf(String strKey) {
        Integer nPort = mapPort.get(strKey);
        if (nPort == null)
            throw new IllegalArgumentException("no such port: " + strKey);
        return nPort.intValue();
    }


    public int nPortLedger(String strRole) {
        return nPortOf(strRole + "." + STR_KEY_LEDGER);
    }


    public int nPortAdmin(String strRole) {
        return nPortOf(strRole + "." + STR_KEY_ADMIN);
    }


    public int nPortJson(String strRole) {
        return nPortOf(strRole + "." + STR_KEY_JSON);
    }


    public int nPortValidator(String strRole) {
        return nPortOf(strRole + "." + STR_KEY_VALIDATOR);
    }


    public int nPortHttpHealth(String strRole) {
        return nPortOf(strRole + "." + STR_KEY_HTTP_HEALTH);
    }


    public int nPortGrpcHealth(String strRole) {
        return nPortOf(strRole + "." + STR_KEY_GRPC_HEALTH);
    }


    public int nPortUi(String strRole) {
        return nPortOf(strRole + "." + STR_KEY_UI);
    }


    public int nPortScan() {
        return nPortOf("scan-app.admin");
    }


    public int nPortSvApp() {
        return nPortOf("sv-app.admin");
    }


    /**
     * Ports whose absence is a DEFECT - the same set `LocalNetSpec` names, read
     * off this numbering instead of the bundle's.
     *
     * @return label to port
     */
    public Map<String, Integer> mapPortRequired() {
        Map<String, Integer> mapOut = new LinkedHashMap<>();
        for (String strRole : LST_ROLE) {
            mapOut.put(strRole + " ledger-api", nPortLedger(strRole));
            mapOut.put(strRole + " participant admin-api", nPortAdmin(strRole));
            mapOut.put(strRole + " validator admin-api", nPortValidator(strRole));
        }
        mapOut.put("sequencer public-api", nPortOf("sequencer.public"));
        mapOut.put("sequencer admin-api", nPortOf("sequencer.admin"));
        mapOut.put("mediator admin-api", nPortOf("mediator.admin"));
        mapOut.put("scan", nPortScan());
        mapOut.put("sv", nPortSvApp());
        return mapOut;
    }


    /**
     * @return label to port, the ones reported rather than asserted
     */
    public Map<String, Integer> mapPortObserved() {
        Map<String, Integer> mapOut = new LinkedHashMap<>();
        for (String strRole : LST_ROLE) {
            mapOut.put(strRole + " http-health", nPortHttpHealth(strRole));
            mapOut.put(strRole + " grpc-health", nPortGrpcHealth(strRole));
            mapOut.put(strRole + " json-api", nPortJson(strRole));
        }
        mapOut.put("app-sequencer public-api", nPortOf("app-sequencer.public"));
        mapOut.put("app-sequencer admin-api", nPortOf("app-sequencer.admin"));
        mapOut.put("app-mediator admin-api", nPortOf("app-mediator.admin"));
        for (String strRole : LST_ROLE) {
            mapOut.put(strRole + " UI", nPortUi(strRole));
        }
        return mapOut;
    }


    /**
     * @param strHost what the endpoints are dialled on
     * @return label to readyz url, the five the bundle's health checks use
     */
    public Map<String, String> mapReadyz(String strHost) {
        Map<String, String> mapOut = new LinkedHashMap<>();
        for (String strRole : LST_ROLE) {
            mapOut.put(strRole + " validator", "http://" + strHost + ":"
                    + nPortValidator(strRole) + "/api/validator/readyz");
        }
        mapOut.put("scan", "http://" + strHost + ":" + nPortScan() + "/api/scan/readyz");
        mapOut.put("sv", "http://" + strHost + ":" + nPortSvApp() + "/api/sv/readyz");
        return mapOut;
    }


    /**
     * THE -C OVERRIDES THAT MOVE THE STACK ONTO THIS NUMBERING.
     *
     * Every key was read out of the 0.8.1 bundle rather than derived. The
     * canton namespace names the CONCRETE nodes, because each role's own file
     * sets the port on the concrete path and an anchor override would lose to
     * it; the splice namespace names the two anchors where the bundle uses
     * them, which is the shape `LocalNetSpec.mapHostOverrides` already proved
     * works.
     *
     * The three strings carrying a port - scan's two urls and the sequencer's
     * advertised url - are rewritten here too. The sequencer one is ADVERTISED
     * INTO TOPOLOGY STATE at DSO founding and cannot be corrected in place, so
     * a run directory founded on other ports has to be discarded with --fresh.
     *
     * @param strNamespace canton or splice
     * @param strHost what the nodes dial each other on
     * @return -C key to value
     */
    public Map<String, String> mapOverride(String strNamespace, String strHost) {
        Map<String, String> mapOut = new LinkedHashMap<>();
        if (LocalNetRunner.STR_NS_CANTON.equals(strNamespace)) {
            for (String strRole : LST_ROLE) {
                String strNode = "canton.participants." + strRole + ".";
                mapOut.put(strNode + "ledger-api.port", String.valueOf(nPortLedger(strRole)));
                mapOut.put(strNode + "admin-api.port", String.valueOf(nPortAdmin(strRole)));
                mapOut.put(strNode + "http-ledger-api.port", String.valueOf(nPortJson(strRole)));
                mapOut.put(strNode + "monitoring.http-health-server.port",
                        String.valueOf(nPortHttpHealth(strRole)));
                mapOut.put(strNode + "monitoring.grpc-health-server.port",
                        String.valueOf(nPortGrpcHealth(strRole)));
            }
            mapOut.put("canton.sequencers.sequencer.public-api.port",
                    String.valueOf(nPortOf("sequencer.public")));
            mapOut.put("canton.sequencers.sequencer.admin-api.port",
                    String.valueOf(nPortOf("sequencer.admin")));
            mapOut.put("canton.sequencers.sequencer.monitoring.grpc-health-server.port",
                    String.valueOf(nPortOf("sequencer.grpc-health")));
            mapOut.put("canton.sequencers.app-sequencer.public-api.port",
                    String.valueOf(nPortOf("app-sequencer.public")));
            mapOut.put("canton.sequencers.app-sequencer.admin-api.port",
                    String.valueOf(nPortOf("app-sequencer.admin")));
            mapOut.put("canton.sequencers.app-sequencer.monitoring.grpc-health-server.port",
                    String.valueOf(nPortOf("app-sequencer.grpc-health")));
            mapOut.put("canton.mediators.mediator.admin-api.port",
                    String.valueOf(nPortOf("mediator.admin")));
            mapOut.put("canton.mediators.mediator.monitoring.grpc-health-server.port",
                    String.valueOf(nPortOf("mediator.grpc-health")));
            mapOut.put("canton.mediators.app-mediator.admin-api.port",
                    String.valueOf(nPortOf("app-mediator.admin")));
            mapOut.put("canton.mediators.app-mediator.monitoring.grpc-health-server.port",
                    String.valueOf(nPortOf("app-mediator.grpc-health")));
            return mapOut;
        }

        String strUrlScan = "http://" + strHost + ":" + nPortScan();
        for (String strRole : LST_ROLE) {
            String strApp = "canton.validator-apps." + strRole + "-validator_backend.";
            mapOut.put(strApp + "admin-api.port", String.valueOf(nPortValidator(strRole)));
        }
        // app-provider and app-user name their participant client themselves;
        // sv reaches its own through the _sv_participant_client anchor.
        for (String strRole : List.of("app-provider", "app-user")) {
            String strApp = "canton.validator-apps." + strRole + "-validator_backend."
                    + "participant-client.";
            mapOut.put(strApp + "admin-api.port", String.valueOf(nPortAdmin(strRole)));
            mapOut.put(strApp + "ledger-api.client-config.port",
                    String.valueOf(nPortLedger(strRole)));
        }
        mapOut.put("_sv_participant_client.admin-api.port", String.valueOf(nPortAdmin("sv")));
        mapOut.put("_sv_participant_client.ledger-api.client-config.port",
                String.valueOf(nPortLedger("sv")));
        mapOut.put("canton.validator-apps.sv-validator_backend.scan-client.url", strUrlScan);

        mapOut.put("canton.scan-apps.scan-app.admin-api.port", String.valueOf(nPortScan()));
        mapOut.put("canton.scan-apps.scan-app.synchronizer-nodes.current.sequencer.port",
                String.valueOf(nPortOf("sequencer.admin")));
        mapOut.put("canton.scan-apps.scan-app.synchronizer-nodes.current.mediator.port",
                String.valueOf(nPortOf("mediator.admin")));

        mapOut.put("canton.sv-apps.sv.admin-api.port", String.valueOf(nPortSvApp()));
        mapOut.put("canton.sv-apps.sv.scan.public-url", strUrlScan);
        mapOut.put("canton.sv-apps.sv.scan.internal-url", strUrlScan);
        String strNodes = "canton.sv-apps.sv.local-synchronizer-nodes.current.";
        mapOut.put(strNodes + "sequencer.admin-api.port",
                String.valueOf(nPortOf("sequencer.admin")));
        mapOut.put(strNodes + "sequencer.internal-api.port",
                String.valueOf(nPortOf("sequencer.public")));
        mapOut.put(strNodes + "sequencer.external-public-api-url",
                "http://" + strHost + ":" + nPortOf("sequencer.public"));
        mapOut.put(strNodes + "mediator.admin-api.port",
                String.valueOf(nPortOf("mediator.admin")));
        return mapOut;
    }


    /**
     * The ports the bundle carries in its ENVIRONMENT rather than its conf.
     * Applied after the stager has read the env files, so these win.
     *
     * @param strHost what the addresses point at
     * @return environment name to value
     */
    public Map<String, String> mapEnvOverride(String strHost) {
        Map<String, String> mapOut = new LinkedHashMap<>();
        mapOut.put("DB_PORT", String.valueOf(nPortPostgres));
        mapOut.put("SV_UI_PORT", String.valueOf(nPortUi("sv")));
        mapOut.put("APP_PROVIDER_UI_PORT", String.valueOf(nPortUi("app-provider")));
        mapOut.put("APP_USER_UI_PORT", String.valueOf(nPortUi("app-user")));
        mapOut.put("SPLICE_APP_VALIDATOR_SCAN_ADDRESS",
                "http://" + strHost + ":" + nPortScan());
        mapOut.put("SPLICE_APP_VALIDATOR_SV_SPONSOR_ADDRESS",
                "http://" + strHost + ":" + nPortSvApp());
        return mapOut;
    }


    /**
     * @return role to UI port, the shape LocalNetUi takes
     */
    public Map<String, Integer> mapPortUi() {
        Map<String, Integer> mapOut = new LinkedHashMap<>();
        for (String strRole : LST_ROLE) {
            mapOut.put(strRole, nPortUi(strRole));
        }
        return mapOut;
    }

}
