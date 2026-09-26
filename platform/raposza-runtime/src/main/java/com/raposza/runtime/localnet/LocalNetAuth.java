// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * What the three LocalNet participants verify on their Ledger APIs.
 *
 * <h2>Why the file is rewritten and not overridden - the answer to 21a 4.5</h2>
 *
 * `conf/canton/&lt;role&gt;/app-auth.conf` sets
 * `canton.participants.&lt;role&gt;.ledger-api.auth-services` to a HOCON LIST.
 * A `-C` override sets a PATH, and no path indexes into an array, so the list
 * cannot be extended or retyped from the command line. The stager therefore
 * writes the file - measured off the 0.8.1 bundle, 2026-09-21.
 *
 * <h2>TWO VERIFIERS, AND THE UNSAFE ONE STAYS FIRST</h2>
 *
 * The splice apps do not merely verify, they MINT: each carries
 * `participant-client.ledger-api.auth-config.type = "self-signed"` with the
 * bundle's shared secret, and `auth.algorithm = "hs-256-unsafe"`. Replacing the
 * participants' verifier outright would leave every token those apps issue
 * unverifiable and the network would not found.
 *
 * So the bundle's `unsafe-jwt-hmac-256` entry is KEPT and the operator's choice
 * is added BESIDE it. The order is deliberate: if Canton turns out to honour
 * only the first entry, the stack still starts and the splice apps still work,
 * and the only casualty is the operator's token being refused - a visible,
 * harmless result. The other order would kill the network to find the same
 * thing out.
 *
 * THE ONE EXCEPTION IS `wildcard`, which is written ALONE. A participant that
 * checks nothing accepts the splice apps' tokens too, so there is nothing for a
 * second entry to do.
 *
 * **CANTON REFUSES TWO VERIFIERS THAT SHARE AN AUDIENCE** - measured on
 * 3.5.14, 2026-09-21: `CONFIG_VALIDATION_ERROR(8,0)`, "Multiple authorization
 * service configured with the same audience", named for all three roles, and
 * the canton process exits before it binds anything. So the two entries are
 * told apart BY TARGET: the bundle's HMAC one keeps
 * `${AUTH_&lt;ROLE&gt;_AUDIENCE}`, which is what the splice apps mint against, and
 * the operator's takes a target of its own. {@link #STR_AUDIENCE_BUNDLE} is
 * therefore REFUSED as that target rather than carried to a start that dies
 * forty seconds later on a configuration error.
 *
 * <h2>THE LIFETIME CEILING IS PART OF THE ENTRY - and it is why a correctly
 * configured stack still refused every token</h2>
 *
 * The bundle's own tokens carry no `exp` at all - `LocalNetToken`. An OpenID
 * Provider's do: this project mints at 24 h. With `max-token-life` absent the
 * participant applies its own default ceiling, which is minutes, and refuses
 * the token with a log line that says only that it was rejected. So the ttl the
 * MINT issues at is rendered into the entry the participant verifies with, from
 * one value passed by the caller.
 *
 * IT IS THE CALLER'S TO COMPUTE, not this class's. The key is refused outright
 * below Canton 3.5.6 - `AuthOverlay.VER_FLOOR_TOKEN_LIFE` - and this module does
 * not see `raposza-canton` (S-2), so a null ttl leaves the key out.
 *
 * <h2>What is NOT touched</h2>
 *
 * The splice apps' own auth. Moving those to a provider needs the HOCON names
 * of their non-unsafe variants - the client-credentials client and the RS256
 * algorithm - and those are in neither the bundle's compose tree nor the
 * corpus. BaseNet configures the same thing through Helm values, which is the
 * chart's surface and not these keys.
 *
 * Author Claude/bentzn
 *
 * @param strType what the operator's entry is, or null to leave the bundle's
 *        file exactly as it ships
 * @param strUrlJwks the key set url, for {@link #STR_TYPE_JWKS} only
 * @param strSecret the shared secret, for {@link #STR_TYPE_HMAC} only
 * @param strFileCert the X.509 certificate, for the three `-crt` types only
 * @param strAudience the audience that entry demands, or null when it is
 *        scope-based
 * @param strScope the scope that entry demands, or null when it is
 *        audience-based
 * @param ttlToken what the mint issues at, rendered as `max-token-life`, or
 *        null to leave the key out
 * @param strIssuer the provider's base url, which is what a browser signing in
 *        with the authorization code flow is given as its `authority`; null
 *        leaves the web UIs on the bundle's browser-minted tokens
 * @param strClientId the OAuth client the web UIs identify as
 */
public record LocalNetAuth(String strType, String strUrlJwks, String strSecret,
        String strFileCert, String strAudience, String strScope, Duration ttlToken,
        String strIssuer, String strClientId) {

    /** The bundle's own verifier, kept beside every operator entry. */
    public static final String STR_TYPE_HMAC = "unsafe-jwt-hmac-256";

    /**
     * 3.x spells it this way. The 2.x name `jwt-rs-256-jwks` does not parse -
     * `AuthOverlay`'s type comment records the class list it was read from.
     */
    public static final String STR_TYPE_JWKS = "jwt-jwks";

    /** No check at all, and the one entry that is written on its own. */
    public static final String STR_TYPE_WILDCARD = "wildcard";

    /** From conf/canton/&lt;role&gt;/app-auth.conf, where it is a literal. */
    public static final String STR_SECRET = LocalNetToken.STR_SECRET;

    /**
     * What the bundle's own entry demands, and therefore the one target the
     * operator's entry may not carry - see the type comment.
     */
    public static final String STR_AUDIENCE_BUNDLE = LocalNetToken.STR_AUDIENCE;

    /** RFC 7518 section 3.2, and Canton's own verifier does not enforce it. */
    private static final int N_BYTES_SECRET_MIN = 32;

    /**
     * Every splice app that carries an `auth` block, MEASURED off the bundle's
     * own `conf/splice/<role>/app-auth.conf`: the three validator backends,
     * and `sv-apps.sv`, which takes the same `${_sv_auth}` as the SV's own
     * validator. `scan-apps.scan-app` is NOT here - the shipped conf gives it
     * no auth block and its UI carries none either.
     */
    private static final String[] ARR_PATH_APP = {
            "canton.validator-apps.sv-validator_backend",
            "canton.validator-apps.app-provider-validator_backend",
            "canton.validator-apps.app-user-validator_backend",
            "canton.sv-apps.sv" };

    private static final String STR_NL = "\n";


    public LocalNetAuth {
        if (strType != null && strType.isBlank())
            throw new IllegalArgumentException("an auth-service type must not be blank");
        if (strType != null && !STR_TYPE_WILDCARD.equals(strType)) {
            if (strAudience == null && strScope == null)
                throw new IllegalArgumentException(strType + " needs an audience or a scope");
            if (STR_AUDIENCE_BUNDLE.equals(strAudience)) {
                throw new IllegalArgumentException("the audience must not be "
                        + STR_AUDIENCE_BUNDLE + " - that is what the bundle's own"
                        + " verifier demands, and Canton refuses two auth services"
                        + " configured with the same audience");
            }
        }
        if (STR_TYPE_JWKS.equals(strType) && (strUrlJwks == null || strUrlJwks.isBlank()))
            throw new IllegalArgumentException(STR_TYPE_JWKS + " needs a JWKS url");
        if (STR_TYPE_HMAC.equals(strType)) {
            if (strSecret == null || strSecret.isBlank())
                throw new IllegalArgumentException(STR_TYPE_HMAC + " needs a shared secret");
            if (strSecret.getBytes(StandardCharsets.UTF_8).length
                    < N_BYTES_SECRET_MIN) {
                throw new IllegalArgumentException("a shared secret must be at least "
                        + N_BYTES_SECRET_MIN + " bytes for HS256 (RFC 7518 section 3.2);"
                        + " Canton accepts a shorter one and then refuses every token"
                        + " signed with it");
            }
        }
        if (strType != null && strType.endsWith("-crt")
                && (strFileCert == null || strFileCert.isBlank()))
            throw new IllegalArgumentException(strType + " needs a certificate file");
    }


    /**
     * @return the bundle as it ships - one HS256 verifier and nothing else
     */
    public static LocalNetAuth ofUnsafe() {
        return new LocalNetAuth(null, null, null, null, null, null, null, null, null);
    }


    /**
     * @return no check at all, written as the only entry
     */
    public static LocalNetAuth ofWildcard() {
        return new LocalNetAuth(STR_TYPE_WILDCARD, null, null, null, null, null, null, null, null);
    }


    /**
     * @param strUrlJwksNew where the provider publishes its public keys
     * @param strAudienceNew the audience its tokens carry, or null for a
     *        scope-based entry
     * @param strScopeNew the scope its tokens carry, or null for an
     *        audience-based entry
     * @param ttlTokenNew what the mint issues at, or null below the version
     *        floor for `max-token-life`
     * @return the bundle's verifier plus an asymmetric one beside it
     */
    public static LocalNetAuth ofJwks(String strUrlJwksNew, String strAudienceNew,
            String strScopeNew, Duration ttlTokenNew) {
        return new LocalNetAuth(STR_TYPE_JWKS, strUrlJwksNew, null, null,
                strAudienceNew, strScopeNew, ttlTokenNew, null, null);
    }


    /**
     * @param strSecretNew the shared secret the operator's tokens are signed
     *        with, which is NOT the bundle's
     * @param strAudienceNew the audience those tokens carry, or null
     * @param strScopeNew the scope those tokens carry, or null
     * @param ttlTokenNew what the mint issues at, or null
     * @return the bundle's verifier plus a second symmetric one beside it
     */
    public static LocalNetAuth ofSecret(String strSecretNew, String strAudienceNew,
            String strScopeNew, Duration ttlTokenNew) {
        return new LocalNetAuth(STR_TYPE_HMAC, null, strSecretNew, null,
                strAudienceNew, strScopeNew, ttlTokenNew, null, null);
    }


    /**
     * @param strTypeNew jwt-rs-256-crt, jwt-es-256-crt or jwt-es-512-crt
     * @param strFileCertNew the X.509 certificate, readable by the canton
     *        process this window starts
     * @param strAudienceNew the audience those tokens carry, or null
     * @param strScopeNew the scope those tokens carry, or null
     * @param ttlTokenNew what the mint issues at, or null
     * @return the bundle's verifier plus a certificate-backed one beside it
     */
    public static LocalNetAuth ofCertificate(String strTypeNew, String strFileCertNew,
            String strAudienceNew, String strScopeNew, Duration ttlTokenNew) {
        return new LocalNetAuth(strTypeNew, null, null, strFileCertNew,
                strAudienceNew, strScopeNew, ttlTokenNew, null, null);
    }


    /**
     * The provider the WEB UIs sign in at.
     *
     * SEPARATE FROM THE KEY SET URL, because the two are different things to
     * two different readers: a participant is handed the JWKS document and
     * verifies signatures with it, while a browser is handed the ISSUER and
     * fetches `/.well-known/openid-configuration` from it to find the
     * authorization and token endpoints for itself. One cannot be derived from
     * the other without assuming a path convention.
     *
     * @param strIssuerNew the provider's base url
     * @param strClientIdNew the client the UIs identify as
     * @return the same auth, carrying the provider
     */
    public LocalNetAuth withProvider(String strIssuerNew, String strClientIdNew) {
        return new LocalNetAuth(strType, strUrlJwks, strSecret, strFileCert,
                strAudience, strScope, ttlToken, strIssuerNew, strClientIdNew);
    }


    /**
     * @return whether the web UIs are to sign in at a provider rather than
     *         mint their own HS256 tokens
     */
    public boolean isProvider() {
        return isJwks() && strIssuer != null && !strIssuer.isBlank();
    }


    /**
     * @return whether the stager rewrites the bundle's app-auth.conf at all
     */
    public boolean isRewritten() {
        return strType != null;
    }


    /**
     * @return whether a second, asymmetric verifier is configured
     */
    public boolean isJwks() {
        return STR_TYPE_JWKS.equals(strType);
    }


    /**
     * The env prefix a role's auth names carry.
     *
     * MEASURED off env/&lt;role&gt;-auth-on.env: `sv` is `SV`, `app-provider` is
     * `APP_PROVIDER`, `app-user` is `APP_USER`.
     *
     * @param strRole sv, app-provider or app-user
     * @return the prefix, upper case with hyphens as underscores
     */
    public static String strEnvRole(String strRole) {
        return strRole.toUpperCase(Locale.ROOT).replace('-', '_');
    }


    /**
     * The whole of one role's `app-auth.conf`, rewritten.
     *
     * THE SUBSTITUTIONS ARE KEPT. `${AUTH_&lt;ROLE&gt;_AUDIENCE}` and
     * `${AUTH_&lt;ROLE&gt;_VALIDATOR_USER_NAME}` still resolve from the environment,
     * so one value reaches the participant, the splice app beside it and the UI,
     * rather than three copies that can disagree.
     *
     * @param strRole sv, app-provider or app-user
     * @return the file content, ending with a newline
     */
    public String strConfCanton(String strRole) {
        String strEnv = strEnvRole(strRole);
        StringBuilder sb = new StringBuilder();
        sb.append("# WRITTEN BY LocalNetStager - the bundle's own file is replaced").append(STR_NL);
        sb.append("canton.participants.").append(strRole).append(" {").append(STR_NL);
        sb.append("  ledger-api {").append(STR_NL);
        if (STR_TYPE_WILDCARD.equals(strType)) {
            // ALONE. A participant that checks nothing accepts the splice
            // apps' own tokens, so the bundle's entry has nothing left to do.
            sb.append("    auth-services = [{").append(STR_NL);
            sb.append("      type = ").append(STR_TYPE_WILDCARD).append(STR_NL);
            sb.append("    }]").append(STR_NL);
        }
        else {
            sb.append("    auth-services = [{").append(STR_NL);
            sb.append("      type = ").append(STR_TYPE_HMAC).append(STR_NL);
            sb.append("      target-audience = ${AUTH_").append(strEnv).append("_AUDIENCE}")
                    .append(STR_NL);
            sb.append("      secret = \"").append(STR_SECRET).append("\"").append(STR_NL);
            sb.append("    }");
            if (strType != null) {
                sb.append(", {").append(STR_NL);
                sb.append("      type = ").append(strType).append(STR_NL);
                if (strUrlJwks != null)
                    appendString(sb, "url", strUrlJwks);
                if (strFileCert != null)
                    appendString(sb, "certificate", strFileCert);
                if (strSecret != null)
                    appendString(sb, "secret", strSecret);
                if (strAudience != null)
                    appendString(sb, "target-audience", strAudience);
                if (strScope != null)
                    appendString(sb, "target-scope", strScope);
                if (ttlToken != null) {
                    sb.append("      max-token-life = ").append(ttlToken.toSeconds())
                            .append("s").append(STR_NL);
                }
                sb.append("    }");
            }
            sb.append("]").append(STR_NL);
        }
        sb.append(STR_NL);
        sb.append("    user-management-service.additional-admin-user-id = ${AUTH_")
                .append(strEnv).append("_VALIDATOR_USER_NAME}").append(STR_NL);
        sb.append("  }").append(STR_NL);
        sb.append("}").append(STR_NL);
        return sb.toString();
    }


    /**
     * Safe for a log line: what is checked, never the secret.
     *
     * @return one line
     */
    /**
     * The splice apps' own verifier, as an override appended to the staged
     * `app.conf`.
     *
     * <h2>THE KEY NAMES ARE MEASURED, NOT INFERRED</h2>
     *
     * `javap` on `splice-node.jar`, 2026-09-21:
     * `org.lfdecentralizedtrust.splice.auth.AuthConfig` is a sealed type whose
     * variants are `Hs256Unsafe(audience, secret)` - which is what the bundle
     * ships - and `Rs256(audience, jwksUrl, connectionTimeout, readTimeout)`.
     * Canton spells a config key as the kebab-case of the field, which every
     * pair in this tree shows: `targetAudience`/`target-audience`,
     * `maxTokenLife`/`max-token-life`. So the block is `algorithm = "rs-256"`,
     * `audience` and `jwks-url`, and the two timeouts keep their defaults.
     *
     * <h2>ASSIGNED TWICE, AND THAT IS THE VENDOR'S OWN IDIOM</h2>
     *
     * HOCON MERGES an object over an object, so assigning the rs-256 block
     * over the bundle's would leave `secret` standing beside it - a key
     * `Rs256` does not have, and a config error at startup. The bundle's
     * `compose-disable-auth.yaml` solves the same problem the same way: set
     * the key to `""` first, then to the new object. That is copied here
     * rather than invented.
     *
     * <h2>ONE AUDIENCE, ON THE VENDOR'S OWN ADVICE</h2>
     *
     * `validator_helm.rst`: "When first starting out, it is suggested to
     * configure both JWT token audiences below to the same value". So the
     * apps demand what the participants demand, and ONE token from the
     * provider works against the Ledger API and the validator API alike.
     *
     * WHAT IS NOT TOUCHED is `participant-client.ledger-api.auth-config`,
     * which stays `self-signed` over the bundle's secret. That is how each app
     * still reaches its own participant, and it is why the bundle's HMAC
     * verifier stays beside the operator's on every participant.
     *
     * @return the HOCON, ending with a newline, or an empty string when the
     *         bundle's own verifier is being kept
     */
    public String strConfSpliceOverride() {
        if (!isJwks())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append(STR_NL);
        sb.append("# WRITTEN BY LocalNetStager - the splice apps verify what the")
                .append(" participants verify").append(STR_NL);
        for (String strPath : ARR_PATH_APP) {
            sb.append(strPath).append(".auth = \"\"").append(STR_NL);
            sb.append(strPath).append(".auth = {").append(STR_NL);
            sb.append("  algorithm = \"rs-256\"").append(STR_NL);
            sb.append("  audience = \"").append(strAudience).append("\"").append(STR_NL);
            sb.append("  jwks-url = \"").append(strUrlJwks).append("\"").append(STR_NL);
            sb.append("}").append(STR_NL);
        }
        return sb.toString();
    }


    public String describe() {
        if (strType == null)
            return "the bundle's own " + STR_TYPE_HMAC;
        if (STR_TYPE_WILDCARD.equals(strType))
            return "no check (wildcard)";
        return strType + " beside the bundle's " + STR_TYPE_HMAC + ", "
                + (strAudience != null ? "audience " + strAudience : "scope " + strScope)
                + (ttlToken == null ? ", no max-token-life"
                        : ", max-token-life " + ttlToken.toSeconds() + "s");
    }


    /**
     * NOTHING, DELIBERATELY. The operator's audience must NOT reach
     * `AUTH_&lt;ROLE&gt;_AUDIENCE`: that is what the SPLICE apps mint against and
     * what the HMAC verifier beside it checks, and the two entries would then
     * agree again and Canton would refuse the pair.
     *
     * @return an empty map
     */
    public Map<String, String> mapEnvOverride() {
        return Map.of();
    }


    private static void appendString(StringBuilder sb, String strKey, String strValue) {
        sb.append("      ").append(strKey).append(" = \"").append(strValue).append("\"")
                .append(STR_NL);
    }

}
