<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# raposza-sandbox

What a sandbox IS: the STACK. Two of them - a 3.x
`SandboxStack` and a 2.x `Sandbox2xStack` - plus the sandbox
spec, the launcher choice, and the process that runs Canton's own `sandbox`
subcommand.

Everything this module used to hold about Canton itself - installations, PQS,
DARs, configuration, node launching - is `raposza-canton`'s now. What stays
is the ORDER things happen in and the shape of a sandbox.

Depends on `raposza-canton`, which depends on `raposza-runtime`. `pom.xml`
refuses a declared dependency on `raposza-localnet`, at `validate`.

A project must be executable from here without the GUI.

## Launching Canton 3.x

The Canton jar has a `sandbox` subcommand, and it ships the topology. Inside
the 3.4 and 3.5 jars is a `sandbox/sandbox.conf` defining a participant, a
sequencer and a mediator; the subcommand starts them and bootstraps the
synchronizer from it.

So this module writes no participant configuration for 3.x. It OVERLAYS that
file with `-c`, restating only what has to change. A configuration of our own
would be a reimplementation of a file the vendor revises per release, and every
revision would arrive as a difference someone had to notice.

Readiness is asked two ways: Canton prints a ready line, and it writes the port
file only once the stack is serving. The file survives a rewording; the line
arrives first.

Canton 2.x has no such subcommand - its usage line is daemon, run and generate
- and needs a configuration and a console bootstrap script written by hand.
`CantonSandboxProcess` refuses anything below 3.0 rather than assembling a
command that would fail obscurely.

## Storage

Participant, sequencer, sequencer driver and mediator go on PostgreSQL in four
separate databases, or none of them do. A participant that survives a restart
beside a synchronizer that forgets is a desynchronised stack, not a faster one.

The embedded server is `embedded-postgres`, which unpacks a real server binary
and runs it as an ordinary process - the sandbox requires no container runtime.
Its default `synchronous_commit` is `off`, which Canton's sequencer writer
refuses to start against, so every database this module creates is given
`synchronous_commit = on`.

## PQS

`PqsSpec`, `ScribeProcess` and `ScribeTables` are `raposza-canton`'s, as
`com.raposza.canton.pqs`: scribe attaches to a PARTICIPANT rather
than to a sandbox topology, and a second stack family would want it unchanged.

What stays here is the ORDERING, which is a property of the stack rather than
of PQS: `stack.usePqs(spec)` before `start()`, scribe up after Canton is
serving because it connects to the Ledger API, and down before Canton goes,
because a client left streaming from a participant that is being taken away
reports the shutdown as a fault. See `raposza-canton`'s README for the
REAL/MOCK distinction and the derived database name.

## Authentication, and the console

The overlays, the bootstraps, `CantonLine`, `Canton2xConfig` and the two port
records are `raposza-canton`'s, as `com.raposza.canton.topology`.
See that module's README for the participant/console node-name distinction,
the pinned admin token and the two bootstrap sentinels.

What stays here is `SandboxSpec` - the spec OF a sandbox, which is this
module's own shape - and the stack classes that decide WHEN a bootstrap script
runs and WHICH launcher runs it.

## Installations

`install/` is `raposza-canton`'s, as
`com.raposza.canton.install`: which Canton and which PQS are on this
machine is a question about Canton, not about a sandbox topology, and callers
that start nothing at all were already asking it. See that module's README for
the two installation models and the three rules the code enforces.

## Tests

The unit tests run everywhere. One is opt-in, because it starts real
software:

```
RAPOSZA_IT_PG=1 ./test.sh   the embedded PostgreSQL server
```

