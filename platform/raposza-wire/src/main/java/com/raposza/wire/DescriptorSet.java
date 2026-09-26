// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A growing set of proto file descriptors, keyed by file name.
 *
 * Server reflection answers one symbol at a time and repeats itself freely: two
 * services declared in one file produce that file twice, and a file arrives
 * again as a dependency of another. Keying by name makes the duplicate free and
 * makes the result stable - insertion order is preserved, so the same
 * participant answered twice produces the same bytes.
 *
 * It is deliberately separate from {@link ServerReflection}. Assembling a
 * descriptor set is pure computation over bytes and is unit-testable with no
 * server, no channel and no participant; the client is the part that needs one.
 * The split is what lets the assembly be tested at all.
 *
 * Author Claude/bentzn
 */
public final class DescriptorSet {

    private final Map<String, FileDescriptorProto> mapFile = new LinkedHashMap<>();


    /**
     * @param bsFile one serialised {@code FileDescriptorProto}
     * @return true when the file was new, false when it was already held
     * @throws WireException when the bytes are not a file descriptor
     */
    public boolean add(ByteString bsFile) {
        if (bsFile == null)
            throw new IllegalArgumentException("bsFile is required");

        FileDescriptorProto proto;
        try {
            proto = FileDescriptorProto.parseFrom(bsFile);
        }
        catch (InvalidProtocolBufferException ex) {
            throw new WireException("a reflection response carried " + bsFile.size()
                    + " bytes that are not a FileDescriptorProto: " + ex, ex);
        }

        if (proto.getName().isEmpty())
            throw new WireException("a file descriptor arrived with no name, so it cannot be keyed");
        if (mapFile.containsKey(proto.getName()))
            return false;

        mapFile.put(proto.getName(), proto);
        // SEE mapBuilt. A descriptor built against the smaller set is
        // stale the moment a file lands, and the failure it causes names
        // a missing type rather than the cache.
        mapBuilt.clear();
        return true;
    }


    /**
     * @param collBsFile serialised file descriptors, in any order
     * @return how many were new
     */
    public int addAll(Collection<ByteString> collBsFile) {
        if (collBsFile == null)
            throw new IllegalArgumentException("collBsFile is required");

        int cntNew = 0;
        for (ByteString bsFile : collBsFile) {
            if (add(bsFile))
                cntNew++;
        }
        return cntNew;
    }


    public boolean contains(String strName) {
        return mapFile.containsKey(strName);
    }


    public int cntFile() {
        return mapFile.size();
    }


    public List<String> lstFileName() {
        return List.copyOf(mapFile.keySet());
    }


    /**
     * Files named as a dependency by something already held, and not held.
     *
     * A descriptor set that omits a file its members import is not usable: a
     * consumer resolving the set will fail on the missing import, and the
     * failure names the file rather than the fetch that never happened. The
     * caller closes over this until it is empty.
     *
     * @return the missing file names, in the order they were first named
     */
    public Set<String> setMissingDependency() {
        Set<String> setMissing = new LinkedHashSet<>();
        for (FileDescriptorProto proto : mapFile.values()) {
            for (String strDependency : proto.getDependencyList()) {
                if (!mapFile.containsKey(strDependency))
                    setMissing.add(strDependency);
            }
        }
        return setMissing;
    }


    /**
     * @return true when every dependency named by every held file is also held
     */
    public boolean isClosed() {
        return setMissingDependency().isEmpty();
    }


    /**
     * @return the whole set as a serialised {@code FileDescriptorSet}, which is
     *         what protoc, grpcurl and a protobuf runtime all read directly
     */
    public byte[] arrBytes() {
        FileDescriptorSet.Builder builder = FileDescriptorSet.newBuilder();
        for (FileDescriptorProto proto : mapFile.values()) {
            builder.addFile(proto);
        }
        return builder.build().toByteArray();
    }


    /**
     * The descriptors already built, so a service looked up twice is built
     * once.
     *
     * CLEARED WHENEVER A FILE ARRIVES. A built {@code FileDescriptor} closes
     * over the dependency instances it was built with, so a set that grew
     * after a build would hand out descriptors resolved against the older,
     * smaller set - and the failure would surface as a missing type rather
     * than as a stale cache.
     */
    private final Map<String, Descriptors.FileDescriptor> mapBuilt = new LinkedHashMap<>();


    /**
     * One file, RESOLVED - a protobuf descriptor object rather than the bytes
     * it was captured as, with its imports built and wired underneath it.
     *
     * This is what turns a reflection capture into something that can be
     * CALLED. A {@code FileDescriptorProto} names its message types as strings;
     * a {@code FileDescriptor} carries them as objects a {@code DynamicMessage}
     * can be built against.
     *
     * @param strName the file name, as the server keyed it
     * @return its descriptor
     * @throws WireException when the file, or anything it imports, was never
     *         captured, or when the set does not validate
     */
    public Descriptors.FileDescriptor fileFor(String strName) {
        return fileFor(strName, new LinkedHashSet<String>());
    }


    /**
     * @param strName the file to build
     * @param setOnStack what is already being built, to catch an import cycle
     *        rather than recursing until the stack goes
     * @return its descriptor
     */
    private Descriptors.FileDescriptor fileFor(String strName, Set<String> setOnStack) {
        Descriptors.FileDescriptor fileHave = mapBuilt.get(strName);
        if (fileHave != null)
            return fileHave;

        FileDescriptorProto proto = mapFile.get(strName);
        if (proto == null)
            throw new WireException("no descriptor was captured for " + strName);
        if (!setOnStack.add(strName))
            throw new WireException("the imports of " + strName + " form a cycle: " + setOnStack);

        List<Descriptors.FileDescriptor> lstDependency = new ArrayList<>();
        for (String strDependency : proto.getDependencyList()) {
            lstDependency.add(fileFor(strDependency, setOnStack));
        }

        Descriptors.FileDescriptor fileNew;
        try {
            fileNew = Descriptors.FileDescriptor.buildFrom(proto,
                    lstDependency.toArray(new Descriptors.FileDescriptor[0]));
        }
        catch (Descriptors.DescriptorValidationException ex) {
            throw new WireException("the captured descriptor of " + strName
                    + " does not validate: " + ex.getMessage(), ex);
        }

        setOnStack.remove(strName);
        mapBuilt.put(strName, fileNew);
        return fileNew;
    }


    /**
     * A service by its fully qualified name.
     *
     * EVERY FILE IS SEARCHED rather than the name being mapped to a file path.
     * A proto package and a file path agree by convention and not by rule, and
     * the one place this project has to survive is a vendor renaming its
     * packages between generations - which Canton did.
     *
     * @param strFullService e.g. {@code some.pkg.PackageService}
     * @return its descriptor
     * @throws WireException when no captured file declares it
     */
    /**
     * Every service the captured files declare.
     *
     * @return them, in file order
     */
    public List<Descriptors.ServiceDescriptor> lstService() {
        List<Descriptors.ServiceDescriptor> lstOut = new ArrayList<>();
        for (String strName : List.copyOf(mapFile.keySet())) {
            lstOut.addAll(fileFor(strName).getServices());
        }
        return lstOut;
    }


    public Descriptors.ServiceDescriptor serviceFor(String strFullService) {
        if (strFullService == null || strFullService.isBlank())
            throw new IllegalArgumentException("a service name is required");

        for (String strName : List.copyOf(mapFile.keySet())) {
            for (Descriptors.ServiceDescriptor service : fileFor(strName).getServices()) {
                if (strFullService.equals(service.getFullName()))
                    return service;
            }
        }
        throw new WireException("no captured file declares the service " + strFullService);
    }


    /**
     * @param strFullMethod the gRPC spelling, {@code some.pkg.Service/Method}
     * @return its descriptor
     * @throws WireException when the service or the method is not there
     */
    public Descriptors.MethodDescriptor methodFor(String strFullMethod) {
        if (strFullMethod == null || strFullMethod.indexOf('/') < 0) {
            throw new IllegalArgumentException("a full method name is required: "
                    + strFullMethod);
        }

        int idxSlash = strFullMethod.indexOf('/');
        String strService = strFullMethod.substring(0, idxSlash);
        String strMethod = strFullMethod.substring(idxSlash + 1);

        Descriptors.ServiceDescriptor service = serviceFor(strService);
        Descriptors.MethodDescriptor method = service.findMethodByName(strMethod);
        if (method != null)
            return method;

        List<String> lstKnown = new ArrayList<>();
        for (Descriptors.MethodDescriptor methodHere : service.getMethods()) {
            lstKnown.add(methodHere.getName());
        }
        throw new WireException(strService + " has no method " + strMethod
                + "; it has " + lstKnown);
    }


    @Override
    public String toString() {
        return "descriptor set: " + mapFile.size() + " files, "
                + (isClosed() ? "closed" : setMissingDependency().size() + " dependencies missing");
    }
}
