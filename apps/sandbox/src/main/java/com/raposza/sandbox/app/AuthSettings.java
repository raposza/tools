// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import com.raposza.canton.install.VersionId;
import com.raposza.canton.topology.AuthOverlay;
import com.raposza.jwt.AuthPlan;
import com.raposza.jwt.TokenShape;

import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

/**
 * The participant's `ledger-api.auth-services` block, as a form can hold it.
 *
 * <h2>One member per type Canton actually has</h2>
 *
 * The sealed set in `com.digitalasset.canton.config.AuthServiceConfig`, read
 * from the 3.4.11 and 3.5.11 jars, is `JwtJwks`, `JwtRs256Crt`, `JwtEs256Crt`,
 * `JwtEs512Crt`, `UnsafeJwtHmac256` and `Wildcard`. All six are offered here
 * because all six are things a participant can be configured with, and the
 * point of this window is being able to test against each of them.
 *
 * What each one needs beside the type is different, which is why the form shows
 * a different field for each: a secret for HMAC, a certificate file for the
 * three `-crt` types, and a URL for JWKS. A form that showed all four at once
 * would be a form where three of them are ignored without saying so.
 *
 * <h2>The lifetime is not a setting</h2>
 *
 * Twenty-four hours, fixed. `max-token-lifetime` is what decides whether a
 * token is accepted, and with it unset the participant refuses a token that
 * lives longer than five minutes.
 * The overlay renders the ceiling and the mint issues at the same number, so
 * the two cannot drift; making it a field would only create a way for them to.
 *
 * @param mode which auth-service type the participant gets
 * @param shape AUDIENCE or SCOPE; never CUSTOM, which is measured REFUSED on
 *        3.5.11 and which `AuthOverlay.of` declines over a JWKS as UNMEASURED
 * @param strValue the audience for AUDIENCE, the scope for SCOPE; blank takes
 *        the convention for the shape
 * @param strSecret the shared secret, for {@link Mode#UNSAFE_HMAC_256} only
 * @param strFileCert the X.509 certificate, for the three `-crt` types only
 * @param strUrlJwks where the participant reads the public JWKS, for
 *        {@link Mode#JWKS} only; blank takes the local mint
 *
 * Author Claude/bentzn
 */
public record AuthSettings(Mode mode, TokenShape shape, String strValue, String strSecret,
        String strFileCert, String strUrlJwks) {

    /**
     * What a participant checks. The label is what the form shows; the type is
     * what Canton's configuration calls it.
     */
    public enum Mode {

        /** No check at all, written explicitly rather than omitted. */
        NONE(AuthOverlay.STR_TYPE_WILDCARD, "none (wildcard)"),

        /** HMAC over a plaintext shared secret. */
        UNSAFE_HMAC_256(AuthOverlay.STR_TYPE_HMAC_256, "unsafe-jwt-hmac-256"),

        RS256_CRT(AuthOverlay.STR_TYPE_RS256_CRT, "jwt-rs-256-crt"),

        ES256_CRT(AuthOverlay.STR_TYPE_ES256_CRT, "jwt-es-256-crt"),

        ES512_CRT(AuthOverlay.STR_TYPE_ES512_CRT, "jwt-es-512-crt"),

        /** RS256, ES256 or ES512 against a key set fetched from a URL. */
        JWKS(AuthOverlay.STR_TYPE_JWKS, "jwt-jwks");

        private final String strType;

        private final String strLabel;


        Mode(String strTypeNew, String strLabelNew) {
            this.strType = strTypeNew;
            this.strLabel = strLabelNew;
        }


        public String strType() {
            return strType;
        }


        /**
         * The JOSE algorithm a token for this type has to be signed with, and
         * therefore the mint key it has to be signed by - the mint's kid for an
         * algorithm is its name in lower case.
         *
         * NAMED RATHER THAN LEFT TO THE DEFAULT. The mint picks one asymmetric
         * algorithm when it is not told which, so an `jwt-es-512-crt`
         * participant asked for a token without this would be handed one signed
         * with an RSA key and would refuse it - configured, started, and
         * unexercisable, which is exactly what the symmetric column was before
         * the secret was passed through.
         *
         * @return the algorithm, or null when this type does not pin one
         */
        public String strAlgMint() {
            switch (this) {
                case UNSAFE_HMAC_256:
                    return "HS256";
                case RS256_CRT:
                    return "RS256";
                case ES256_CRT:
                    return "ES256";
                case ES512_CRT:
                    return "ES512";
                default:
                    return null;
            }
        }


        /**
         * @return whether this type reads its key from a certificate file
         */
        public boolean flagCertificate() {
            return this == RS256_CRT || this == ES256_CRT || this == ES512_CRT;
        }


        /**
         * @return whether a token shape and its target value mean anything here
         */
        public boolean flagTargets() {
            return this != NONE;
        }


        @Override
        public String toString() {
            return strLabel;
        }


        /**
         * @param strName a member name or a label, in any case
         * @return the member, or JWKS when nothing matches
         */
        public static Mode of(String strName) {
            if (strName == null || strName.isBlank())
                return JWKS;

            String strWanted = strName.trim();
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(strWanted)
                        || mode.strLabel.equalsIgnoreCase(strWanted)
                        || mode.strType.equalsIgnoreCase(strWanted)) {
                    return mode;
                }
            }
            return JWKS;
        }
    }

    public static final String STR_KEY_MODE = "auth.mode";

    public static final String STR_KEY_SHAPE = "auth.shape";

    public static final String STR_KEY_VALUE = "auth.value";

    public static final String STR_KEY_SECRET = "auth.secret";

    public static final String STR_KEY_CERT = "auth.certificate";

    public static final String STR_KEY_JWKS = "auth.jwks.url";

    /**
     * TWENTY-FOUR HOURS, FIXED, and see the type comment for why it is not a
     * field. A WEEK IS KNOWN TO FAIL: 3.5.11 refused a seven-day token with
     * `token lifetime too long`.
     */
    public static final int N_HOURS_TOKEN = 24;

    /**
     * What an HMAC participant gets when nothing was typed.
     *
     * THIRTY-EIGHT BYTES, and the length is the point. The mint signs an HS256
     * token with the UTF-8 bytes of this same string, and RFC 7518 section 3.2
     * requires at least 32 of them. The previous default was 23, which Canton
     * accepted as a configuration and which no token could ever be minted
     * against.
     */
    public static final String STR_SECRET_DEFAULT = "raposza-sandbox-unsafe-shared-secret";

    /**
     * The shortest secret the form will accept, in bytes. See
     * {@link #STR_SECRET_DEFAULT}.
     */
    public static final int N_BYTES_SECRET_MIN = 32;


    public AuthSettings {
        if (mode == null)
            mode = Mode.JWKS;
        if (shape == null || shape == TokenShape.CUSTOM)
            shape = TokenShape.AUDIENCE;
        strValue = strValue == null ? "" : strValue.trim();
        strSecret = strSecret == null ? "" : strSecret.trim();
        strFileCert = strFileCert == null ? "" : strFileCert.trim();
        strUrlJwks = strUrlJwks == null ? "" : strUrlJwks.trim();
    }


    /**
     * @return JWKS against the local mint, audience-based
     */
    public static AuthSettings ofDefaults() {
        return new AuthSettings(Mode.JWKS, TokenShape.AUDIENCE, "", "", "", "");
    }


    /**
     * @param props what was on disk; may be null
     * @param fallback what each absent or unusable key becomes
     * @return the settings
     */
    public static AuthSettings ofProperties(Properties props, AuthSettings fallback) {
        AuthSettings back = fallback == null ? ofDefaults() : fallback;
        if (props == null)
            return back;

        return new AuthSettings(
                props.getProperty(STR_KEY_MODE) == null ? back.mode()
                        : Mode.of(props.getProperty(STR_KEY_MODE)),
                shapeOf(props, back.shape()),
                strOf(props, STR_KEY_VALUE, back.strValue()),
                strOf(props, STR_KEY_SECRET, back.strSecret()),
                strOf(props, STR_KEY_CERT, back.strFileCert()),
                strOf(props, STR_KEY_JWKS, back.strUrlJwks()));
    }


    /**
     * @param props the properties to write into
     */
    public void putInto(Properties props) {
        props.setProperty(STR_KEY_MODE, mode.name());
        props.setProperty(STR_KEY_SHAPE, shape.name());
        props.setProperty(STR_KEY_VALUE, strValue);
        // NOT A SECRET IN ANY MEANINGFUL SENSE - it is the plaintext key of a
        // participant this window started for testing, and `unsafe` is in the
        // type name. It is written so a restart does not lose it.
        props.setProperty(STR_KEY_SECRET, strSecret);
        props.setProperty(STR_KEY_CERT, strFileCert);
        props.setProperty(STR_KEY_JWKS, strUrlJwks);
    }


    /**
     * @param version which Canton the participant runs
     * @return the lifetime a token for it may carry, which is 24 h where the
     *         participant can be told to allow it and {@link
     *         AuthOverlay#TTL_BELOW_FLOOR} where it cannot
     */
    public Duration ttlToken(VersionId version) {
        return AuthOverlay.ttlTokenFor(version, Duration.ofHours(N_HOURS_TOKEN));
    }


    /**
     * @param nameParticipant the participant node name
     * @return the audience an AUDIENCE token carries
     */
    public String strAudienceFor(String nameParticipant) {
        return strValue.isEmpty() ? AuthPlan.audienceFor(nameParticipant) : strValue;
    }


    /**
     * @return the scope a SCOPE token carries
     */
    public String strScopeEffective() {
        return strValue.isEmpty() ? AuthPlan.STR_SCOPE_DEFAULT : strValue;
    }


    /**
     * @param strUrlMint the local mint's own JWKS url
     * @return where the participant will read its keys
     */
    public String strUrlJwksEffective(String strUrlMint) {
        return strUrlJwks.isEmpty() ? strUrlMint : strUrlJwks;
    }


    public String strSecretEffective() {
        return strSecret.isEmpty() ? STR_SECRET_DEFAULT : strSecret;
    }


    /**
     * The participant's side, rendered.
     *
     * @param version which Canton the participant runs; it decides the JWKS
     *        type name and whether a lifetime ceiling is rendered at all
     * @param nameParticipant the participant node name, for the audience
     *        convention
     * @param strUrlMint the local mint's JWKS url, used when none was typed
     * @return the overlay
     * @throws IllegalArgumentException when a required field is empty
     */
    public AuthOverlay overlay(VersionId version, String nameParticipant, String strUrlMint) {
        String strAudience = shape == TokenShape.AUDIENCE
                ? strAudienceFor(nameParticipant) : null;
        String strScope = shape == TokenShape.SCOPE ? strScopeEffective() : null;

        switch (mode) {
            case NONE:
                return AuthOverlay.ofWildcard();
            case UNSAFE_HMAC_256:
                return AuthOverlay.ofSecret(version, strSecretEffective(), strAudience,
                        strScope, ttlToken(version));
            case JWKS:
                return AuthOverlay.ofJwksUrl(version, strUrlJwksEffective(strUrlMint),
                        strAudience, strScope, ttlToken(version));
            default:
                if (strFileCert.isEmpty()) {
                    throw new IllegalArgumentException(mode.strType()
                            + " needs a certificate file");
                }
                return AuthOverlay.ofCertificate(version, mode.strType(), strFileCert,
                        strAudience, strScope, ttlToken(version));
        }
    }


    /**
     * Safe for a status line: what is checked, never the secret.
     */
    public String describe() {
        if (mode == Mode.NONE)
            return "no check (wildcard)";
        return mode.strType() + ", " + shape.name().toLowerCase(Locale.ROOT) + ", "
                + N_HOURS_TOKEN + " h at or above " + AuthOverlay.VER_FLOOR_TOKEN_LIFE
                + ", " + AuthOverlay.TTL_BELOW_FLOOR.toSeconds() + " s below it";
    }


    private static String strOf(Properties props, String strKey, String strFallback) {
        String strRaw = props.getProperty(strKey);
        return strRaw == null ? strFallback : strRaw.trim();
    }


    private static TokenShape shapeOf(Properties props, TokenShape shapeFallback) {
        String strRaw = props.getProperty(STR_KEY_SHAPE);
        if (strRaw == null || strRaw.isBlank())
            return shapeFallback;

        String strWanted = strRaw.trim().toUpperCase(Locale.ROOT);
        for (TokenShape shapeHere : TokenShape.values()) {
            if (shapeHere.name().equals(strWanted) && shapeHere != TokenShape.CUSTOM)
                return shapeHere;
        }
        return shapeFallback;
    }

}
