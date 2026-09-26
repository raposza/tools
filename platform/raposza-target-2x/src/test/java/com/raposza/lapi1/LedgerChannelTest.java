// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.LedgerException;
import com.raposza.api.TokenSource_i;
import com.raposza.api.profile.AccessMode;
import com.raposza.api.profile.HostProfile;
import com.raposza.api.profile.HostProfileStore;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * These cover the parts that do not need a participant: header construction,
 * per-call token fetch, and channel configuration from a profile.
 *
 * The reads themselves are verified against a live sandbox by hand until an
 * integration module exists - a clean clone must build with no Canton running.
 *
 * Author Claude/bentzn
 */
class LedgerChannelTest {

    private static final Metadata.Key<String> KEY_AUTH =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);


    /** Captures the headers a call would have gone out with. */
    private static final class CapturingChannel extends Channel {

        Metadata headers;

        @Override
        public <ReqT, RspT> ClientCall<ReqT, RspT> newCall(
                MethodDescriptor<ReqT, RspT> method, CallOptions options) {
            return new ClientCall<ReqT, RspT>() {

                @Override
                public void start(Listener<RspT> listener, Metadata hdrs) {
                    headers = hdrs;
                }

                @Override
                public void request(int cnt) {
                }

                @Override
                public void cancel(String strMessage, Throwable cause) {
                }

                @Override
                public void halfClose() {
                }

                @Override
                public void sendMessage(ReqT message) {
                }

            };
        }

        @Override
        public String authority() {
            return "test";
        }

    }


    private static final class StubSource implements TokenSource_i {

        final AtomicInteger cntCall = new AtomicInteger();
        String strToken;

        StubSource(String strToken) {
            this.strToken = strToken;
        }

        @Override
        public String token() {
            cntCall.incrementAndGet();
            return strToken;
        }

        @Override
        public String describe() {
            return "stub";
        }

    }


    private static Metadata callThrough(TokenSource_i source) {
        CapturingChannel raw = new CapturingChannel();
        Channel wrapped = ClientInterceptors.intercept(raw, new BearerTokenInterceptor(source));
        wrapped.newCall(null, CallOptions.DEFAULT).start(null, new Metadata());
        return raw.headers;
    }


    @Test
    void tokenIsSentAsABearerHeader() {
        Metadata headers = callThrough(new StubSource("abc.def.ghi"));
        assertEquals("Bearer abc.def.ghi", headers.get(KEY_AUTH));
    }


    @Test
    void noSourceMeansNoHeader() {
        assertNull(callThrough(null).get(KEY_AUTH));
    }


    @Test
    void blankTokenMeansNoHeader() {
        assertNull(callThrough(new StubSource("   ")).get(KEY_AUTH));
    }


    /**
     * The token is fetched per call rather than captured once, so a source that
     * renews internally is honoured without the channel knowing about expiry.
     */
    @Test
    void tokenIsFetchedOnEveryCall() {
        StubSource source = new StubSource("first");
        CapturingChannel raw = new CapturingChannel();
        Channel wrapped = ClientInterceptors.intercept(raw, new BearerTokenInterceptor(source));

        wrapped.newCall(null, CallOptions.DEFAULT).start(null, new Metadata());
        assertEquals("Bearer first", raw.headers.get(KEY_AUTH));

        source.strToken = "second";
        wrapped.newCall(null, CallOptions.DEFAULT).start(null, new Metadata());
        assertEquals("Bearer second", raw.headers.get(KEY_AUTH));

        assertEquals(2, source.cntCall.get());
    }


    @Test
    void profileWithoutALedgerPortIsRejectedByName() {
        HostProfile profile = HostProfileStore.parse("JSON only  https  h  -  7575  -  -  ro  -", 1);
        LedgerException ex = assertThrows(LedgerException.class,
                () -> new LedgerChannel(profile, null));
        assertTrue(ex.getMessage().contains("JSON only"));
    }


    @Test
    void plaintextAndTlsFollowTheProfileProtocol() {
        HostProfile plain = HostProfileStore.parse("Local  http  localhost  5011  -  -  -  rw  -", 1);
        HostProfile tls = HostProfileStore.parse("Remote  https  localhost  5011  -  -  -  ro  -", 1);

        assertFalse(plain.isTls());
        assertTrue(tls.isTls());
        assertEquals(AccessMode.READ_WRITE, plain.mode());
        assertEquals(AccessMode.READ_ONLY, tls.mode());

        // Building the channel must not attempt a connection; gRPC connects lazily.
        try (LedgerChannel chPlain = new LedgerChannel(plain, null);
                LedgerChannel chTls = new LedgerChannel(tls, null)) {
            assertEquals("Local", chPlain.profile().nameDisplay());
            assertEquals("Remote", chTls.profile().nameDisplay());
        }
    }

}
