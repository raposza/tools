// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.lapi1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.LedgerException;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.TxNode;
import com.raposza.api.model.TxTree;

import com.daml.ledger.api.v1.EventOuterClass;
import com.daml.ledger.api.v1.TransactionOuterClass;
import com.daml.ledger.api.v1.ValueOuterClass;

import com.google.protobuf.Timestamp;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

/**
 * Tree assembly, with no participant involved.
 *
 * The wire form is a flat map plus root ids, so the interesting failures are
 * structural - a dangling child id, a cycle, a node kind nobody handled - and
 * every one of them is reachable without a ledger. They are tested here rather
 * than live because a sandbox will not produce them on demand.
 *
 * toTree is private; reached reflectively rather than widened for a test. The
 * alternative is a package-private seam that exists only for this file, which
 * is a worse trade on a class whose surface is the LedgerClient_i contract.
 *
 * Author Claude/bentzn
 */
class Lapi1TreeTest {

    private static final String ID_PKG = "5d4f288902e3de632a6e4c4c82596c8ebca93f62c5339488ee47f6fc0c2f5dd6";


    private static TxTree toTree(TransactionOuterClass.TransactionTree tree) throws Exception {
        Method mth = Lapi1Client.class.getDeclaredMethod("toTree",
                TransactionOuterClass.TransactionTree.class);
        mth.setAccessible(true);
        try {
            return (TxTree) mth.invoke(null, tree);
        }
        catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException rex)
                throw rex;
            throw ex;
        }
    }


    private static ValueOuterClass.Identifier ident(String nameEntity) {
        return ValueOuterClass.Identifier.newBuilder().setPackageId(ID_PKG).setModuleName("Main")
                .setEntityName(nameEntity).build();
    }


    private static EventOuterClass.CreatedEvent created(String idEvent, String idContract) {
        return EventOuterClass.CreatedEvent.newBuilder().setEventId(idEvent)
                .setContractId(idContract).setTemplateId(ident("Account"))
                .setCreateArguments(ValueOuterClass.Record.newBuilder()
                        .setRecordId(ident("Account"))
                        .addFields(ValueOuterClass.RecordField.newBuilder().setLabel("label")
                                .setValue(ValueOuterClass.Value.newBuilder().setText("primary"))))
                .addSignatories("bank").addObservers("alice").build();
    }


    private static EventOuterClass.ExercisedEvent exercised(String idEvent, boolean flagConsuming,
            String... arrChild) {
        EventOuterClass.ExercisedEvent.Builder bld = EventOuterClass.ExercisedEvent.newBuilder()
                .setEventId(idEvent).setContractId("00old").setTemplateId(ident("Account"))
                .setChoice("Deposit").setConsuming(flagConsuming).addActingParties("alice")
                .setChoiceArgument(ValueOuterClass.Value.newBuilder()
                        .setRecord(ValueOuterClass.Record.newBuilder()
                                .addFields(ValueOuterClass.RecordField.newBuilder()
                                        .setLabel("amount")
                                        .setValue(ValueOuterClass.Value.newBuilder()
                                                .setNumeric("100.0000000000")))));
        for (String idChild : arrChild) {
            bld.addChildEventIds(idChild);
        }
        return bld.build();
    }


    private static TransactionOuterClass.TreeEvent wrap(EventOuterClass.CreatedEvent ev) {
        return TransactionOuterClass.TreeEvent.newBuilder().setCreated(ev).build();
    }


    private static TransactionOuterClass.TreeEvent wrap(EventOuterClass.ExercisedEvent ev) {
        return TransactionOuterClass.TreeEvent.newBuilder().setExercised(ev).build();
    }


    /**
     * An exercise with a create underneath it - the shape the "what did this
     * do" pane exists to show. Flat map in, nested tree out.
     */
    @Test
    void exerciseWithChildCreateNests() throws Exception {
        TransactionOuterClass.TransactionTree tree = TransactionOuterClass.TransactionTree
                .newBuilder().setTransactionId("tx-1").setCommandId("cmd-1").setOffset("0000007")
                .setEffectiveAt(Timestamp.newBuilder().setSeconds(1_785_486_666L)
                        .setNanos(581_794_000))
                .putEventsById("#tx-1:0", wrap(exercised("#tx-1:0", true, "#tx-1:1")))
                .putEventsById("#tx-1:1", wrap(created("#tx-1:1", "00new")))
                .addRootEventIds("#tx-1:0").build();

        TxTree txTree = toTree(tree);

        assertEquals("tx-1", txTree.idUpdate());
        assertEquals(Optional.of("cmd-1"), txTree.idCommand());
        assertEquals(Optional.empty(), txTree.idWorkflow());
        assertEquals("0000007", txTree.offset());
        assertEquals(Instant.ofEpochSecond(1_785_486_666L, 581_794_000L), txTree.instEffective());
        assertEquals(1, txTree.lstRoot().size());

        TxNode.Exercised root = (TxNode.Exercised) txTree.lstRoot().get(0);
        assertEquals("Deposit", root.nameChoice());
        assertTrue(root.flagConsuming());
        assertEquals("Main:Account", root.idTemplate().shortName());
        assertEquals(java.util.List.of("alice"), root.lstActor());

        DamlValue.Rec arg = (DamlValue.Rec) root.argument();
        assertEquals("amount", arg.lstField().get(0).nameField());

        assertEquals(1, root.lstChild().size());
        TxNode.Created child = (TxNode.Created) root.lstChild().get(0);
        assertEquals("00new", child.idContract());
        assertEquals("label", child.payload().lstField().get(0).nameField());
        assertTrue(child.lstChild().isEmpty());
    }


    /**
     * A choice returning unit sets no exercise_result. Null, not a fabricated
     * Unit - the two are different claims and the renderer should be able to
     * tell them apart.
     */
    @Test
    void absentExerciseResultIsNullNotUnit() throws Exception {
        TransactionOuterClass.TransactionTree tree = TransactionOuterClass.TransactionTree
                .newBuilder().setTransactionId("tx-2")
                .putEventsById("#tx-2:0", wrap(exercised("#tx-2:0", false)))
                .addRootEventIds("#tx-2:0").build();

        TxNode.Exercised root = (TxNode.Exercised) toTree(tree).lstRoot().get(0);
        assertNull(root.valueResult());
        assertFalse(root.flagConsuming());
    }


    @Test
    void presentExerciseResultConverts() throws Exception {
        EventOuterClass.ExercisedEvent ev = exercised("#tx-3:0", true).toBuilder()
                .setExerciseResult(ValueOuterClass.Value.newBuilder().setContractId("00result"))
                .build();

        TransactionOuterClass.TransactionTree tree = TransactionOuterClass.TransactionTree
                .newBuilder().setTransactionId("tx-3").putEventsById("#tx-3:0", wrap(ev))
                .addRootEventIds("#tx-3:0").build();

        TxNode.Exercised root = (TxNode.Exercised) toTree(tree).lstRoot().get(0);
        assertEquals(new DamlValue.ContractRef("00result"), root.valueResult());
    }


    @Test
    void severalRootsKeepLedgerOrder() throws Exception {
        TransactionOuterClass.TransactionTree tree = TransactionOuterClass.TransactionTree
                .newBuilder().setTransactionId("tx-4")
                .putEventsById("#tx-4:0", wrap(created("#tx-4:0", "00a")))
                .putEventsById("#tx-4:1", wrap(created("#tx-4:1", "00b")))
                .addRootEventIds("#tx-4:0").addRootEventIds("#tx-4:1").build();

        TxTree txTree = toTree(tree);
        assertEquals("00a", ((TxNode.Created) txTree.lstRoot().get(0)).idContract());
        assertEquals("00b", ((TxNode.Created) txTree.lstRoot().get(1)).idContract());
    }


    /**
     * The failure that matters: a branch the transaction references but does
     * not carry. Dropping it silently renders a transaction that looks like it
     * did less than it did.
     */
    @Test
    void danglingChildIdFailsLoudly() {
        TransactionOuterClass.TransactionTree tree = TransactionOuterClass.TransactionTree
                .newBuilder().setTransactionId("tx-5")
                .putEventsById("#tx-5:0", wrap(exercised("#tx-5:0", true, "#tx-5:9")))
                .addRootEventIds("#tx-5:0").build();

        LedgerException ex = assertThrows(LedgerException.class, () -> toTree(tree));
        assertTrue(ex.getMessage().contains("#tx-5:9"), ex.getMessage());
    }


    @Test
    void cycleFailsRatherThanRecursingForever() {
        TransactionOuterClass.TransactionTree tree = TransactionOuterClass.TransactionTree
                .newBuilder().setTransactionId("tx-6")
                .putEventsById("#tx-6:0", wrap(exercised("#tx-6:0", true, "#tx-6:1")))
                .putEventsById("#tx-6:1", wrap(exercised("#tx-6:1", true, "#tx-6:0")))
                .addRootEventIds("#tx-6:0").build();

        LedgerException ex = assertThrows(LedgerException.class, () -> toTree(tree));
        assertTrue(ex.getMessage().contains("not a tree"), ex.getMessage());
    }


    @Test
    void unsetNodeKindFailsLoudly() {
        TransactionOuterClass.TransactionTree tree = TransactionOuterClass.TransactionTree
                .newBuilder().setTransactionId("tx-7")
                .putEventsById("#tx-7:0", TransactionOuterClass.TreeEvent.getDefaultInstance())
                .addRootEventIds("#tx-7:0").build();

        assertThrows(LedgerException.class, () -> toTree(tree));
    }


    @Test
    void emptyTransactionYieldsNoRoots() throws Exception {
        TxTree txTree = toTree(TransactionOuterClass.TransactionTree.newBuilder()
                .setTransactionId("tx-8").build());
        assertTrue(txTree.lstRoot().isEmpty());
        assertEquals(Optional.empty(), txTree.idCommand());
    }

}
