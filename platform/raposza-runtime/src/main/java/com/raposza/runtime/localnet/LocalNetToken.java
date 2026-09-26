// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * LocalNet's tokens, minted the way LocalNet's own console mints them.
 *
 * MEASURED, NOT DESIGNED. `docker/console/entrypoint.sh:18` in the 0.7.4 bundle
 * is one line:
 *
 * <pre>
 * jwt-cli encode hs256 --s unsafe --p '{"sub": "'"$sub"'", "aud": "'"$aud"'"}'
 * </pre>
 *
 * and `conf/canton/&lt;role&gt;/app-auth.conf` names the other half:
 * `type = unsafe-jwt-hmac-256`, `secret = "unsafe"`,
 * `target-audience = ${AUTH_&lt;ROLE&gt;_AUDIENCE}`. So the payload carries a subject
 * and an audience and NOTHING ELSE - no issuer, no expiry, no scope. The absence
 * of `exp` is copied deliberately: Canton 3.x refuses a token whose lifetime is
 * too long, and a lifetime this project invented would be the first thing to
 * suspect when a call is refused for a reason that has nothing to do with it.
 *
 * THE SECRET IS HARDCODED IN THE BUNDLE, not read from the environment.
 * `SPLICE_APP_UI_UNSAFE_SECRET` defaults to the same string and feeds the UIs;
 * the participant's own auth service takes the literal in the conf file. They
 * agree today and they are not the same setting.
 *
 * This is not authentication. It is a shared constant published in a
 * development bundle, and it belongs to LocalNet alone.
 *
 * Author Claude/bentzn
 */
public final class LocalNetToken {

    /** From conf/canton/&lt;role&gt;/app-auth.conf, where it is a literal. */
    public static final String STR_SECRET = "unsafe";

    /** From env/&lt;role&gt;-auth-on.env. */
    public static final String STR_AUDIENCE = "https://canton.network.global";

    /** From env/&lt;role&gt;-auth-on.env, AUTH_&lt;ROLE&gt;_VALIDATOR_USER_NAME. */
    public static final String STR_SUBJECT_LEDGER = "ledger-api-user";

    private static final String STR_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private static final String STR_MAC = "HmacSHA256";

    private LocalNetToken() {
    }


    /**
     * @param strSubject the user the token speaks for
     * @param strAudience what the participant was configured to demand
     * @return a signed compact JWT
     */
    public static String mint(String strSubject, String strAudience) {
        return mint(strSubject, strAudience, STR_SECRET);
    }


    /**
     * @param strSubject the user the token speaks for
     * @param strAudience what the participant was configured to demand
     * @param strSecret the shared secret the auth service verifies against
     * @return a signed compact JWT
     */
    public static String mint(String strSubject, String strAudience, String strSecret) {
        String strPayload = "{\"sub\":\"" + escape(strSubject) + "\",\"aud\":\""
                + escape(strAudience) + "\"}";
        String strSigned = encode(STR_HEADER) + "." + encode(strPayload);
        return strSigned + "." + sign(strSigned, strSecret);
    }


    private static String sign(String strSigned, String strSecret) {
        try {
            Mac mac = Mac.getInstance(STR_MAC);
            mac.init(new SecretKeySpec(strSecret.getBytes(StandardCharsets.UTF_8), STR_MAC));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(strSigned.getBytes(StandardCharsets.UTF_8)));
        }
        catch (GeneralSecurityException ex) {
            throw new IllegalStateException("could not sign a token: " + ex.getMessage(), ex);
        }
    }


    private static String encode(String strPart) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(strPart.getBytes(StandardCharsets.UTF_8));
    }


    /**
     * A subject and an audience are configuration values, not free text, so the
     * only thing that can break the JSON is a quote or a backslash arriving from
     * an env file. Escaped rather than rejected: refusing a value the bundle
     * itself supplied would be this class deciding what LocalNet may configure.
     */
    private static String escape(String strValue) {
        return strValue.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
