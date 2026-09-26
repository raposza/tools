// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What LocalNet is, expressed as data.
 *
 * Every constant here was read out of the shipped bundle - env/common.env,
 * env/postgres.env, docker/splice/health-check.sh - and not derived. The
 * database names are the ONLY deliberate divergence: upstream names them with
 * hyphens, SandboxPostgres accepts an unquoted identifier only, so each node is
 * pointed at an underscore equivalent through -C. The names are internal and
 * nothing outside this process reads them.
 *
 * Author Claude/bentzn
 */
public final class LocalNetSpec {

    /** Role prefixes, from env/common.env. */
    public static final String STR_PREFIX_SV = "4";

    public static final String STR_PREFIX_APP_PROVIDER = "3";

    public static final String STR_PREFIX_APP_USER = "2";

    /** Port suffixes, from env/common.env. */
    public static final String STR_SUFFIX_LEDGER = "901";

    public static final String STR_SUFFIX_ADMIN = "902";

    public static final String STR_SUFFIX_VALIDATOR = "903";

    public static final String STR_SUFFIX_HTTP_HEALTH = "900";

    public static final String STR_SUFFIX_GRPC_HEALTH = "961";

    public static final String STR_SUFFIX_JSON = "975";

    /** Fixed ports, from conf/splice/sv/app.conf. */
    public static final int N_PORT_SCAN = 5012;

    public static final int N_PORT_SV = 5014;

    public static final int N_PORT_SEQUENCER_INTERNAL = 5008;

    public static final int N_PORT_SEQUENCER_ADMIN = 5009;

    public static final int N_PORT_MEDIATOR_ADMIN = 5007;

    public static final int N_PORT_APP_SEQUENCER_INTERNAL = 5018;

    public static final int N_PORT_APP_SEQUENCER_ADMIN = 5019;

    public static final int N_PORT_APP_MEDIATOR_ADMIN = 5017;

    /** The embedded cluster. Fixed rather than drawn, per SandboxPostgres. */
    public static final int N_PORT_PG = 32101;

    private static final String[] ARR_ROLE = { "sv", "app-provider", "app-user" };

    private LocalNetSpec() {
    }


    /**
     * @param strRole sv, app-provider or app-user
     * @return the prefix that role's ports carry
     */
    public static String strPrefix(String strRole) {
        if ("sv".equals(strRole))
            return STR_PREFIX_SV;
        if ("app-provider".equals(strRole))
            return STR_PREFIX_APP_PROVIDER;
        if ("app-user".equals(strRole))
            return STR_PREFIX_APP_USER;
        throw new IllegalArgumentException("no such role: " + strRole);
    }


    /**
     * Ports whose absence is a DEFECT. Every one of these is measured: the
     * ledger and validator ports are what the runner gates on, the participant
     * admin ports are what the splice apps dial (and refused when the canton
     * namespace held no participants, 2026-08-26), and 5007/5008/5009 are what
     * stock Canton was measured to bind against this conf tree.
     *
     * @return label to port
     */
    public static Map<String, Integer> mapPortRequired() {
        Map<String, Integer> mapPort = new LinkedHashMap<>();
        for (String strRole : ARR_ROLE) {
            String strPrefix = strPrefix(strRole);
            mapPort.put(strRole + " ledger-api", Integer.parseInt(strPrefix + STR_SUFFIX_LEDGER));
            mapPort.put(strRole + " participant admin-api",
                    Integer.parseInt(strPrefix + STR_SUFFIX_ADMIN));
            mapPort.put(strRole + " validator admin-api",
                    Integer.parseInt(strPrefix + STR_SUFFIX_VALIDATOR));
        }
        mapPort.put("sequencer public-api", N_PORT_SEQUENCER_INTERNAL);
        mapPort.put("sequencer admin-api", N_PORT_SEQUENCER_ADMIN);
        mapPort.put("mediator admin-api", N_PORT_MEDIATOR_ADMIN);
        mapPort.put("scan", N_PORT_SCAN);
        mapPort.put("sv", N_PORT_SV);
        return mapPort;
    }


    /**
     * Ports REPORTED and not asserted, because whether each should answer in a
     * UI-less v0.0.1 has not been measured. A probe that fails on one of these
     * would be reporting its own assumption as a broken node - the failure this
     * split exists to prevent. The three UI ports are served by LocalNetWeb in
     * this process, so an open one is expected and a closed one means --no-ui
     * or a bundle carrying no web-uis. Swagger is here for the opposite reason:
     * nothing starts it, so an OPEN one means a leftover container.
     *
     * @return label to port
     */
    public static Map<String, Integer> mapPortObserved() {
        Map<String, Integer> mapPort = new LinkedHashMap<>();
        for (String strRole : ARR_ROLE) {
            String strPrefix = strPrefix(strRole);
            mapPort.put(strRole + " http-health",
                    Integer.parseInt(strPrefix + STR_SUFFIX_HTTP_HEALTH));
            mapPort.put(strRole + " grpc-health",
                    Integer.parseInt(strPrefix + STR_SUFFIX_GRPC_HEALTH));
            mapPort.put(strRole + " json-api", Integer.parseInt(strPrefix + STR_SUFFIX_JSON));
        }
        mapPort.put("app-sequencer public-api", N_PORT_APP_SEQUENCER_INTERNAL);
        mapPort.put("app-sequencer admin-api", N_PORT_APP_SEQUENCER_ADMIN);
        mapPort.put("app-mediator admin-api", N_PORT_APP_MEDIATOR_ADMIN);
        mapPort.put("sv UI", LocalNetUi.N_PORT_UI_SV);
        mapPort.put("app-provider UI", LocalNetUi.N_PORT_UI_APP_PROVIDER);
        mapPort.put("app-user UI", LocalNetUi.N_PORT_UI_APP_USER);
        mapPort.put("swagger (none in v0.0.1)", 9090);
        return mapPort;
    }


    /**
     * @param strHost what the endpoints are dialled on
     * @return label to readyz url, the five the bundle's own health checks use
     */
    public static Map<String, String> mapReadyz(String strHost) {
        Map<String, String> mapUrl = new LinkedHashMap<>();
        for (String strRole : ARR_ROLE) {
            mapUrl.put(strRole + " validator",
                    "http://" + strHost + ":" + strPrefix(strRole) + STR_SUFFIX_VALIDATOR
                            + "/api/validator/readyz");
        }
        mapUrl.put("scan", "http://" + strHost + ":" + N_PORT_SCAN + "/api/scan/readyz");
        mapUrl.put("sv", "http://" + strHost + ":" + N_PORT_SV + "/api/sv/readyz");
        return mapUrl;
    }


    /**
     * @return the twelve databases, keyed by the -C path that points a node at
     *         one; iteration order is the order they are created in
     */
    public static Map<String, String> mapDatabases(String strNamespace) {
        Map<String, String> mapDb = new LinkedHashMap<>();
        if ("canton".equals(strNamespace)) {
            mapDb.put("canton.participants.sv.storage.config.properties.databaseName",
                    "participant_sv");
            mapDb.put("canton.participants.app-provider.storage.config.properties.databaseName",
                    "participant_app_provider");
            mapDb.put("canton.participants.app-user.storage.config.properties.databaseName",
                    "participant_app_user");
            mapDb.put("canton.sequencers.sequencer.storage.config.properties.databaseName",
                    "sequencer");
            mapDb.put("canton.sequencers.app-sequencer.storage.config.properties.databaseName",
                    "app_sequencer");
            mapDb.put("canton.mediators.mediator.storage.config.properties.databaseName",
                    "mediator");
            mapDb.put("canton.mediators.app-mediator.storage.config.properties.databaseName",
                    "app_mediator");
            return mapDb;
        }
        mapDb.put("canton.validator-apps.app-provider-validator_backend"
                + ".storage.config.properties.databaseName", "validator_app_provider");
        mapDb.put("canton.validator-apps.app-user-validator_backend"
                + ".storage.config.properties.databaseName", "validator_app_user");
        mapDb.put("canton.validator-apps.sv-validator_backend"
                + ".storage.config.properties.databaseName", "validator_sv");
        mapDb.put("canton.scan-apps.scan-app.storage.config.properties.databaseName", "scan");
        mapDb.put("canton.sv-apps.sv.storage.config.properties.databaseName", "sv");
        return mapDb;
    }


    /**
     * THE EMBEDDED CLUSTER'S CREDENTIALS. Zonky starts it with a superuser and
     * no host authentication, and every node's storage block is written with
     * these two - so they are what a consumer needs and there is no second
     * account to hand out.
     */
    public static final String STR_DB_USER = "postgres";

    public static final String STR_DB_PASSWORD = "postgres";

    /**
     * WHERE PQS WRITES, on this same cluster - his instruction of 2026-09-22
     * that the Sandbox's LocalNetND run a PQS against the app-provider ledger.
     *
     * It is named here so `startPostgres` ensures it with the rest. NOTHING IN
     * THIS MODULE STARTS SCRIBE and nothing here can: `ScribeProcess` sits in
     * `raposza-canton`, which is above this one, and the enforcer rule
     * `s2-runtime-declares-nothing-above-it` is what says so. The owner is
     * `apps/sandbox`'s `LocalNetPqs`; this database is the one thing the stack
     * itself has to provide for it.
     */
    public static final String STR_DB_PQS = "pqs";


    /**
     * @return every database name this run needs, both namespaces
     */
    public static List<String> lstAllDatabases() {
        List<String> lstDb = new ArrayList<>(mapDatabases("canton").values());
        lstDb.addAll(mapDatabases("splice").values());
        lstDb.add(STR_DB_PQS);
        return lstDb;
    }


    /**
     * THE ONE SPELLING, read out of the map the stager writes rather than
     * rebuilt from the role: a second spelling is a second thing to keep in
     * step the day a database is renamed.
     *
     * @param strRole sv, app-provider or app-user
     * @return the database Canton keeps that role's participant in, or null
     */
    public static String strDbParticipant(String strRole) {
        return strDbOf("canton", ".participants." + strRole + ".");
    }


    /**
     * @param strRole sv, app-provider or app-user
     * @return the database that role's validator app keeps its state in, or
     *         null
     */
    public static String strDbValidator(String strRole) {
        return strDbOf("splice", ".validator-apps." + strRole + "-validator_backend.");
    }


    /**
     * @param strNamespace canton or splice
     * @param strNeedle what the configuration key carries in the middle
     * @return the database name, or null when no key matches
     */
    private static String strDbOf(String strNamespace, String strNeedle) {
        for (Map.Entry<String, String> entry : mapDatabases(strNamespace).entrySet()) {
            if (entry.getKey().contains(strNeedle))
                return entry.getValue();
        }
        return null;
    }


    /**
     * The container hostname `canton` resolves to nothing outside a Docker
     * network. Four of these are anchors and carry several use sites each; the
     * rest are unanchored and have to be named one by one.
     *
     * @param strHost what `canton` becomes
     * @return -C key/value pairs for the splice namespace
     */
    public static Map<String, String> mapHostOverrides(String strHost) {
        Map<String, String> mapHost = new LinkedHashMap<>();
        mapHost.put("_validator_backend.participant-client.admin-api.address", strHost);
        mapHost.put("_validator_backend.participant-client.ledger-api.client-config.address",
                strHost);
        mapHost.put("_sv_participant_client.admin-api.address", strHost);
        mapHost.put("_sv_participant_client.ledger-api.client-config.address", strHost);
        mapHost.put("canton.scan-apps.scan-app.synchronizer-nodes.current.sequencer.address",
                strHost);
        mapHost.put("canton.scan-apps.scan-app.synchronizer-nodes.current.mediator.address",
                strHost);
        mapHost.put("canton.sv-apps.sv.local-synchronizer-nodes.current"
                + ".sequencer.admin-api.address", strHost);
        mapHost.put("canton.sv-apps.sv.local-synchronizer-nodes.current"
                + ".sequencer.internal-api.address", strHost);
        mapHost.put("canton.sv-apps.sv.local-synchronizer-nodes.current"
                + ".mediator.admin-api.address", strHost);

        // NOT a dial. This is advertised into topology state when the DSO is
        // founded, and it cannot be corrected afterwards without a reset. On a
        // workstation loopback is right; on a server it must be the address
        // remote developers reach.
        mapHost.put("canton.sv-apps.sv.local-synchronizer-nodes.current"
                + ".sequencer.external-public-api-url",
                "http://" + strHost + ":" + N_PORT_SEQUENCER_INTERNAL);
        return mapHost;
    }
}
