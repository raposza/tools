// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a running stack says it is, as one JSON object. B-8.
 *
 * <h2>Why this exists</h2>
 *
 * Explorer, Workbench and anything else written against this stack needs a
 * ledger API port, a JDBC url and a key set url, and until this existed the
 * only ways to get them were to be told by a person or to read
 * `sandbox.properties` out of a run directory the reader has to know the
 * location of already. Both are hand-over by convention. This is hand-over by
 * protocol: one url, always the same, that answers the question.
 *
 * <h2>IT IS A TOPOLOGY OF NODES</h2>
 *
 * Until 2026-09-21 the document was singular from end to end: one `auth`, one
 * `mint` and one `report`, which was {@link ReadyReport} embedded whole. That
 * describes a Sandbox and cannot describe LocalNet, whose three participants
 * each have their own ports, database and credential. So the shape is now
 * `topology` plus a `nodes` array, `report` is GONE, and a Sandbox is the
 * array with one entry - the operator's instruction of 2026-09-21, and
 * `release_plan.md` section 6.
 *
 * `report` was not kept beside `nodes` as a compatibility projection. Nothing
 * here had been released - every pom was `0.1.0-SNAPSHOT` - so there was no
 * consumer to be compatible with, and keeping it would have written a second
 * spelling of the same ports into the model permanently. The reason
 * `ReadyReport` was embedded verbatim in the first place was to avoid exactly
 * that; {@link DiscoveryNode} carries its keys into each node unchanged for
 * the same reason.
 *
 * <h2>What is here when nothing is running</h2>
 *
 * THE STATE, THE TOPOLOGY, THE DISCOVERY PORT AND THE PROVIDER, AND NOTHING
 * ELSE - operator instruction, 2026-08-22, unchanged by the reshape. `canton`,
 * `run` and `nodes` all describe a stack and a stopped one has none, so a
 * consumer reading them would be reading what the window is POINTED AT as
 * though it were a fact. An edition nothing is running, a directory with no
 * ledger in it and a key set no participant is checking against are worse than
 * silence, because each one looks like an answer.
 *
 * The provider stays. It is a service with its own lifetime rather than a
 * property of a stack, and `running: false` there is a true statement about
 * something that exists.
 *
 * <h2>Immutable, and built on the event thread</h2>
 *
 * Every field is a value the window read while it held the event thread. The
 * HTTP handler runs on its own thread and only ever renders one of these, so
 * no Swing component is touched from off the event thread - which is the whole
 * reason the document is a record and not a callback into the window.
 *
 * @param strState what the window is doing, a SandboxService.State name
 * @param nPortDiscovery the port this document was served from
 * @param strTopology what this is - one of the STR_TOPOLOGY constants - stated
 *        rather than inferred, so a consumer never has to count nodes to find
 *        out what it reached
 * @param strVersion the Canton version selected, or null when none is
 * @param strEdition its edition, or null
 * @param dirRun the run directory the window is pointed at, or null
 * @param fileLog where Canton is writing, or null when nothing is
 * @param flagProvider whether the OpenID Provider is answering
 * @param strProviderMode `embedded` when this window runs it, `external` when
 *        it was pointed at one, or null when unknown
 * @param mapProvider the provider's own endpoints - its discovery document,
 *        issuer, key set and token endpoint. May be null or empty
 * @param lstNode the nodes, in the order a consumer should read them; empty
 *        when nothing is running
 *
 * Author Claude/bentzn
 */
public record DiscoveryDoc(String strState, int nPortDiscovery, String strTopology,
        String strVersion, String strEdition, Path dirRun, Path fileLog,
        boolean flagProvider, String strProviderMode, Map<String, Object> mapProvider,
        List<DiscoveryNode> lstNode) {

    /**
     * COPIED AND MADE UNMODIFIABLE. A record holding a collection the caller
     * still has a reference to is a record whose value changes without anybody
     * writing to it - and this one is handed across a thread boundary.
     */
    public DiscoveryDoc {
        mapProvider = mapProvider == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(mapProvider));
        lstNode = lstNode == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(lstNode));
    }


    /** Says what this is, so a consumer that reached the wrong port knows. */
    public static final String STR_PRODUCT = "raposza-sandbox";

    /**
     * Bumped when a key is REMOVED or its meaning changes. Adding a key does
     * not bump it: a consumer that reads the keys it knows is unaffected by
     * one it has never heard of, and a version that moved for every addition
     * would train every consumer to ignore it.
     *
     * 2 SINCE 2026-09-21, because `report` and `mint` were removed and `auth`
     * moved into each node.
     */
    public static final int N_SCHEMA = 2;

    public static final String STR_KEY_PRODUCT = "product";

    public static final String STR_KEY_SCHEMA = "schema";

    public static final String STR_KEY_STATE = "state";

    public static final String STR_KEY_DISCOVERY = "discovery";

    public static final String STR_KEY_TOPOLOGY = "topology";

    public static final String STR_KEY_CANTON = "canton";

    public static final String STR_KEY_RUN = "run";

    /**
     * THE PROVIDER, and it is spelt `oidc` rather than `mint`. `mint` was this
     * project's word for its own service; what the key names is an OpenID
     * Provider, which may be ours or may be anybody's.
     */
    public static final String STR_KEY_OIDC = "oidc";

    public static final String STR_KEY_NODES = "nodes";

    /**
     * WHOSE PROVIDER IT IS, and the one thing a consumer cannot infer from the
     * url. `embedded` is a process this window owns and stops when it closes;
     * `external` is a service that was already there and outlives the window.
     */
    public static final String STR_KEY_MODE = "mode";

    public static final String STR_MODE_EMBEDDED = "embedded";

    public static final String STR_MODE_EXTERNAL = "external";

    /** One participant, one database cluster, one credential. */
    public static final String STR_TOPOLOGY_SANDBOX = "sandbox";

    /** Splice LocalNet: sv, app-provider and app-user. */
    public static final String STR_TOPOLOGY_LOCALNET = "localnet";

    private static final ObjectMapper MAPPER = new ObjectMapper();


    /**
     * @return whether a stack is serving, which is the one answer a consumer
     *         cannot get any other way
     */
    public boolean flagRunning() {
        return !lstNode.isEmpty();
    }


    /**
     * The document, as nested maps.
     *
     * A SECTION WITH NOTHING IN IT IS OMITTED, not written as null. A consumer
     * testing for the presence of `nodes` is asking the question it means -
     * "is there a stack" - where one testing a null would be relying on this
     * class writing the key at all.
     *
     * @return a new map each call, in a fixed key order
     */
    public Map<String, Object> map() {
        Map<String, Object> mapOut = new LinkedHashMap<>();
        mapOut.put(STR_KEY_PRODUCT, STR_PRODUCT);
        mapOut.put(STR_KEY_SCHEMA, Integer.valueOf(N_SCHEMA));
        mapOut.put(STR_KEY_STATE, strState == null ? "UNKNOWN" : strState);
        put(mapOut, STR_KEY_TOPOLOGY, strTopology);

        Map<String, Object> mapDiscovery = new LinkedHashMap<>();
        mapDiscovery.put("port", Integer.valueOf(nPortDiscovery));
        mapOut.put(STR_KEY_DISCOVERY, mapDiscovery);

        // THE PROVIDER BEFORE THE NODES, because a consumer that has to
        // authenticate reads it first and every node's `auth` block refers
        // back to it.
        Map<String, Object> mapOidc = new LinkedHashMap<>();
        mapOidc.put("running", Boolean.valueOf(flagProvider));
        put(mapOidc, STR_KEY_MODE, strProviderMode);
        if (mapProvider != null)
            mapOidc.putAll(mapProvider);
        mapOut.put(STR_KEY_OIDC, mapOidc);

        // NOTHING ABOUT A STACK UNLESS THERE IS ONE - operator instruction,
        // 2026-08-22. These describe what the window is POINTED AT, and a
        // consumer reading them off a stopped Sandbox would read an intention
        // as a fact.
        if (!flagRunning())
            return mapOut;

        if (strVersion != null) {
            Map<String, Object> mapCanton = new LinkedHashMap<>();
            mapCanton.put("version", strVersion);
            put(mapCanton, "edition", strEdition);
            mapOut.put(STR_KEY_CANTON, mapCanton);
        }

        if (dirRun != null || fileLog != null) {
            Map<String, Object> mapRun = new LinkedHashMap<>();
            put(mapRun, "dir", dirRun == null ? null : dirRun.toString());
            // THE LOG, and it is the reason the run directory is published at
            // all: a consumer that wants to know why a start failed has no
            // other way to find the file.
            put(mapRun, "log", fileLog == null ? null : fileLog.toString());
            mapOut.put(STR_KEY_RUN, mapRun);
        }

        List<Map<String, Object>> lstOut = new ArrayList<>();
        for (DiscoveryNode nodeHere : lstNode) {
            lstOut.add(nodeHere.map());
        }
        mapOut.put(STR_KEY_NODES, lstOut);
        return mapOut;
    }


    /**
     * @return the document, pretty-printed, ending in a newline
     */
    public String strJson() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map()) + "\n";
        }
        catch (JsonProcessingException ex) {
            // Every value in the map is a String, an Integer, a Boolean, a
            // List or another map of the same, so this cannot happen - and a
            // caller that had to handle it would be handling nothing.
            throw new IllegalStateException("the discovery document did not serialise", ex);
        }
    }


    private static void put(Map<String, Object> map, String strKey, String strValue) {
        if (strValue != null && !strValue.isEmpty())
            map.put(strKey, strValue);
    }
}
