// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import com.raposza.api.LedgerException;
import com.raposza.api.TokenSource_i;
import com.raposza.api.profile.HostProfile;

import io.grpc.ClientInterceptors;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * A gRPC channel built from a connection profile.
 *
 * TLS follows the profile's protocol: https means transport security, http
 * means plaintext. There is deliberately no override - a profile that says
 * https and connects in the clear is worse than one that fails.
 *
 * Author Claude/bentzn
 */
public final class LedgerChannel implements AutoCloseable {

    private final ManagedChannel channel;
    private final Channel channelAuth;
    private final HostProfile profile;


    /**
     * @param profile the connection profile; must expose a ledger port
     * @param source token supply, or null for an unauthenticated ledger
     * @throws LedgerException when the profile has no ledger port
     */
    public LedgerChannel(HostProfile profile, TokenSource_i source) {
        if (profile == null)
            throw new LedgerException("no profile supplied");
        if (!profile.hasLedgerPort()) {
            throw new LedgerException("profile '" + profile.nameDisplay()
                    + "' exposes no gRPC ledger port");
        }

        this.profile = profile;

        ManagedChannelBuilder<?> bld =
                ManagedChannelBuilder.forAddress(profile.nameHost(), profile.portLedger());
        if (profile.isTls()) {
            bld.useTransportSecurity();
        }
        else {
            bld.usePlaintext();
        }

        this.channel = bld.build();
        this.channelAuth = ClientInterceptors.intercept(channel,
                new BearerTokenInterceptor(source));
    }


    /**
     * @return the channel every stub should be built on; it carries the auth
     *         interceptor
     */
    public Channel authenticated() {
        return channelAuth;
    }


    public HostProfile profile() {
        return profile;
    }


    @Override
    public void close() {
        channel.shutdownNow();
        try {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

}
