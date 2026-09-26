// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.idp;

import com.raposza.jwt.AuthPlan;
import com.raposza.jwt.TokenShape;
import com.raposza.jwt.TokenSpec;

import java.time.Duration;

/**
 * One registered client of the test identity provider.
 *
 * A registration is what a real OAuth deployment would create through an admin
 * API and what Dex or Hydra would take from a configuration file. Here it is a
 * record handed to {@link TestIdp} before it starts, which is the whole reason
 * this module exists rather than a staged binary: nothing has to be obtained,
 * migrated or registered before the sandbox works.
 *
 * <h2>The shape is fixed at registration, not at request time</h2>
 *
 * The client presents credentials; it does not ask for claims. What comes back
 * is decided here - the subject, and exactly one of an audience and a scope.
 * A requested scope is IGNORED rather than honoured, because a token endpoint
 * that mints what it is asked for is an authorisation hole with a test label
 * on it, and because scribe's `--pipeline-oauth-scope` defaults to a value
 * (`Default`) that no participant is configured against.
 *
 * <h2>Exactly one of an audience and a scope</h2>
 *
 * Canton accepts one shape and presenting the wrong one fails authentication in
 * a way that reads like a key problem - see {@link TokenShape}. Both set, or
 * neither, is refused here rather than resolved by precedence.
 *
 * <h2>The lifetime is deliberately short</h2>
 *
 * {@link #TTL_DEFAULT} is five minutes, against {@code ProfileAuth.TTL_DEFAULT}
 * of a thousand years. The two are not in disagreement: a token pasted into a
 * GUI profile must not expire mid-diagnostic, and a token this endpoint issues
 * is renewable by the client that asked for it. scribe re-requests one minute
 * before expiry - `--pipeline-oauth-preemptexpiry`, default PT1M - so a short
 * lifetime is what makes renewal a path that actually runs rather than one
 * that is argued about.
 *
 * @param strClientId the client identifier, matched verbatim
 * @param strClientSecret the shared secret; NEVER logged and never in toString
 * @param strSubject the `sub` claim, which on an audience or scope token is the
 *        ledger user id the participant derives rights from
 * @param strAudience the `aud` claim, or null for a scope-based client
 * @param strScope the `scope` claim, or null for an audience-based client
 * @param ttl how long an issued token is valid for
 *
 * Author Claude/bentzn
 */
public record IdpClient(String strClientId, String strClientSecret, String strSubject,
        String strAudience, String strScope, Duration ttl) {

    /** Short on purpose; see the type comment. */
    public static final Duration TTL_DEFAULT = Duration.ofMinutes(5);


    public IdpClient {
        require(strClientId, "a client id");
        require(strClientSecret, "a client secret");
        require(strSubject, "a subject");
        if (ttl == null || ttl.isZero() || ttl.isNegative())
            throw new IdpException("the token lifetime must be positive");

        boolean flagAudience = !isBlank(strAudience);
        boolean flagScope = !isBlank(strScope);
        if (flagAudience && flagScope) {
            throw new IdpException("client '" + strClientId + "' carries both an audience and a"
                    + " scope; Canton accepts one shape, not both");
        }
        if (!flagAudience && !flagScope) {
            throw new IdpException("client '" + strClientId + "' carries neither an audience nor a"
                    + " scope, so nothing it is issued would satisfy a participant");
        }
    }


    /**
     * An audience-based client, which is what this project's participants are
     * configured for - `AuthOverlay.ofJwksAudience`.
     *
     * @param strClientId the client identifier
     * @param strClientSecret the shared secret
     * @param strSubject the ledger user id the token speaks for
     * @param strAudience the value the participant compares `aud` against
     * @return the registration, with the default lifetime
     */
    public static IdpClient ofAudience(String strClientId, String strClientSecret,
            String strSubject, String strAudience) {
        return new IdpClient(strClientId, strClientSecret, strSubject, strAudience, null,
                TTL_DEFAULT);
    }


    /**
     * @param strClientId the client identifier
     * @param strClientSecret the shared secret
     * @param strSubject the ledger user id the token speaks for
     * @param strScope the value the participant compares `scope` against
     * @return the registration, with the default lifetime
     */
    public static IdpClient ofScope(String strClientId, String strClientSecret, String strSubject,
            String strScope) {
        return new IdpClient(strClientId, strClientSecret, strSubject, null, strScope,
                TTL_DEFAULT);
    }


    /**
     * THE SEAM, on this side of it. A registration derived from the same
     * {@link AuthPlan} the participant's own configuration is rendered from.
     *
     * <h2>Why a factory rather than a caller assembling it</h2>
     *
     * `AuthOverlay` held one scope and the live tests minted another, and no
     * unit test could see the disagreement because each side was authored
     * separately and neither was wrong on its own. A client built from the
     * plan carries the
     * plan's audience or scope and the plan's lifetime, so `tokenSpec` and
     * {@link AuthPlan#specFor} agree by construction rather than by review.
     *
     * <h2>Two plans are refused rather than resolved</h2>
     *
     * A CUSTOM plan, because this endpoint has no custom-claim shape to issue -
     * that is 2.x's arrangement, and 3.5.11 was measured refusing it.
     *
     * A plan carrying an audience BESIDE a scope, which {@link AuthPlan}
     * permits and which was measured authenticating. It is refused HERE
     * because a
     * registration mints one shape, so such a plan has two token sides and no
     * single answer. Register a second client if a run ever needs both; do not
     * add a precedence rule, which is how one side quietly stops matching the
     * other.
     *
     * @param strClientId the client identifier
     * @param strClientSecret the shared secret
     * @param strSubject the ledger user id the token speaks for
     * @param plan what the participant will verify
     * @return the registration, at the plan's own lifetime
     * @throws IdpException when the plan has no shape this endpoint can issue
     */
    public static IdpClient ofPlan(String strClientId, String strClientSecret, String strSubject,
            AuthPlan plan) {
        if (plan == null)
            throw new IdpException("a plan is required");
        if (plan.shape() == TokenShape.CUSTOM) {
            throw new IdpException("a CUSTOM plan has no shape this endpoint issues: the custom"
                    + " claim is 2.x's arrangement and 3.5.11 refuses it");
        }

        boolean flagAudience = !isBlank(plan.strAudience());
        boolean flagScope = !isBlank(plan.strScope());
        if (flagAudience && flagScope) {
            throw new IdpException("the plan carries an audience AND a scope, which a participant"
                    + " accepts and one registration cannot mint: it issues one shape. Register"
                    + " a second client rather than choosing between them");
        }

        if (flagAudience) {
            return new IdpClient(strClientId, strClientSecret, strSubject, plan.strAudience(),
                    null, plan.ttlToken());
        }
        return new IdpClient(strClientId, strClientSecret, strSubject, null, plan.strScope(),
                plan.ttlToken());
    }


    /**
     * @param ttlNew the lifetime of an issued token
     * @return a copy carrying it
     */
    public IdpClient withTtl(Duration ttlNew) {
        return new IdpClient(strClientId, strClientSecret, strSubject, strAudience, strScope,
                ttlNew);
    }


    /**
     * What a token issued to this client contains.
     *
     * @param strIssuer the `iss` claim, which only this provider can supply
     *        because its port is not known until it binds; may be null
     * @return the spec, of the one shape this client was registered for
     */
    public TokenSpec tokenSpec(String strIssuer) {
        if (!isBlank(strAudience)) {
            return new TokenSpec(TokenShape.AUDIENCE, strSubject, strIssuer, strAudience, null,
                    ttl, null, null, false, null, null, null);
        }
        return new TokenSpec(TokenShape.SCOPE, strSubject, strIssuer, null, strScope, ttl, null,
                null, false, null, null, null);
    }


    /**
     * @return true when this client is issued audience-based tokens
     */
    public boolean isAudienceBased() {
        return !isBlank(strAudience);
    }


    /**
     * Safe for a log line: who the client is and what it gets, never the
     * secret.
     *
     * @return the description
     */
    public String describe() {
        return strClientId + " -> sub " + strSubject + ", "
                + (isAudienceBased() ? "audience-based (" + strAudience + ")"
                        : "scope-based (" + strScope + ")")
                + ", ttl " + ttl;
    }


    /**
     * OVERRIDDEN, and that is the point: a record's generated toString prints
     * every component, and one of them is a shared secret. This type is
     * routinely put into a failure message.
     */
    @Override
    public String toString() {
        return "idp client: " + describe();
    }


    private static boolean isBlank(String strValue) {
        return strValue == null || strValue.isBlank();
    }


    private static void require(String strValue, String strWhat) {
        if (isBlank(strValue))
            throw new IdpException(strWhat + " is required");
    }

}
