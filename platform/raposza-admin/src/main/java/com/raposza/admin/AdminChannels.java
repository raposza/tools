// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.admin;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;

/**
 * A channel to a local participant's admin API.
 *
 * <h2>Plaintext, and that is what the API is</h2>
 *
 * The admin API takes no token and no TLS on a local sandbox - the vendor's own
 * tooling drives it with `grpcurl -plaintext`. TLS and mTLS are configuration a
 * deployed node opts into, and a Sandbox on 127.0.0.1 does not.
 *
 * <h2>The transport is chosen HERE and nowhere above</h2>
 *
 * `raposza-wire` declares no transport on purpose, and the enforcer bars the
 * modules above this one from declaring gRPC at all. So the one place a
 * transport may be named is a translation module, and this is it. The builder
 * is named explicitly rather than going through `ManagedChannelBuilder`,
 * because provider lookup inside a shaded jar fails in a way that reads like a
 * network problem.
 *
 * Author Claude/bentzn
 */
public final class AdminChannels {

    /**
     * A DAR is sent and returned whole, and the default is 4 MB.
     */
    private static final int N_BYTES_MESSAGE_MAX = 256 * 1024 * 1024;

    private AdminChannels() {
    }


    /**
     * @param strHost where the participant listens
     * @param nPort its admin API port
     * @return an open channel; the caller shuts it down
     */
    public static ManagedChannel open(String strHost, int nPort) {
        if (strHost == null || strHost.isBlank())
            throw new IllegalArgumentException("a host is required");
        if (nPort < 1 || nPort > 65535)
            throw new IllegalArgumentException("not a port: " + nPort);

        return NettyChannelBuilder.forAddress(strHost, nPort)
                .usePlaintext()
                .maxInboundMessageSize(N_BYTES_MESSAGE_MAX)
                .build();
    }
}
