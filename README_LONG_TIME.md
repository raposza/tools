<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Raposza - the long description

Developer tooling for teams building applications on Canton.

`README.md` is the short front page. `BUILD_AND_INSTALL.md` is the step by step
from a clone to a running window. `SECURITY.md` is the reporting channel and
the security model.

| | |
| --- | --- |
| **Raposza Sandbox** | A stable local Canton development stack with deterministic configuration and optional persistent storage |
| **Raposza Workbench** | Inspect and manipulate a ledger: schema, contracts, transactions, commands |
| **Raposza Admin** | Topology and package vetting over the Canton admin API. Not an application of its own: `platform/raposza-admin` is the client and the Sandbox window's DARs tab is the surface |
| **CaQL** | A shared language, not a tool. Seeding in Raposza Sandbox; query and write surface in Raposza Workbench |
| **LocalNetND** | Not a product of its own: the Sandbox's second topology, a Splice LocalNet run natively - the vendor's bundle as ordinary JVM processes, with the web UIs served natively. The runner is `platform/raposza-runtime`'s `localnet` package |

## Why this exists

The vendor's local development story assumes a container runtime is available
somewhere. In many environments it is not, and some machines have no route to
the internet at all. For those users there is no supported local path.

Every Raposza component runs as an ordinary operating-system process. No Docker,
no Podman, no OCI image extraction. Artefacts can be resolved from a directory
on the machine rather than fetched.

## What this is not

Not a re-implementation of anyone else's deployment. The Canton Network's
payment and incentive layer - amulet, wallets, scan, name service - is a
multi-service application distributed as container compositions and governed
on-ledger by network vote. Teams that need it run the vendor's local network as
shipped. **If a feature requires amulet, it is out of scope here.**

Not a Daml editor, not a Canton console replacement, not a PQS replacement, not
a block explorer.

## Vendor artefacts are not included

Canton and `scribe.jar` are obtained by you, under your own licence, and are
never redistributed here. The tools locate what you supply. This is deliberate,
not an omission.

## Canton support

Support is claimed PER CAPABILITY, against the versions it was measured on. Code
that exists but has not been run against a participant is not support. A
project-wide statement about which Canton generation is supported would be wrong
in one direction or the other, so there is none.

| Capability | Canton 2.x | Canton 3.x |
| --- | --- | --- |
| Sandbox stack | **Supported** - 2.8, 2.9 and 2.10 lines. A participant and an embedded domain, no sequencer/mediator split | **Supported** - 3.4 and 3.5 lines, through Canton's own `sandbox` subcommand |
| Sandbox restart with retained state | **Not measured** | **Supported** on `--launcher daemon` with a `--data-dir`, verified on 3.5.11. The default launcher cannot restart such a stack |
| Admin API - list, upload, remove, vet, unvet a DAR | **Supported** - every code path exercised on every installed version | **Supported** - the same, with vetting state read from the synchronizer topology store on 3.5.12 |
| Local JWKS mint (Raposza OIDC, a separate artefact) | **In development** - the arm is written and has never been run | **Supported** - proved end to end on 3.5.12 |
| PQS | **Experimental** - vendor scribe where one is staged, a stand-in on the 2.10 line, an in-process mock otherwise | **Supported** - real scribe, ingest verified |
| Workbench Ledger API | **Supported** - Ledger API v1, measured on 2.9.6. Explore only; there is no Act pane | **Supported** - Ledger API v2 through `Lapi2Client`, which `Target3x.connect()` returns. Explore only; there is no Act pane. `UPDATE_BY_EVENT_ID` and `INTERFACE_FILTER` are declared unsupported on 3.4.4 - 3.5.12; the rest is unmeasured |
| CaQL headless runner | **Supported** - over the v1 target | **Supported** - over the v2 target. Twelve 3.x cells to 3.5.14 carried the 24-cell matrix, run `2026-09-15T154730Z`; the one cell without a result is `3.5.13-open_source`, which no SDK bundle ships |
| LocalNet (native Splice) | **Not applicable** - LocalNet is a Splice deployment | **Supported** - runs the Splice bundle's own Canton as OS processes; measured against staged Splice 0.7.x bundles |

**Supported** measured working. **Experimental** works, but not on every path.
**In development** written, not yet measured. **Not measured** nobody has
looked. **Not implemented** the code is not there.

2.x is not a compatibility afterthought: it is a second translation
implementation behind the same interface, maintained alongside the first. The
two generations are at different stages in different places, and the table above
is the only statement of where.

## Status

Early. `workbench` builds and runs against a Canton 2.x participant over Ledger
API v1; see `apps/workbench/README.md`.

`platform/raposza-sandbox` starts a Canton 3.4 or 3.5 stack - participant,
sequencer and mediator - on an embedded PostgreSQL, headless, with no container
runtime, with JWKS authentication, start-time DAR upload and PQS; see
`platform/raposza-sandbox/README.md`. Its 2.x column starts a participant and an
embedded domain on Canton 2.9 against a mock PQS.

**`raposza-sandbox` is a library, not an application.** `apps/sandbox` is the
application that drives it - `run-sandbox.sh` opens its window, and
`run-sandbox.sh --cli` starts a stack in the terminal and stops it on Ctrl-C.

**What "stable" covers, and what it does not.** The CONFIGURATION is
deterministic: the same arguments produce the same topology, the same port
block and the same DAR set on every run. The STATE is not carried over by
default - participant identity and the PostgreSQL cluster are recreated on
every run unless `--launcher daemon` and a `--data-dir` are asked for, and a
snapshot can only be restored by the version that took it. Restoring a defined
ledger environment is a supported workflow on that launcher, not on the default
one.

`raposza-runtime` holds process supervision, the embedded PostgreSQL server
and the free-port guard. `raposza-canton` holds installations, configuration,
overlays, node launching, DARs and PQS. `raposza-sandbox` holds the stack
itself: what a sandbox is and the order it starts in.

The Canton 3.x Ledger API client is `Lapi2Client`, over Ledger API v2.

## Requirements

* JDK 21
* Maven 3.9+

## Build and test

```
./build.sh
./test.sh
```

The suite is headless and needs no participant.

**What the tests here are for.** Public tests establish the correctness of the
released software: unit tests, regression tests for public behaviour,
lightweight component tests where they are needed, and tests that define
externally observable behaviour. An outside contributor should be able to change
something and find out from this repository alone whether it broke Raposza.

What is deliberately not here is accumulated compatibility evidence -
cross-version sweeps, Canton and PQS compatibility matrices, vendor-behaviour
probes and live-environment runs. Those establish what a vendor build does
rather than what this code does, and they need installed Canton binaries that
cannot be redistributed. The distinction is purpose, not whether a test
technically qualifies as a unit test.

## Current limitations

What is measured, per capability and per Canton generation, is the table under
"Canton support" above, and nothing outside it is claimed. What the tools
deliberately do not defend against - test credentials, loopback as the only
boundary, downloads checked by TLS alone - is `docs/security-review.md` section
11, and `SECURITY.md` carries the short form.

## Licence

Apache License 2.0. See `LICENSE` and `NOTICE`. Contributions are accepted
under the same licence.
