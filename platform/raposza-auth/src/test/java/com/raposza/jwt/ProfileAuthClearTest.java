// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What clearing a stored token is allowed to leave behind.
 *
 * Author Claude/bentzn
 */
class ProfileAuthClearTest {

    private static Path write(Path dir, String strBody) throws IOException {
        Path file = dir.resolve("local-sandbox.properties");
        Files.writeString(file, strBody, StandardCharsets.UTF_8);
        return file;
    }


    /**
     * The declaration moves with the credential. Leaving auth=token behind
     * produces a file that refuses to load, which reads as a settings error
     * rather than as the absence that was asked for.
     */
    @Test
    void clearingATokenOnlyProfileRemovesTheFile(@TempDir Path dir) throws IOException {
        Path file = write(dir, "auth = token\ntoken = abc.def.ghi\n");

        String strOutcome = ProfileAuthStore.clearToken(file);

        assertFalse(Files.exists(file));
        assertTrue(strOutcome.contains("cleared"));
    }


    /** Clearing a credential is not discarding a configuration. */
    @Test
    void otherSettingsSurviveAndTheModeBecomesNone(@TempDir Path dir) throws IOException {
        Path file = write(dir, "auth = token\ntoken = abc.def.ghi\nsubject = alice\n"
                + "read.as = Alice::1220ab\n");

        ProfileAuthStore.clearToken(file);

        assertTrue(Files.exists(file));
        ProfileAuth auth = ProfileAuthStore.load(file);
        assertEquals(AuthMode.NONE, auth.mode());
        assertNull(auth.strToken());
        assertEquals("alice", auth.strSubject());
        assertEquals(1, auth.lstReadAs().size());
    }


    /** A jwks profile keeps its mode: there is no stored token to remove. */
    @Test
    void aJwksProfileKeepsItsMode(@TempDir Path dir) throws IOException {
        Path file = write(dir, "auth = jwks\njwks.file = /tmp/keys.json\n");

        String strOutcome = ProfileAuthStore.clearToken(file);

        assertEquals(AuthMode.JWKS, ProfileAuthStore.load(file).mode());
        assertTrue(strOutcome.contains("No stored token"));
    }


    /** Clearing what is not there is not an error. */
    @Test
    void clearingAnAbsentFileSaysSoRatherThanThrowing(@TempDir Path dir) {
        String strOutcome = ProfileAuthStore.clearToken(dir.resolve("nothing.properties"));

        assertTrue(strOutcome.contains("No settings file"));
    }

}
