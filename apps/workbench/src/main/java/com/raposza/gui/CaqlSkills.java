// Copyright (c) 2026 bentzn
// SPDX-License-Identifier: Apache-2.0
package com.raposza.gui;

/**
 * The whole of CaQL on one page, for a person or for a model.
 *
 * <h2>Why it is in the application rather than in a file beside it</h2>
 *
 * A reference that ships separately is a reference that goes stale silently:
 * the language moves, the page does not, and nobody finds out until a
 * statement written from it is refused. Here it is compiled into the same jar
 * as the parser it describes.
 *
 * <h2>Written to be pasted</h2>
 *
 * Plain text, no markup, one topic per block, every form shown as it is
 * typed. That is what a person skims and what a model needs; a page that is
 * good for one is good for the other, and two pages would disagree.
 *
 * Author Claude/bentzn
 */
public final class CaqlSkills {

    private CaqlSkills() {
    }


    /** @return the reference, ready for a text area or a clipboard */
    public static String text() {
        return """
                CaQL - COMPLETE REFERENCE
                =========================

                CaQL is a small statement language for working a Canton ledger:
                allocate parties, create users, create contracts, exercise
                choices, read them back. It runs against the participant this
                window is connected to, as the user named in `work as` above,
                and it works on both the 2.x and 3.x Ledger API generations.

                It is NOT Daml and it is NOT a Daml Script. There is no SDK, no
                DAR and no compile step: a statement is parsed, resolved against
                the packages the participant carries, and sent.


                RUNNING
                -------

                Run           runs the whole editor, top to bottom.
                Ctrl-Enter    runs ONLY the statement the caret is in, however
                              many lines it is laid out over.

                A run stops at the first statement that fails. Statements after
                it are not sent. The transcript below the editor names each
                statement by its line number, so the line you ran and the line
                reported are the same line.

                While a run is in flight, Results shows its PROGRESS, one line
                per statement: where the run is, the statement's line in the
                editor, how it ended, how long it took and its first line.

                  [ 3/122]  line 26    running                  treasury = ...
                  [ 3/122]  line 26    committed         0.21 s treasury = ...

                The running line is replaced by the outcome when the statement
                ends; the full transcript replaces the progress when the run
                is over.

                Identifiers in the editor may be written SHORT, as the panes
                show them - `PlantOperator-d4d95138::` followed by the cut
                marker and the last six characters of the fingerprint. They are
                put back to their full form against what this window has read
                before anything is sent. A short id that matches nothing, or
                matches more than one thing, stops the run and names itself.


                THE STATEMENTS
                --------------

                Reads - allowed on a read-only connection:

                  AS <party> QUERY <template> [WHERE <clause>];
                  AS <party> FETCH <contractId>;
                  AS <party> FETCH <template> [WHERE <clause>] SINGLE;
                  LIST PARTIES;
                  LIST USERS;
                  LIST PACKAGES;
                  GET USER "<userId>";
                  GET LEDGER END;
                  ASSERT <query> COUNT <n>;

                A QUERY may be filtered on payload contents:

                  AS $plant QUERY Main:Batch WHERE weight > 10.0;
                  AS $plant QUERY Main:Batch WHERE holder.name = "acme" AND made >= "2026-01-01";
                  AS $plant QUERY Main:Batch WHERE NOT (open = true OR note = null);
                  AS $plant QUERY Main:Batch WHERE owner = $plant;
                  ASSERT AS $plant QUERY Main:Batch WHERE cases >= 3 COUNT 2;

                A comparison is <field> <op> <value>: the field is a name,
                dotted into nested records; the operator is one of = < <= > >=
                (there is no !=, write NOT field = value); the value is quoted
                text, a number, true, false, null or a $binding, never another
                field. NOT binds tightest, then AND, then OR; parentheses
                regroup. The value is coerced against the field's declared
                type, so "2026-01-01" is a date on a Date field and $plant is
                checked to hold a party. Int64, Numeric, Text, Date and
                Timestamp order; party, contract id, Bool and enum compare
                for equality only. A field a contract lacks, or an Optional
                holding None, is a non-match rather than a failure; a field
                the template does not declare stops the statement.

                The filter runs in the Workbench over what the read returned -
                neither Ledger API filters by payload - so it never widens what
                the parties can see, and the read is capped at 1000 before the
                filter sees it. The transcript records countRead before the
                filter, count after it, and truncated when the cap was hit.

                FETCH ... SINGLE takes the same WHERE, and binds the ONE contract
                that survives it - none, more than one, or one out of a read
                that hit the 1000 cap are each refused, the last because a
                match beyond the cap cannot be ruled out:

                  h = AS $treasury FETCH Utility.Registry.Holding.V0.Holding:Holding
                        WHERE owner = $treasury AND lock = null AND amount = 60000000.0
                        SINGLE;

                `= null` asks whether an Optional is None, whatever it holds - an
                Optional of a RECORD included, which no other comparison reaches.
                `null` takes `=` only, and only on an Optional field.

                Writes - refused on a read-only connection:

                  AS <party> CREATE <template> WITH <json>;
                  AS <party> EXERCISE ON <contractId> <Choice> [VIA <interface>] [WITH <json>];
                  AS <party> EXERCISE ON KEY <template> <json> <Choice> [WITH <json>];
                  ALLOCATE PARTY "<hint>";
                  CREATE USER "<userId>" WITH <json>;
                  DELETE USER "<userId>";
                  GRANT  canActAs|canReadAs <party> TO   USER "<userId>";
                  REVOKE canActAs|canReadAs <party> FROM USER "<userId>";
                  PRUNE TO <offset>;

                And one wrapper:

                  EXPECT <status> <statement>;

                Party allocation and user management are administrative calls
                rather than ledger submissions, and they count as writes: a
                read-only connection is a promise not to change the participant.

                <party> may be a LIST, comma separated:

                  AS $operator, $provider
                     CREATE Utility.Registry.App.V0.Service.Provider:ProviderService
                     WITH { "operator": "$operator", "provider": "$provider" };

                Every party named acts - actAs on CREATE and EXERCISE - or reads
                - readAs on QUERY and FETCH. A contract two parties must sign is
                created by naming both.


                INTERFACE CHOICES - VIA
                -----------------------

                A choice a template inherits from an interface it implements is
                exercised like its own. Which one is sent:

                  * the template's own choice of that name, when it has one;
                  * else the one implemented interface that declares it;
                  * else - MORE THAN ONE interface declares it - the statement is
                    refused, naming every one of them. Say which with VIA:

                  AS $treasury, $registrar
                     EXERCISE ON $factory TransferFactory_Transfer
                     VIA Splice.Api.Token.TransferInstructionV1:TransferFactory
                     WITH { ... };

                The interface is written Module:Entity, like a template.

                VIA comes after the choice, so completion still offers the
                choices once the contract is known. The transcript records
                interfaceId whenever the target is not the template.


                BINDINGS
                --------

                A statement that produces ONE thing can name it:

                  alice = ALLOCATE PARTY "Alice";
                  acct  = AS $bank CREATE Main:Account WITH { "owner": "$alice" };
                  off   = GET LEDGER END;

                Binding forms: FETCH, FETCH ... SINGLE, CREATE, EXERCISE,
                EXERCISE ON KEY, ALLOCATE PARTY, GET LEDGER END.

                Not binding, by grammar rather than by omission: QUERY, LIST,
                CREATE USER, DELETE USER, GRANT, REVOKE, ASSERT, EXPECT, PRUNE.
                QUERY answers with a collection; a collection would need
                indexing, indexing is an expression, and this language has none.

                A binding is single-assignment. Rebinding a name is refused when
                the script is parsed, with the whole script in view.

                A binding holds a VALUE AND ITS TYPE. That is why it exists: a
                contract id carries no template, a decimal carries no scale, and
                an empty list carries no element type.

                A field of a bound record is reachable with a dot:

                  $r.owner.party

                Records only. No index, no operator, no arithmetic, no call.

                ONE CONVERSION, asked for by name: `.asText` on a PARTY is that
                party's id, typed TEXT.

                  "subject": "$treasury.asText"

                A party is NOT turned into text on its own - a Text field given a
                party binding is refused - because an implicit rule would let a
                party land in any label without a word. On a record, `asText` is
                an ordinary field name; on anything else it is refused, naming
                what it reached.

                A binding to a contract that a later consuming choice archived
                is STALE, and using it fails locally rather than on the wire.
                One exception: EXERCISE ON KEY stales nothing, because the
                participant picks which contract the key resolves to and that
                id never reaches the run.


                VALUES
                ------

                `WITH` takes the same JSON the detail pane renders a payload in,
                so a payload can be copied out, edited and submitted.

                JSON alone does not say enough - "10.0" is a Numeric of some
                scale or an Int64, and a string is a party, a text or a contract
                id depending on context. So it is coerced against the type the
                package registry gives for that template or choice. A field that
                does not exist, a missing field and a wrong shape are all caught
                BEFORE anything is sent, and the error names the path to the
                field.

                Encoding, which is the Daml JSON API's own:

                  Int64, Numeric     strings, so no precision is lost
                  Text, Party        strings
                  ContractId         a string
                  Timestamp, Date    ISO strings
                  Bool               true / false
                  Optional           null, or the bare value
                  List               an array
                  Record             an object of its field names
                  Variant            { "tag": ..., "value": ... }
                  Enum               its constructor name, as a string
                  TextMap            an object
                  GenMap             an array of [key, value] pairs
                  Unit               an empty object

                CREATE USER ... WITH is the one exception: a user is not a Daml
                value, so its payload is checked against a fixed schema -
                `primaryParty` and `rights`, nothing else - and its errors read
                differently from every other WITH.

                  CREATE USER "alice-app" WITH {
                    "primaryParty": "$alice",
                    "rights": [ { "canActAs": "$alice" },
                                { "canReadAs": "$bank" } ]
                  };


                SUBSTITUTION
                ------------

                A `$name` inside a JSON string is replaced when the WHOLE string
                is the reference:

                  { "owner": "$alice" }        the binding, with its type
                  { "label": "owner-$alice" }  literal text, no interpolation

                A string that must begin with a literal dollar is written `$$`.

                Substitution happens inside coercion, so the binding's type is
                checked against the type expected at that point. A field wanting
                a party and a binding holding a contract id are both strings in
                JSON; nothing further down would have noticed.


                SYNTAX
                ------

                Keywords are upper case and reserved.
                Comments run from `--` to the end of the line.

                EVERY STATEMENT ENDS WITH `;`. There is no exception, not even
                for the last statement in the editor - a script whose final
                statement carries none is refused before anything is sent. A
                newline is whitespace wherever it falls, so a statement may be
                laid out over as many lines as it reads well on, and two
                statements may share one line.

                Ctrl-Enter runs the statement the caret is in, cut at the first
                `;` outside a text literal and outside a WITH payload, so a
                statement laid out over several lines runs from any of them.

                A `;` inside a text literal or inside a WITH payload is not a
                terminator; it is part of the value.

                A template is written `Module:Entity`, or
                `packageId:Module:Entity` when two packages on the ledger carry
                the same module and entity name.

                A PARTY MAY BE WRITTEN WITHOUT QUOTES. These are the same
                statement:

                  AS Plant-1::1220abcd QUERY Main:Batch;
                  AS "Plant-1::1220abcd" QUERY Main:Batch;

                What makes the bare form a party is the `::` a party id
                carries. A party HINT, a user id and a contract id have none,
                so `ALLOCATE PARTY "Alice"`, `GET USER "alice-app"` and
                `FETCH "0076..."` still take their quotes - and inside a WITH
                payload everything stays strict JSON, so what a detail pane
                renders pastes straight back.

                AS maps to a right by statement: readAs for QUERY and FETCH,
                actAs for CREATE and EXERCISE. ALLOCATE PARTY and CREATE USER
                take no AS.


                LIMITS WORTH KNOWING BEFORE YOU HIT THEM
                ----------------------------------------

                * AS names the parties that act, or that read. A submission
                  needing actAs AND an extra readAs cannot be written.
                * There are no disclosed contracts. A contract a choice needs to
                  see - a registry's configuration, a rule, a credential - has
                  to be visible to one of the AS parties: name its stakeholder
                  among them.
                * WITH on a QUERY and on FETCH ... SINGLE is refused; WHERE, on
                  a QUERY, is the filter. FETCH ... SINGLE only succeeds on a
                  template with exactly one visible contract.
                * ASSERT compares against a literal count. There is no operator
                  and neither side may be computed.
                * PRUNE is irreversible and its outcome is a property of the
                  participant's pruning rules, not of this language.
                * There is no EXECUTE. CaQL runs CaQL; it does not run a Daml
                  Script.


                A WHOLE SCRIPT
                --------------

                  alice = ALLOCATE PARTY "Alice";
                  bank  = ALLOCATE PARTY "Bank";

                  CREATE USER "alice-app" WITH {
                    "primaryParty": "$alice",
                    "rights": [ { "canActAs": "$alice" } ]
                  };

                  acct = AS $bank CREATE Main:Account WITH {
                    "owner": "$alice",
                    "bank": "$bank",
                    "balance": "0.0"
                  };

                  AS $bank EXERCISE ON $acct Deposit WITH { "amount": "10.0" };
                  AS $alice QUERY Main:Account;
                  ASSERT AS $alice QUERY Main:Account COUNT 1;
                """;
    }

}
