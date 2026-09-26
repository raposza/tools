// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi2;

import com.raposza.api.LedgerException;
import com.raposza.api.TokenSource_i;
import com.raposza.api.profile.HostProfile;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import java.util.concurrent.TimeUnit;

/**
 * A gRPC channel to a Canton 3.x participant, built from a connection profile.
 *
 * TLS follows the profile's protocol: https means transport security, http
 * means plaintext. There is deliberately no override - a profile that says
 * https and connects in the clear is worse than one that fails.
 *
 * <h2>Why this is not the 2.x channel</h2>
 *
 * It is the same code, and it is here anyway. The v1 binding jar and the v2
 * classes share 306 fully qualified names and can never occupy one classpath,
 * so a module that reached into the other target for a channel would drag that
 * collision along with it. The two generations are parallel implementations by
 * design; this is what that costs, and it is eighty lines.
 *
 * Author Claude/bentzn
 */
public final class Lapi2Channel implements AutoCloseable {

    private static final Metadata.Key<String> KEY_AUTH =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;

    private final Channel channelAuth;

    private final HostProfile profile;


    /**
     * @param profile the connection profile; must expose a ledger port
     * @param source token supply, or null for an unauthenticated ledger
     * @throws LedgerException when the profile has no ledger port
     */
    public Lapi2Channel(HostProfile profile, TokenSource_i source) {
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
        this.channelAuth = ClientInterceptors.intercept(channel, bearer(source));
    }


    /**
     * The token is fetched PER CALL rather than captured here, so a token
     * source that renews internally is honoured without the channel knowing
     * anything about expiry.
     *
     * @param source the token supply, or null
     * @return an interceptor that attaches the bearer token
     */
    private static ClientInterceptor bearer(TokenSource_i source) {
        return new ClientInterceptor() {

            @Override
            public <ReqT, RspT> ClientCall<ReqT, RspT> interceptCall(
                    MethodDescriptor<ReqT, RspT> method, CallOptions options, Channel next) {

                return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RspT>(
                        next.newCall(method, options)) {

                    @Override
                    public void start(Listener<RspT> listener, Metadata headers) {
                        if (source != null) {
                            String strToken = source.token();
                            if (strToken != null && !strToken.isBlank())
                                headers.put(KEY_AUTH, "Bearer " + strToken);
                        }
                        super.start(listener, headers);
                    }

                };
            }

        };
    }


    /**
     * @return the channel every call should be built on; it carries the auth
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
