<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# AGENTS.md

Author Claude/bentzn

Operating notes for anyone - person or assistant - working in this repository.
Read this first, then `docs/architecture.md`, which is the reference and carries
the reasoning behind everything stated here as a rule.

## What this is

Raposza: developer tooling for teams building applications on Canton. Three
applications, no unified launcher, and none of them requires a container
runtime.

| | |
| --- | --- |
| `apps/sandbox` | starts a local Canton stack, headless or in a window |
| `apps/workbench` | inspects and acts on a participant |
| the JWKS mint | Raposza OIDC, its own tree `raposza_oidc/` beside this one, resolved as an artefact - D-774 |

`README.md` carries the capability support matrix - what works, on which Canton
generation, measured against which version. Read it before claiming anything
about what the tool supports.

## Scripts - modify these rather than handing out Maven incantations

* `./build.sh` - `mvn install -DskipTests`
* `./test.sh` - the one-command all-green check. Headless, needs no
  participant. One opt-in gate: `RAPOSZA_IT_PG=1` adds the suite that starts
  the embedded PostgreSQL
* `./run-sandbox.sh [--build] [--cli]` - the Sandbox, from the shaded jar. The
  window by default; `--cli` runs the stack in the terminal
* `./run.sh [--build]` - the Workbench
* `./clean.sh` - `mvn clean`

All of them run from the repository root, not from a module directory. Do not
silence build output.

## Layout

```
raposza/                  development aggregator, packaging pom
  platform/               shared modules, one version
    raposza-model/         domain types and domain services
    raposza-spi/           translation SPI and capability declarations
    raposza-auth/          token and key material
    raposza-test-idp/      local test identity provider
    raposza-cache/         package cache on the filesystem
    raposza-lf/            Daml-LF archive reading and AST mapping
    raposza-wire/          descriptor-driven Value conversion, both ways
    raposza-admin/         the Canton admin API, through descriptors
    raposza-resolve/       the probe chain and template references
    raposza-render/        renderers, the coercer, command emitters
    caql-core/            the language: parser, runner, transcript, audit
    raposza-target-2x/     Ledger API v1  (Canton 2.9, 2.10)
    raposza-target-3x/     Ledger API v2  (Canton 3.4, 3.5)
    raposza-runtime/       process supervision, embedded PostgreSQL, free ports
    raposza-canton/        installations, config, launching, DARs, PQS
    raposza-sandbox/       the stack: what a sandbox IS and its start order
    raposza-bom/           bill of materials - NOT ACTIVE, no source
  apps/
    sandbox/
    workbench/
  docs/                   architecture reference
  *.sh                    the scripts above
```

Group root: `com.raposza`.

**The root reactor is a development aggregator, not the release structure.** It
lists `platform`, `apps/sandbox` and `apps/workbench` - the mint left it for
its own tree, D-774 - and each application takes
`platform/pom.xml` as its parent. The intended structure is one independent
build per application consuming released platform artefacts through
`raposza-bom`. Nothing has been released, so the BOM is empty. Do not write
documentation describing the target as if it were the tree.

## The thing that must never silently break

**Vendor SDK types - Canton, Daml-LF, protobuf, gRPC - live only in the
translation modules.** Those are `raposza-lf`, `raposza-wire`,
`raposza-admin`, `raposza-target-2x` and `raposza-target-3x`. Everywhere
else the dependency is banned by maven-enforcer in `platform/pom.xml`, and the
build fails rather than the boundary eroding quietly. Each translation module
skips the rule in its own pom, so the exemption is visible where it applies.

The whole design rests on two Ledger API generations and two Daml-LF generations
converging on one semantic model, with more expected. The moment a generated
protobuf class is reachable from `raposza-model`, every consumer acquires
version branches and adding a version becomes a rewrite rather than a module.

If a value cannot be expressed in `DamlType` / `DamlValue`, that is a finding
about the domain model. Say so and stop. Do not widen a module to admit a wire
type.

The enforcer does not search transitively, and this is deliberate. An
application depends on a target at runtime scope and `bannedDependencies` has no
scope filter, so a transitive search would fail the application module for a
dependency it is entitled to have. Do not set `searchTransitive` and expect a
green build; tightening this needs a different rule.

## Invariants that outrank convenience

**Decoder selection is per archive, not per connection.** Each Daml-LF archive
carries its own version and one participant hosts several. Anything that picks a
decoder once at connect time is a defect.

**An absent access mode reads as READ_ONLY.** Profile catalogues move between
machines and people. A catalogue arriving without a mode must never grant write
access to a shared participant. Do not helpfully default to read-write.

**`verbose` must be set on EVERY read.** Measured on Ledger API v1 and v2: with
the flag set, records carry field labels and a record identifier at every
nesting depth; without it, both are absent. Omitting it strips the only thing
that makes a payload renderable without type metadata, and the failure is
silent.

**The declared type is optional in every renderer method.** Because of the
above, values render correctly with no type metadata. Code that requires a type
before it will render anything breaks the Explore pane, which is designed to
work before any decoder exists.

**The response envelope differs by Ledger API generation; the payload does
not.** v1 returns `active_contracts[]`, v2 returns `created_event`, and both
carry the record as `create_arguments`. Code that keys on the envelope belongs
in a target module, never above one.

**Probe order in the resolver is correctness, not performance.** Contract ids
and update ids are both LedgerStrings. A probe that is unsure returns a
candidate; it does not guess.

**A ledger rejection is a result, not an exception.** `submit` returns
`SubmitResult` with `flagOk` false and the Canton error code intact. Only
transport and protocol failures throw `LedgerException`.

**Capability is declared, not inferred.** A target states, per capability, one
of SUPPORTED, UNSUPPORTED or UNMEASURED, and a measured claim cannot be
constructed without the version and the date behind it. `UnsupportedCapability`
is a DIFFERENT TYPE from `LedgerException`, so "correctly refused because
unsupported" and "broken" are different catch blocks. Code that discovers
capability by catching an error conflates the two. UNMEASURED never blocks.

**A declaration is complete or it does not build.** Adding a member to
`Capability` fails every target until each says something about it. Silence is
not an abstention; it reads as a refusal to every caller.

**Nothing above the translation modules may branch on a capability or on a
version.** A capability exists so a refusal can be reported honestly. If code
above the line needs to know which Canton it is talking to, the neutral model
failed and that is the finding to report - not a conditional to add. The one
exception is `raposza-sandbox`'s `caps/`, which is below the line and whose
whole job is starting a particular Canton.

**An application discovers its target; it never names one.** `Targets.only()`,
never `new SomeClient(...)`. The two Ledger API binding jars collide on 306
class names, so an application can host exactly one target, and which one is a
fact about its build.

**Share mechanisms; keep differing policies explicit.** The 2.x and 3.x columns
contain code that looks alike. Port allocation, filesystem discovery, JVM
command construction and readiness waiting are mechanisms and may be shared.
Topology, bootstrap semantics and persistence behaviour are policies and stay
separate. One class with `if (majorVersion == 2)` inside it trades visible
duplication for invisible behavioural difference. `docs/architecture.md` section
12 has the test to apply.

## Conventions

* Java 21, no `var`. K&R braces, 2 blank lines between methods, catch on a new
  line, always braces for `for`/`while`, single-line `if` without braces is OK.
* Hungarian-lite: `lst`/`str`/`cnt`/`set`/`coll`/`idx`/`arr`; reverse naming
  (`fileProp` not `propFile`). DTOs are records.
* Interfaces are suffixed `_i`.
* Every source file starts with the copyright line and
  `SPDX-License-Identifier: Apache-2.0`, then `Author Claude/bentzn` in the
  type comment. ASCII-only source. SI units.
* Javadoc tags lowercase, prefixed `@`.
* Contributions are accepted under the Apache License 2.0; see `LICENSE`.

## Tests

**Public tests establish the correctness of the released software** - unit
tests, regression tests for public behaviour, lightweight component tests where
they are needed, and tests defining externally observable behaviour. The bar for
a new test is whether an outside contributor needs it to find out that a change
broke something.

What does not belong here is accumulated compatibility evidence: cross-version
sweeps, Canton and PQS compatibility matrices, vendor-behaviour probes and
live-environment runs. Those establish what a vendor build does rather than what
this code does, and they need installed Canton binaries that cannot be
redistributed.

A live-gated test is not automatically out of scope. `RAPOSZA_IT_PG` gates a
test that pins behaviour this software promises, and it is public. The question
is what a test establishes, not what it needs to run.

## Never commit

Keys, tokens, JWKS files with private material, `.env` files, IDE state, build
output, a populated `~/.raposza/` cache, or any vendor artefact. Canton and
`scribe.jar` are obtained by the user under their own licence and are never
redistributed here. See `.gitignore`.
