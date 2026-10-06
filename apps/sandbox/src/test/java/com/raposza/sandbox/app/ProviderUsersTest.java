// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The provider's user list, read as `UiController.mapUsers` writes it.
 *
 * THE CONTROLS CAN FAIL: a name listed twice must come out once, the claims
 * object must not be read as more names, and a body with no `names` must be
 * refused rather than read as a provider with nobody on it.
 *
 * Author Claude/bentzn
 */
class ProviderUsersTest {

    @Test
    void theNamesAndTheFileAreRead() throws IOException {
        ProviderUsers.Listing listing = ProviderUsers.listingOf(
                "{\"file\":\"/home/u/.raposza/jwtmint/keys/users.json\","
                + "\"names\":[\"app-provider\",\"treasury\",\"fund\",\"treasury\"],"
                + "\"claims\":{\"treasury\":{\"name\":\"Nanoq Treasury\"}}}");
        assertEquals("/home/u/.raposza/jwtmint/keys/users.json", listing.strFile());
        assertEquals(List.of("app-provider", "treasury", "fund"), listing.lstName());
    }


    @Test
    void bothShapesOfAUserGiveItsPassword() throws IOException {
        Map<String, String> mapPassword = ProviderUsers.mapPasswordOf(
                "{\"alice\":\"a1\",\"bob\":{\"password\":\"b2\",\"claims\":{\"name\":\"Bob\"}}}");
        assertEquals(Map.of("alice", "a1", "bob", "b2"), mapPassword);
    }


    @Test
    void aBodyWithoutNamesIsRefused() {
        assertThrows(IOException.class,
                () -> ProviderUsers.listingOf("{\"error\":\"that is not the admin credential\"}"));
    }

}
