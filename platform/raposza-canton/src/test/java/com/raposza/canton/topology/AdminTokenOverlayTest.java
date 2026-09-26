// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the overlay must state EXPLICITLY, because the defaults point the other
 * way.
 *
 * `adminClaim` and `actAsAnyPartyClaim` are both false in Canton 3.5.11 -
 * measured with jshell. An overlay that omits `admin-claim` therefore produces
 * a participant whose admin token cannot administer over the Ledger API, and
 * the resulting failure is a refused `users.create` in the middle of a console
 * script rather than anything that mentions a claim.
 *
 * Author Claude/bentzn
 */
class AdminTokenOverlayTest {

    private static final String STR_TOKEN = "raposza-admin-token";


    @Test
    void theAdminClaimIsWrittenRatherThanAssumed() {
        String strConf = AdminTokenOverlay.ofAdmin(STR_TOKEN).render();

        assertTrue(strConf.contains(AdminTokenOverlay.STR_KEY_ADMIN_CLAIM + " = true"),
                "admin-claim is not set; Canton defaults it to false and the console would be"
                        + " refused on the Ledger API");
    }


    /**
     * The shortcut is available and is not the default. Written as a test so
     * that a change of mind has to be deliberate.
     */
    @Test
    void actAsAnyPartyIsOffUnlessTheFactoryThatNamesItIsUsed() {
        assertTrue(AdminTokenOverlay.ofAdmin(STR_TOKEN).render()
                .contains(AdminTokenOverlay.STR_KEY_ACT_AS_ANY + " = false"));
        assertFalse(AdminTokenOverlay.ofAdmin(STR_TOKEN).flagActAsAnyParty());

        assertTrue(AdminTokenOverlay.ofAdminActingAsAnyParty(STR_TOKEN).render()
                .contains(AdminTokenOverlay.STR_KEY_ACT_AS_ANY + " = true"));
        assertTrue(AdminTokenOverlay.ofAdminActingAsAnyParty(STR_TOKEN).flagActAsAnyParty());
    }


    @Test
    void theBlockSitsOnLedgerApiBesideAuthServices() {
        String strConf = AdminTokenOverlay.ofAdmin(STR_TOKEN).render();

        assertTrue(strConf.contains("canton.participants." + StorageOverlay.STR_NODE_PARTICIPANT
                + ".ledger-api." + AdminTokenOverlay.STR_KEY_BLOCK));
    }


    @Test
    void aNamedParticipantReachesTheKeyPath() {
        assertTrue(AdminTokenOverlay.ofAdmin(STR_TOKEN).render("other")
                .contains("canton.participants.other.ledger-api."));
    }


    @Test
    void theTokenIsQuotedAndPresent() {
        String strConf = AdminTokenOverlay.ofAdmin(STR_TOKEN).render();

        assertTrue(strConf.contains(AdminTokenOverlay.STR_KEY_FIXED + " = \"" + STR_TOKEN + "\""));
    }


    /**
     * The duration is Canton's own and applies to a token it mints. Omitted
     * unless asked for, so the overlay states only what it means to change.
     */
    @Test
    void theDurationIsOmittedUnlessGiven() {
        assertFalse(AdminTokenOverlay.ofAdmin(STR_TOKEN).render()
                .contains(AdminTokenOverlay.STR_KEY_DURATION));
        assertTrue(AdminTokenOverlay.ofAdmin(STR_TOKEN).withDuration(Duration.ofMinutes(30))
                .render().contains(AdminTokenOverlay.STR_KEY_DURATION + " = 1800s"));
    }


    /** A token in a log line is a credential leak; the point of describe is to be logged. */
    @Test
    void describeCarriesNoToken() {
        assertFalse(AdminTokenOverlay.ofAdmin(STR_TOKEN).describe().contains(STR_TOKEN));
        assertFalse(AdminTokenOverlay.ofAdmin(STR_TOKEN).toString().contains(STR_TOKEN));
    }


    @Test
    void theMeasuredDefaultIsRecorded() {
        assertEquals(Duration.ofMinutes(5), AdminTokenOverlay.DURATION_DEFAULT);
    }


    /**
     * Canton refuses the block outright without this, by validation rather than
     * by parsing - so it is not a key that a stack silently ignores. Rendered
     * from this overlay so that the two travel together.
     *
     * NOT asserted: that the flag is rendered BEFORE the block. It is, for a
     * reader, but HOCON is not order-sensitive across distinct keys, so
     * requiring it would dress a preference as a requirement. The first version
     * of this test did assert it and failed - on the file's own comment line,
     * which contained the key and therefore matched before the statement did.
     */
    @Test
    void theEscapeHatchTravelsWithTheBlock() {
        String strConf = AdminTokenOverlay.ofAdmin(STR_TOKEN).render();

        assertTrue(strConf.contains(AdminTokenOverlay.STR_KEY_NON_STANDARD + " = yes"),
                "without the flag Canton refuses to start with a pinned admin token");
        assertTrue(strConf.contains("." + AdminTokenOverlay.STR_KEY_BLOCK + " {"),
                "the block itself is missing from the overlay that exists to render it");
    }


    @Test
    void theArgumentsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> AdminTokenOverlay.ofAdmin(" "));
        assertThrows(IllegalArgumentException.class,
                () -> AdminTokenOverlay.ofAdmin(STR_TOKEN).render(null));
        assertThrows(IllegalArgumentException.class,
                () -> AdminTokenOverlay.ofAdmin(STR_TOKEN).withDuration(Duration.ZERO));
    }
}
