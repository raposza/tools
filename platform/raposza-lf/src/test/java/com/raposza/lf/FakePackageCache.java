// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lf;

import com.raposza.api.PackageCache_i;
import com.raposza.api.model.PackageShape;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A cache in memory that counts its reads, so a test can assert that a hit did
 * not decode anything a second time.
 *
 * Author Claude/bentzn
 */
final class FakePackageCache implements PackageCache_i {

    private final Map<String, PackageShape> mapShape = new LinkedHashMap<>();

    private int cntGet;


    void add(PackageShape shape) {
        mapShape.put(shape.idPackage(), shape);
    }


    int cntGet() {
        return cntGet;
    }


    @Override
    public Set<String> ids() {
        return Set.copyOf(mapShape.keySet());
    }


    @Override
    public Optional<PackageShape> get(String idPackage) {
        cntGet++;
        return Optional.ofNullable(mapShape.get(idPackage));
    }


    @Override
    public Optional<byte[]> archive(String idPackage) {
        return Optional.empty();
    }


    @Override
    public void put(String idPackage, byte[] arrArchive, PackageShape shape) {
        mapShape.put(idPackage, shape);
    }


    @Override
    public void invalidateDecoded() {
        mapShape.clear();
    }

}
