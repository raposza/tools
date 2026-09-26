// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Constructor;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * `todo.md` A-37: the Workbench opens LocalNetND's three ledgers as
 * app-provider, app-user, then sv - whatever order the document lists them in.
 *
 * The order was delivered twice and lost twice to whole-file replaces, with
 * every digest green. This is what makes a third loss a red gate.
 *
 * THE CONTROL CAN FAIL: the document lists sv FIRST, which is the order the
 * Sandbox producer writes, so a window that took the producer's order would
 * open sv first and fail here.
 *
 * Author Claude/bentzn
 */
class MainWindowTabOrderTest {

    private static final String STR_LOCALNET = """
            {
              "schema" : 2,
              "state" : "RUNNING",
              "topology" : "localnet",
              "nodes" : [
                { "name" : "sv", "role" : "sv", "port.ledger-api" : 30010 },
                { "name" : "app-provider", "role" : "app-provider", "port.ledger-api" : 30020 },
                { "name" : "app-user", "role" : "app-user", "port.ledger-api" : 30030 }
              ]
            }
            """;


    @Test
    void appProviderThenAppUserThenSv() throws Exception {
        Discovery doc = of(STR_LOCALNET);
        assertEquals(List.of(1, 2, 0), MainWindow.lstIdxInTabOrder(doc));
    }


    @Test
    void anUnknownRoleKeepsTheProducersOrderBehindTheTwo() {
        assertEquals(0, MainWindow.nRankRole("app-provider"));
        assertEquals(1, MainWindow.nRankRole("app-user"));
        assertEquals(2, MainWindow.nRankRole("sv"));
        assertEquals(2, MainWindow.nRankRole(""));
    }


    private static Discovery of(String strJson) throws Exception {
        JsonNode node = new ObjectMapper().readTree(strJson);
        Constructor<Discovery> ctor =
                Discovery.class.getDeclaredConstructor(String.class, JsonNode.class);
        ctor.setAccessible(true);
        return ctor.newInstance("http://127.0.0.1:32001/", node);
    }
}
