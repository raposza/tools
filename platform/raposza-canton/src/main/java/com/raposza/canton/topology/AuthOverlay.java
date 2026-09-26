// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import java.time.Duration;

import com.raposza.canton.install.VersionId;
import com.raposza.jwt.AuthPlan;

/**
 * The overlay that puts authentication on a 3.x sandbox's Ledger API.
 *
 * Like {@link StorageOverlay} it overlays rather than replaces: the `sandbox`
 * subcommand processes its own configuration from inside the jar, and this
 * restates one key over it.
 *
 * <h2>The type name changed between 2.x and 3.x, and only the type name</h2>
 *
 * The sealed set in `com.digitalasset.canton.config.AuthServiceConfig`, read
 * from the 3.4.11 and 3.5.11 jars:
 *
 * <pre>
 * JwtJwks   JwtRs256Crt   JwtEs256Crt   JwtEs512Crt   UnsafeJwtHmac256   Wildcard
 * </pre>
 *
 * There is no `JwtRs256Jwks`, so the 2.x spelling `jwt-rs-256-jwks` does not
 * parse on 3.x. It is kept here as a constant because a name that fails is
 * worth naming: a configuration carried across from a 2.x participant will
 * fail at start-up, and the message will be about an unknown type rather than
 * about the migration that caused it.
 *
 * The FIELDS did not change. `JwtJwks` was read with javap on both 3.x jars
 * and carries `url`, `targetAudience` and `targetScope` exactly as the 2.x
 * type did, plus `privileged`, `accessLevel`, `users` and `maxTokenLife`,
 * which have defaults and are not set here. So a working 2.9 auth-services
 * block moves to 3.x by renaming the type and nothing else.
 *
 * <h2>What is measured and what is not</h2>
 *
 * `jwt-jwks` is MEASURED. A 3.5.11 sandbox came up with it in its overlay -
 * Canton refuses to parse an auth-service type it does not know, so the start
 * settled the spelling.
 *
 * 3.4.11 has NOT been started with it. The class list is identical, which is an
 * argument rather than a measurement. `unsafe-jwt-hmac-256` is measured on
 * 3.4.11 open source, 3.4.11 enterprise and 3.5.11, and remains the control:
 * when a JWKS stack will not start, swapping to it separates a wrong name from
 * a wrong key.
 *
 * A stack that comes up confirms the NAME and not the URL scheme. `url` is a
 * NonEmptyString, so `file:` parses whatever Canton later does with it, and
 * the scheme is first exercised when a token is verified. Confirming a `file:`
 * JWKS on 3.x therefore needs a Ledger API call, which this module has no
 * client for.
 *
 * Author Claude/bentzn
 */
public final class AuthOverlay {

    /** 3.x. JWKS-backed RS256 and ES256 verification. */
    public static final String STR_TYPE_JWKS = "jwt-jwks";

    /** 2.x only. Present so the difference has a name; it will NOT parse on 3.x. */
    public static final String STR_TYPE_JWKS_2X = "jwt-rs-256-jwks";

    /** Symmetric HS256. Measured on 3.4.11 and 3.5.11. */
    public static final String STR_TYPE_HMAC_256 = "unsafe-jwt-hmac-256";

    /**
     * RS256 against a public key read from an X.509 certificate file. Both
     * PEM and DER are accepted by Canton; nothing here inspects the file.
     */
    public static final String STR_TYPE_RS256_CRT = "jwt-rs-256-crt";

    /** ES256, ECDSA over P-256. */
    public static final String STR_TYPE_ES256_CRT = "jwt-es-256-crt";

    /** ES512, ECDSA over P-521 - not P-512, whatever the name says. */
    public static final String STR_TYPE_ES512_CRT = "jwt-es-512-crt";

    /** No check at all. */
    public static final String STR_TYPE_WILDCARD = "wildcard";

    /**
     * The audience convention Canton's own documentation uses. It is a plain
     * string on both sides - the participant compares it to the token's `aud`
     * - so the only requirement is that the minter and the participant agree.
     */
    public static final String STR_AUDIENCE_PREFIX = "https://daml.com/jwt/aud/participant/";

    /** The conventional scope for a scope-based participant. */
    public static final String STR_SCOPE_DEFAULT = "daml_ledger_api";

    /**
     * The oldest Canton on which `max-token-life` is written into the overlay.
     *
     * Measured. One config load per version, the same rendered `auth.conf`
     * fed to each jar, reading only whether the key was refused:
     *
     * <pre>
     * refuse   3.4.4 3.4.5 3.4.6 3.4.7 3.4.8 3.4.9 3.4.10 3.4.11 3.5.1
     * accept   3.5.2 3.5.3 3.5.4 3.5.5 3.5.6 3.5.10 3.5.11 3.5.12
     * </pre>
     *
     * So the key is not a property of the 3.x FAMILY. The whole 3.4 line
     * refuses it and so does 3.5.1; it arrives with 3.5.2.
     *
     * <h2>The floor is 3.5.6, not 3.5.2, on purpose</h2>
     *
     * 3.5.2 to 3.5.5 ACCEPT the key; whether they ENFORCE it is not measured,
     * and a key that is read is not a key that is honoured. 3.5.6 is the
     * lowest version on which the whole path - key written, long token minted,
     * token accepted - has been confirmed. The two directions do not cost the
     * same. Too high, and {@link #ttlTokenFor} mints below
     * {@link #TTL_CEILING_BELOW_FLOOR}, so every token is still accepted and
     * only its lifetime is shorter. Too low, on a version that accepts the key
     * and ignores it, and the mint issues long tokens the participant refuses.
     * None of 3.5.2 to 3.5.5 is carried by an SDK bundle; each arrives only as
     * an explicitly added component.
     */
    public static final VersionId VER_FLOOR_TOKEN_LIFE = VersionId.of(3, 5, 6);

    /**
     * The lifetime ceiling a participant BELOW the floor enforces by itself.
     *
     * Measured on Canton 3.4.9 Enterprise and again on 3.5.11, by presenting
     * tokens of known lifetime to `GET /v2/users` on the participant's own
     * JSON Ledger API: the ceiling is five minutes, exactly. What arrived at
     * 3.5.6 is the ability to OVERRIDE it; the default is five minutes on
     * both lines.
     *
     * THE CHECK IS ON REMAINING LIFETIME, NOT ELAPSED TIME. Canton compares the
     * token's `exp` against now plus this ceiling, which is why a 24 h token is
     * refused on its first call rather than working for five minutes and then
     * failing. That is what makes {@link #TTL_BELOW_FLOOR} workable at all: a
     * client that re-mints stays inside the window indefinitely.
     */
    public static final Duration TTL_CEILING_BELOW_FLOOR = Duration.ofSeconds(300);

    /**
     * What to actually mint below the floor, and it is DELIBERATELY under the
     * ceiling.
     *
     * A token minted at exactly {@link #TTL_CEILING_BELOW_FLOOR} sits on the
     * line, where clock skew between minter and participant, the `nbf` this
     * project writes, and the latency of the call itself all push the wrong
     * way. Sixty seconds of margin costs nothing: scribe's `preemptExpiry`
     * defaults to PT1M, so it asks for a new token with three minutes still on
     * the clock, measured on a deployment where this arrangement runs.
     */
    public static final Duration TTL_BELOW_FLOOR = Duration.ofSeconds(240);



    private final String strType;
    private final String strUrl;
    private final String strAudience;
    private final String strScope;
    private final String strSecret;

    /**
     * The certificate file for the three `-crt` types.
     *
     * UNMEASURED BESIDE A TARGET. `target-audience` and `target-scope` are
     * measured on `JwtJwks` and are rendered here for the certificate types
     * too, on the argument that they share a trait - which is an argument
     * and not a measurement. Canton refuses an unknown key by path, file and
     * line, so a stack that starts settles it and one that does not names
     * the key. Do not promote this note without a start behind it.
     */
    private final String strCertificate;

    /**
     * The participant's own ceiling on a token's lifetime, or null to leave the
     * key out.
     *
     * Measured, and it is not what reflection said. `maxTokenLife`
     * reads `Duration.Inf` on all six `AuthServiceConfig` subtypes - that is
     * the CONSTRUCTOR default. With the key absent a 5 m token is accepted and
     * a 487 s token is refused; with `max-token-life = 24h` set, 24 h is
     * accepted. So Canton's configuration layer supplies a finite default of
     * its own that the case class never mentions, and `com.daml.jwt
     * .JwtVerifier` is what enforces it.
     *
     * The lesson is wider than this field: reading `apply$default$N` gives what
     * the constructor would use, NOT what Canton uses when a key is missing.
     * That mistake has been made here twice, reading case-class defaults as
     * though they were configuration defaults.
     */
    private final String strMaxTokenLife;


    private AuthOverlay(String strType, String strUrl, String strAudience, String strScope,
            String strSecret) {
        this(strType, strUrl, strAudience, strScope, strSecret, null);
    }


    private AuthOverlay(String strType, String strUrl, String strAudience, String strScope,
            String strSecret, String strMaxTokenLife) {
        this.strType = strType;
        this.strUrl = strUrl;
        this.strAudience = strAudience;
        this.strScope = strScope;
        this.strSecret = strSecret;
        this.strMaxTokenLife = strMaxTokenLife;
        this.strCertificate = null;
    }


    private AuthOverlay(String strType, String strCertificate, String strAudience,
            String strScope, String strMaxTokenLife, boolean flagCertificate) {
        this.strType = strType;
        this.strUrl = null;
        this.strAudience = strAudience;
        this.strScope = strScope;
        this.strSecret = null;
        this.strMaxTokenLife = strMaxTokenLife;
        this.strCertificate = strCertificate;
    }


    /**
     * Audience-based tokens verified against a JWKS.
     *
     * @param strUrlJwks where the participant reads the public JWKS
     * @param strAudience the value the token's `aud` claim must carry
     * @return the overlay
     */
    public static AuthOverlay ofJwksAudience(String strUrlJwks, String strAudience) {
        return ofJwksAudience(CantonLine.V3X, strUrlJwks, strAudience);
    }


    /**
     * Audience-based tokens verified against a JWKS, on a named line.
     *
     * @param line which generation the participant belongs to; it decides the
     *        type name and nothing else, because the fields did not change
     * @param strUrlJwks where the participant reads the public JWKS
     * @param strAudience the value the token's `aud` claim must carry
     * @return the overlay
     */
    public static AuthOverlay ofJwksAudience(CantonLine line, String strUrlJwks,
            String strAudience) {
        if (line == null)
            throw new IllegalArgumentException("a line is required");
        require(strUrlJwks, "a JWKS url");
        require(strAudience, "an audience");
        return new AuthOverlay(line.strTypeJwks(), strUrlJwks, strAudience, null, null);
    }


    /**
     * Scope-based tokens verified against a JWKS.
     *
     * @param strUrlJwks where the participant reads the public JWKS
     * @param strScope the value the token's `scope` claim must carry
     * @return the overlay
     */
    public static AuthOverlay ofJwksScope(String strUrlJwks, String strScope) {
        return ofJwksScope(CantonLine.V3X, strUrlJwks, strScope);
    }


    /**
     * Scope-based tokens verified against a JWKS, on a named line.
     *
     * @param line which generation the participant belongs to
     * @param strUrlJwks where the participant reads the public JWKS
     * @param strScope the value the token's `scope` claim must carry
     * @return the overlay
     */
    public static AuthOverlay ofJwksScope(CantonLine line, String strUrlJwks, String strScope) {
        if (line == null)
            throw new IllegalArgumentException("a line is required");
        require(strUrlJwks, "a JWKS url");
        require(strScope, "a scope");
        return new AuthOverlay(line.strTypeJwks(), strUrlJwks, null, strScope, null);
    }


    /**
     * The symmetric fallback. Its value here is that it is MEASURED: when a
     * JWKS stack will not start, swapping to this separates a wrong type name
     * from a wrong key.
     *
     * @param strSecret the shared secret
     * @return the overlay
     */
    public static AuthOverlay ofUnsafeHmac256(String strSecret) {
        require(strSecret, "a secret");
        return new AuthOverlay(STR_TYPE_HMAC_256, null, null, null, strSecret);
    }


    /**
     * The symmetric fallback, pinning a scope as well.
     *
     * `target-scope` on this type was INFERRED from `JwtJwks`, where javap
     * measured it, and is now measured here: a 3.5.11 stack started with it,
     * and an audience-only token that the secret-only stack had ACCEPTED was
     * refused once it was present, with `Scope doesn't match the target value`
     * in the log. That second half is the part a start alone could never say -
     * a stack that comes up proves every key parsed, not that any key is read.
     *
     * @param strSecret the shared secret
     * @param strScope the value a token's `scope` claim must carry
     * @return the overlay
     */
    public static AuthOverlay ofUnsafeHmac256(String strSecret, String strScope) {
        require(strSecret, "a secret");
        require(strScope, "a scope");
        return new AuthOverlay(STR_TYPE_HMAC_256, null, null, strScope, strSecret);
    }


    /**
     * THE SEAM. Renders the participant's side of an {@link AuthPlan}, whose
     * other side is the token minted from the same object.
     *
     * This exists because the two were written separately and disagreed. This
     * class has held `daml_ledger_api` in {@link #STR_SCOPE_DEFAULT} since it
     * was written and nothing ever minted a token carrying it; the live test
     * built a shape of its own. No unit test could see the disagreement, and
     * finding it took a live run.
     *
     * <h2>An audience beside a scope is deliberately not rendered</h2>
     *
     * On a SCOPE plan the participant pins the scope and the audience is a
     * TOKEN-side value. That is the arrangement measured working on 3.5.11,
     * twice - a token carrying both authenticated against a participant that
     * pinned only the scope. Rendering `target-audience` as well would
     * configure a participant nobody has started.
     *
     * <h2>One case is refused rather than guessed</h2>
     *
     * A CUSTOM plan over a JWKS is how 2.x verifies custom-claim tokens, with
     * neither a target-audience nor a target-scope, and no stack here has ever
     * been started that way. The message says UNMEASURED in those words,
     * because a reader needs the difference between a shape that was rejected
     * and one nobody has tried.
     *
     * @param version which Canton the participant runs, which decides both the
     *        JWKS type name and whether a lifetime ceiling is rendered
     * @param plan what the participant must verify
     * @return the overlay
     */
    public static AuthOverlay of(VersionId version, AuthPlan plan) {
        if (version == null)
            throw new IllegalArgumentException("a version is required");
        if (plan == null)
            throw new IllegalArgumentException("a plan is required");

        CantonLine line = CantonLine.ofMajor(version.major());
        String strLife = strLifeFor(version, plan.ttlToken());

        if (!plan.flagJwks()) {
            return new AuthOverlay(STR_TYPE_HMAC_256, null, null, plan.strScope(),
                    plan.strSecretHmac(), strLife);
        }

        switch (plan.shape()) {
            case SCOPE:
                require(plan.strScope(), "a scope");
                return new AuthOverlay(line.strTypeJwks(), plan.strUrlJwks(), null,
                        plan.strScope(), null, strLife);
            case AUDIENCE:
                require(plan.strAudience(), "an audience");
                return new AuthOverlay(line.strTypeJwks(), plan.strUrlJwks(),
                        plan.strAudience(), null, null, strLife);
            default:
                throw new IllegalArgumentException("a CUSTOM claim over a JWKS is UNMEASURED:"
                        + " 2.x verifies one with no target-audience and no target-scope, and no"
                        + " stack here has been started that way. Use a shared secret, or"
                        + " measure it first");
        }
    }


    /**
    /**
     * The lifetime a token for this participant may carry.
     *
     * THE ONE PLACE THE RULE LIVES, because both sides read it. The participant
     * is configured from it - `max-token-life` at or above the floor - and the
     * mint issues from it, and a project that computed them separately is
     * exactly the drift a live run had to find.
     *
     * @param version which Canton the participant runs
     * @param ttlWanted the lifetime the caller would prefer
     * @return ttlWanted at or above {@link #VER_FLOOR_TOKEN_LIFE}, otherwise
     *         capped at {@link #TTL_BELOW_FLOOR}
     */
    public static Duration ttlTokenFor(VersionId version, Duration ttlWanted) {
        if (version == null)
            throw new IllegalArgumentException("a version is required");
        if (ttlWanted == null)
            throw new IllegalArgumentException("a lifetime is required");
        if (version.compareTo(VER_FLOOR_TOKEN_LIFE) >= 0)
            return ttlWanted;
        return ttlWanted.compareTo(TTL_BELOW_FLOOR) <= 0 ? ttlWanted : TTL_BELOW_FLOOR;
    }


    /**
     * A duration in the spelling Canton's configuration takes.
     *
     * Seconds throughout, deliberately: `24h` and `86400s` are the same ceiling
     * and only one of them survives a plan whose lifetime is not a whole number
     * of hours. A unit chosen for how it reads is how a rendered value stops
     * matching the value it came from.
     *
     * @param ttl the plan's token lifetime
     * @return the HOCON value, never null
     */
    private static String hocon(Duration ttl) {
        return ttl.toSeconds() + "s";
    }


    /**
     * The ceiling, or nothing at all below {@link #VER_FLOOR_TOKEN_LIFE}.
     *
     * <h2>THE GATE IS A VERSION FLOOR, NOT A FAMILY</h2>
     *
     * A participant below {@link #VER_FLOOR_TOKEN_LIFE} rejects the key by
     * path, file and line and does not start. That refusal is NOT a 2.x/3.x
     * difference: installs on both lines below the floor refuse it alike, and
     * the key arrives partway through the 3.5 series.
     *
     * NOTHING IS RENDERED IN ITS PLACE below the floor. No older spelling for
     * a lifetime ceiling exists on either line, and inventing one produces the
     * same refusal with a different key name in it.
     *
     * <h2>What this leaves open, said plainly</h2>
     *
     * With the key absent the participant applies its own default ceiling,
     * which is far shorter than the lifetime this project mints at. So every
     * version below the floor will START correctly and may then REFUSE every
     * token at verification, with a log line saying only that the token was
     * rejected. The first authenticated call on a below-floor stack is what
     * settles it.
     *
     * @param version which Canton the participant runs
     * @param ttl the token lifetime the caller is minting at
     * @return the HOCON value, or null to leave the key out
     */
    private static String strLifeFor(VersionId version, Duration ttl) {
        if (version.compareTo(VER_FLOOR_TOKEN_LIFE) < 0)
            return null;
        return hocon(ttl);
    }


    /**
     * Explicitly no authentication. NOT the same as omitting the overlay: this
     * writes the key, which is how a stack states that the empty check was
     * intended rather than forgotten.
     *
     * @return the overlay
     */
    /**
     * A JWKS-backed participant, with whichever target the caller pins.
     *
     * @param version which Canton the participant runs
     * @param strUrlJwks where the participant reads the public JWKS; a
     *        `file:` url is measured on 2.9 and unmeasured on 3.x
     * @param strAudience the `aud` to compare against, or null
     * @param strScope the `scope` to require, or null
     * @param ttl the ceiling on a token's remaining lifetime
     * @return the overlay
     */
    public static AuthOverlay ofJwksUrl(VersionId version, String strUrlJwks,
            String strAudience, String strScope, Duration ttl) {
        if (version == null)
            throw new IllegalArgumentException("a version is required");
        require(strUrlJwks, "a JWKS url");
        return new AuthOverlay(CantonLine.ofMajor(version.major()).strTypeJwks(),
                strUrlJwks, strAudience, strScope, null, strLifeFor(version, ttl));
    }


    /**
     * The symmetric type, with whichever target the caller pins.
     *
     * THE VERSION IS TAKEN even though the type name is the same everywhere. It
     * decides whether the lifetime ceiling is rendered at all - see
     * {@link #strLifeFor} - and a factory that did not know the version would
     * write a key that every install below the floor refuses.
     *
     * @param version which Canton the participant runs
     * @param strSecret the plaintext shared secret
     * @param strAudience the `aud` to compare against, or null
     * @param strScope the `scope` to require, or null
     * @param ttl the ceiling on a token's remaining lifetime
     * @return the overlay
     */
    public static AuthOverlay ofSecret(VersionId version, String strSecret,
            String strAudience, String strScope, Duration ttl) {
        if (version == null)
            throw new IllegalArgumentException("a version is required");
        require(strSecret, "a secret");
        return new AuthOverlay(STR_TYPE_HMAC_256, null, strAudience, strScope,
                strSecret, strLifeFor(version, ttl));
    }


    /**
     * One of the three certificate types.
     *
     * THE VERSION IS TAKEN for the reason {@link #ofSecret} gives.
     *
     * @param version which Canton the participant runs
     * @param strTypeCrt {@link #STR_TYPE_RS256_CRT}, {@link #STR_TYPE_ES256_CRT}
     *        or {@link #STR_TYPE_ES512_CRT}
     * @param strFileCert the X.509 certificate file, PEM or DER
     * @param strAudience the `aud` to compare against, or null
     * @param strScope the `scope` to require, or null
     * @param ttl the ceiling on a token's remaining lifetime
     * @return the overlay
     */
    public static AuthOverlay ofCertificate(VersionId version, String strTypeCrt,
            String strFileCert, String strAudience, String strScope, Duration ttl) {
        if (version == null)
            throw new IllegalArgumentException("a version is required");
        require(strTypeCrt, "a type");
        require(strFileCert, "a certificate file");
        if (!STR_TYPE_RS256_CRT.equals(strTypeCrt)
                && !STR_TYPE_ES256_CRT.equals(strTypeCrt)
                && !STR_TYPE_ES512_CRT.equals(strTypeCrt)) {
            throw new IllegalArgumentException(strTypeCrt
                    + " is not a certificate-backed auth-service type");
        }
        return new AuthOverlay(strTypeCrt, strFileCert, strAudience, strScope,
                strLifeFor(version, ttl), true);
    }


    public static AuthOverlay ofWildcard() {
        return new AuthOverlay(STR_TYPE_WILDCARD, null, null, null, null);
    }


    /**
     * @param strParticipant the participant name, e.g. the sandbox's own
     * @return the conventional audience string for it
     */
    public static String audienceForParticipant(String strParticipant) {
        require(strParticipant, "a participant name");
        return STR_AUDIENCE_PREFIX + strParticipant;
    }


    public String strType() {
        return strType;
    }


    /**
     * @return the HOCON to hand to Canton with -c
     */
    public String render() {
        return render(StorageOverlay.STR_NODE_PARTICIPANT);
    }


    /**
     * @param strParticipant the participant node the overlay applies to. On 3.x
     *        this is the bundled configuration's own name; on 2.x it is
     *        whatever {@link Canton2xConfig} called the node, and a mismatch
     *        here produces a stack that starts happily with no authentication
     *        on the API it was supposed to guard
     * @return the HOCON to hand to Canton with -c
     */
    public String render(String strParticipant) {
        require(strParticipant, "a participant name");

        StringBuilder sb = new StringBuilder();
        sb.append("// SPDX-License-Identifier: Apache-2.0\n");
        sb.append("// Generated by raposza-canton; only auth-services is restated.\n");
        sb.append("canton.participants.").append(strParticipant)
                .append(".ledger-api.auth-services = [\n");
        sb.append("  {\n");
        sb.append("    type = ").append(strType).append("\n");
        if (strUrl != null)
            appendString(sb, "    ", "url", strUrl);
        if (strAudience != null)
            appendString(sb, "    ", "target-audience", strAudience);
        if (strScope != null)
            appendString(sb, "    ", "target-scope", strScope);
        // NO DANGLING GUARD IN FRONT OF THIS. Until 2026-08-19 the line above
        // the certificate branch was a bodyless `if (strSecret != null)`, which
        // bound the branch to it: an overlay with a certificate and no secret -
        // which is EVERY certificate overlay, since the two are never both set -
        // rendered an auth-service block with a type and no key at all. The
        // three `-crt` types have therefore never been configured correctly by
        // this class.
        if (strCertificate != null)
            appendString(sb, "    ", "certificate", strCertificate);
        if (strSecret != null)
            appendString(sb, "    ", "secret", strSecret);
        if (strMaxTokenLife != null)
            sb.append("    max-token-life = ").append(strMaxTokenLife).append("\n");
        sb.append("  }\n");
        sb.append("]\n");
        return sb.toString();
    }


    /**
     * Safe for a log line: the type and what it checks, never the secret and
     * never a token.
     */
    public String describe() {
        if (strAudience != null)
            return strType + ", audience-based (" + strAudience + ")";
        if (strScope != null)
            return strType + ", scope-based (" + strScope + ")";
        if (strCertificate != null)
            return strType + ", certificate " + strCertificate;
        if (strSecret != null)
            return strType + ", shared secret (not shown)";
        return strType;
    }


    @Override
    public String toString() {
        return "auth overlay: " + describe();
    }


    private static void appendString(StringBuilder sb, String strPad, String strKey,
            String strValue) {
        sb.append(strPad).append(strKey).append(" = \"").append(escape(strValue)).append("\"\n");
    }


    /**
     * HOCON quoted strings take the JSON escapes. Only the two that can appear
     * in a path or an audience are handled; anything else in one of those is a
     * problem worth failing on elsewhere.
     */
    private static String escape(String strValue) {
        return strValue.replace("\\", "\\\\").replace("\"", "\\\"");
    }


    private static void require(String strValue, String strWhat) {
        if (strValue == null || strValue.isBlank())
            throw new IllegalArgumentException(strWhat + " is required");
    }

}
