// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.canton.topology;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The user scribe reads as, fetched or created - raposza/tools#4.
 *
 * Author Claude/bentzn
 */
class PqsReaderScriptTest {

    private static final Path DIR_WORK = Path.of("/tmp/raposza-test-work");

    private static final String STR_USER = "raposza-pqs";


    @Test
    void theSubcommandScriptFetchesOrCreatesTheReader() {
        Canton3xBootstrap bootstrap = new Canton3xBootstrap().withPqsReader(STR_USER);
        String strScript = bootstrap.render(DIR_WORK);
        String strParticipant = bootstrap.strParticipant();

        assertEquals(STR_USER, bootstrap.strUserPqs());
        assertTrue(strScript.contains(strParticipant + ".ledger_api.users.get(id = \"" + STR_USER
                + "\")"), strScript);
        assertTrue(strScript.contains("case _: com.digitalasset.canton.console.CommandFailure"),
                strScript);
        assertTrue(strScript.contains("      id = \"" + STR_USER + "\",\n"), strScript);
        assertTrue(strScript.contains("readAsAnyParty = true)"), strScript);
        assertTrue(strScript.indexOf("_pqsReader") < strScript.indexOf(Canton3xBootstrap.STR_DONE),
                "the reader is made before the script says it is done");
    }


    @Test
    void theDaemonScriptDoesTheSameAndKeepsItThroughTheOtherSteps() {
        Canton3xDaemonBootstrap bootstrap = new Canton3xDaemonBootstrap().withPqsReader(STR_USER)
                .withPing().withDars(List.of(Path.of("/x/a.dar")));
        String strScript = bootstrap.render(DIR_WORK);

        assertEquals(STR_USER, bootstrap.strUserPqs());
        assertTrue(strScript.contains(".ledger_api.users.get(id = \"" + STR_USER + "\")"),
                strScript);
        assertTrue(strScript.contains("readAsAnyParty = true)"), strScript);
    }


    @Test
    void noReaderIsAskedForNoneIsRendered() {
        assertNull(new Canton3xBootstrap().strUserPqs());
        assertFalse(new Canton3xBootstrap().render(DIR_WORK).contains("_pqsReader"));
        assertFalse(new Canton3xDaemonBootstrap().render(DIR_WORK).contains("_pqsReader"));
    }


    @Test
    void anIdAScalaLiteralWouldHaveToEscapeIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xBootstrap().withPqsReader("a\"b"));
        assertThrows(IllegalArgumentException.class,
                () -> new Canton3xDaemonBootstrap().withPqsReader(" "));
    }
}
