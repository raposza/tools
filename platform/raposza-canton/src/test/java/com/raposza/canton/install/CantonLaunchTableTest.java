// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.install;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table is GENERATED, so this asserts the INVARIANTS a regeneration must
 * keep rather than the exact contents - a test that pinned every flag would
 * fail on the next Canton release for the correct reason, which is a bad
 * trade.
 *
 * The four facts pinned here were each measured and each contradicts something
 * the codebase assumed: `sandbox` starts at 3.4.4 rather than at 3.0, the port
 * flags are uniform across every version that has the subcommand, `daemon`
 * exists everywhere, and an unmeasured version answers UNKNOWN rather than NO.
 *
 * Author Claude/bentzn
 */
class CantonLaunchTableTest {

    private static final String STR_SANDBOX = "sandbox";


    @Test
    void everyMeasuredBinaryOffersTheDaemonLauncher() {
        assertFalse(CantonLaunchTable.lstEntry().isEmpty());
        for (CantonLaunchTable.Entry entry : CantonLaunchTable.lstEntry()) {
            assertTrue(entry.hasCommand("daemon"), entry.version() + " has no daemon");
            assertTrue(entry.hasCommand("run"), entry.version() + " has no run");
        }
    }


    /**
     * THE FINDING THIS TABLE EXISTS FOR. `CantonSandboxProcess` guarded on
     * major >= 3, and measurement showed the subcommand appearing at 3.4.4.
     * Every 2.x binary lacks it and every entry that has it is 3.4 or later.
     */
    @Test
    void theSandboxSubcommandIsAbsentOnEvery2x() {
        for (CantonLaunchTable.Entry entry : CantonLaunchTable.lstEntry()) {
            if (entry.version().major() == 2)
                assertFalse(entry.hasCommand(STR_SANDBOX),
                        entry.version() + " unexpectedly offers sandbox");
        }
    }


    /**
     * The port flags being uniform is what lets port assignment carry no
     * version branch at all. If a future release drops one, this fails and the
     * branch becomes necessary - which is the moment worth being told about.
     */
    @Test
    void everySandboxCapableBinaryTakesTheSamePortFlags() {
        List<String> lstFlag = List.of("--ledger-api-port", "--admin-api-port",
                "--sequencer-public-port", "--sequencer-admin-port",
                "--mediator-admin-port", "--canton-port-file");

        int cntChecked = 0;
        for (CantonLaunchTable.Entry entry : CantonLaunchTable.lstEntry()) {
            if (!entry.hasCommand(STR_SANDBOX))
                continue;
            cntChecked++;
            for (String strFlag : lstFlag) {
                assertTrue(entry.hasFlag(STR_SANDBOX, strFlag),
                        entry.version() + " sandbox does not take " + strFlag);
            }
        }
        assertTrue(cntChecked > 0, "no measured binary offers the sandbox subcommand");
    }


    @Test
    void everyEntryDeclaresAManifestEntryPoint() {
        for (CantonLaunchTable.Entry entry : CantonLaunchTable.lstEntry()) {
            assertFalse(entry.strMainClass().isBlank());
            assertTrue(entry.lstFlag(CantonLaunchTable.STR_SCOPE_GLOBAL).contains("--config"),
                    entry.version() + " does not take --config");
        }
    }


    /**
     * A version nobody measured answers UNKNOWN. Returning false here would be
     * the hardcoded rule again in a different shape, and the caller would have
     * no way to tell a measured NO from an absent row.
     */
    @Test
    void anUnmeasuredVersionIsEmptyRatherThanFalse() {
        VersionId version = VersionId.parse("3.0.0");

        assertTrue(CantonLaunchTable.find(version).isEmpty());
        assertEquals(Optional.empty(), CantonLaunchTable.hasCommand(version, STR_SANDBOX));
    }


    /**
     * A Daml Assistant install declares no edition, so a lookup carrying
     * UNKNOWN must not miss a row measured as OPEN_SOURCE or ENTERPRISE.
     */
    @Test
    void anUnknownEditionMatchesAMeasuredOne() {
        for (CantonLaunchTable.Entry entry : CantonLaunchTable.lstEntry()) {
            assertTrue(CantonLaunchTable.find(entry.version(), Edition.UNKNOWN).isPresent());
            assertTrue(CantonLaunchTable.find(entry.version(), entry.edition()).isPresent());
        }
    }
}
