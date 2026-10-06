<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Architecture

Author Claude/bentzn

Public reference for maintainers. Covers the module map, the two version axes,
the semantic model, and the decisions a maintainer needs in order to extend the
software without breaking it.

## 1. Shape

```
raposza/                    development aggregator, packaging pom
  platform/                 shared modules, released together
    raposza-model/           domain types and domain services. No Canton/Daml/proto
    raposza-spi/             translation SPI and capability declarations
    raposza-auth/            token and key material
    raposza-cache/           PackageCache_i on the filesystem, and its JSON
    raposza-rawar/           RAWAR, the RAposza Web ARchive: a web directory's canonical
                            hash, its descriptor, the .rawar file. Knows no Canton
    raposza-lf/              the Daml-LF archive reader, the AST mapping, TypeRegistry_i
    raposza-wire/            protobuf Value conversion, descriptor-driven, both ways
    raposza-admin/           the Canton admin API, spoken through descriptors
    raposza-resolve/         Resolver_i, the probe chain, and template references
    raposza-render/          ValueRenderer_i, the JSON coercer
    caql-core/              the language: parser, runner, transcript, audit log
    raposza-target-2x/       Ledger API v1 client  (Canton 2.9, 2.10)
    raposza-target-3x/       Ledger API v2 client  (Canton 3.4, 3.5)
    raposza-test-idp/        local test identity provider
    raposza-runtime/         process supervision, embedded PostgreSQL, free ports, native Splice LocalNet
    raposza-canton/          installations, config, overlays, node launching, DARs, PQS
    raposza-sandbox/         the stack: what a sandbox IS, the order it starts in,
                            and what each Canton version supports
    raposza-bom/             bill of materials - NOT ACTIVE. No source, not in the
                            reactor, imported by nothing
  apps/
    sandbox/                 headless entry point and the window
    workbench/               Swing front end, main, and the headless script runner
  docs/                     this document
```

Every module in the reactor carries source. A module joins the reactor when
it is about to be worked in rather than when it is finished, and it joins it
on the day it gains its first class: a module outside the reactor inherits no
dependency management and no compiler release, and its first class then fails
for a reason that has nothing to do with the class.

**The reactor is a development aggregator and not the release structure.** The
root `pom.xml` lists `platform` and both applications, and each application
takes `platform/pom.xml` as its parent. The intended structure is one
independent build per application, each consuming released platform artefacts
through `raposza-bom`. Nothing has been released, so the BOM is empty and the
aggregator stands in for it. Documentation describing the applications as
independently versioned consumers of the BOM is describing the target and not
this tree.

### The Sandbox and the 3.x topology

The Canton jar has a `sandbox` subcommand in the 3.4 and 3.5 generations, and
it ships the topology: a `sandbox/sandbox.conf` inside the jar defines a
participant, a sequencer and a mediator, and the subcommand bootstraps the
synchronizer from it. `raposza-sandbox` therefore writes no participant
configuration for 3.x - it overlays that file with `-c` and restates only what
changes, which is storage and authentication. Writing our own would
reimplement a file the vendor revises per release.

Canton 2.x has no such subcommand. Its usage line is daemon, run and generate,
and it needs a configuration and a console bootstrap script of our own. The
two columns are deliberately different code paths rather than one path with a
version switch inside it.

The JSON API differs across the same boundary and in the same direction: in
3.x it is `http-ledger-api` on the participant, and in 2.x it is a separate
process.

### What a version supports, and where an answer comes from

`raposza-sandbox` carries a `caps/` package answering, per Canton version and
edition, whether a given thing is there. It exists because the major version is
a bad predictor: the `sandbox` subcommand appears at 3.4.4 and not at Canton 3,
and `--multi-sync` at 3.5 and not at 3.4. A rule keyed on the major gets both
wrong, and a surface that wrote such a rule beside a layout method would put a
fact about Canton somewhere nobody looking for it would find it.

`CantonLaunchTable` in `raposza-canton` holds one row per binary whose usage
line was read, and it deliberately returns nothing for a version it has never
seen. That refusal is right for a table and useless for an application, which
has to lay something out for whatever happens to be installed. `caps/` is the
layer between the two: it answers for every version, and it says whether the
answer came off that exact binary, was extrapolated from the nearest row on the
same minor line, or is unknown. Extrapolation prefers the line over the major,
because that is where the differences fall.

Two of the features are not about the vendor binary at all - whether the JSON
API is a process of its own, and whether PQS can run - and those are facts
about which stack this project starts. They are exact for the two supported
generations and unknown for anything else, and they do not extrapolate.

A control is hidden only when a feature is known to be absent. Unknown exposes
it: taking a control away from the one person who could find out whether it
works is worse than showing one that turns out to do nothing.

**This is not a licence to branch on a version above the translation modules.**
The rule stands - a capability exists so a refusal can be
reported honestly, and code above the line that needs to know which Canton it is
talking to is a finding about the neutral model. `caps/` is below that line, in
the module whose whole job is starting a particular Canton, and its consumers
are the sandbox application's own surfaces.

**`raposza-model` and `raposza-spi` are two modules and not one.** Both forbid the
same dependencies, so the split buys nothing in what may be depended on; it buys
direction. `raposza-model` is what the domain SPEAKS - `DamlType`, `Contract`,
`Resolver_i`, `TypeRegistry_i`. `raposza-spi` is what a translation target
IMPLEMENTS - `LedgerClient_i`, `LfDecoder_i`, `Target_i`. A module that only
consumes the vocabulary does not receive the contracts, and `raposza-model` has
no reason to change when a Canton version is added.

Group root: `com.raposza`.

## 2. The two version axes

Supporting Daml 2.9 through 3.x means two INDEPENDENT axes. Conflating them is
the main way this design fails.

**Ledger API generation.** Canton 2.x speaks Ledger API v1
(`ActiveContractsService`, `TransactionService`, domains, `ledger_id`). Canton
3.x speaks v2 (`StateService`, `UpdateService`, synchronizers, no `ledger_id`).
Isolated behind `LedgerClient_i`, one module per generation.

**Daml-LF version.** 2.9/2.10 emit LF 1.14/1.15/1.17 from `daml_lf_1.proto`;
Daml 3 uses `daml_lf_2.proto`. Separate protos, and separate generated Java
trees with no shared types - but ONE published reader decodes both, so there is
a single decoder behind `LfDecoder_i` rather than one per generation. Measured:
the reader built for 3.4.11 decodes an LF 1.15 archive without complaint.

Module names carry the CANTON major version (`raposza-target-2x`,
`raposza-target-3x`) and the Java packages carry the LEDGER API generation
(`lapi1`, `lapi2`). Both numbers are visible because both matter and they are
not the same number: an operator reaches for the Canton version, a maintainer
reading the client code needs the API generation.

**Neither generation is "the supported one".** 2.x is a live parallel
implementation maintained alongside 3.x, not a compatibility layer bolted
underneath, and the two are at different stages in different places: the sandbox
runs both, while the Ledger API client stack is complete on 2.x and a stub on
3.x. Support is therefore stated per capability, and the matrix in the root
`README.md` is the authoritative one.

**A target is discovered, never named.** `Target_i` implementations register
through `java.util.ServiceLoader` and an application calls `Targets.only()`. No
application imports a client class. This is not tidiness: the v1 binding jar and
the v2 Canton jar collide on 306 fully qualified class names, measured, so an
application can host exactly one target whatever its dependency graph says.
Which one it hosts is a fact about its build, not a runtime choice. Two targets
on one classpath now fails loudly instead of failing on whichever jar won the
ordering.

**LF version is a property of the archive, not of the connection.** Each archive
carries its own and one participant routinely hosts several at once. No dispatch
is needed: the payload names its own version and the reader branches on it.

Two further differences the client layer absorbs, both measured:

**Response envelope.** v1 returns `active_contracts[]`; v2 returns
`created_event`. Both carry the record as `create_arguments`, so only the
envelope handling is generation-specific.

**Party identity.** On 2.x an allocation hint lands in `display_name` and the
party id is an opaque `party-<uuid>::<fingerprint>`. On 3.x the hint lands in the
party id as `<hint>-<suffix>::<fingerprint>` and there is no display name.
`PartyInfo.label()` prefers the display name and falls back to the id, which is
the right string on either generation without a version branch.

## 3. The semantic model

`raposza-model` models Daml semantics, never wire formats:

```
DamlType    Prim | Numeric | ListOf | OptionalOf | TextMapOf | GenMapOf | Ref
DamlValue   Unit | Bool | Int64 | Decimal | Text | TimeVal | DateVal | Party
            | ContractRef | Rec | Variant | EnumVal | Lst | Opt | TextMap | GenMap
DataShape   Rec | Variant | EnumShape
```

No protobuf type is reachable from this module, and no dependency on Canton,
Daml or protobuf may be added to its pom. Two decoders and two Ledger API
clients converge here, which is why the GUI contains no version branches.

**This is the load-bearing constraint of the codebase.** If the semantic model
cannot express something a new LF version produces, both decoders and the GUI
change together. Treat a proposal to leak a generated type into `raposza-model`
or `raposza-spi` as a defect.

The constraint is a build failure rather than a convention: maven-enforcer in
`platform/pom.xml` bans a Canton, Daml-LF, protobuf or gRPC dependency in every
module except the five translation modules - `raposza-lf`, `raposza-wire`,
`raposza-admin`, `raposza-target-2x` and `raposza-target-3x` - which skip
the rule in their own pom so the exemption list is visible where it applies. The
rule does not yet search transitively; an application depends on a target at
runtime scope and `bannedDependencies` has no scope filter, so tightening it
needs a different rule rather than a flag.

## 4. Type discovery

Types come from the participant's package service (`ListPackages`,
`GetPackage`), decoded into `PackageShape`. The tool therefore holds exactly
what the participant holds, and no version skew is possible.

Local DAR files are an optional supplement for pre-loading, never the primary
source: a package id is a content hash, so a locally held DAR must be the
identical build rather than merely equivalent source.

Decoding uses the published Daml-LF archive reader rather than a hand-written
decoder over generated protobuf classes. The reader handles interning tables,
the type oneof and parameterised type application - the three places a
hand-written decoder is wrong subtly rather than loudly - and reports failures as
typed errors instead of as plausible wrong answers. It costs a Scala runtime and
roughly 48 MB in the launcher jar, and the per-LF-version generated protos it
replaces are not published as releases beyond LF 1.15.

The schema decode is used, which erases every expression. Templates, choices and
types are what a diagnostic tool needs; expression bodies are weight.

Two consequences worth knowing before reading the mapping code. A template's own
choice list does NOT include choices it gains by implementing an interface, so
the two are unioned and each choice records where it came from; `Archive` is
declared by both and is de-duplicated with the template's own winning. And a
choice argument is a reference to a synthetic record rather than an inline field
list, so it is resolved through the registry like any other type reference.

There is no code generation, at build time or run time. The tool is dynamic by
design; generated types would be read reflectively and would buy nothing they
were designed to buy.

## 5. Package cache

Package ids are content hashes, therefore immutable, therefore the cache never
needs invalidating.

```
~/.raposza/packages/<package-id>.dalf     raw archive as fetched
~/.raposza/packages/<package-id>.json     decoded PackageShape
```

On connect: list package ids, subtract what is cached, fetch and decode only
the remainder. The raw archive is kept so a decoder fix can re-decode without
re-fetching. The cache is per user, not per profile - two participants hosting
the same package share one entry.

## 6. Rendering

`ValueRenderer_i<T>` sits between the semantic model and any presentation, so
Swing, JSON, plain text and HTML are the same code path with different
implementations.

The declared type is OPTIONAL in every renderer method. Values served with the
`verbose` flag set carry record field labels AND a record identifier, at every
nesting depth, which is enough to render any contract or transaction correctly
with no type metadata at all. A type improves the result; it is not a
precondition. This is why the Explore pane works before any decoder exists.

This is measured behaviour on both Ledger API generations, not an assumption.
Without the flag, labels and identifiers are both absent - so `verbose` must be
set on every read, and forgetting it fails silently rather than loudly.

A command emitter - a reproducible grpcurl, console, JSON API or Daml Script
invocation for any command the GUI submits - is NOT built. The interface that
named it, `CommandEmitter_i`, had no implementation and was removed on
2026-10-05.

## 7. Resolution

`Resolver_i` turns a pasted string into a `Resolved`. It is an ordered chain of
`RefProbe_i`, not a switch statement: contract ids and update ids are both
LedgerStrings and can look alike, so probe ORDER is a correctness concern.

A probe that recognises the syntax but is not certain returns a candidate and
lets the UI disambiguate. Silently choosing the wrong interpretation is worse
than asking.

## 7a. Capability, and what a target may honestly claim

Supporting a Canton version is claimed PER CAPABILITY and by measurement. A
target declares, for every capability, one of three values:

```
SUPPORTED     measured green, against a named version, on a named date
UNSUPPORTED   refused by design, against a named version, on a named date
UNMEASURED    nobody has looked
```

`Capabilities.of(...)` refuses a declaration that omits any capability, so
adding a capability breaks every target's build until each one says something
about it - including "nobody has looked". A declaration with a hole in it reads
as a refusal to every caller and as an oversight to none.

A measured claim cannot be constructed without the version and the date behind
it. That is the difference between a declaration and a to-do list.

**Three values rather than a boolean, and the third is the point.** A boolean
forces "not measured yet" to be written as "no", which is a claim nobody made
and which is indistinguishable from a measured refusal six months later.

**The distinction the declaration exists to carry** is between a target that
correctly refused something it says it cannot do, and a target that is broken.
`UnsupportedCapability` and `LedgerException` are different types for exactly
that reason. Code that discovers capability by catching an error conflates them
and should be treated as a defect.

**UNMEASURED never blocks.** `require()` throws only on a declared refusal.
Refusing an unmeasured capability would disable the tool against precisely the
versions nobody has got to yet - which is every new one, which is when the tool
is most wanted. The gate reports; it does not refuse.

**A declaration describes the TARGET, not Canton.** "Interface filters are
unsupported on 2.9.6" says the 2.x target refuses them. It says nothing about
whether a 2.9.6 participant could serve them.

**Nothing above the translation modules may branch on a capability.** It exists
so a refusal can be reported honestly, not so a renderer can grow a version
conditional. If a module above the line needs to know which version it is
talking to, the neutral model failed and that is the finding.

Adding a Canton version is therefore a new module implementing `Target_i`, its
capability declaration, and one line in `META-INF/services`. It is never a
change to `raposza-model` or to `raposza-spi`.

## 8. Safety

Connection profiles carry an access mode fixed at creation and not changeable
during a session. `READ_ONLY` disables the Act pane entirely.

**An absent mode reads as `READ_ONLY`.** Profile catalogues are copied between
machines and between people; a catalogue that arrives without a mode must not
grant write access to a shared participant by default.

Every submission is written to a local audit log: who, what, when, and the
resulting update id.

## 9. Authentication, and what a token may not do

Three ways to present a credential, chosen per profile: none, a token pasted or
stored, or one minted locally from a JWKS key file. `TokenSource_i` hides which,
and `null` means send no header at all.

**The mode is declared, never inferred.** `~/.raposza/profiles/<slug>.properties`
carries `auth=none|token|jwks`, and an absent file means `none`. A file that
declares a mode without the material for it is an ERROR; so is an unrecognised
mode. Neither falls back to anonymous, because inferring the mode from which
keys happen to be present turns a typo in `token=` into a silent anonymous
connection to a participant that was configured to expect a credential - the one
failure the file exists to prevent. Clearing a token therefore also returns the
declaration to `none`.

Everything below was measured against a Canton 2.9.6 participant configured to
demand a token, not inferred from the API.

**A token carries rights, and no token carries all of them.** The custom claim
`https://daml.com/ledger-api` grants `admin`, `actAs` and `readAs` separately.
`admin` reaches the party list, the user list and the participant id. It does
NOT let you act as, or read as, any party - those are the two arrays, and the
party ids in them cannot be written until the parties exist. A credential that
does everything is not the normal case; two credentials are.

**Therefore no section of a read may fail the whole read.** `parties()`,
`users()` and `activeContracts()` each carry their own failure into
`LedgerSnapshot`, and the navigator shows the reason on the section. A tree that
empties itself because one of four calls was refused is useless exactly where it
is most needed, and an empty section is indistinguishable from an empty ledger
unless it says why.

**When the participant will not name the parties, the token does.** A read
credential is refused `parties()` by design, which would leave the party picker
empty and no contract read attempted - a window that reads nothing while holding
a credential that could read everything. `readAs` and `actAs` name exactly the
parties that credential can use, so they populate the picker instead. The token
is NOT verified for this: the signature and the expiry are the participant's
business, and every call made with a party from the claim is authorised by the
participant on its own terms.

**But a connection that can read nothing is not a connection.** When the party
list is refused AND the token names none, the refusal is reported rather than
dressed up as a successful connect over four empty sections. The status bar says
`NOT CONNECTED` and the result pane names the missing token.

**Identity fields degrade to empty, and empty must never render.** The
participant id needs an admin token. A read-only operator is turned away from it
and gets an empty string, which as a blank line reads as "this participant has
no id" - not a thing that is true of any participant. The front end substitutes
the reason.

**Submission needs an application id in the token.** Reads do not, so its
absence is invisible until the first command: Canton refuses to default the
`application_id` field when the claim does not carry one, and reports it as
`INVALID_ARGUMENT` on the submission rather than as anything about the token.
`application.id` in the profile settings file supplies it.

**A long-lived token is accepted.** The default minted lifetime is a thousand
years. Canton validates it without complaint; the status bar shows the expiry so
a credential's lifetime is never a hidden property.

## 10. Profile catalogue format

Whitespace separated, one profile per line, `#` comments. The name may contain
spaces, so parsing anchors on the protocol token.

```
name...  protocol  host  ledger-port  json-port  scope  audience  [mode  colour]
```

Six fields after the protocol is the legacy form and parses; eight is the
current form. `-` means absent. `mode` is `ro` or `rw`.

An absent `mode` reads as `ro`. The `-` placeholders are not optional: a line
carrying a mode and a colour but no scope or audience leaves six fields that
parse, with the mode swallowed as a scope and the profile silently read-only.
That line is rejected by number rather than accepted, because guessing what it
meant would turn a typo into a write-enabled profile.

## 11. The window

Three cards share the result area: the detail pane, the transaction tree and the
contract table. `MainWindow` owns the card layout; each pane owns its own state
and exposes listeners rather than reaching back.

**The contract table shows what was read, and says so.** Its filters and its
search run over the loaded rows and never call the participant - Reload in the
navigator is the only path to fresh data. The row count and the read cap are
stated above the rows rather than in a tooltip, because a sortable table reads as
a query interface and the one sentence that says otherwise has to be where the
eye already is. Derivation lives in `ContractRows`, which contains no Swing and
is tested without a display.

**A resolved identifier selects itself** in the navigator and the table, matched
on identity rather than on the label - labels are truncated, package-qualified or
display names, any of which would select the wrong node. The programmatic
selection does not fire the selection listeners, which would otherwise overwrite
the resolution's own rendering one frame later.

**Only one text field reaches the participant.** The navigator filter and the
table's search narrow what is loaded; the resolver asks. All three say which they
are, in the label, the placeholder and the not-found text.

**The window icon is exported at build.** `apps/workbench/src/main/svg/icon.svg`
is the master, its letter converted to paths so the render depends on no
installed font; `raposza-design-maven-plugin` writes `icon.png` into the classes
directory, and no PNG is committed. The renderer lives in the plugin's own
classpath and reaches neither jar. `AppIconTest` asserts what a build can get
wrong - on the classpath, square, large enough, not blank - and that the master
holds no text; nothing about the artwork. `AppIcon` carries the X11 application
class beside the images; the Sandbox has its own copy of all of it.

## 12. Sharing between the 2.x and 3.x columns

**Share mechanisms; keep differing policies explicit.**

The two columns contain code that looks alike, and the tempting move is one
class with `if (majorVersion == 2)` inside it. That trades visible duplication
for invisible behavioural difference, and the behavioural difference is the
thing a maintainer has to be able to see.

SHARE the mechanism: port allocation and validation, filesystem and installation
discovery, JVM command construction, readiness waiting, generic process
lifecycle.

Those exist, and they are named here so a maintainer does not write a second
one. `PortGuard.requireInRange` and `PortGuard.nAbove`, and `ProcessSettle` and
`JvmCommand` beside them, are in `raposza-runtime`. `InstallFs` and
`ScribeLaunch` and `ConsoleScript` are in `raposza-canton`. Every one of them
was two identical copies before it was one.

KEEP SEPARATE the policy: topology, bootstrap semantics, persistence behaviour
where the generations differ, and generation-specific API adaptation. Two
bootstrap strategies whose emitted scripts look similar are still two policies,
and merging them because the text resembles itself is the failure this rule
exists to prevent.

The test is what a change to one generation costs. If a Canton 3.6 difference
can be absorbed by editing one 3.x class, the split is right. If it forces a new
branch inside shared code, the shared code was a policy in disguise.

## 13. Conventions

* Java 21, no `var`. K&R braces, 2 blank lines between methods, catch on a new
  line, always braces for `for`/`while`, single-line `if` without braces is OK.
* Hungarian-lite: `lst`/`str`/`cnt`/`set`/`coll`/`idx`/`arr`; reverse naming
  (`fileProp` not `propFile`). DTOs are records.
* Interfaces are suffixed `_i`.
* Every source file starts with `SPDX-License-Identifier: Apache-2.0`, then
  `Author Claude/bentzn` in the type comment. ASCII-only source. SI units.
