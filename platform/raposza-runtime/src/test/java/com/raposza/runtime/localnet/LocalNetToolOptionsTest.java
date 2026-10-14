// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.runtime.localnet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Loopback is never proxied for the two LocalNetND namespaces - `todo.md` W-14,
 * measured 2026-10-13: a guest's `JAVA_TOOL_OPTIONS` named a proxy and no
 * bypass, and every validator's call to scan at `127.0.0.1` went to it.
 *
 * Author Claude/bentzn
 */
class LocalNetToolOptionsTest {

    /** The guest's own value: the proxy kept, the loopback bypass added. */
    @Test
    void aProxyWithNoBypassGainsTheLoopbackList() {
        String strGuest = "-Dhttps.proxyHost=192.168.122.1 -Dhttps.proxyPort=3128"
                + " -Dhttp.proxyHost=192.168.122.1 -Dhttp.proxyPort=3128";

        assertEquals(strGuest + " -Dhttp.nonProxyHosts=localhost|127.*|[::1]",
                LocalNetRunner.strToolOptions(strGuest));
    }


    /** Nothing set: the list alone. */
    @Test
    void nothingSetIsTheListAlone() {
        assertEquals("-Dhttp.nonProxyHosts=localhost|127.*|[::1]",
                LocalNetRunner.strToolOptions(null));
        assertEquals("-Dhttp.nonProxyHosts=localhost|127.*|[::1]",
                LocalNetRunner.strToolOptions("  "));
    }


    /** A bypass list the machine sets is the operator's, left as it is. */
    @Test
    void aListAlreadySetIsLeftAlone() {
        String strSet = "-Dhttp.proxyHost=p -Dhttp.nonProxyHosts=*.corp|localhost";

        assertEquals(strSet, LocalNetRunner.strToolOptions(strSet));
    }

}
