// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import com.raposza.api.TokenSource_i;
import com.raposza.api.profile.HostProfile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * One settings file per profile, holding what the catalogue must not.
 *
 *   ~/.raposza/profiles/&lt;slug&gt;.properties
 *
 * Keys, all optional:
 *
 * <pre>
 *   auth             none | token | jwks     default none
 *   token            the bearer token        auth=token
 *   jwks.file        path to a JWKS file     auth=jwks
 *   jwks.kid         key id, else the first private key
 *   subject          the `sub` claim, normally the ledger user id
 *   scope            overrides the catalogue's scope
 *   audience         overrides the catalogue's audience
 *   act.as           comma separated, custom-claim tokens only
 *   read.as          comma separated, custom-claim tokens only
 *   admin            true | false, custom-claim tokens only
 *   application.id   custom-claim tokens only
 *   ledger.id        custom-claim tokens only
 *   participant.id   custom-claim tokens only
 *   ttl.seconds      lifetime of a MINTED token, default 1000 years
 * </pre>
 *
 * An absent file means AuthMode.NONE, which is what a local sandbox wants and
 * what every profile got before this existed. A file that exists but does not
 * parse is an error rather than a silent fall back to NONE: connecting
 * anonymously to a participant whose settings were meant to authenticate is the
 * failure this whole file is here to prevent.
 *
 * The file is keyed by a slug of the DISPLAY NAME. Two catalogue lines with the
 * same display name therefore share one settings file, which is a consequence
 * of the catalogue having no other stable key and is stated here rather than
 * discovered.
 *
 * Author Claude/bentzn
 */
public final class ProfileAuthStore {

    /** Under the home directory, beside the catalogue. */
    public static final String DIR_PROFILE = ".raposza/profiles";

    private static final String KEY_AUTH = "auth";
    private static final String KEY_TOKEN = "token";


    private ProfileAuthStore() {
    }


    /**
     * @param dirHome the user's home directory
     * @param nameDisplay the profile's display name
     * @return where that profile's settings live, whether or not it exists
     */
    public static Path fileFor(Path dirHome, String nameDisplay) {
        return dirHome.resolve(DIR_PROFILE).resolve(slug(nameDisplay) + ".properties");
    }


    /**
     * @param nameDisplay a display name, which may contain spaces and slashes
     * @return a lower case file-name-safe form of it, never empty
     */
    public static String slug(String nameDisplay) {
        if (nameDisplay == null || nameDisplay.isBlank())
            return "unnamed";

        StringBuilder bld = new StringBuilder();
        String strIn = nameDisplay.trim().toLowerCase(Locale.ROOT);
        for (int idx = 0; idx < strIn.length(); idx++) {
            char ch = strIn.charAt(idx);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9'))
                bld.append(ch);
            else if (bld.length() > 0 && bld.charAt(bld.length() - 1) != '-')
                bld.append('-');
        }

        while (bld.length() > 0 && bld.charAt(bld.length() - 1) == '-') {
            bld.setLength(bld.length() - 1);
        }
        return bld.length() == 0 ? "unnamed" : bld.toString();
    }


    /**
     * @param fileSettings the settings file; absent means NONE
     * @return the settings
     * @throws TokenException when the file exists but cannot be read or is
     *         inconsistent with the mode it declares
     */
    public static ProfileAuth load(Path fileSettings) {
        if (fileSettings == null || !Files.exists(fileSettings))
            return ProfileAuth.NONE;

        Properties prop = new Properties();
        try (Reader rdr = Files.newBufferedReader(fileSettings, StandardCharsets.UTF_8)) {
            prop.load(rdr);
        }
        catch (IOException ex) {
            throw new TokenException("cannot read profile settings: " + fileSettings, ex);
        }

        AuthMode mode = parseMode(prop.getProperty(KEY_AUTH), fileSettings);
        String strToken = trimToNull(prop.getProperty(KEY_TOKEN));
        String strJwks = trimToNull(prop.getProperty("jwks.file"));

        if (mode == AuthMode.TOKEN && strToken == null)
            throw new TokenException("auth=token but no token= in " + fileSettings);
        if (mode == AuthMode.JWKS && strJwks == null)
            throw new TokenException("auth=jwks but no jwks.file= in " + fileSettings);

        return new ProfileAuth(mode, strToken, strJwks == null ? null : Path.of(strJwks),
                trimToNull(prop.getProperty("jwks.kid")), trimToNull(prop.getProperty("subject")),
                trimToNull(prop.getProperty("scope")), trimToNull(prop.getProperty("audience")),
                splitList(prop.getProperty("act.as")), splitList(prop.getProperty("read.as")),
                Boolean.parseBoolean(prop.getProperty("admin", "false")),
                trimToNull(prop.getProperty("application.id")),
                trimToNull(prop.getProperty("ledger.id")),
                trimToNull(prop.getProperty("participant.id")),
                parseTtl(prop.getProperty("ttl.seconds"), fileSettings));
    }


    /**
     * Stores a pasted token so it does not have to be pasted again, and sets
     * auth=token.
     *
     * Rewrites the whole file from its parsed keys, so COMMENTS AND ORDERING
     * ARE LOST. Said here because a hand-edited settings file coming back
     * reordered looks like corruption.
     *
     * @param fileSettings where to write
     * @param strToken the token
     * @throws TokenException when the file cannot be written
     */
    public static void saveToken(Path fileSettings, String strToken) {
        if (strToken == null || strToken.isBlank())
            throw new TokenException("refusing to save an empty token");

        Properties prop = new Properties();
        if (Files.exists(fileSettings)) {
            try (Reader rdr = Files.newBufferedReader(fileSettings,
                    StandardCharsets.UTF_8)) {
                prop.load(rdr);
            }
            catch (IOException ex) {
                throw new TokenException("cannot read profile settings: " + fileSettings, ex);
            }
        }

        prop.setProperty(KEY_AUTH, AuthMode.TOKEN.name().toLowerCase(Locale.ROOT));
        prop.setProperty(KEY_TOKEN, strToken.trim());

        try {
            Files.createDirectories(fileSettings.getParent());
            try (Writer wrt = Files.newBufferedWriter(fileSettings,
                    StandardCharsets.UTF_8)) {
                prop.store(wrt, "workbench profile settings - contains a credential");
            }
        }
        catch (IOException ex) {
            throw new TokenException("cannot write profile settings: " + fileSettings, ex);
        }

        restrict(fileSettings);
    }


    /**
     * Removes a stored token, leaving the profile anonymous.
     *
     * The declaration moves WITH the credential. A declared mode with no
     * material for it is an ERROR rather than a fall back to anonymous - see
     * docs/architecture.md section 9 - so a clear that removed only the token
     * would leave a file that refuses to load, and the next connection would
     * report a settings error rather than the absence the operator asked for.
     *
     * Other keys survive. A profile that also carries jwks settings, a subject
     * or party lists keeps them, because clearing a credential is not the same
     * as discarding a configuration. When nothing but the declaration is left,
     * the file goes: an empty settings file and no settings file mean the same
     * thing, and the one that does not exist cannot be mistaken for a
     * credential store.
     *
     * NOT a secure erase. The bytes are overwritten by a rewrite or released by
     * a delete; what the filesystem does with them afterwards is its business,
     * and a token that has been on disk should be treated as compromised and
     * revoked at the issuer rather than merely deleted here.
     *
     * @param fileSettings the profile's settings file
     * @return what happened, for showing to the operator
     * @throws TokenException when the file exists and cannot be read or written
     */
    public static String clearToken(Path fileSettings) {
        if (!Files.exists(fileSettings))
            return "No settings file for this profile:\n" + fileSettings;

        Properties prop = new Properties();
        try (Reader rdr = Files.newBufferedReader(fileSettings, StandardCharsets.UTF_8)) {
            prop.load(rdr);
        }
        catch (IOException ex) {
            throw new TokenException("cannot read profile settings: " + fileSettings, ex);
        }

        boolean flagHadToken = trimToNull(prop.getProperty(KEY_TOKEN)) != null;
        prop.remove(KEY_TOKEN);

        String strMode = trimToNull(prop.getProperty(KEY_AUTH));
        if (strMode != null
                && AuthMode.TOKEN.name().toLowerCase(Locale.ROOT).equalsIgnoreCase(strMode)) {
            prop.setProperty(KEY_AUTH, AuthMode.NONE.name().toLowerCase(Locale.ROOT));
        }

        if (isBare(prop)) {
            try {
                Files.delete(fileSettings);
            }
            catch (IOException ex) {
                throw new TokenException("cannot delete profile settings: " + fileSettings, ex);
            }
            return (flagHadToken ? "Stored token cleared and the file removed:\n"
                    : "No stored token was there; the empty file was removed:\n") + fileSettings;
        }

        try {
            try (Writer wrt = Files.newBufferedWriter(fileSettings, StandardCharsets.UTF_8)) {
                prop.store(wrt, "workbench profile settings");
            }
        }
        catch (IOException ex) {
            throw new TokenException("cannot write profile settings: " + fileSettings, ex);
        }
        restrict(fileSettings);

        return (flagHadToken ? "Stored token cleared; other settings kept:\n"
                : "No stored token was there; other settings kept:\n") + fileSettings;
    }


    /**
     * @param prop settings after the token has been removed
     * @return true when nothing but an anonymous declaration remains, so the
     *         file carries no information the absence of a file does not
     */
    private static boolean isBare(Properties prop) {
        for (String nameKey : prop.stringPropertyNames()) {
            if (!KEY_AUTH.equals(nameKey))
                return false;
            String strMode = trimToNull(prop.getProperty(KEY_AUTH));
            if (strMode != null
                    && !AuthMode.NONE.name().toLowerCase(Locale.ROOT).equalsIgnoreCase(strMode)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Owner-only, where the filesystem supports it. A failure here is NOT
     * fatal - the token is already written and refusing to continue would not
     * unwrite it - but it is reported so a credential is never quietly
     * world-readable.
     *
     * @param fileSettings the file just written
     * @return null when the permissions were set, otherwise why they were not
     */
    public static String restrict(Path fileSettings) {
        try {
            Set<PosixFilePermission> setPerm = new HashSet<>();
            setPerm.add(PosixFilePermission.OWNER_READ);
            setPerm.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(fileSettings, setPerm);
            return null;
        }
        catch (UnsupportedOperationException ex) {
            return "filesystem does not support POSIX permissions";
        }
        catch (IOException ex) {
            return ex.getMessage() == null ? ex.toString() : ex.getMessage();
        }
    }


    /**
     * Builds the token supply for a connection.
     *
     * @param auth the profile's settings
     * @param profile the catalogue entry, for its scope and audience
     * @return the source, or NULL for AuthMode.NONE. Null is what
     *         BearerTokenInterceptor already means by "send no header", so an
     *         unauthenticated connection stays exactly what it was
     * @throws TokenException when the settings cannot produce a token
     */
    public static TokenSource_i sourceFor(ProfileAuth auth, HostProfile profile) {
        switch (auth.mode()) {
            case NONE:
                return null;

            case TOKEN:
                return new StaticTokenSource(auth.strToken());

            case JWKS:
                return new JwksFileTokenSource(auth.fileJwks(), auth.idKey(),
                        specFor(auth, profile));

            default:
                throw new TokenException("unhandled auth mode: " + auth.mode());
        }
    }


    /**
     * The spec a minted token is built from.
     *
     * Scope and audience come from the settings file when set and from the
     * catalogue otherwise. Both set is a configuration error rather than
     * something to resolve by precedence: Canton accepts one shape, and
     * guessing which produces an authentication failure that reads like a key
     * problem.
     *
     * actAs, readAs and admin apply to the CUSTOM shape ONLY. On the audience
     * and scope shapes the participant derives rights from the user named in
     * `sub`, so party lists there would be written and ignored.
     *
     * @param auth the settings
     * @param profile the catalogue entry
     * @return the spec
     */
    public static TokenSpec specFor(ProfileAuth auth, HostProfile profile) {
        String strScope = auth.strScope() != null ? auth.strScope()
                : (profile == null ? null : profile.strScope());
        String strAudience = auth.strAudience() != null ? auth.strAudience()
                : (profile == null ? null : profile.strAudience());

        boolean flagScope = strScope != null && !strScope.isBlank();
        boolean flagAudience = strAudience != null && !strAudience.isBlank();

        if (flagScope && flagAudience) {
            throw new TokenException("profile '"
                    + (profile == null ? "?" : profile.nameDisplay())
                    + "' has both a scope and an audience; Canton accepts one shape, not both");
        }

        if (flagAudience) {
            return new TokenSpec(TokenShape.AUDIENCE, auth.strSubject(), null, strAudience, null,
                    auth.ttl(), null, null, false, null, null, null);
        }
        if (flagScope) {
            return new TokenSpec(TokenShape.SCOPE, auth.strSubject(), null, null, strScope,
                    auth.ttl(), null, null, false, null, null, null);
        }

        return new TokenSpec(TokenShape.CUSTOM, auth.strSubject(), null, null, null, auth.ttl(),
                auth.lstActAs(), auth.lstReadAs(), auth.flagAdmin(), auth.idApplication(),
                auth.idLedger(), auth.idParticipant());
    }


    static AuthMode parseMode(String strMode, Path fileSettings) {
        String str = trimToNull(strMode);
        if (str == null)
            return AuthMode.NONE;

        switch (str.toLowerCase(Locale.ROOT)) {
            case "none":
                return AuthMode.NONE;
            case "token":
                return AuthMode.TOKEN;
            case "jwks":
                return AuthMode.JWKS;
            default:
                throw new TokenException("auth=" + str + " in " + fileSettings
                        + " - expected none, token or jwks");
        }
    }


    static Duration parseTtl(String strTtl, Path fileSettings) {
        String str = trimToNull(strTtl);
        if (str == null)
            return ProfileAuth.TTL_DEFAULT;

        long numSec;
        try {
            numSec = Long.parseLong(str);
        }
        catch (NumberFormatException ex) {
            throw new TokenException("ttl.seconds=" + str + " in " + fileSettings
                    + " is not a number");
        }
        if (numSec <= 0)
            throw new TokenException("ttl.seconds must be positive in " + fileSettings);
        return Duration.ofSeconds(numSec);
    }


    static List<String> splitList(String strValue) {
        String str = trimToNull(strValue);
        if (str == null)
            return List.of();

        List<String> lstOut = new ArrayList<>();
        for (String strPart : str.split(",")) {
            String strTrim = strPart.trim();
            if (!strTrim.isEmpty())
                lstOut.add(strTrim);
        }
        return List.copyOf(lstOut);
    }


    static String trimToNull(String strValue) {
        if (strValue == null)
            return null;
        String str = strValue.trim();
        return str.isEmpty() ? null : str;
    }

}
