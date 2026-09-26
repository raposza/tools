// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import com.raposza.api.TokenSource_i;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Attaches the bearer token to every outgoing call.
 *
 * The token is fetched PER CALL rather than captured at construction, so a
 * TokenSource_i that renews internally is honoured without the channel knowing
 * anything about expiry.
 *
 * Author Claude/bentzn
 */
public final class BearerTokenInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> KEY_AUTH =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final TokenSource_i source;


    /**
     * @param source supplies the token; null means an unauthenticated ledger,
     *               which a local sandbox usually is
     */
    public BearerTokenInterceptor(TokenSource_i source) {
        this.source = source;
    }


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

}
