// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import com.raposza.api.TokenSource_i;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.util.List;

/**
 * Wraps a token the operator pasted in. No key material, no renewal - when it
 * expires the operator pastes another.
 *
 * Author Claude/bentzn
 */
public final class StaticTokenSource implements TokenSource_i {

    private final String strToken;
    private final String strDescription;


    /**
     * @param strToken the bearer token, without the "Bearer " prefix
     * @throws TokenException when the token is absent
     */
    public StaticTokenSource(String strToken) {
        if (strToken == null || strToken.isBlank())
            throw new TokenException("no token supplied");
        this.strToken = strToken.trim();
        this.strDescription = describeToken(this.strToken);
    }


    @Override
    public String token() {
        return strToken;
    }


    @Override
    public String describe() {
        return strDescription;
    }


    /**
     * Summarises a token for the status bar WITHOUT reproducing any part of it.
     *
     * Only iss, aud and sub are reported, and only when the token parses. An
     * unparseable token yields a fixed string rather than a prefix or a length,
     * because both leak.
     */
    private static String describeToken(String strToken) {
        try {
            JWTClaimsSet claims = SignedJWT.parse(strToken).getJWTClaimsSet();
            StringBuilder bld = new StringBuilder("pasted token");

            String iss = claims.getIssuer();
            if (iss != null && !iss.isBlank())
                bld.append(", iss ").append(iss);

            List<String> lstAud = claims.getAudience();
            if (lstAud != null && !lstAud.isEmpty())
                bld.append(", aud ").append(String.join(" ", lstAud));

            String sub = claims.getSubject();
            if (sub != null && !sub.isBlank())
                bld.append(", sub ").append(sub);

            if (claims.getExpirationTime() != null)
                bld.append(", expires ").append(claims.getExpirationTime().toInstant());

            return bld.toString();
        }
        catch (ParseException ex) {
            return "pasted token (not a readable JWT)";
        }
    }

}
