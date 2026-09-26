// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.pqs;

import com.raposza.canton.install.PqsInstallation;
import com.raposza.canton.install.PqsSource;
import com.raposza.canton.install.VersionId;
import com.raposza.runtime.db.PostgresCoordinates;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which column gets `--target-postgres-schema`, asserted without a PostgreSQL
 * and without a scribe.
 *
 * The flag has THREE behaviours and only one of them is a failure that
 * announces itself: 3.4 and 3.5 honour it, vendor v0.5.5 accepts it and drops
 * it, and the 2.x stand-in refuses it as unknown. The middle one
 * is the silently-wrong category - per-run schema isolation does not happen and
 * nothing errors - so what has to be checked is the ARGUMENT VECTOR rather than
 * a run, because a run cannot tell the first two apart.
 *
 * THE MOCK IS THE SAME QUESTION with no binary to read the answer off. Its
 * command is rendered to be pasted onto a licensed machine, which is a machine
 * of some GENERATION, and until 2026-08-16 it rendered the 3.x shape on both -
 * offering a 2.x operator the one form that generation accepts and ignores.
 *
 * `command()` builds a list and starts nothing, which is what makes this a unit
 * test.
 *
 * Author Claude/bentzn
 */
class ScribeProcessSchemaTest {

    private static final PostgresCoordinates PG =
            new PostgresCoordinates("localhost", 33321, "pqs_x", "raposza", "raposza");

    private static final String STR_FLAG = "--target-postgres-schema";


    private static PqsInstallation install(String strVersion, String strLine) {
        return new PqsInstallation(VersionId.parse(strVersion), strLine,
                Path.of("/tmp/scribe.jar"), "041", "3.5.2", PqsSource.MANUAL);
    }


    private static ScribeProcess process(PqsInstallation installation) {
        return new ScribeProcess(installation, null, PG, "pqs", "localhost", 6865, null,
                null, Path.of("/tmp"), 0, N_PORT_HEALTH);
    }


    private static ScribeProcess mock(String strCantonLine) {
        return new ScribeProcess(null, strCantonLine, PG, "pqs", "localhost", 6865, null,
                null, Path.of("/tmp"), 0, N_PORT_HEALTH);
    }


    /**
     * Not 8080, so that a rendered command carrying scribe's own default would
     * be visible as one.
     */
    private static final int N_PORT_HEALTH = 6870;


    /**
     * Measured, and asserted here because the binary will not say so
     * itself: scribe v3.5.7 takes `--health-port` and SILENTLY IGNORES
     * `--health.port`. A rename of the constant that put the dotted form back
     * would leave every stack on 8080 with nothing failing until two of them
     * ran at once.
     */
    @Test
    void theHealthPortIsNamedWithTheSpellingScribeReads() {
        List<String> lstCommand = process(install("3.5.7", "3.5")).command();

        assertTrue(lstCommand.contains("--health-port=" + N_PORT_HEALTH),
                "the health port must be named, and with a hyphen: " + lstCommand);
        assertFalse(lstCommand.stream().anyMatch(str -> str.startsWith("--health.port")),
                "--health.port is accepted and ignored by scribe, so it must never"
                        + " be rendered: " + lstCommand);
    }


    /**
     * @return a scribe with no health port, which is what a caller that has no
     *         block to take one from gets
     */
    private static ScribeProcess processNoHealthPort() {
        return new ScribeProcess(install("3.5.7", "3.5"), null, PG, "pqs", "localhost", 6865,
                null, null, Path.of("/tmp"), 0, 0);
    }


    @Test
    void zeroLeavesTheFlagOutAltogether() {
        assertFalse(processNoHealthPort().command().stream()
                .anyMatch(str -> str.startsWith("--health")),
                "0 must leave the flag out rather than render it as a zero");
    }


    private static boolean namesSchema(ScribeProcess scribe) {
        for (String strArg : scribe.command()) {
            if (strArg.startsWith(STR_FLAG))
                return true;
        }
        return false;
    }


    @Test
    void theThreeAndFiveLinesAreGivenTheFlag() {
        assertTrue(process(install("3.5.7", "3.5")).isSchemaNamed());
        assertTrue(namesSchema(process(install("3.5.7", "3.5"))));
        assertTrue(namesSchema(process(install("3.4.3", "3.4"))));
    }


    @Test
    void theTwoColumnIsNotGivenTheFlagAtAll() {
        // v0.5.5 pairs with Canton 2.10 and the LINE is what says so: the
        // scribe version is 0.5.5, so a major read off the version would be 0
        // and this branch would be taken on every 3.x binary as well.
        ScribeProcess scribe = process(install("0.5.5", "2.10"));

        assertFalse(scribe.isSchemaNamed());
        assertFalse(namesSchema(scribe), "the flag is inert on 2.x and the"
                + " stand-in refuses it outright");

        // And everything else it needs is still there, so this is an omission
        // rather than a truncated command.
        List<String> lstCommand = scribe.command();
        assertTrue(lstCommand.contains("--target-postgres-database=" + PG.strDatabase()));
        assertTrue(lstCommand.contains("postgres-document"));
    }


    /**
     * The line the mock PRINTS is a claim about the generation it stands in
     * front of, not about the schema it created for itself in-process.
     */
    @Test
    void theMockFollowsTheGenerationItStandsInFor() {
        ScribeProcess scribe3x = mock("3.5");

        assertFalse(scribe3x.isReal());
        assertTrue(scribe3x.isSchemaNamed());
        assertTrue(namesSchema(scribe3x), "on 3.x the flag is honoured and the"
                + " rendered command carries it");

        ScribeProcess scribe2x = mock("2.10");

        assertFalse(scribe2x.isReal());
        assertTrue(scribe2x.isCanton2x());
        assertFalse(scribe2x.isSchemaNamed(), "the mock cannot offer a 2.x operator"
                + " a flag that v0.5.5 accepts and drops");
        assertFalse(namesSchema(scribe2x));
        assertFalse(scribe2x.commandForShell().contains(STR_FLAG));

        // The omission is the only difference; the rest of the line is intact.
        assertTrue(scribe2x.command().contains("--target-postgres-database=" + PG.strDatabase()));
        assertTrue(scribe2x.command().contains("postgres-document"));
    }


    /**
     * No caller in the tree produces this - every one of them resolves from a
     * Canton installation - and it is asserted so the fallback is a decision
     * rather than an accident. start() logs a warning in this case.
     */
    @Test
    void aMockWithNoGenerationRendersTheThreeShape() {
        ScribeProcess scribe = mock(null);

        assertFalse(scribe.isCanton2x());
        assertTrue(scribe.isSchemaNamed());
        assertTrue(namesSchema(scribe));
    }

}
