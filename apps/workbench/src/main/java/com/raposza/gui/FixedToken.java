// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raposza.api.TokenSource_i;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * One credential, for whichever user it was issued to.
 *
 * <h2>The user is READ OFF THE TOKEN, not asked for</h2>
 *
 * A person holding a single JWT cannot become somebody else by picking a name
 * in a combo, so the combo must not offer one. What it CAN do is say who this
 * token makes them, which is what the `sub` claim is for - and saying it is
 * worth more than it looks, because the whole of this session's confusion was
 * a token whose subject nobody had looked at.
 *
 * NOTHING IS VERIFIED HERE. The payload is decoded, not checked: the signature
 * is the participant's business and this class only wants a display name. A
 * token that will be refused still gets its `sub` read, which is exactly the
 * case where a person needs to see it.
 *
 * Author Claude/bentzn
 */
public final class FixedToken implements UserTokens_i {

    /** What to call a user whose token does not say. */
    public static final String STR_USER_UNKNOWN = "(the token names no subject)";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final transient TokenSource_i source;

    private final String idUser;


    /**
     * @param sourceNew the one credential, or null for an unauthenticated
     *        participant
     */
    public FixedToken(TokenSource_i sourceNew) {
        this.source = sourceNew;
        this.idUser = sourceNew == null ? "(no credential)" : strSubjectOf(sourceNew);
    }


    @Override
    public boolean canChoose() {
        return false;
    }


    @Override
    public List<String> lstUser() {
        return List.of(idUser);
    }


    @Override
    public TokenSource_i sourceFor(String idUserWanted) {
        return source;
    }


    @Override
    public String describe() {
        return source == null ? "none" : source.describe();
    }


    /**
     * @param sourceRead the credential to look inside
     * @return its `sub` claim, or a stand-in when it has none or cannot be read
     */
    private static String strSubjectOf(TokenSource_i sourceRead) {
        try {
            String[] arrPart = sourceRead.token().split("[.]");
            if (arrPart.length < 2)
                return STR_USER_UNKNOWN;

            byte[] arrByte = Base64.getUrlDecoder().decode(arrPart[1]);
            JsonNode node = MAPPER.readTree(new String(arrByte, StandardCharsets.UTF_8));
            String strSub = node.path("sub").asText("");
            return strSub.isBlank() ? STR_USER_UNKNOWN : strSub;
        }
        catch (RuntimeException | java.io.IOException ex) {
            return STR_USER_UNKNOWN;
        }
    }

}
