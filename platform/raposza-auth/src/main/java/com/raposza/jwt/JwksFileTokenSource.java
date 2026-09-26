// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import com.raposza.api.TokenSource_i;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Mints tokens from a local JWKS file holding private key material.
 *
 * JWK parsing is delegated to Nimbus rather than hand-rolled. Decoding RSA CRT
 * parameters and dispatching on key type is commodity, security-critical work
 * with a long tail of encoding edge cases, and a bespoke parser here would be
 * a maintenance liability for no gain.
 *
 * Renewal is internal: a token is reused until shortly before it expires and
 * then re-minted. Callers ask per request and do not inspect the result.
 *
 * Author Claude/bentzn
 */
public final class JwksFileTokenSource implements TokenSource_i {

    /** Re-mint this long before expiry, so a token never expires mid-flight. */
    private static final Duration MARGIN = Duration.ofSeconds(30);

    private final JWK jwk;
    private final TokenSpec spec;
    private final Path fileJwks;
    private final JwtMinter minter;

    private String strToken;
    private Instant instRenewAt = Instant.MIN;


    /**
     * @param fileJwks a JWKS file containing at least one private key
     * @param idKey the key id to use, or null to take the first private key
     * @param spec what the minted tokens should contain
     * @throws TokenException when the file cannot be read or holds no usable key
     */
    public JwksFileTokenSource(Path fileJwks, String idKey, TokenSpec spec) {
        if (fileJwks == null)
            throw new TokenException("no JWKS file supplied");
        if (spec == null)
            throw new TokenException("no token spec supplied");
        if (!Files.isReadable(fileJwks))
            throw new TokenException("JWKS file is not readable: " + fileJwks);

        this.fileJwks = fileJwks;
        this.spec = spec;
        this.jwk = selectKey(fileJwks, idKey);
        this.minter = new JwtMinter(jwk);
    }


    @Override
    public synchronized String token() {
        if (strToken == null || Instant.now().isAfter(instRenewAt)) {
            strToken = minter.mint(spec);
            instRenewAt = Instant.now().plus(spec.ttl()).minus(MARGIN);
        }
        return strToken;
    }


    /**
     * Never returns key material or a token: this reaches the status bar and
     * the logs. File NAME only, key id and type only, shape only.
     */
    @Override
    public String describe() {
        StringBuilder bld = new StringBuilder();
        bld.append(fileJwks.getFileName());
        bld.append(", kid ").append(JwtMinter.describeKey(jwk));
        bld.append(", ").append(shapeLabel());
        if (!TokenSpec.isBlank(spec.strSubject()))
            bld.append(", sub ").append(spec.strSubject());
        return bld.toString();
    }


    private String shapeLabel() {
        switch (spec.shape()) {
            case AUDIENCE:
                return "audience-based (" + spec.strAudience() + ")";
            case SCOPE:
                return "scope-based (" + spec.strScope() + ")";
            default:
                return "custom Daml claim";
        }
    }


    private static JWK selectKey(Path fileJwks, String idKey) {
        JWKSet set;
        try {
            set = JWKSet.parse(Files.readString(fileJwks));
        }
        catch (IOException ex) {
            throw new TokenException("could not read JWKS file: " + fileJwks, ex);
        }
        catch (ParseException ex) {
            throw new TokenException("not a valid JWKS file: " + fileJwks, ex);
        }

        if (idKey != null && !idKey.isBlank()) {
            JWK jwk = set.getKeyByKeyId(idKey);
            if (jwk == null)
                throw new TokenException("no key with id '" + idKey + "' in " + fileJwks);
            if (!jwk.isPrivate())
                throw new TokenException("key '" + idKey + "' in " + fileJwks + " is public-only");
            return jwk;
        }

        List<JWK> lstKey = set.getKeys();
        for (int idx = 0; idx < lstKey.size(); idx++) {
            if (lstKey.get(idx).isPrivate())
                return lstKey.get(idx);
        }
        throw new TokenException("no private key in " + fileJwks
                + " (" + lstKey.size() + " key(s), all public-only)");
    }

}
