// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

import com.raposza.api.model.Contract;
import com.raposza.api.model.PartyInfo;

import java.util.List;

/**
 * The script the CaQL editor opens with when the ledger is the Pharma fixture.
 *
 * <h2>TWO SCRIPTS, BECAUSE THERE ARE TWO ORGANIZATIONS</h2>
 *
 * `release_plan.md` section 5: "EXAMPLES ARE PER ROLE, NOT PARAMETERISED. sv,
 * app-provider and app-user see different contracts, so three example sets."
 * This class is two of those three. The Super Validator hosts no Pharma party
 * and holds no contract of this fixture - his correction of 2026-09-21, that
 * the sv is infrastructure rather than an organization - so its tab is offered
 * nothing and that is the correct answer rather than a gap.
 *
 * Which script a tab gets is decided by the parties the CONNECTED participant
 * hosts, not by the parties it can see. {@link PartyInfo#flagLocal} is the
 * participant's own `is_local` off its party-details response -
 * `Lapi2Client:271` - and it is the only discriminator there is: both
 * organizations' parties are visible from both participants on this topology,
 * so presence alone would hand the producer's script to the supplier's tab.
 * MEASURED at his console 2026-09-22: sv listed all fourteen parties and
 * stayed empty, app-provider opened with Kestrel, app-user with Vinterberg.
 *
 * <h2>IT IS LAID OUT TO FIT THE PANE - his instruction, 2026-09-22</h2>
 *
 * Every line is at most {@link #CNT_WIDTH} columns in the short-id form the
 * editor shows, so the starter is read without dragging the horizontal
 * scrollbar. The statements were one line each until that instruction, on the
 * reasoning that a reader runs them one at a time with Ctrl-Enter.
 *
 * THAT REASONING WAS WRONG AND THE EDITOR ALREADY DISAGREED.
 * `CaqlPanel.runLine` cuts the statement with `CaqlParser.spanAt`, which scans
 * to the first `;` at brace depth zero and does not care about newlines, so
 * Ctrl-Enter runs a statement from ANY of its lines. `CaqlSkills` said the
 * opposite in two places and has been corrected with this change.
 *
 * With `Short ids` cleared the ids go back to their full form and the lines
 * run long again; nothing can be laid out to hold a 130-character party id
 * inside a pane, and that is unchanged behaviour rather than a regression.
 *
 * <h2>THE WRITES STAY ON ONE PARTICIPANT, and they have to</h2>
 *
 * A tab holds one connection and one token, so it can act only as parties that
 * participant hosts. Every cross-organizational step of the fixture -
 * `AcceptOrder`, `Respond` - is controlled from the other side and cannot be
 * the write half of either script.
 *
 * So each side exercises a chain that is wholly its own, and neither chain
 * names a party of the other organization in a choice argument:
 *
 * PRODUCER - `Inspect` on a receipt that arrived, then `QuarantineLot` on what
 * that produced. The quarantine NAMES NO SUPPLIER PARTY, which is the whole
 * point of the fixture: MEASURED 2026-09-22, app-user reloaded after the
 * producer's chain picked up the new `IncomingInspection` - the control - and
 * showed no `Main:Quarantine` at all.
 *
 * SUPPLIER - `IssueCoa` on a lot it made, then `Ship` on the certificate that
 * produced. Each exercise takes its target out of the previous result,
 * `$coa`, which is the whole of what a binding is for.
 *
 * <h2>THE ADMINISTRATIVE PAIR, as `participant_admin`</h2>
 *
 * Each side ends as Aviation's does, with `ALLOCATE PARTY` and `CREATE USER`.
 * They need admin rights on the connection and say so. `participant_admin`
 * exists on both organizational participants - his tabs of 2026-09-22 list it
 * `[admin]` - and a tab connected as it ran the Pharma set to
 * `Completed successfully.`, exercising AS Kestrel-QA, on 2026-09-23 - D-845.
 * That was the measurement this pair waited for, `todo.md` A-30.
 *
 * THE TRAINEE IS THE ORGANIZATION'S OWN, `Kestrel-Trainee` or
 * `Vinterberg-Trainee`, with a user of the same name in lower case. Both
 * participants' parties are visible from both, so one name on each would list
 * twice under one name on a LocalNetND that ran both scripts. Neither name
 * starts with a role hint, so a trainee never changes which script a tab gets.
 *
 * The pair needs no contract, so it is emitted on a ledger without the
 * anchor too. NO `superuser` on those lines: it holds parties and no admin
 * rights - D-808.
 *
 * <h2>The ids are the ledger's own, SHORTENED</h2>
 *
 * {@link ShortIds} is applied to the finished script and {@link LongIds} puts
 * the full form back on the way to the participant. Parties are written BARE;
 * a contract id keeps its quotes, because a bare one reads as a template
 * reference.
 *
 * Author Claude/bentzn
 */
public final class PharmaScript {

    /**
     * THE LAYOUT WIDTH, in columns of the short-id form.
     *
     * `AviationScript` carries the same constant and the same value. Two
     * literals rather than one shared home is deliberate: the two starters are
     * independent of each other, and the cost of them drifting is that the two
     * tabs wrap differently, which is cosmetic.
     */
    public static final int CNT_WIDTH = 88;

    /** The module every template of the fixture sits in. */
    public static final String NAME_MODULE = "Main";

    /** The template the producer's write half exercises. */
    public static final String NAME_RECEIPT = "MaterialReceipt";

    /** The template the supplier's write half exercises. */
    public static final String NAME_LOT = "ProductionLot";

    /** What the producer's chain creates first. */
    public static final String NAME_INSPECTION = "IncomingInspection";

    /** What the supplier's chain creates first. */
    public static final String NAME_COA = "CertificateOfAnalysis";

    /** The business identity of the inspection this script performs. */
    public static final String STR_INSPECTION_CAQL = "INS-CAQL";

    /** The business identity of the quarantine it leads to. */
    public static final String STR_QUARANTINE_CAQL = "QUA-CAQL";

    /** The business identity of the certificate this script issues. */
    public static final String STR_COA_CAQL = "COA-CAQL";

    /** The business identity of the shipment it leads to. */
    public static final String STR_SHIPMENT_CAQL = "SHP-CAQL";

    private static final String USER_SUPER = "superuser";

    private static final String USER_QA = "quality-assurance";

    private static final String USER_RECEIVING = "receiving";

    private static final String USER_PRODUCTION = "production";

    private static final String USER_SHIPPING = "shipping";

    private static final String USER_ADMIN = "participant_admin";

    /** The producer's trainee: the party hint, and the user id beside it. */
    private static final String HINT_TRAINEE_PRODUCER = "Kestrel-Trainee";

    private static final String USER_TRAINEE_PRODUCER = "kestrel-trainee";

    /** The supplier's trainee. */
    private static final String HINT_TRAINEE_SUPPLIER = "Vinterberg-Trainee";

    private static final String USER_TRAINEE_SUPPLIER = "vinterberg-trainee";

    /**
     * THE PARTY HINTS ARE `PharmaStory`'s, organization first.
     *
     * They are spelled out here rather than imported: `apps/workbench` does not
     * depend on `apps/sandbox`, and the fixture's party names are as much part
     * of the ledger the Workbench reads as its template names are.
     */
    private static final String HINT_PROCUREMENT = "Kestrel-Procurement";

    private static final String HINT_QA_PRODUCER = "Kestrel-QualityAssurance";

    private static final String HINT_RECEIVING = "Kestrel-Receiving";

    private static final String HINT_MANUFACTURING = "Kestrel-Manufacturing";

    private static final String HINT_SALES = "Vinterberg-Sales";

    private static final String HINT_PRODUCTION = "Vinterberg-Production";

    private static final String HINT_QA_SUPPLIER = "Vinterberg-QualityAssurance";

    private static final String HINT_SHIPPING = "Vinterberg-Shipping";

    /** Later than either story's certificate, so the read has rows and moves. */
    private static final String STR_SINCE = "2026-10-01T00:00:00Z";

    private static final String STR_INSPECTED_AT = "2027-02-03T09:20:00Z";

    private static final String STR_QUARANTINED_AT = "2027-02-03T10:05:00Z";

    private static final String STR_ISSUED_AT = "2027-02-03T08:40:00Z";

    private static final String STR_SHIPPED_AT = "2027-02-04T06:15:00Z";

    /** One level of the WITH block, and the JSON inside it. */
    private static final String PAD_WITH = "    ";

    private static final String PAD_FIELD = "        ";

    private static final String PAD_ITEM = "            ";

    private static final String PAD_INNER = "              ";


    private PharmaScript() {
    }


    /**
     * @param snapshot what the last read returned, may be null
     * @return the script for this participant's organization, or null when
     *         this is not the fixture
     */
    public static String strFor(LedgerSnapshot snapshot) {
        if (snapshot == null)
            return null;
        return strFor(snapshot.lstParty(), snapshot.lstTemplate());
    }


    /**
     * @param lstParty the parties the participant reported
     * @param lstTemplate the templates the read found instances of
     * @return the script, or null when this participant hosts neither
     *         organization's four roles
     */
    public static String strFor(List<PartyInfo> lstParty,
            List<LedgerSnapshot.TemplateGroup> lstTemplate) {
        String idProcurement = idFor(lstParty, HINT_PROCUREMENT);
        String idQaProducer = idFor(lstParty, HINT_QA_PRODUCER);
        String idReceiving = idFor(lstParty, HINT_RECEIVING);
        String idManufacturing = idFor(lstParty, HINT_MANUFACTURING);
        if (idProcurement != null && idQaProducer != null && idReceiving != null
                && idManufacturing != null) {
            return ShortIds.text(strProducer(idQaProducer, idReceiving, idManufacturing,
                    idContractOf(lstTemplate, NAME_RECEIPT)));
        }

        String idSales = idFor(lstParty, HINT_SALES);
        String idProduction = idFor(lstParty, HINT_PRODUCTION);
        String idQaSupplier = idFor(lstParty, HINT_QA_SUPPLIER);
        String idShipping = idFor(lstParty, HINT_SHIPPING);
        if (idSales != null && idProduction != null && idQaSupplier != null
                && idShipping != null) {
            return ShortIds.text(strSupplier(idQaSupplier, idProduction, idShipping,
                    idContractOf(lstTemplate, NAME_LOT)));
        }

        return null;
    }


    /**
     * @param idQa Quality Assurance, who inspects and quarantines
     * @param idReceiving who took delivery
     * @param idManufacturing who would have used the material
     * @param idReceipt the receipt to inspect, or null when there is not one
     * @return the producer's script
     */
    private static String strProducer(String idQa, String idReceiving, String idManufacturing,
            String idReceipt) {
        StringBuilder buf = new StringBuilder();
        buf.append("-- Pharma fixture, Kestrel - the producer's participant.\n");
        buf.append("-- Ctrl-Enter runs the statement the caret is in, however many lines it\n");
        buf.append("-- runs to; Run runs all of it.\n");
        buf.append("\n");
        buf.append("-- READS\n");
        buf.append(who(USER_QA));
        buf.append("LIST PARTIES;\n");
        buf.append("LIST USERS;\n");
        buf.append("GET USER " + quoted(USER_QA) + ";\n");
        // ONE QUERY, sieved on the field the write half sets: run it again
        // afterwards and the count has moved.
        buf.append(query(idQa, NAME_INSPECTION, "conforms = false"));
        if (idReceipt != null) {
            buf.append("\n").append(who(USER_RECEIVING));
            buf.append("receipt = AS ").append(idReceiving).append(" FETCH ")
                    .append(quoted(idReceipt)).append(";\n");
            buf.append("\n");
            buf.append("-- WRITES. An incoming inspection of our own, and the quarantine it\n");
            buf.append("-- leads to. THE QUARANTINE NAMES NO SUPPLIER PARTY: run the supplier\n");
            buf.append("-- tab's script after this one and the contract is not there. That is\n");
            buf.append("-- the fixture's whole point.\n");
            buf.append(who(USER_QA));
            buf.append(inspect(idQa, idReceipt, idManufacturing));
            buf.append("\n").append(who(USER_QA));
            buf.append(quarantine(idQa));
        }
        buf.append(administrative(HINT_TRAINEE_PRODUCER, USER_TRAINEE_PRODUCER));

        return buf.toString();
    }


    /**
     * @param idQa Quality Assurance, who tests the lot and signs for it
     * @param idProduction who made it
     * @param idShipping who ships it
     * @param idLot the lot to certify, or null when there is not one
     * @return the supplier's script
     */
    private static String strSupplier(String idQa, String idProduction, String idShipping,
            String idLot) {
        StringBuilder buf = new StringBuilder();
        buf.append("-- Pharma fixture, Vinterberg - the supplier's participant.\n");
        buf.append("-- Ctrl-Enter runs the statement the caret is in, however many lines it\n");
        buf.append("-- runs to; Run runs all of it.\n");
        buf.append("\n");
        buf.append("-- READS\n");
        buf.append(who(USER_QA));
        buf.append("LIST PARTIES;\n");
        buf.append("LIST USERS;\n");
        buf.append("GET USER " + quoted(USER_QA) + ";\n");
        buf.append(query(idQa, NAME_COA, "issuedAt >= " + quoted(STR_SINCE)));
        if (idLot != null) {
            buf.append("\n").append(who(USER_PRODUCTION));
            buf.append("lot = AS ").append(idProduction).append(" FETCH ")
                    .append(quoted(idLot)).append(";\n");
            buf.append("\n");
            buf.append("-- WRITES. A certificate of our own on the lot, and the shipment it\n");
            buf.append("-- leads to. The second exercise takes its target out of the first\n");
            buf.append("-- one's result, $coa.\n");
            buf.append(who(USER_QA));
            buf.append(issueCoa(idQa, idLot));
            buf.append("\n").append(who(USER_SHIPPING));
            buf.append(ship(idShipping));
        }
        buf.append(administrative(HINT_TRAINEE_SUPPLIER, USER_TRAINEE_SUPPLIER));

        return buf.toString();
    }


    /**
     * WHO HAS TO RUN THE LINE UNDER IT.
     *
     * `work as` decides the token and the token decides whether the
     * participant accepts the statement, so every statement says which user it
     * needs. The organization's own `superuser` holds its four parties and no
     * others - D-808, and D-550 is why it is not an any-party credential - so
     * it can run every line of its own side's script and none of the other's.
     *
     * @param idUser the user whose rights the statement needs
     * @return the comment line, terminated
     */
    private static String who(String idUser) {
        return "-- User: " + idUser + " or " + USER_SUPER + "\n";
    }


    /**
     * `ALLOCATE PARTY` and then `CREATE USER` on the party it returned, as
     * Aviation's script ends. Only `participant_admin` can run either, so the
     * comment names it and no alternative.
     *
     * @param strHint the trainee's party hint
     * @param idUser the trainee's user id
     * @return the section, its leading blank line included
     */
    private static String administrative(String strHint, String idUser) {
        return "\n"
                + "-- ADMINISTRATIVE. Both need admin rights on this connection.\n"
                + "-- User: " + USER_ADMIN + "\n"
                + "trainee = ALLOCATE PARTY " + quoted(strHint) + ";\n"
                + "CREATE USER " + quoted(idUser) + " WITH {\n"
                + PAD_WITH + "\"primaryParty\": \"$trainee\",\n"
                + PAD_WITH + "\"rights\": [ { \"canReadAs\": \"$trainee\" } ]\n"
                + "};\n";
    }


    /**
     * THE CLAUSE GOES ON ITS OWN LINE when the whole statement would not fit,
     * which is the one wrap a read needs.
     *
     * @param idParty who reads
     * @param nameEntity the template, unqualified
     * @param strWhere the clause, without the keyword
     * @return the QUERY statement, terminated
     */
    private static String query(String idParty, String nameEntity, String strWhere) {
        String strHead = "AS " + idParty + " QUERY " + NAME_MODULE + ":" + nameEntity;
        String strOne = strHead + " WHERE " + strWhere + ";";
        if (ShortIds.text(strOne).length() <= CNT_WIDTH)
            return strOne + "\n";
        return strHead + "\n" + PAD_WITH + "WHERE " + strWhere + ";\n";
    }


    /**
     * The inspection carries a list of records with a record inside each -
     * `QualityTest` around `Range` - and an `Optional Text` beside them, which
     * is the datatype spread `fixture_pharma.md`'s Rich data section asks the
     * fixture to render.
     *
     * `Decimal` GOES AS A QUOTED STRING and `Time` as an RFC 3339 instant,
     * measured against a LocalNetND participant -
     * `private/probes/localnet/AviationLoad.java`, and `PharmaStory` submits
     * exactly these shapes.
     *
     * @param idQa the controller
     * @param idReceipt the receipt this inspects
     * @param idManufacturing the choice's one party argument, producer-side
     * @return the EXERCISE statement, terminated
     */
    private static String inspect(String idQa, String idReceipt, String idManufacturing) {
        return "inspection = AS " + idQa + "\n"
                + PAD_WITH + "EXERCISE ON " + quoted(idReceipt) + " Inspect WITH {\n"
                + PAD_FIELD + field("inspectionNumber", STR_INSPECTION_CAQL) + ",\n"
                + PAD_FIELD + field("inspectedAt", STR_INSPECTED_AT) + ",\n"
                + PAD_FIELD + "\"tests\": [\n"
                + PAD_ITEM + test("Particulate matter", "Light obscuration", "14.80",
                        "count/mL", "0.00", "12.00", false) + ",\n"
                + PAD_ITEM + test("Extractable volume", "Gravimetric", "10.31", "mL",
                        "10.00", "10.50", true) + "\n"
                + PAD_FIELD + "],\n"
                + PAD_FIELD + "\"conforms\": false,\n"
                + PAD_FIELD
                + field("remark", "Particulate count 14.80 against a ceiling of 12.00")
                + ",\n"
                + PAD_FIELD + field("producerManufacturing", idManufacturing) + "\n"
                + PAD_WITH + "};\n";
    }


    /**
     * THE ONE CONTRACT THE OTHER PARTICIPANT CANNOT SEE. `Quarantine` names no
     * supplier party, so until a deviation is raised the supplier's tab does
     * not know the lot is held.
     *
     * @param idQa the controller
     * @return the EXERCISE statement, terminated
     */
    private static String quarantine(String idQa) {
        return "quarantine = AS " + idQa + "\n"
                + PAD_WITH + "EXERCISE ON $inspection QuarantineLot WITH {\n"
                + PAD_FIELD + field("quarantineNumber", STR_QUARANTINE_CAQL) + ",\n"
                + PAD_FIELD + field("quarantinedAt", STR_QUARANTINED_AT) + ",\n"
                + PAD_FIELD
                + field("reason", "Particulate matter above specification on incoming test")
                + "\n"
                + PAD_WITH + "};\n";
    }


    /**
     * The supplier's quality assertion, signed by the person who made it. Its
     * results CONFORM, which is what makes it the supplier's own answer rather
     * than a copy of the producer's.
     *
     * @param idQa the controller
     * @param idLot the lot this certifies
     * @return the EXERCISE statement, terminated
     */
    private static String issueCoa(String idQa, String idLot) {
        return "coa = AS " + idQa + "\n"
                + PAD_WITH + "EXERCISE ON " + quoted(idLot) + " IssueCoa WITH {\n"
                + PAD_FIELD + field("certificateNumber", STR_COA_CAQL) + ",\n"
                + PAD_FIELD + field("issuedAt", STR_ISSUED_AT) + ",\n"
                + PAD_FIELD + "\"tests\": [\n"
                + PAD_ITEM + test("Particulate matter", "Light obscuration", "6.40",
                        "count/mL", "0.00", "12.00", true) + ",\n"
                + PAD_ITEM + test("Identity", "FTIR", "99.20", "%", "98.00", "100.00", true)
                + "\n"
                + PAD_FIELD + "]\n"
                + PAD_WITH + "};\n";
    }


    /**
     * The second incarnation, and its target is the FIRST one's result.
     * `IssueCoa` is nonconsuming, so the lot is still there afterwards and the
     * certificate is what moved.
     *
     * @param idShipping the only controller of the choice
     * @return the EXERCISE statement, terminated
     */
    private static String ship(String idShipping) {
        return "shipment = AS " + idShipping + "\n"
                + PAD_WITH + "EXERCISE ON $coa Ship WITH {\n"
                + PAD_FIELD + field("shipmentNumber", STR_SHIPMENT_CAQL) + ",\n"
                + PAD_FIELD + field("shippedAt", STR_SHIPPED_AT) + ",\n"
                + PAD_FIELD + field("carrier", "Nordkyl Logistik") + "\n"
                + PAD_WITH + "};\n";
    }


    /**
     * ONE QualityTest OVER THREE LINES, because one line of it runs to about
     * 160 columns and the permitted range is the part a reader compares
     * against the measured value.
     *
     * @param strName what the test is called
     * @param strMethod how it was performed
     * @param strMeasured the value, as the API wants a Decimal
     * @param strUnit its unit
     * @param strLow the permitted minimum
     * @param strHigh the permitted maximum
     * @param flagPassed whether it conformed
     * @return one QualityTest, as JSON, its first line unindented
     */
    private static String test(String strName, String strMethod, String strMeasured,
            String strUnit, String strLow, String strHigh, boolean flagPassed) {
        return "{ " + field("name", strName) + ", " + field("method", strMethod) + ",\n"
                + PAD_INNER + field("measured", strMeasured) + ", "
                + field("unit", strUnit) + ", \"passed\": " + flagPassed + ",\n"
                + PAD_INNER + "\"permitted\": { " + field("low", strLow) + ", "
                + field("high", strHigh) + " } }";
    }


    private static String field(String nameField, String strValue) {
        return quoted(nameField) + ": " + quoted(strValue);
    }


    private static String quoted(String strValue) {
        return "\"" + strValue + "\"";
    }


    /**
     * @param lstTemplate what was read
     * @param nameEntity the template wanted
     * @return the id of its first contract, or null when there is not one
     */
    private static String idContractOf(List<LedgerSnapshot.TemplateGroup> lstTemplate,
            String nameEntity) {
        LedgerSnapshot.TemplateGroup group = group(lstTemplate, nameEntity);
        if (group == null || group.lstContract() == null || group.lstContract().isEmpty())
            return null;

        Contract contract = group.lstContract().get(0);
        return contract == null ? null : contract.idContract();
    }


    /**
     * @param lstTemplate what was read
     * @param nameEntity the template wanted
     * @return its group, or null
     */
    private static LedgerSnapshot.TemplateGroup group(
            List<LedgerSnapshot.TemplateGroup> lstTemplate, String nameEntity) {
        if (lstTemplate == null)
            return null;

        for (LedgerSnapshot.TemplateGroup group : lstTemplate) {
            if (group.idTemplate() != null
                    && NAME_MODULE.equals(group.idTemplate().nameModule())
                    && nameEntity.equals(group.idTemplate().nameEntity())) {
                return group;
            }
        }
        return null;
    }


    /**
     * LOCAL ONLY. A party this participant can see but does not host is one it
     * cannot act as, and both organizations' parties are visible from both
     * participants on this topology.
     *
     * @param lstParty the parties
     * @param strHint what the fixture names the role
     * @return the one locally hosted party id starting with it, or null when
     *         it is not there or is there twice
     */
    private static String idFor(List<PartyInfo> lstParty, String strHint) {
        String idFound = null;
        if (lstParty == null)
            return null;

        for (PartyInfo party : lstParty) {
            if (party.idParty() == null || !party.flagLocal()
                    || !party.idParty().startsWith(strHint)) {
                continue;
            }
            if (idFound != null)
                return null;
            idFound = party.idParty();
        }
        return idFound;
    }

}
