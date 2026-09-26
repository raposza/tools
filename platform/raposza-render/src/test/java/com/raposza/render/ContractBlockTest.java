// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raposza.api.model.Contract;
import com.raposza.api.model.DamlValue;
import com.raposza.api.model.DataId;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * The contract detail form: one field per line, nesting by indent.
 *
 * Author Claude/bentzn
 */
class ContractBlockTest {

    private static final DataId ID_ACCOUNT = new DataId("pkg1", "Main", "Account");


    private static DamlValue.Rec.Field fld(String nameField, DamlValue value) {
        return new DamlValue.Rec.Field(nameField, value);
    }


    private static DamlValue.Rec rec(DamlValue.Rec.Field... arrField) {
        return new DamlValue.Rec(null, List.of(arrField));
    }


    private static Contract contract(DamlValue.Rec payload) {
        return new Contract("00abc", "1", ID_ACCOUNT, payload, List.of("bank::22", "alice::22"),
                List.of(), Optional.empty(), "", Optional.empty());
    }


    @Test
    void theWholeContractGoesDownThePage() {
        assertEquals("""
                  contractId:     00abc
                  eventId:        1
                  templateId:     pkg1:Main:Account
                  payload:
                    label:        primary
                    balance:      10.0000000000

                  signatories:
                    bank::22
                    alice::22

                  observers:      (empty)

                  key:            (none)
                  active:         true
                """,
                ContractBlock.block(contract(rec(fld("label", new DamlValue.Text("primary")),
                        fld("balance", new DamlValue.Decimal(
                                new java.math.BigDecimal("10.0000000000")))))));
    }


    /** A text is not quoted, because the pane is read rather than parsed. */
    @Test
    void scalarsAreWrittenAsTheLedgerSentThem() {
        assertEquals("primary", ContractBlock.strInline(new DamlValue.Text("primary")));
        assertEquals("alice::22", ContractBlock.strInline(new DamlValue.Party("alice::22")));
        assertEquals(ContractBlock.STR_NONE, ContractBlock.strInline(new DamlValue.Opt(null)));
        assertEquals("primary",
                ContractBlock.strInline(new DamlValue.Opt(new DamlValue.Text("primary"))));
        assertEquals(ContractBlock.STR_EMPTY,
                ContractBlock.strInline(new DamlValue.Lst(List.of())));
    }


    @Test
    void aNestedRecordIndentsRatherThanBracing() {
        String strOut = ContractBlock.block(contract(rec(fld("address",
                rec(fld("city", new DamlValue.Text("Setubal")))))));

        assertTrue(strOut.contains("  payload:\n    address:\n      city:       Setubal\n"),
                strOut);
    }


    /**
     * A list of scalars is what a party list is, and an index in front of each
     * one would be noise. A nested element gets one because it has to hang
     * under something.
     */
    @Test
    void aListOfScalarsIsWrittenBare() {
        String strOut = ContractBlock.block(contract(rec(fld("tags",
                new DamlValue.Lst(List.of(new DamlValue.Text("a"), new DamlValue.Text("b")))))));

        assertTrue(strOut.contains("    tags:\n      a\n      b\n"), strOut);
    }


    @Test
    void anEmptyPayloadSaysSoRatherThanShowingBraces() {
        assertTrue(ContractBlock.block(contract(rec()))
                .contains("  payload:        " + ContractBlock.STR_EMPTY + "\n"));
    }

}
