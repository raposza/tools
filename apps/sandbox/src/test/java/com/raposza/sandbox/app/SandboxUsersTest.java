// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The user list `GET /v2/users` answers with, read into names and the next
 * page - and a body that is not that shape refused rather than read as empty.
 *
 * Author Claude/bentzn
 */
class SandboxUsersTest {

    @Test
    void theIdsComeInLedgerOrderOnceEachWithTheNextPageToken() throws IOException {
        SandboxUsers.Page page = SandboxUsers.pageOf("{\"users\":[{\"id\":\"participant_admin\"},"
                + "{\"id\":\"technician\",\"primaryParty\":\"p::1220\"},{\"id\":\"technician\"},"
                + "{\"id\":\"\"}],\"nextPageToken\":\"abc\"}");
        assertEquals(List.of("participant_admin", "technician"), page.lstUser());
        assertEquals("abc", page.strNext());

        assertEquals("", SandboxUsers.pageOf("{\"users\":[]}").strNext());
        assertThrows(IOException.class, () -> SandboxUsers.pageOf("not json {"));
    }


    /** 2.x - `GET /v1/users`, the WinVM 2026-10-30, where `/v2/users` was a 404. */
    @Test
    void twoXListsItsUsersAsResultAndUserId() throws IOException {
        assertEquals(List.of("participant_admin", "technician"), SandboxUsers.lstOfV1(
                "{\"result\":[{\"userId\":\"participant_admin\",\"primaryParty\":null},"
                        + "{\"userId\":\"technician\",\"primaryParty\":\"p::1220\"},"
                        + "{\"userId\":\"technician\"}],\"status\":200}"));
        assertThrows(IOException.class, () -> SandboxUsers.lstOfV1("{\"users\":[]}"));
        assertEquals(SandboxUsers.STR_PATH_V1,
                SandboxUsers.strPathUsers(com.raposza.canton.install.VersionId.of(2, 10, 4)));
        assertEquals(SandboxUsers.STR_PATH_V2,
                SandboxUsers.strPathUsers(com.raposza.canton.install.VersionId.of(3, 4, 11)));
        assertEquals(SandboxUsers.STR_PATH_V2, SandboxUsers.strPathUsers(null));
    }
}
