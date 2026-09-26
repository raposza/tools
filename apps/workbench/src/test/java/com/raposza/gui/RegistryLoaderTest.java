// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.LedgerException;
import com.raposza.api.PackageCache_i;
import com.raposza.api.TypeRegistry_i;
import com.raposza.api.model.DataId;
import com.raposza.api.model.PackageShape;
import com.raposza.spi.LfDecoder_i;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Fetching only what is missing, and surviving an archive that will not decode.
 *
 * Author Claude/bentzn
 */
class RegistryLoaderTest {

    /** Cache in memory that records what it was asked to store. */
    private static final class Cache implements PackageCache_i {

        private final Map<String, PackageShape> mapShape = new LinkedHashMap<>();
        private final List<String> lstPut = new ArrayList<>();

        @Override
        public Set<String> ids() {
            return Set.copyOf(mapShape.keySet());
        }

        @Override
        public Optional<PackageShape> get(String idPackage) {
            return Optional.ofNullable(mapShape.get(idPackage));
        }

        @Override
        public Optional<byte[]> archive(String idPackage) {
            return Optional.empty();
        }

        @Override
        public void put(String idPackage, byte[] arrArchive, PackageShape shape) {
            lstPut.add(idPackage);
            mapShape.put(idPackage, shape);
        }

        @Override
        public void invalidateDecoded() {
            mapShape.clear();
        }
    }


    /** Decodes anything except the bytes "bad". */
    private static final class Decoder implements LfDecoder_i {

        @Override
        public boolean accepts(byte[] arrArchive) {
            return true;
        }

        @Override
        public PackageShape decode(byte[] arrArchive) {
            String strBody = new String(arrArchive, StandardCharsets.UTF_8);
            if ("bad".equals(strBody))
                throw new LedgerException("cannot decode");

            return new PackageShape(strBody, null, null, "LF", List.of(), List.of(), List.of());
        }
    }


    private static FakeNavClient client(String... arrId) {
        FakeNavClient client = new FakeNavClient();
        for (String idPackage : arrId) {
            client.armPackage(idPackage, idPackage.getBytes(StandardCharsets.UTF_8));
        }
        return client;
    }


    @Test
    void everythingMissingIsFetchedAndCached() {
        Cache cache = new Cache();
        RegistryLoader.load(client("pkg1", "pkg2"), cache, new Decoder());

        assertEquals(List.of("pkg1", "pkg2"), cache.lstPut);
    }


    /**
     * A package id is a content hash, so a cached entry can never be stale and
     * re-fetching it is pure cost.
     */
    @Test
    void whatIsAlreadyCachedIsNotFetchedAgain() {
        Cache cache = new Cache();
        cache.put("pkg1", new byte[0],
                new PackageShape("pkg1", null, null, "LF", List.of(), List.of(), List.of()));
        cache.lstPut.clear();

        RegistryLoader.load(client("pkg1", "pkg2"), cache, new Decoder());

        assertEquals(List.of("pkg2"), cache.lstPut);
    }


    /**
     * One archive that will not decode must not cost the operator every
     * template on the participant.
     */
    @Test
    void oneUndecodableArchiveDoesNotSinkTheRest() {
        Cache cache = new Cache();
        TypeRegistry_i registry = RegistryLoader.load(client("pkg1", "bad", "pkg2"), cache,
                new Decoder());

        assertEquals(List.of("pkg1", "pkg2"), cache.lstPut);
        assertTrue(registry.shape(new DataId("nope", "No", "Such")).isEmpty());
    }


    @Test
    void aRefusedPackageListIsNotAnEmptyLedger() {
        FakeNavClient client = new FakeNavClient();
        client.armPackagesFailure(new LedgerException("PERMISSION_DENIED"));

        assertThrows(LedgerException.class,
                () -> RegistryLoader.load(client, new Cache(), new Decoder()));
    }

}
