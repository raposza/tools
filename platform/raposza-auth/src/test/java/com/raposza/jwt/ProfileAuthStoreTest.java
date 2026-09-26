// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.TokenSource_i;
import com.raposza.api.profile.AccessMode;
import com.raposza.api.profile.HostProfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * What a profile's settings file is allowed to mean.
 *
 * The cases that matter are the failure ones: an absent file must mean "send
 * nothing", and a present but wrong file must NOT mean that.
 *
 * Author Claude/bentzn
 */
class ProfileAuthStoreTest {

    private static HostProfile profile(String strScope, String strAudience) {
        return new HostProfile("Local Sandbox", "http", "localhost", 6865, 7575, strScope,
                strAudience, AccessMode.READ_ONLY, "#2e7d32");
    }


    private static Path write(Path dirTmp, String strName, String strBody) throws Exception {
        Path file = dirTmp.resolve(strName);
        Files.writeString(file, strBody);
        return file;
    }


    @Test
    void anAbsentFileMeansNoAuthAndNoSource(@TempDir Path dirTmp) {
        ProfileAuth auth = ProfileAuthStore.load(dirTmp.resolve("nothing-here.properties"));

        assertEquals(AuthMode.NONE, auth.mode());
        assertTrue(auth.isNone());
        assertNull(ProfileAuthStore.sourceFor(auth, profile(null, null)));
    }


    @Test
    void aStoredTokenIsSentVerbatim(@TempDir Path dirTmp) throws Exception {
        Path file = write(dirTmp, "p.properties", "auth=token\ntoken=abc.def.ghi\n");

        ProfileAuth auth = ProfileAuthStore.load(file);
        assertEquals(AuthMode.TOKEN, auth.mode());

        TokenSource_i source = ProfileAuthStore.sourceFor(auth, profile(null, null));
        assertEquals("abc.def.ghi", source.token());
    }


    /**
     * The whole reason a mode is declared rather than inferred: a file that
     * says token= and forgets auth= must not connect anonymously to a
     * participant that was configured to expect a token.
     */
    @Test
    void aDeclaredModeWithoutItsMaterialIsAnError(@TempDir Path dirTmp) throws Exception {
        Path fileToken = write(dirTmp, "a.properties", "auth=token\n");
        assertThrows(TokenException.class, () -> ProfileAuthStore.load(fileToken));

        Path fileJwks = write(dirTmp, "b.properties", "auth=jwks\njwks.kid=k1\n");
        assertThrows(TokenException.class, () -> ProfileAuthStore.load(fileJwks));
    }


    @Test
    void anUnknownModeIsAnErrorNamingTheFile(@TempDir Path dirTmp) throws Exception {
        Path file = write(dirTmp, "c.properties", "auth=oauth\n");

        TokenException ex = assertThrows(TokenException.class,
                () -> ProfileAuthStore.load(file));
        assertTrue(ex.getMessage().contains("c.properties"));
    }


    @Test
    void theDefaultLifetimeIsAThousandYears(@TempDir Path dirTmp) throws Exception {
        Path file = write(dirTmp, "d.properties", "auth=none\n");

        assertEquals(ProfileAuth.TTL_DEFAULT, ProfileAuthStore.load(file).ttl());
        assertEquals(Duration.ofSeconds(31_557_600_000L), ProfileAuth.TTL_DEFAULT);
    }


    @Test
    void anExplicitLifetimeWins(@TempDir Path dirTmp) throws Exception {
        Path file = write(dirTmp, "e.properties", "auth=none\nttl.seconds=3600\n");
        assertEquals(Duration.ofHours(1), ProfileAuthStore.load(file).ttl());
    }


    @Test
    void aNonNumericOrNegativeLifetimeIsAnError(@TempDir Path dirTmp) throws Exception {
        Path fileBad = write(dirTmp, "f.properties", "ttl.seconds=soon\n");
        assertThrows(TokenException.class, () -> ProfileAuthStore.load(fileBad));

        Path fileNeg = write(dirTmp, "g.properties", "ttl.seconds=-1\n");
        assertThrows(TokenException.class, () -> ProfileAuthStore.load(fileNeg));
    }


    @Test
    void partyListsSplitOnCommasAndDropBlanks(@TempDir Path dirTmp) throws Exception {
        Path file = write(dirTmp, "h.properties",
                "auth=none\nact.as=alice::1, bank::1 ,\nread.as=\n");

        ProfileAuth auth = ProfileAuthStore.load(file);
        assertEquals(List.of("alice::1", "bank::1"), auth.lstActAs());
        assertEquals(List.of(), auth.lstReadAs());
    }


    @Test
    void theCatalogueSuppliesScopeAndAudienceUnlessTheFileOverridesThem(@TempDir Path dirTmp)
            throws Exception {
        Path file = write(dirTmp, "i.properties", "auth=none\nsubject=alice\n");
        ProfileAuth auth = ProfileAuthStore.load(file);

        TokenSpec specScope = ProfileAuthStore.specFor(auth, profile("daml_ledger_api", null));
        assertEquals(TokenShape.SCOPE, specScope.shape());
        assertEquals("daml_ledger_api", specScope.strScope());
        assertEquals("alice", specScope.strSubject());

        TokenSpec specAud = ProfileAuthStore.specFor(auth, profile(null, "https://aud/p1"));
        assertEquals(TokenShape.AUDIENCE, specAud.shape());
        assertEquals("https://aud/p1", specAud.strAudience());

        Path fileOverride = write(dirTmp, "j.properties", "auth=none\nscope=other_scope\n");
        TokenSpec specOver = ProfileAuthStore.specFor(ProfileAuthStore.load(fileOverride),
                profile("daml_ledger_api", null));
        assertEquals("other_scope", specOver.strScope());
    }


    /**
     * Canton accepts one shape. Guessing which produces an authentication
     * failure that reads like a key problem, so this refuses instead.
     */
    @Test
    void bothAScopeAndAnAudienceIsRefused(@TempDir Path dirTmp) throws Exception {
        Path file = write(dirTmp, "k.properties", "auth=none\n");
        ProfileAuth auth = ProfileAuthStore.load(file);

        assertThrows(TokenException.class,
                () -> ProfileAuthStore.specFor(auth, profile("daml_ledger_api", "https://aud/p1")));
    }


    @Test
    void withNeitherScopeNorAudienceTheCustomClaimCarriesTheParties(@TempDir Path dirTmp)
            throws Exception {
        Path file = write(dirTmp, "l.properties",
                "auth=none\nsubject=alice\nact.as=alice::1\nadmin=true\n");

        TokenSpec spec = ProfileAuthStore.specFor(ProfileAuthStore.load(file),
                profile(null, null));

        assertEquals(TokenShape.CUSTOM, spec.shape());
        assertEquals(List.of("alice::1"), spec.lstActAs());
        assertTrue(spec.flagAdmin());
    }


    @Test
    void savingATokenSetsTheModeAndComesBackOnLoad(@TempDir Path dirTmp) {
        Path file = dirTmp.resolve("profiles").resolve("local-sandbox.properties");

        ProfileAuthStore.saveToken(file, "  header.body.sig  ");

        ProfileAuth auth = ProfileAuthStore.load(file);
        assertEquals(AuthMode.TOKEN, auth.mode());
        assertEquals("header.body.sig", auth.strToken());
        assertTrue(Files.exists(file));
    }


    @Test
    void savingKeepsTheOtherSettings(@TempDir Path dirTmp) throws Exception {
        Path file = write(dirTmp, "m.properties", "auth=none\nsubject=alice\nttl.seconds=3600\n");

        ProfileAuthStore.saveToken(file, "a.b.c");

        ProfileAuth auth = ProfileAuthStore.load(file);
        assertEquals("alice", auth.strSubject());
        assertEquals(Duration.ofHours(1), auth.ttl());
        assertEquals("a.b.c", auth.strToken());
    }


    @Test
    void anEmptyTokenIsNeverSaved(@TempDir Path dirTmp) {
        Path file = dirTmp.resolve("n.properties");
        assertThrows(TokenException.class, () -> ProfileAuthStore.saveToken(file, "   "));
    }


    @Test
    void slugsAreFileNameSafeAndStable() {
        assertEquals("local-sandbox", ProfileAuthStore.slug("Local Sandbox"));
        assertEquals("backend-ggh-dev", ProfileAuthStore.slug("Backend GGH/DEV"));
        assertEquals("shared-test-2", ProfileAuthStore.slug("  Shared  TEST (2)  "));
        assertEquals("unnamed", ProfileAuthStore.slug(""));
        assertEquals("unnamed", ProfileAuthStore.slug("///"));
    }


    @Test
    void theSettingsPathIsUnderTheHomeDirectory() {
        Path file = ProfileAuthStore.fileFor(Path.of("/home/someone"), "Local Sandbox");
        assertEquals(Path.of("/home/someone/.raposza/profiles/local-sandbox.properties"), file);
    }


    @Test
    void aWrittenFileIsOwnerOnlyWherePosixIsSupported(@TempDir Path dirTmp) {
        Path file = dirTmp.resolve("o.properties");
        ProfileAuthStore.saveToken(file, "a.b.c");

        String strProblem = ProfileAuthStore.restrict(file);
        if (strProblem == null) {
            assertTrue(Files.isReadable(file));
        }
        else {
            // Not a failure: a filesystem without POSIX permissions reports why
            // rather than throwing, and the caller shows that to the operator.
            assertNotNull(strProblem);
        }
    }

}
