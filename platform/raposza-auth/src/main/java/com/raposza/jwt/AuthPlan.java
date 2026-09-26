// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import java.time.Duration;
import java.util.List;

/**
 * One description of how a participant authenticates, from which BOTH sides are
 * rendered: the participant's own configuration and the tokens presented to it.
 *
 * <h2>Why this type exists</h2>
 *
 * It exists because the two sides were authored separately and disagreed.
 * `AuthOverlay` has held `daml_ledger_api` in `STR_SCOPE_DEFAULT` since it was
 * written, and nothing had ever minted a token carrying it - the live test
 * hardcoded a shape of its own. Two authors of one contract is the defect,
 * and this record is the correction: the plan is the single source, and
 * whatever writes the participant's HOCON and whatever mints its tokens both
 * derive from this object rather than from each other's assumptions.
 *
 * <h2>Choosing a shape is not a preference</h2>
 *
 * {@link TokenShape#CUSTOM} authenticates on Canton 2.x and is REFUSED on
 * 3.5.11, measured both ways with the same minter. A 3.x participant reads such
 * a token as a standard JWT and rejects it for a missing scope; the custom claim
 * is never consulted. So the shapes available depend on the target generation,
 * which makes this a capability question rather than a configuration one, and a
 * caller offering a shape a target cannot verify is offering something that
 * cannot work.
 *
 * What is NOT known: whether 3.x selects its verification path from the token's
 * CONTENTS or from its own configuration. The evidence is consistent with both,
 * so CUSTOM against a 3.x target is UNMEASURED rather than REFUSED.
 *
 * <h2>Scope and audience are not exclusive, which was not expected</h2>
 *
 * A token carrying `scope` AND `aud` authenticated on 3.5.11, on a participant
 * configured with a secret alone and again with `target-scope` set. So an
 * audience beside a scope is permitted here and {@link TokenShape#SCOPE}'s own
 * javadoc already says so. An audience ALONE is a different shape and is
 * refused once the participant pins a target scope - which is the sense in
 * which the shapes remain mutually exclusive.
 *
 * Note that {@link TokenSpec#fromProfile} refuses a profile carrying both,
 * deliberately and on different grounds: a profile is a stored guess about a
 * participant nobody has asked, and there the ambiguity is worth rejecting.
 * This record describes a participant that is about to be STARTED from it, so
 * there is no guess to reject.
 *
 * @param shape which of the three Canton shapes the participant verifies
 * @param strUrlJwks where the participant reads the public JWKS, null for HMAC
 * @param strSecretHmac the shared secret, null for JWKS
 * @param strAudience the `aud` the participant compares against; required for
 *        AUDIENCE, permitted beside SCOPE, otherwise null
 * @param strScope the `scope` the participant requires; required for SCOPE
 * @param ttlToken how long a minted token is valid for
 *
 * Author Claude/bentzn
 */
public record AuthPlan(TokenShape shape, String strUrlJwks, String strSecretHmac,
        String strAudience, String strScope, Duration ttlToken) {

    /**
     * TWENTY-FOUR HOURS, DECIDED AND MEASURED.
     *
     * The operator chose this default and it is configurable. It is also
     * ABOVE a cap nobody has located: 3.5.11 refused a seven-day token with
     * `token lifetime too long` while `JwtJwks.maxTokenLife` defaults to
     * `Duration.Inf`, so the limit comes from somewhere unread.
     *
     * On 3.5.11 a 24 h token minted at this value
     * was accepted by a participant configured through `AuthOverlay.of`,
     * which renders `max-token-life` from this same lifetime. The same run
     * refused an unauthenticated call and a correctly signed token carrying
     * the wrong audience, so the acceptance is evidence rather than a
     * participant waving everything through.
     */
    public static final Duration TTL_DEFAULT = Duration.ofHours(24);

    /** The conventional scope for a scope-based participant. */
    public static final String STR_SCOPE_DEFAULT = "daml_ledger_api";

    /** The audience convention Canton's own documentation uses. */
    public static final String STR_AUDIENCE_PREFIX =
            "https://daml.com/jwt/aud/participant/";


    public AuthPlan {
        if (shape == null)
            throw new IllegalArgumentException("a shape is required");
        if (ttlToken == null || ttlToken.isZero() || ttlToken.isNegative())
            throw new IllegalArgumentException("ttlToken must be positive");

        boolean flagJwks = !isBlank(strUrlJwks);
        boolean flagHmac = !isBlank(strSecretHmac);
        if (flagJwks == flagHmac) {
            throw new IllegalArgumentException(
                    "exactly one of a JWKS url and an HMAC secret is required");
        }

        if (shape == TokenShape.AUDIENCE && isBlank(strAudience))
            throw new IllegalArgumentException("an audience is required for an audience-based plan");
        if (shape == TokenShape.SCOPE && isBlank(strScope))
            throw new IllegalArgumentException("a scope is required for a scope-based plan");
    }


    /**
     * Scope-based, verified against a JWKS. The shape 3.5.11 was measured to
     * accept.
     *
     * @param strUrlJwks where the participant reads the public JWKS
     * @return the plan, at the default lifetime
     */
    public static AuthPlan ofJwksScope(String strUrlJwks) {
        return new AuthPlan(TokenShape.SCOPE, strUrlJwks, null, null, STR_SCOPE_DEFAULT,
                TTL_DEFAULT);
    }


    /**
     * Audience-based, verified against a JWKS.
     *
     * @param strUrlJwks where the participant reads the public JWKS
     * @param nameParticipant the participant node name, which the convention
     *        appends to {@link #STR_AUDIENCE_PREFIX}
     * @return the plan, at the default lifetime
     */
    public static AuthPlan ofJwksAudience(String strUrlJwks, String nameParticipant) {
        return new AuthPlan(TokenShape.AUDIENCE, strUrlJwks, null,
                audienceFor(nameParticipant), null, TTL_DEFAULT);
    }


    /**
     * The symmetric fallback, scope-based. Its value is that it is MEASURED on
     * 3.4.11 and 3.5.11 and needs nothing serving a JWKS, which is what makes
     * it usable from a probe.
     *
     * @param strSecret the shared secret
     * @return the plan, at the default lifetime
     */
    public static AuthPlan ofUnsafeHmac256Scope(String strSecret) {
        return new AuthPlan(TokenShape.SCOPE, null, strSecret, null, STR_SCOPE_DEFAULT,
                TTL_DEFAULT);
    }


    /**
     * The legacy custom claim over a shared secret. Measured working on 2.x
     * and measured REFUSED on 3.5.11. Callers must check the target's
     * declared capability before offering it.
     *
     * @param strSecret the shared secret
     * @return the plan, at the default lifetime
     */
    public static AuthPlan ofUnsafeHmac256Custom(String strSecret) {
        return new AuthPlan(TokenShape.CUSTOM, null, strSecret, null, null, TTL_DEFAULT);
    }


    /**
     * @param ttl the lifetime to mint at
     * @return the same plan at a different lifetime
     */
    public AuthPlan withTtl(Duration ttl) {
        return new AuthPlan(shape, strUrlJwks, strSecretHmac, strAudience, strScope, ttl);
    }


    /**
     * @param strAudienceNew the audience to carry beside the scope
     * @return the same plan carrying an audience
     */
    public AuthPlan withAudience(String strAudienceNew) {
        return new AuthPlan(shape, strUrlJwks, strSecretHmac, strAudienceNew, strScope, ttlToken);
    }


    /**
     * The token side of this plan, for a caller that needs no party rights -
     * every SCOPE and AUDIENCE token, since those shapes carry none.
     *
     * @param strUser the ledger user id, minted as `sub`
     * @return a spec that satisfies this plan by construction
     */
    public TokenSpec specFor(String strUser) {
        return specFor(strUser, List.of(), List.of(), false);
    }


    /**
     * The token side of this plan, with party rights. The rights are carried
     * only by {@link TokenShape#CUSTOM}; on the other two shapes they are
     * accepted and ignored, because there they live on the ledger USER rather
     * than in the token.
     *
     * @param strUser the ledger user id, minted as `sub`
     * @param lstActAs parties the token may act as; CUSTOM only
     * @param lstReadAs parties the token may read as; CUSTOM only
     * @param flagAdmin whether the token carries admin rights; CUSTOM only
     * @return a spec that satisfies this plan by construction
     */
    public TokenSpec specFor(String strUser, List<String> lstActAs, List<String> lstReadAs,
            boolean flagAdmin) {
        if (isBlank(strUser))
            throw new IllegalArgumentException("a user is required");

        return new TokenSpec(shape, strUser, null, strAudience, strScope, ttlToken,
                lstActAs, lstReadAs, flagAdmin, strUser, null, null);
    }


    /**
     * @return true when the participant verifies against a JWKS
     */
    public boolean flagJwks() {
        return !isBlank(strUrlJwks);
    }


    /**
     * @param nameParticipant the participant node name
     * @return the conventional audience for it
     */
    public static String audienceFor(String nameParticipant) {
        if (isBlank(nameParticipant))
            throw new IllegalArgumentException("a participant name is required");
        return STR_AUDIENCE_PREFIX + nameParticipant;
    }


    static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

}
