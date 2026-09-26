<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# raposza-runtime

Process supervision, embedded PostgreSQL, the free-port guard, health and
lifecycle ordering.

**Nothing here knows what Canton is.** A class that names a Canton version, an
edition, a HOCON key or a participant belongs in `raposza-canton`. The test
is not "does it currently compile without Canton" - it is "would a project that
had never heard of Canton still want this".

## Status

**Holds `runtime.process`, `runtime.db` and `runtime.port`.** Process
supervision, the embedded PostgreSQL server and the free-port guard.

| package | classes |
| --- | --- |
| `runtime/process/` | `ManagedProcess`, `ProcessException` |
| `runtime/db/` | `SandboxPostgres`, `PostgresCoordinates`, `DatabaseException` |
| `runtime/port/` | `PortGuard` |

**`PortGuard` is generic, and was not always.** It carried two
`requireStackPortsFree` overloads taking `SandboxPorts` and `Sandbox2xPorts` -
Canton node shapes - with no import line to show for it, because all three
classes shared a package. Those overloads are now `requireFree(int)` methods on
the records themselves, in `raposza-canton`. What remains here is `isFree`,
`occupied` and `requireFree(Map)`: find a free port, refuse a collision, name
every occupied one.

**`SandboxPostgres` keeps its name.** It is the one class in the module whose
name still says "sandbox", and renaming it is a separate decision from moving
it: the type appears in `apps/sandbox` and in four conformance suites, and a
rename would be a public-surface change riding along inside a mechanical move.
Named here so it is not mistaken for an oversight.

**The packages were renamed rather than carried over.** A module that must not
know what a sandbox is cannot hold packages called
`com.raposza.sandbox.*`; the names would have re-created inside this
module the multi-referent "Sandbox" ambiguity the corpus resolved on
2026-08-14.
## The rule

`pom.xml` refuses a declared dependency on `raposza-canton`,
`raposza-sandbox` or `raposza-localnet`, at `validate`. The
last of those matters during the migration: a package that moves here and drags
its old module back in would compile, pass and quietly restore the cycle the
split exists to remove.
