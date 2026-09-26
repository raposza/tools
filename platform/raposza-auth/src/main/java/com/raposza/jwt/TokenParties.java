// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the parties a token claims to grant.
 *
 * This exists because parties() is an ADMIN call. A token minted to read
 * contracts as two parties is routinely refused the party list, and the
 * participant then tells the tool nothing about who those two parties are -
 * while the token itself names them exactly.
 *
 * NOTHING HERE IS VERIFICATION. The signature is not checked, the expiry is not
 * checked, and the issuer is not consulted. That is deliberate and it is safe:
 * the answer is used to populate a picker, and every call made with the result
 * is authorised by the participant on its own terms. A token that lies about
 * its parties produces a picker with entries that fail on use, which is the same
 * outcome as a token whose rights were revoked a second ago.
 *
 * Only the custom claim carries party names. A scope-based or audience-based
 * token authorises a USER, whose rights live on the participant, so there is
 * nothing to read here and the answer is empty rather than an error.
 *
 * Author Claude/bentzn
 */
public final class TokenParties {

    private TokenParties() {
    }


    /**
     * Parties the token names, readAs first, then any actAs not already listed.
     *
     * @param strToken a compact-serialised JWT, may be null
     * @return party ids in a stable order, empty when the token names none,
     *         cannot be parsed, or carries no custom claim
     */
    public static List<String> readable(String strToken) {
        if (strToken == null || strToken.isBlank())
            return List.of();

        Map<String, Object> mapClaim = ledgerApiClaim(strToken);
        if (mapClaim == null)
            return List.of();

        // A Set for the dedup, insertion-ordered so readAs stays first: the
        // picker's order is what the operator reads, and actAs parties are the
        // ones they are less likely to be looking for.
        Set<String> setParty = new LinkedHashSet<>();
        addAll(setParty, mapClaim.get("readAs"));
        addAll(setParty, mapClaim.get("actAs"));
        return List.copyOf(setParty);
    }


    /**
     * Parties the token claims to ACT as, and nothing else.
     *
     * Separate from readable() because the two answer different questions.
     * readable() populates a picker, where a party the token can only read as
     * still belongs. Deciding whether a choice may be exercised does not: a
     * read-as party in an actAs test would enable a button the participant will
     * refuse.
     *
     * AN EMPTY ANSWER IS NOT A PARTY LIST OF LENGTH ZERO. A connection with no
     * token, or with a scope-based one, knows nothing about who is acting, and
     * a caller deciding availability has to be able to say so rather than
     * concluding that nothing may be exercised.
     *
     * @param strToken a compact-serialised JWT, may be null
     * @return actAs party ids in token order, empty when the token names none,
     *         cannot be parsed, or carries no custom claim
     */
    public static List<String> actAs(String strToken) {
        if (strToken == null || strToken.isBlank())
            return List.of();

        Map<String, Object> mapClaim = ledgerApiClaim(strToken);
        if (mapClaim == null)
            return List.of();

        Set<String> setParty = new LinkedHashSet<>();
        addAll(setParty, mapClaim.get("actAs"));
        return List.copyOf(setParty);
    }


    /**
     * @param strToken a compact-serialised JWT
     * @return the custom claim body, null when absent or unreadable
     */
    private static Map<String, Object> ledgerApiClaim(String strToken) {
        try {
            JWT jwt = JWTParser.parse(strToken);
            Object objClaim = jwt.getJWTClaimsSet().getClaim(JwtMinter.CLAIM_LEDGER_API);
            if (!(objClaim instanceof Map))
                return null;

            Map<?, ?> mapRaw = (Map<?, ?>) objClaim;
            Map<String, Object> mapClaim = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapRaw.entrySet()) {
                if (entry.getKey() != null)
                    mapClaim.put(entry.getKey().toString(), entry.getValue());
            }
            return mapClaim;
        }
        catch (Exception ex) {
            // A token this tool cannot parse is still a token the participant
            // may accept. Returning nothing leaves the party list to the
            // participant, which is where it came from before this existed.
            return null;
        }
    }


    /**
     * @param setParty accumulator
     * @param objList what the claim held under readAs or actAs, any type
     */
    private static void addAll(Set<String> setParty, Object objList) {
        if (!(objList instanceof List))
            return;

        List<?> lstRaw = (List<?>) objList;
        List<String> lstParty = new ArrayList<>();
        for (Object obj : lstRaw) {
            if (obj == null)
                continue;
            String idParty = obj.toString().trim();
            if (!idParty.isEmpty())
                lstParty.add(idParty);
        }
        setParty.addAll(lstParty);
    }

}
