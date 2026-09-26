// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.wire;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assembly half of the reflection capture, tested with no server.
 *
 * Every fixture here is a real serialised {@code FileDescriptorProto} built in
 * process, not a recorded blob: a fixture that came off a participant would
 * make this test a statement about that participant.
 *
 * Author Claude/bentzn
 */
class DescriptorSetTest {

    private static ByteString bsFile(String strName, String... arrDependency) {
        FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder().setName(strName);
        for (String strDependency : arrDependency) {
            builder.addDependency(strDependency);
        }
        return builder.build().toByteString();
    }


    @Test
    void aFileIsHeldOnceHoweverOftenItArrives() {
        DescriptorSet set = new DescriptorSet();

        assertTrue(set.add(bsFile("a.proto")));
        assertFalse(set.add(bsFile("a.proto")), "the same file counted twice");
        assertEquals(1, set.cntFile());
    }


    @Test
    void insertionOrderIsPreservedSoTheBytesAreStable() {
        DescriptorSet setFirst = new DescriptorSet();
        setFirst.addAll(List.of(bsFile("a.proto"), bsFile("b.proto"), bsFile("c.proto")));

        DescriptorSet setAgain = new DescriptorSet();
        setAgain.addAll(List.of(bsFile("a.proto"), bsFile("b.proto"), bsFile("c.proto")));

        assertEquals(List.of("a.proto", "b.proto", "c.proto"), setFirst.lstFileName());
        assertArrayEqualsBytes(setFirst.arrBytes(), setAgain.arrBytes());
    }


    @Test
    void addAllCountsOnlyWhatWasNew() {
        DescriptorSet set = new DescriptorSet();
        set.add(bsFile("a.proto"));

        assertEquals(1, set.addAll(List.of(bsFile("a.proto"), bsFile("b.proto"))));
        assertEquals(2, set.cntFile());
    }


    @Test
    void aDependencyThatIsNotHeldIsReportedMissing() {
        DescriptorSet set = new DescriptorSet();
        set.add(bsFile("a.proto", "b.proto", "c.proto"));

        assertFalse(set.isClosed());
        assertEquals(List.of("b.proto", "c.proto"), List.copyOf(set.setMissingDependency()));
    }


    @Test
    void theSetClosesWhenEveryImportArrives() {
        DescriptorSet set = new DescriptorSet();
        set.add(bsFile("a.proto", "b.proto"));
        set.add(bsFile("b.proto", "c.proto"));
        assertFalse(set.isClosed());

        set.add(bsFile("c.proto"));
        assertTrue(set.isClosed());
        assertTrue(set.setMissingDependency().isEmpty());
    }


    @Test
    void theResultParsesBackAsAFileDescriptorSet() throws Exception {
        DescriptorSet set = new DescriptorSet();
        set.addAll(List.of(bsFile("a.proto", "b.proto"), bsFile("b.proto")));

        FileDescriptorSet parsed = FileDescriptorSet.parseFrom(set.arrBytes());
        assertEquals(2, parsed.getFileCount());
        assertEquals("a.proto", parsed.getFile(0).getName());
        assertEquals("b.proto", parsed.getFile(1).getName());
        assertEquals(List.of("b.proto"), parsed.getFile(0).getDependencyList());
    }


    @Test
    void truncatedBytesAreRejectedWithTheirSize() {
        DescriptorSet set = new DescriptorSet();

        // Field 1, wire type 2, declaring five bytes and carrying one. A
        // TRUNCATED length-delimited field is what a cut-short response looks
        // like on the wire, and protobuf refuses it outright.
        //
        // The first version of this test used {0x08, 0x01} - field 1 as a
        // VARINT where the schema says string - and it does not throw.
        // protobuf-java treats a known field arriving with the wrong wire type
        // as an unknown field and SKIPS it, so the message parses with an empty
        // name and the no-name guard is what fires. Measured by the test
        // failing.
        WireException ex = assertThrows(WireException.class,
                () -> set.add(ByteString.copyFrom(new byte[] { 0x0A, 0x05, 0x61 })));
        assertTrue(ex.getMessage().contains("3 bytes"));
    }


    @Test
    void aFileWithNoNameCannotBeKeyed() {
        DescriptorSet set = new DescriptorSet();
        ByteString bsUnnamed = FileDescriptorProto.newBuilder().build().toByteString();

        assertThrows(WireException.class, () -> set.add(bsUnnamed));
    }


    private static void assertArrayEqualsBytes(byte[] arrExpected, byte[] arrActual) {
        assertEquals(ByteString.copyFrom(arrExpected), ByteString.copyFrom(arrActual),
                "the same files in the same order produced different bytes");
    }
}
