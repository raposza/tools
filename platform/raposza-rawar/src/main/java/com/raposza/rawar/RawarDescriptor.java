// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.rawar;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `rawar.json` - what a RAWAR says about itself. `rawar.md` section 3.
 *
 * Identity is `treeSha256`, written by the exporter from the tree and never
 * by hand; `version` is for people. `dars` records what the RAWAR was built
 * and tested against, for other developers and for forensics - NOT for
 * deployment: the DARs travel with the BOM. A recorded package id that a host
 * does not hold is a loud warning there, never a refusal - his decision.
 *
 * What is deliberately NOT here: the issuer, the ledger URL, the audience,
 * any address. Those are the host's, served under the mount as `_env.json`.
 *
 * @param nFormat the descriptor format's version, 1
 * @param strName unique on a host; the archive is `<name>.rawar`
 * @param strVersion the developer's version, informational
 * @param strTreeSha256 the canonical tree hash, `RawarTree`
 * @param strMount the DEFAULT mount, `/` unless the developer says otherwise;
 *        a host may override it and the descriptor never binds the host
 * @param builtWith what the exporting host ran
 * @param lstDar every DAR the RAWAR was built and tested against
 * @param strCommit the source commit when built from a repository, else null
 *
 * Author Claude/bentzn
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RawarDescriptor(
        @JsonProperty("rawar") int nFormat,
        @JsonProperty("name") String strName,
        @JsonProperty("version") String strVersion,
        @JsonProperty("treeSha256") String strTreeSha256,
        @JsonProperty("mount") String strMount,
        @JsonProperty("builtWith") BuiltWith builtWith,
        @JsonProperty("dars") List<Dar> lstDar,
        @JsonProperty("commit") String strCommit) {

    /** The format this code writes and reads. */
    public static final int N_FORMAT = 1;

    /** The default mount. */
    public static final String STR_MOUNT_DEFAULT = "/";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(SerializationFeature.INDENT_OUTPUT, true);


    /**
     * what the exporting host ran when the RAWAR was exported
     *
     * @param strSdk the Daml SDK, e.g. `3.5.11`
     * @param strCanton the Canton version
     * @param strSplice the Splice version, or null on a one-participant Sandbox
     * @param strHost the host kind, e.g. `sandbox-localnetnd`
     * @param strAt UTC instant, ISO-8601
     */
    public record BuiltWith(
            @JsonProperty("sdk") String strSdk,
            @JsonProperty("canton") String strCanton,
            @JsonProperty("splice") String strSplice,
            @JsonProperty("host") String strHost,
            @JsonProperty("at") String strAt) {
    }


    /**
     * one DAR the RAWAR was built and tested against
     *
     * @param strName the package name
     * @param strVersion the package version
     * @param strPackageId the package id - the DAR's own identity, what the
     *        participant vets and what a gate compares
     */
    public record Dar(
            @JsonProperty("name") String strName,
            @JsonProperty("version") String strVersion,
            @JsonProperty("packageId") String strPackageId) {
    }


    /**
     * this descriptor with another tree hash - what the exporter does after
     * hashing
     *
     * @param strHash the hash
     * @return a copy
     */
    public RawarDescriptor withTreeSha256(String strHash) {
        return new RawarDescriptor(nFormat, strName, strVersion, strHash, strMount, builtWith, lstDar, strCommit);
    }


    /**
     * what is wrong with this descriptor, if anything
     *
     * @return every objection, empty when it is well-formed
     */
    public List<String> lstObjection() {
        List<String> lstOut = new ArrayList<>();
        if (nFormat != N_FORMAT)
            lstOut.add("rawar format " + nFormat + " - this code reads " + N_FORMAT);
        if (!RawarTree.flagNameValid(strName))
            lstOut.add("name " + strName + " - lower case, digits and - only, 1 to 63 characters");
        if (strMount == null || !strMount.startsWith("/"))
            lstOut.add("mount " + strMount + " - must start with /");
        if (strTreeSha256 == null || !strTreeSha256.matches("[0-9a-f]{64}"))
            lstOut.add("treeSha256 " + strTreeSha256 + " - 64 lowercase hex characters");
        if (lstDar == null)
            lstOut.add("dars is absent - an empty list when none");
        return lstOut;
    }


    /**
     * the descriptor as bytes, indented, UTF-8
     *
     * @return the JSON
     */
    public byte[] arrJson() {
        try {
            return (MAPPER.writeValueAsString(this) + "\n").getBytes(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("a record cannot fail to serialise", e);
        }
    }


    /**
     * writes `rawar.json` at the root of a RAWAR directory
     *
     * @param dirRoot the RAWAR directory
     * @throws IOException when it cannot be written
     */
    public void write(Path dirRoot) throws IOException {
        Files.write(dirRoot.resolve(RawarTree.STR_DESCRIPTOR), arrJson());
    }


    /**
     * reads `rawar.json` from the root of a RAWAR directory
     *
     * @param dirRoot the RAWAR directory
     * @return the descriptor
     * @throws IOException when it is absent or not a descriptor
     */
    public static RawarDescriptor read(Path dirRoot) throws IOException {
        return parse(Files.readAllBytes(dirRoot.resolve(RawarTree.STR_DESCRIPTOR)));
    }


    /**
     * parses a descriptor
     *
     * @param arrJson the JSON
     * @return the descriptor
     * @throws IOException when it is not a descriptor
     */
    public static RawarDescriptor parse(byte[] arrJson) throws IOException {
        return MAPPER.readValue(arrJson, RawarDescriptor.class);
    }
}
