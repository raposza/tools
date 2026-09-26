// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The PQS pane: the facts, once each, and none of the noise.
 *
 * Every input here is a line taken from his run of 2026-09-22, escapes and
 * all, rather than one written to pass.
 *
 * Author Claude/bentzn
 */
class PqsLogTest {

    private final PqsLog log = new PqsLog();


    @Test
    void itKeepsTheBinaryAndTheSchema() {
        String strOut = log.strFor("18:41:22.932 \u001b[36mI\u001b[0m [zio-fiber-176033986]"
                + " com.digitalasset.scribe.appversion.package:17 \u001b[36mscribe, version:"
                + " v3.5.7 (daml-sdk.version: 3.5.2, postgres-document.schema: 041)\u001b[0m");

        assertEquals("scribe v3.5.7, schema 041", strOut);
        // AND NO ESCAPES REACH THE PANE.
        assertTrue(strOut.indexOf('\u001b') < 0, strOut);
    }


    @Test
    void theRefusedTokenIsOneLineAndSaysWhatItMeans() {
        String strFirst = log.strFor("18:41:23.841 \u001b[31mE\u001b[0m [zio-fiber-1278311096]"
                + " com.digitalasset.auth.TokenService:230 \u001b[31mAuth token couldn't be"
                + " acquired due to: HTTP 401\u001b[0m application=scribe");

        assertTrue(strFirst.contains("REFUSED"), strFirst);
        assertTrue(strFirst.contains("HTTP 401"), strFirst);
        assertTrue(strFirst.contains("will not ingest"), strFirst);

        // THE NINETY REPEATS AFTER IT ARE ONE LINE, not ninety.
        for (int cntLoop = 0; cntLoop < 90; cntLoop++) {
            assertNull(log.strFor("18:41:2" + (cntLoop % 10) + ".8 \u001b[31mE\u001b[0m"
                    + " com.digitalasset.auth.TokenService:230 Auth token couldn't be"
                    + " acquired due to: HTTP 401 application=scribe"));
        }
        assertNull(log.strFor("18:41:23.843 com.digitalasset.auth.TokenService.auth:148"
                + " Retrying auth token acquisition: Auth token couldn't be acquired"));
        assertNull(log.strFor("18:41:23.856 com.digitalasset.auth.TokenService:202"
                + " Acquiring auth token"));
    }


    @Test
    void theDatabaseProbeIsPlainEnglishAndSaidOnce() {
        assertEquals("database ready", log.strFor("18:41:23.425 \u001b[36mI\u001b[0m"
                + " [zio-fiber-1748872823] zio.jdbc.shims.postgres:139 \u001b[36mDatabase"
                + " probe (select 1) successful\u001b[0m application=scribe"));
        assertNull(log.strFor("18:41:53.438 zio.jdbc.shims.postgres:139 Database probe"
                + " (select 1) successful application=scribe"));
    }


    /**
     * THE CONFIGURATION DUMP IS A BLOCK. Its lines carry no marker of their
     * own, so a line-at-a-time filter that did not track the block would let
     * sixty lines of HOCON through.
     */
    @Test
    void theConfigurationDumpIsDroppedWhole() {
        assertNull(log.strFor("18:41:22.879 com.digitalasset.scribe.configuration.package:52"
                + " Applied configuration:"));
        assertNull(log.strFor("health {"));
        assertNull(log.strFor("    address=\"127.0.0.1\""));
        assertNull(log.strFor("    port=\"30052\""));
        assertNull(log.strFor("}"));
        // AND THE LINE AFTER THE BLOCK IS READ AGAIN.
        assertEquals("database ready", log.strFor("zio.jdbc.shims.postgres:139 Database"
                + " probe (select 1) successful"));
    }


    @Test
    void theNoiseIsGone() {
        assertNull(log.strFor("[diagnostics] Starting thread dumps collector: interval=PT1M"));
        assertNull(log.strFor("[diagnostics] WARN: Could not self-attach to JVM"));
        assertNull(log.strFor("ATTN! OpenTelemetry Java Agent is not found."));
        assertNull(log.strFor("Please provide OpenTelemetry Java Agent using environment"));
        assertNull(log.strFor("See also https://opentelemetry.io/docs/instrumentation/java/"));
        assertNull(log.strFor("\tat com.digitalasset.scribe.app.ComposableApp.run"));
        assertNull(log.strFor("\tat zio.ZIO$.fail$$anonfun$1(ZIO.scala:3251)"));
        assertNull(log.strFor(""));
        assertNull(log.strFor(null));
        // THE COMMAND LINE TOO. It carries the client secret's placeholder and
        // a token endpoint with the audience in it, and it is in the log file.
        assertNull(log.strFor("$ java -jar /home/bentzn/.pqs/line/3.5/scribe.jar pipeline"));
    }


    @Test
    void anErrorIsKeptWithoutItsStack() {
        String strOut = log.strFor("timestamp=2026-09-22T17:41:54Z level=ERROR"
                + " thread=#zio-fiber-176033986 message=\"\" cause=\"java.sql.SQLException:"
                + " connection refused");

        assertTrue(strOut.startsWith("scribe reported an error: "), strOut);
        assertTrue(strOut.contains("SQLException"), strOut);
        assertTrue(strOut.length() < 240, String.valueOf(strOut.length()));
    }


    /**
     * AND THE ERROR FORM OF A REFUSAL IS NOT A SECOND MESSAGE.
     *
     * The classifier reads the refusal before it reads the level, so the
     * `level=ERROR` line scribe prints after ninety retries carries the same
     * fact as the first refusal and collapses into it. MEASURED, which is how
     * this test came to exist: it first asserted the error wording for this
     * input, the classifier answered with the refusal wording, and the gate
     * said so.
     */
    @Test
    void theErrorFormOfARefusalIsNotASecondMessage() {
        String strFirst = log.strFor("com.digitalasset.auth.TokenService:230 Auth token"
                + " couldn't be acquired due to: HTTP 401 application=scribe");

        assertTrue(strFirst.contains("REFUSED"), strFirst);
        assertNull(log.strFor("timestamp=2026-09-22T17:41:54Z level=ERROR message=\"\""
                + " cause=\"java.io.IOException: Auth token couldn't be acquired due to:"
                + " HTTP 401"));
    }


    @Test
    void aResetShowsItAllAgain() {
        assertEquals("database ready", log.strFor("Database probe (select 1) successful"));
        assertNull(log.strFor("Database probe (select 1) successful"));
        log.reset();
        assertEquals("database ready", log.strFor("Database probe (select 1) successful"));
    }

}
