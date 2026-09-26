// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.sandbox.app;

import java.util.List;

/**
 * Story 1, the clean lot: every command it submits, and WHO submits it.
 *
 * <h2>Why the commands are decided here and not in the window</h2>
 *
 * Each one is a string built from the fixture's own data, and what can be wrong
 * with it - the template named, the party in the wrong field, an `Int` sent as
 * a number where the API wants a string - is wrong whether or not a ledger is
 * running. `SnapshotRules` exists for the same reason and that is why there are
 * fourteen GUI test classes that open no frame.
 *
 * <h2>A PARTY PER ACTUAL USER - his instruction, 2026-09-22</h2>
 *
 * There were two parties, `PharmaProducer` and `Supplier`. Those are the names
 * of companies, and no company signs anything: a person in a role does. The
 * {@link Cast} is the eight roles `fixture_pharma.md` names in its Topology
 * section, each one a person with a ledger user of their own, and every step
 * below submits as the one who would actually perform it.
 *
 * WHICH IS WHY THE STEPS CARRY DIFFERENT SUBMITTERS. `strActAs` per step is the
 * point of the change, not a detail of it: an order raised by Quality Assurance
 * would be as wrong as an order raised by the company.
 *
 * <h2>THE ENCODINGS ARE MEASURED, not assumed</h2>
 *
 * `Int` and `Decimal` go as QUOTED strings and `Date` as `2026-03-12` -
 * `probes/localnet/AviationLoad.java` submits exactly those shapes against a
 * LocalNetND participant, and `Time` goes as an RFC 3339 instant beside them.
 *
 * <h2>Nine steps, five templates left active</h2>
 *
 * The order is the narrative's: specification, order, acceptance, lot,
 * certificate, shipment, receipt, inspection, release. `Shipment` is the one
 * CONSUMING step - the material stops being in transit when it arrives - so the
 * story leaves a trail behind it rather than a flat list.
 *
 * Author Claude/bentzn
 */
public final class PharmaStory {

    /** The module-qualified names, for reading a created event back. */
    public static final String STR_T_SPEC = "Main:MaterialSpecification";

    public static final String STR_T_ORDER = "Main:PurchaseOrder";

    public static final String STR_T_ACCEPT = "Main:SupplierAcceptance";

    public static final String STR_T_LOT = "Main:ProductionLot";

    public static final String STR_T_COA = "Main:CertificateOfAnalysis";

    public static final String STR_T_SHIPMENT = "Main:Shipment";

    public static final String STR_T_RECEIPT = "Main:MaterialReceipt";

    public static final String STR_T_INSPECTION = "Main:IncomingInspection";

    public static final String STR_T_RELEASE = "Main:MaterialRelease";

    /** Story 2's five, from `fixture_pharma.md`'s Candidate Business Objects. */
    public static final String STR_T_QUARANTINE = "Main:Quarantine";

    public static final String STR_T_DEVIATION = "Main:Deviation";

    public static final String STR_T_RESPONSE = "Main:SupplierResponse";

    public static final String STR_T_RETEST = "Main:Retest";

    public static final String STR_T_DISPOSITION = "Main:Disposition";

    /**
     * The producer organization, on `app-provider`.
     *
     * A NAME RATHER THAN A ROLE, because `PharmaProducer` as a party was the
     * defect: it read as an organization signing its own paperwork. The
     * organization is a prefix on its people's parties now and signs nothing.
     */
    public static final String STR_ORG_PRODUCER = "Kestrel";

    /** The supplier organization, on `app-user`. */
    public static final String STR_ORG_SUPPLIER = "Vinterberg";

    /**
     * The party id hints: ORGANIZATION AND FUNCTION, and no person.
     *
     * HIS CORRECTION, 2026-09-22. They carried a surname - `KHolm`, `TBrandt` -
     * which is what a real deployment looks like and which reads as noise to
     * somebody meeting the fixture for the first time: nothing on the screen
     * says who `KHolm` is or why that party signed. The function does say, and
     * a fixture is read far more often than it is realistic.
     *
     * ORGANIZATION FIRST, because a party id is read left to right in a
     * navigator and the organization is what tells the two Quality Assurance
     * parties apart. The hint is what a reader sees in front of the `::` and
     * the fingerprint, so it is the whole of the identity as far as anybody
     * looking at the ledger is concerned.
     */
    public static final String STR_PARTY_PROCUREMENT = "Kestrel-Procurement";

    public static final String STR_PARTY_QA_PRODUCER = "Kestrel-QualityAssurance";

    public static final String STR_PARTY_RECEIVING = "Kestrel-Receiving";

    public static final String STR_PARTY_MANUFACTURING = "Kestrel-Manufacturing";

    public static final String STR_PARTY_SALES = "Vinterberg-Sales";

    public static final String STR_PARTY_PRODUCTION = "Vinterberg-Production";

    public static final String STR_PARTY_QA_SUPPLIER = "Vinterberg-QualityAssurance";

    public static final String STR_PARTY_SHIPPING = "Vinterberg-Shipping";

    /**
     * The ledger users, one per person, and the party above is allocated
     * NAMING the user - which is what grants it `CanActAs`, measured
     * 2026-09-22 in `probes/pharma/rights_probe.py`.
     *
     * THE FUNCTION, NOT THE PERSON - his correction, 2026-09-22. They were
     * `kholm`, `tbrandt` and so on, and a picker offering eight surnames tells
     * a reader nothing about which one may release a lot. `work as
     * quality-assurance` says it.
     *
     * THE TWO ORGANIZATIONS REUSE `quality-assurance`, and that is not a
     * collision: users are per participant, and the producer's QA and the
     * supplier's QA are the same function in two companies. Which company a
     * tab is in is on its status bar and in every party id it shows.
     *
     * PLAIN IDENTIFIERS, in the shape every user id already on these
     * participants has - `app-provider`, `ledger-api-user`.
     */
    public static final String STR_USER_PROCUREMENT = "procurement";

    public static final String STR_USER_QA_PRODUCER = "quality-assurance";

    public static final String STR_USER_RECEIVING = "receiving";

    public static final String STR_USER_MANUFACTURING = "manufacturing";

    public static final String STR_USER_SALES = "sales";

    public static final String STR_USER_PRODUCTION = "production";

    public static final String STR_USER_QA_SUPPLIER = "quality-assurance";

    public static final String STR_USER_SHIPPING = "shipping";

    /**
     * The organization's own superuser - one per participant, so the id is the
     * same on both and means a different thing on each.
     *
     * IT HOLDS ITS OWN ORGANIZATION'S FOUR PARTIES AND NOTHING ELSE. An
     * any-party right would read every contract on the participant, and D-550
     * took `act-as-any-party` off both lines for exactly that: a fixture whose
     * point is that one organization cannot see the other's private data must
     * not ship a credential that can.
     */
    public static final String STR_USER_SUPER = "superuser";

    /** The stable identifiers the story's documents carry. */
    public static final String STR_MATERIAL = "MAT-4471";

    public static final String STR_ORDER = "PO-2026-0148";

    public static final String STR_LOT = "LOT-88213";

    public static final String STR_COA = "COA-88213-1";

    public static final String STR_SHIPMENT = "SHP-30291";

    public static final String STR_INSPECTION = "INS-2026-0311";

    public static final String STR_RELEASE = "REL-2026-0207";


    private PharmaStory() {
    }


    /**
     * The eight people the story is performed by, as allocated party ids.
     *
     * @param strProcurement who raises the order
     * @param strQaProducer who owns the specification, inspects and releases
     * @param strReceiving who takes delivery
     * @param strManufacturing who may use the material once it is released
     * @param strSales who accepts the order
     * @param strProduction who makes the lot
     * @param strQaSupplier who tests it and signs the certificate
     * @param strShipping who ships it
     */
    public record Cast(String strProcurement, String strQaProducer, String strReceiving,
            String strManufacturing, String strSales, String strProduction,
            String strQaSupplier, String strShipping) {

        /** @return the four parties hosted on the producer's participant */
        public List<String> lstProducer() {
            return List.of(strProcurement, strQaProducer, strReceiving, strManufacturing);
        }


        /** @return the four parties hosted on the supplier's participant */
        public List<String> lstSupplier() {
            return List.of(strSales, strProduction, strQaSupplier, strShipping);
        }

    }


    /**
     * @param cast the eight
     * @return the specification, signed by the producer's Quality Assurance
     */
    public static String strSpec(Cast cast) {
        return strCreate(STR_T_SPEC, "\"producerQa\":" + strJson(cast.strQaProducer())
                + ",\"producerProcurement\":" + strJson(cast.strProcurement())
                + ",\"supplierQa\":" + strJson(cast.strQaSupplier())
                + ",\"materialId\":" + strJson(STR_MATERIAL)
                + ",\"description\":\"Sterile glass vial, 10 mL, type I borosilicate\""
                + ",\"tests\":[\"Identity\",\"Particulate matter\",\"Extractable volume\"]");
    }


    /**
     * @param cast the eight
     * @return Procurement's order
     */
    public static String strOrder(Cast cast) {
        return strCreate(STR_T_ORDER, "\"producerProcurement\":"
                + strJson(cast.strProcurement())
                + ",\"producerQa\":" + strJson(cast.strQaProducer())
                + ",\"supplierSales\":" + strJson(cast.strSales())
                + ",\"supplierProduction\":" + strJson(cast.strProduction())
                + ",\"orderNumber\":" + strJson(STR_ORDER)
                + ",\"materialId\":" + strJson(STR_MATERIAL)
                + ",\"quantity\":\"12000\",\"unit\":\"vial\""
                + ",\"requiredBy\":\"2026-11-30\"");
    }


    /**
     * @param strCid the order
     * @return Sales accepting it
     */
    public static String strAcceptOrder(String strCid) {
        return strExercise(STR_T_ORDER, strCid, "AcceptOrder",
                "\"acceptedAt\":\"2026-09-22T09:15:00Z\"");
    }


    /**
     * THE THREE PARTIES THE LOT NEEDS ARE CHOICE ARGUMENTS. The acceptance was
     * signed by Procurement and Sales and does not carry the supplier's own
     * quality and shipping people, nor the producer's goods-in - and a
     * contract nobody can read is not a fixture. Observers take no authority,
     * so passing them in is a statement about who may see the lot, not about
     * who agreed to it.
     *
     * @param cast the eight
     * @param strCid the acceptance
     * @return the lot Production made against it
     */
    public static String strRecordLot(Cast cast, String strCid) {
        return strExercise(STR_T_ACCEPT, strCid, "RecordLot",
                "\"lotNumber\":" + strJson(STR_LOT)
                + ",\"manufacturedOn\":\"2026-09-28\",\"expiresOn\":\"2029-09-28\""
                + ",\"supplierQa\":" + strJson(cast.strQaSupplier())
                + ",\"supplierShipping\":" + strJson(cast.strShipping())
                + ",\"producerReceiving\":" + strJson(cast.strReceiving()));
    }


    /**
     * THE SUPPLIER'S OWN RESULTS, signed by the person who ran them - and they
     * conform, which is what makes this the clean lot. The measured values sit
     * INSIDE the permitted range on every test, and that is what Story 2
     * diverges from.
     *
     * @param strCid the lot
     * @return the certificate of analysis
     */
    public static String strIssueCoa(String strCid) {
        return strExercise(STR_T_LOT, strCid, "IssueCoa",
                "\"certificateNumber\":" + strJson(STR_COA)
                + ",\"issuedAt\":\"2026-10-02T11:40:00Z\",\"tests\":["
                + strTest("Identity", "FTIR", "99.40", "%", "98.00", "100.00", true) + ","
                + strTest("Particulate matter", "USP <788>", "4.10", "count/mL",
                        "0.00", "6.00", true) + ","
                + strTest("Extractable volume", "Gravimetric", "10.28", "mL",
                        "10.00", "10.50", true) + "]");
    }


    /**
     * @param strCid the certificate
     * @return the shipment
     */
    public static String strShip(String strCid) {
        return strExercise(STR_T_COA, strCid, "Ship",
                "\"shipmentNumber\":" + strJson(STR_SHIPMENT)
                + ",\"shippedAt\":\"2026-10-05T06:20:00Z\",\"carrier\":\"Nordkyl Logistik\"");
    }


    /**
     * THE ONE CONSUMING STEP. The material stops being in transit, so the
     * shipment is archived and the receipt takes its place.
     *
     * `receivedBy` IS GONE. It was a Text field naming the person, and the
     * person is the signatory now - which the ledger can prove and a string
     * cannot.
     *
     * @param strCid the shipment
     * @return Receiving's receipt
     */
    public static String strReceive(String strCid) {
        return strExercise(STR_T_SHIPMENT, strCid, "Receive",
                "\"receivedAt\":\"2026-10-07T08:05:00Z\",\"sealIntact\":true");
    }


    /**
     * THE PRODUCER'S OWN RESULTS, independent of the supplier's and close to
     * them without being identical - two organizations measuring the same lot.
     *
     * @param cast the eight, for the Manufacturing party the inspection names
     * @param strCid the receipt
     * @return the incoming inspection
     */
    public static String strInspect(Cast cast, String strCid) {
        return strExercise(STR_T_RECEIPT, strCid, "Inspect",
                "\"inspectionNumber\":" + strJson(STR_INSPECTION)
                + ",\"inspectedAt\":\"2026-10-08T13:25:00Z\",\"tests\":["
                + strTest("Identity", "FTIR", "99.20", "%", "98.00", "100.00", true) + ","
                + strTest("Extractable volume", "Gravimetric", "10.22", "mL",
                        "10.00", "10.50", true) + "]"
                + ",\"conforms\":true,\"remark\":\"Within specification on both tests\""
                + ",\"producerManufacturing\":" + strJson(cast.strManufacturing()));
    }


    /**
     * `releasedBy` IS GONE, for the reason `receivedBy` is: Quality Assurance
     * signs the release, so naming it in a string said what the signature
     * already says.
     *
     * @param strCid the inspection
     * @return the release for production
     */
    public static String strRelease(String strCid) {
        return strExercise(STR_T_INSPECTION, strCid, "ReleaseForProduction",
                "\"releaseNumber\":" + strJson(STR_RELEASE)
                + ",\"releasedAt\":\"2026-10-08T15:00:00Z\"");
    }


    /**
     * STORY 2, THE PROBLEM LOT - `fixture_pharma.md`. A second order of the
     * same material, which the supplier makes, tests and certifies exactly as
     * it did the first, and which the producer's own incoming testing
     * disagrees with.
     *
     * THE DISAGREEMENT IS THE POINT and it is narrow on purpose: one test of
     * three, just outside the permitted range on the producer's instrument and
     * inside it on the supplier's. A lot that failed everything would need no
     * deviation process to resolve.
     */
    public static final String STR_ORDER_2 = "PO-2026-0193";

    public static final String STR_LOT_2 = "LOT-88246";

    public static final String STR_COA_2 = "COA-88246-1";

    public static final String STR_SHIPMENT_2 = "SHP-30418";

    public static final String STR_INSPECTION_2 = "INS-2026-0356";

    public static final String STR_QUARANTINE = "QUA-2026-0042";

    public static final String STR_DEVIATION = "DEV-2026-0117";

    public static final String STR_RETEST = "RET-2026-0388";

    public static final String STR_DISPOSITION = "DIS-2026-0061";

    /**
     * One of `DispositionOutcome`'s five constructors.
     *
     * THE BARE NAME IS THE WHOLE ENCODING. Measured 2026-09-22,
     * `probes/pharma/types_probe.py`: an enum goes as the constructor string
     * and a tagged object is refused with `Expected ujson.Str`.
     */
    public static final String STR_OUTCOME = "ConditionallyAccepted";


    /**
     * @param cast the eight
     * @return the second order, same material and a smaller quantity
     */
    public static String strOrderProblem(Cast cast) {
        return strCreate(STR_T_ORDER, "\"producerProcurement\":"
                + strJson(cast.strProcurement())
                + ",\"producerQa\":" + strJson(cast.strQaProducer())
                + ",\"supplierSales\":" + strJson(cast.strSales())
                + ",\"supplierProduction\":" + strJson(cast.strProduction())
                + ",\"orderNumber\":" + strJson(STR_ORDER_2)
                + ",\"materialId\":" + strJson(STR_MATERIAL)
                + ",\"quantity\":\"8000\",\"unit\":\"vial\""
                + ",\"requiredBy\":\"2027-01-29\"");
    }


    /**
     * @param cast the eight
     * @param strCid the second acceptance
     * @return the second lot
     */
    public static String strRecordLotProblem(Cast cast, String strCid) {
        return strExercise(STR_T_ACCEPT, strCid, "RecordLot",
                "\"lotNumber\":" + strJson(STR_LOT_2)
                + ",\"manufacturedOn\":\"2026-12-14\",\"expiresOn\":\"2029-12-14\""
                + ",\"supplierQa\":" + strJson(cast.strQaSupplier())
                + ",\"supplierShipping\":" + strJson(cast.strShipping())
                + ",\"producerReceiving\":" + strJson(cast.strReceiving()));
    }


    /**
     * THE SUPPLIER'S RESULTS CONFORM, and that is what makes the story. Its
     * identity result is 98.60 against a floor of 98.00 - inside the range,
     * certified in good faith, and 1.50 above what the producer will measure.
     *
     * @param strCid the second lot
     * @return the certificate the supplier issues for it
     */
    public static String strIssueCoaProblem(String strCid) {
        return strExercise(STR_T_LOT, strCid, "IssueCoa",
                "\"certificateNumber\":" + strJson(STR_COA_2)
                + ",\"issuedAt\":\"2026-12-18T09:05:00Z\",\"tests\":["
                + strTest("Identity", "FTIR", "98.60", "%", "98.00", "100.00", true) + ","
                + strTest("Particulate matter", "USP <788>", "5.20", "count/mL",
                        "0.00", "6.00", true) + ","
                + strTest("Extractable volume", "Gravimetric", "10.31", "mL",
                        "10.00", "10.50", true) + "]");
    }


    /**
     * @param strCid the second certificate
     * @return the second shipment
     */
    public static String strShipProblem(String strCid) {
        return strExercise(STR_T_COA, strCid, "Ship",
                "\"shipmentNumber\":" + strJson(STR_SHIPMENT_2)
                + ",\"shippedAt\":\"2026-12-20T05:40:00Z\",\"carrier\":\"Nordkyl Logistik\"");
    }


    /**
     * THE PRODUCER DISAGREES, on one test of two. 97.10 against a floor of
     * 98.00 - `passed` false, `conforms` false, and a remark that names the
     * certificate it disagrees with rather than just saying no.
     *
     * @param cast the eight
     * @param strCid the second receipt
     * @return the inspection that starts the deviation
     */
    public static String strInspectProblem(Cast cast, String strCid) {
        return strExercise(STR_T_RECEIPT, strCid, "Inspect",
                "\"inspectionNumber\":" + strJson(STR_INSPECTION_2)
                + ",\"inspectedAt\":\"2026-12-22T10:50:00Z\",\"tests\":["
                + strTest("Identity", "FTIR", "97.10", "%", "98.00", "100.00", false) + ","
                + strTest("Extractable volume", "Gravimetric", "10.26", "mL",
                        "10.00", "10.50", true) + "]"
                + ",\"conforms\":false,\"remark\":\"Identity 97.10 % against a floor of"
                + " 98.00 %; " + STR_COA_2 + " reports 98.60 % by the same method\""
                + ",\"producerManufacturing\":" + strJson(cast.strManufacturing()));
    }


    /**
     * THE FIRST CONTRACT ONE ORGANIZATION CANNOT SEE. `Quarantine` names no
     * supplier party, so the supplier's participant does not know the lot is
     * held until the deviation is raised.
     *
     * @param strCid the failing inspection
     * @return the quarantine
     */
    public static String strQuarantine(String strCid) {
        return strExercise(STR_T_INSPECTION, strCid, "QuarantineLot",
                "\"quarantineNumber\":" + strJson(STR_QUARANTINE)
                + ",\"quarantinedAt\":\"2026-12-22T11:20:00Z\""
                + ",\"reason\":\"Identity below specification on incoming test;"
                + " material not to be issued to production pending investigation\"");
    }


    /**
     * @param cast the eight, for the supplier party the quarantine does not
     *        carry
     * @param strCid the quarantine
     * @return the deviation, which is where the supplier first hears of it
     */
    public static String strRaiseDeviation(Cast cast, String strCid) {
        return strExercise(STR_T_QUARANTINE, strCid, "RaiseDeviation",
                "\"deviationNumber\":" + strJson(STR_DEVIATION)
                + ",\"raisedAt\":\"2026-12-23T08:15:00Z\""
                + ",\"description\":\"Incoming identity result is below specification"
                + " and disagrees with the certificate of analysis for the same lot\""
                + ",\"producerResult\":"
                + strTest("Identity", "FTIR", "97.10", "%", "98.00", "100.00", false)
                + ",\"supplierQa\":" + strJson(cast.strQaSupplier()));
    }


    /**
     * THE SUPPLIER'S OWN ANSWER, signed by the supplier - the half of the
     * history the producer cannot write. Its retain sample is inside the range
     * again, which is what makes the disagreement an instrument question
     * rather than a bad lot.
     *
     * @param strCid the deviation
     * @return the response
     */
    public static String strRespond(String strCid) {
        return strExercise(STR_T_DEVIATION, strCid, "Respond",
                "\"respondedAt\":\"2026-12-29T13:30:00Z\""
                + ",\"note\":\"Retain sample re-tested on receipt of the deviation."
                + " Result conforms; the batch record shows no excursion\""
                + ",\"evidence\":" + strVariant("RetainSample",
                        strTest("Identity", "FTIR", "99.10", "%", "98.00", "100.00", true)));
    }


    /**
     * @param strCid the supplier's response
     * @return the producer testing again, which conforms
     */
    public static String strRetest(String strCid) {
        return strExercise(STR_T_RESPONSE, strCid, "OrderRetest",
                "\"retestNumber\":" + strJson(STR_RETEST)
                + ",\"retestedAt\":\"2027-01-06T09:45:00Z\",\"tests\":["
                + strTest("Identity", "FTIR", "98.30", "%", "98.00", "100.00", true) + ","
                + strTest("Identity", "HPLC", "98.40", "%", "98.00", "100.00", true) + "]"
                + ",\"conforms\":true,\"conditions\":{"
                + "\"instrument\":\"FTIR-2, cross-calibrated 2027-01-05\""
                + ",\"operator\":\"day shift, second analyst\""
                + ",\"method\":\"Ph. Eur. 2.2.24\"}");
    }


    /**
     * @param cast the eight, for the Manufacturing party the retest does not
     *        carry
     * @param strCid the retest
     * @return the disposition that ends Story 2
     */
    public static String strDispose(Cast cast, String strCid) {
        return strExercise(STR_T_RETEST, strCid, "Dispose",
                "\"dispositionNumber\":" + strJson(STR_DISPOSITION)
                + ",\"decidedAt\":\"2027-01-07T15:10:00Z\""
                + ",\"outcome\":" + strJson(STR_OUTCOME)
                + ",\"rationale\":\"Retest conforms by two methods and the supplier's"
                + " retain sample agrees. Released for production use only, pending"
                + " instrument cross-calibration\""
                + ",\"producerManufacturing\":" + strJson(cast.strManufacturing()));
    }


    /**
     * THE ONE CONSUMING STEP OF STORY 2, and it names the disposition BY
     * CONTRACT ID - so the archived quarantine points at what resolved it
     * instead of repeating it.
     *
     * @param strCid the quarantine
     * @param strCidDisposition what closed it
     * @return the close
     */
    public static String strCloseQuarantine(String strCid, String strCidDisposition) {
        return strExercise(STR_T_QUARANTINE, strCid, "CloseQuarantine",
                "\"closedAt\":\"2027-01-07T15:30:00Z\""
                + ",\"disposition\":" + strJson(strCidDisposition));
    }


    /**
     * One variant, as the API spells it.
     *
     * TAG AND VALUE, and nothing else works: measured 2026-09-22, a
     * constructor-keyed object is refused and the participant states the rule
     * itself - `Variant type json must have 'tag' and 'value' fields`. A
     * constructor that takes nothing carries `"value": {}`.
     *
     * @param strTag the constructor
     * @param strValue its argument, already JSON
     * @return the variant
     */
    public static String strVariant(String strTag, String strValue) {
        return "{\"tag\":" + strJson(strTag) + ",\"value\":" + strValue + "}";
    }


    /**
     * @param strName what the test is called
     * @param strMethod how it was performed
     * @param strMeasured the value, as the API wants a Decimal
     * @param strUnit its unit
     * @param strLow the permitted minimum
     * @param strHigh the permitted maximum
     * @param flagPassed whether it conformed
     * @return one QualityTest, as JSON
     */
    public static String strTest(String strName, String strMethod, String strMeasured,
            String strUnit, String strLow, String strHigh, boolean flagPassed) {
        return "{\"name\":" + strJson(strName) + ",\"method\":" + strJson(strMethod)
                + ",\"measured\":" + strJson(strMeasured) + ",\"unit\":" + strJson(strUnit)
                + ",\"permitted\":{\"low\":" + strJson(strLow) + ",\"high\":"
                + strJson(strHigh) + "},\"passed\":" + flagPassed + "}";
    }


    /**
     * @param strTemplate the module-qualified template name
     * @param strArguments its fields, as JSON without the braces
     * @return a CreateCommand
     */
    public static String strCreate(String strTemplate, String strArguments) {
        return "{\"CreateCommand\":{\"templateId\":" + strJson(strTemplateId(strTemplate))
                + ",\"createArguments\":{" + strArguments + "}}}";
    }


    /**
     * @param strTemplate the module-qualified template name
     * @param strCid the contract to exercise on
     * @param strChoice the choice
     * @param strArguments its fields, as JSON without the braces
     * @return an ExerciseCommand
     */
    public static String strExercise(String strTemplate, String strCid, String strChoice,
            String strArguments) {
        return "{\"ExerciseCommand\":{\"templateId\":" + strJson(strTemplateId(strTemplate))
                + ",\"contractId\":" + strJson(strCid) + ",\"choice\":" + strJson(strChoice)
                + ",\"choiceArgument\":{" + strArguments + "}}}";
    }


    /**
     * @param strTemplate the module-qualified template name, `Main:Name`
     * @return how the API names it when the package is named rather than hashed
     */
    public static String strTemplateId(String strTemplate) {
        return "#" + PharmaFixture.STR_NAME + ":" + strTemplate;
    }


    /**
     * @param strValue any string
     * @return it, quoted and escaped
     */
    public static String strJson(String strValue) {
        return "\"" + strValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

}
