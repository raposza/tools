// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.pqs;

import com.raposza.canton.install.PqsInstallation;
import com.raposza.canton.install.PqsSource;
import com.raposza.canton.install.VersionId;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The database name is the assertion that matters here.
 *
 * Two scribe binaries writing into one database is the failure this record
 * exists to prevent - 3.4.1 writes schema revision 034 and 3.4.3 writes 035 -
 * and it is silent when it happens: Flyway finds a schema at the wrong
 * revision and either migrates it or refuses, and neither reads as "two
 * versions were pointed at one database".
 *
 * Author Claude/bentzn
 */
class PqsSpecTest {

    private static PqsInstallation installation(String strVersion) {
        return new PqsInstallation(VersionId.parse(strVersion), null,
                Path.of("/opt/pqs", strVersion, "scribe.jar"), "035", "3.4.3", PqsSource.UNKNOWN);
    }


    /**
     * ONE NAME ACROSS VERSIONS. The version used to be appended, so every
     * binary wrote its own database; it no longer is, and two binaries of one
     * line share the database the prefix names.
     */
    @Test
    void everyBinaryGetsTheSameDatabase() {
        PqsSpec spec341 = PqsSpec.of(installation("3.4.1"));
        PqsSpec spec343 = PqsSpec.of(installation("3.4.3"));

        assertEquals("pqs", spec341.databaseName());
        assertEquals("pqs", spec343.databaseName());
        assertEquals(spec341.databaseName(), spec343.databaseName());
    }


    @Test
    void theMockWritesTheSameDatabaseAsARealBinary() {
        PqsSpec spec = PqsSpec.ofMock();

        assertFalse(spec.isReal());
        assertEquals("pqs", spec.databaseName());
        assertEquals(PqsSpec.of(installation("3.4.3")).databaseName(), spec.databaseName());
    }


    @Test
    void theDefaultsAreTheOnesScribeProcessUses() {
        PqsSpec spec = PqsSpec.ofMock();

        assertEquals(ScribeProcess.STR_DEFAULT_SCHEMA, spec.strSchema());
        assertEquals(PqsSpec.STR_DEFAULT_PREFIX, spec.strPrefix());
        assertEquals(PqsSpec.TIMEOUT_READY_DEFAULT, spec.timeoutReady());
        assertEquals(0, spec.nHeapMb());
        assertNull(spec.strToken());
    }


    @Test
    void thePrefixIsTheName() {
        PqsSpec spec = PqsSpec.of(installation("3.5.7")).withPrefix("raposza_pqs");

        assertEquals("raposza_pqs", spec.databaseName());
    }


    /**
     * The prefix ends up in an unquoted identifier, which PostgreSQL folds to
     * lower case. Caught here rather than at CREATE DATABASE, where the message
     * names the derived string and not the prefix that produced it.
     */
    @Test
    void anUnusablePrefixIsRefusedWhereItWasGiven() {
        assertThrows(IllegalArgumentException.class, () -> PqsSpec.ofMock().withPrefix("PQS"));
        assertThrows(IllegalArgumentException.class, () -> PqsSpec.ofMock().withPrefix("9pqs"));
        assertThrows(IllegalArgumentException.class, () -> PqsSpec.ofMock().withPrefix("pqs-x"));
    }


    @Test
    void aNonPositiveTimeoutIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> PqsSpec.ofMock().withTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> PqsSpec.ofMock().withTimeout(Duration.ofSeconds(-1)));
    }


    @Test
    void aNegativeHeapIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PqsSpec.ofMock().withHeapMb(-1));
    }


    @Test
    void ofRefusesTheNullThatOfMockExistsFor() {
        assertThrows(IllegalArgumentException.class, () -> PqsSpec.of(null));
    }


    /** The token is a credential and must not travel in a log line. */
    @Test
    void describeCarriesNoToken() {
        PqsSpec spec = PqsSpec.of(installation("3.4.3")).withToken("a-bearer-token");

        assertFalse(spec.describe().contains("a-bearer-token"));
        assertFalse(spec.toString().contains("a-bearer-token"));
        assertTrue(spec.describe().contains("3.4.3"));
        assertTrue(spec.describe().contains("pqs"));
    }


    /**
     * Resolution against a line with no staged binary is the NORMAL case on
     * 2.x, and it answers with the mock rather than with nothing. Whichever
     * this machine has, the spec is usable and says which it is.
     */
    @Test
    void resolutionAlwaysYieldsAUsableSpec() {
        PqsSpec spec = PqsSpec.resolveForLine("2.9");

        assertEquals(spec.isReal(), spec.installation() != null);
        assertFalse(spec.databaseName().isBlank());
    }


    /**
     * The mock stands in front of a GENERATION even with no jar to read one
     * off, and it is the mock's RENDERED COMMAND that made this matter: a 2.x
     * stack printed, as a line to paste on a licensed box, a flag that vendor
     * scribe v0.5.5 accepts and drops.
     */
    @Test
    void theMockCarriesTheGenerationItWasResolvedFor() {
        PqsSpec spec2x = PqsSpec.ofMock("2.10");

        assertFalse(spec2x.isReal());
        assertTrue(spec2x.isGenerationKnown());
        assertTrue(spec2x.isCanton2x());
        assertEquals("2.10", spec2x.strCantonLine());

        assertFalse(PqsSpec.ofMock("3.5").isCanton2x());
        assertFalse(PqsSpec.ofMock().isGenerationKnown());
        assertFalse(PqsSpec.ofMock().isCanton2x());
    }


    /**
     * The binary's DECLARED line wins. A spec asserting a generation its own
     * jar denies is two answers to one question, which is how the stale one
     * survives.
     */
    @Test
    void aRealSpecTakesTheLineFromTheBinary() {
        PqsSpec spec = PqsSpec.of(installation("3.4.3"));

        assertEquals("3.4", spec.strCantonLine());
        assertFalse(spec.isCanton2x());
        assertThrows(IllegalStateException.class, () -> spec.withCantonLine("2.10"));
    }
}
