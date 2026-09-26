// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One node of a topology, and everything needed to reach it.
 *
 * <h2>Why the document is a LIST of these</h2>
 *
 * A Sandbox has one participant and LocalNet has three - `sv`, `app-provider`
 * and `app-user`, `LocalNetSpec:64` - each with its own ports, its own
 * database and its own credential. The document used to describe exactly one,
 * under `report`, so a three-node topology could not be published at all
 * without either re-spelling everything or inventing which of the three
 * `report` meant. It is a list, and a Sandbox is the list with one entry in
 * it.
 *
 * <h2>THE REPORT'S KEYS GO IN UNCHANGED</h2>
 *
 * {@link ReadyReport}'s spelling - `port.ledger-api`, `host`,
 * `participant.id`, `url.jdbc.&lt;db&gt;` - is the same spelling
 * `sandbox.properties` uses, deliberately, so a consumer that reads the file
 * finds the key it already knows. That reason does not weaken by moving into a
 * node, so the keys are carried in at the node's top level rather than nested
 * under a second name. A second spelling of one fact is a second thing to keep
 * in step.
 *
 * <h2>The name and the role are different questions</h2>
 *
 * THE NAME IS WHAT CANTON CALLS IT. For the Sandbox that is `sandbox` -
 * `StorageOverlay.STR_NODE_PARTICIPANT` - and it is the string the participant
 * id and the audience convention are both built from, so it cannot be chosen
 * freely. THE ROLE IS WHAT IT IS FOR, and that is where a consumer looks to
 * find the node it wants. On LocalNet the two coincide, because there the role
 * IS the Canton node name; on the Sandbox they do not, and publishing the role
 * in the name field would put a string in the document that disagrees with the
 * `participant.id` beside it - operator instruction, 2026-09-21.
 *
 * @param strName the node's name, as Canton knows it
 * @param strRole what the node is for; one of the STR_ROLE constants
 * @param auth what this node verifies, or null when it verifies nothing
 * @param strUrlJwks the key set url to publish when the node names none itself
 * @param strUrlToken a url that mints a token this node accepts, or null
 * @param mapOidc what an OIDC client needs that no discovery document carries
 *        - the audience or scope, and who to ask as. May be null or empty
 * @param report what this node is serving, or null when it is serving nothing
 *
 * Author Claude/bentzn
 */
public record DiscoveryNode(String strName, String strRole, AuthSettings auth,
        String strUrlJwks, String strUrlToken, Map<String, Object> mapOidc,
        ReadyReport report) {

    /**
     * COPIED AND MADE UNMODIFIABLE, for the reason {@link DiscoveryDoc} gives:
     * this is handed across a thread boundary.
     */
    public DiscoveryNode {
        mapOidc = mapOidc == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(mapOidc));
    }


    public static final String STR_KEY_NAME = "name";

    public static final String STR_KEY_ROLE = "role";

    public static final String STR_KEY_AUTH = "auth";

    public static final String STR_KEY_OIDC = "oidc";

    /** The Sandbox's single participant, and LocalNet's application side. */
    public static final String STR_ROLE_APP_PROVIDER = "app-provider";

    public static final String STR_ROLE_APP_USER = "app-user";

    public static final String STR_ROLE_SV = "sv";


    /**
     * @return the node, as nested maps; a new map each call, in a fixed order
     */
    public Map<String, Object> map() {
        Map<String, Object> mapOut = new LinkedHashMap<>();
        put(mapOut, STR_KEY_NAME, strName);
        put(mapOut, STR_KEY_ROLE, strRole);
        if (auth != null)
            mapOut.put(STR_KEY_AUTH, mapAuth());
        // LAST, so the node reads name-role-auth before the wall of ports, and
        // WITHOUT a wrapper - see the class comment.
        if (report != null)
            mapOut.putAll(report.map());
        return mapOut;
    }


    /**
     * What this node checks, and how to satisfy it.
     *
     * @return never null
     */
    private Map<String, Object> mapAuth() {
        Map<String, Object> mapOut = new LinkedHashMap<>();
        mapOut.put("mode", auth.mode().name());
        // WHAT CANTON CALLS IT, beside what this application calls it. A
        // consumer comparing against a participant configuration file is
        // reading the type, not the enum member.
        mapOut.put("type", auth.mode().strType());
        if (auth.mode().flagTargets()) {
            mapOut.put("shape", auth.shape().name());
            // THE KEY SET THIS NODE WAS CONFIGURED WITH. The provider block
            // says where the provider is; this says what the participant was
            // actually told, and a consumer comparing the two is how a
            // mismatch gets found rather than guessed at.
            put(mapOut, "jwks", auth.strUrlJwksEffective(strUrlJwks));
            put(mapOut, "token", strUrlToken);
            if (mapOidc != null && !mapOidc.isEmpty())
                mapOut.put(STR_KEY_OIDC, mapOidc);
        }
        return mapOut;
    }


    private static void put(Map<String, Object> map, String strKey, String strValue) {
        if (strValue != null && !strValue.isEmpty())
            map.put(strKey, strValue);
    }
}
