// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * The auth settings of one connection profile, as read from its settings file.
 *
 * Separate from HostProfile on purpose. The catalogue is shared with DevTools3
 * and its line format is fixed at six or eight fields; a token does not belong
 * in a file that gets copied between machines and pasted into chat windows, and
 * widening that format would break the compatibility the format exists for.
 *
 * @param mode how the token is obtained
 * @param strToken the token itself, TOKEN mode only
 * @param fileJwks JWKS file holding private key material, JWKS mode only
 * @param idKey key id within that file, null to take the first private key
 * @param strSubject the `sub` claim, normally the ledger user id
 * @param strScope scope override, null to take the profile's
 * @param strAudience audience override, null to take the profile's
 * @param lstActAs CUSTOM shape only: parties the token may act as
 * @param lstReadAs CUSTOM shape only: parties the token may read as
 * @param flagAdmin CUSTOM shape only: whether the token claims admin
 * @param idApplication CUSTOM shape only, may be null
 * @param idLedger CUSTOM shape only, may be null
 * @param idParticipant CUSTOM shape only, may be null
 * @param ttl lifetime of a minted token; ignored in TOKEN mode, where the
 *        expiry is whatever the pasted token already carries
 *
 * Author Claude/bentzn
 */
public record ProfileAuth(AuthMode mode, String strToken, Path fileJwks, String idKey,
        String strSubject, String strScope, String strAudience, List<String> lstActAs,
        List<String> lstReadAs, boolean flagAdmin, String idApplication, String idLedger,
        String idParticipant, Duration ttl) {

    /**
     * One thousand Julian years, by decision rather than by accident.
     *
     * A minted token that expires mid-session produces an UNAUTHENTICATED in
     * the middle of a diagnostic, which is the worst moment for one. The trade
     * is a credential with no practical expiry sitting in a file: the settings
     * file is written 0600 where the filesystem allows it, and it lives under
     * ~/.raposza, which the publication gate already excludes from anything
     * that leaves the machine.
     */
    public static final Duration TTL_DEFAULT = Duration.ofSeconds(31_557_600_000L);

    /** What a profile with no settings file gets. */
    public static final ProfileAuth NONE = new ProfileAuth(AuthMode.NONE, null, null, null, null,
            null, null, List.of(), List.of(), false, null, null, null, TTL_DEFAULT);


    public ProfileAuth {
        if (mode == null)
            throw new TokenException("no auth mode");
        if (ttl == null || ttl.isZero() || ttl.isNegative())
            throw new TokenException("ttl must be positive");

        lstActAs = lstActAs == null ? List.of() : List.copyOf(lstActAs);
        lstReadAs = lstReadAs == null ? List.of() : List.copyOf(lstReadAs);
    }


    /** @return true when this profile sends no authorization header */
    public boolean isNone() {
        return mode == AuthMode.NONE;
    }

}
