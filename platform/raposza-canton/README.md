<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# raposza-canton

Everything that knows what Canton is: installations, versions, editions,
configuration and overlays, node launching, PQS/scribe.

Depends on `raposza-runtime`. Knows nothing about sandboxes: a class that
mentions a sandbox topology, a persistence MODE or a CaQL fixture belongs in
`raposza-sandbox`.

## Status

**Holds `canton.install`, `canton.pqs`, `canton.dar`, `canton.topology` and
`canton.process`.** Thirty-four classes, 200 tests.

| package | classes |
| --- | --- |
| `canton/install/` | `CantonInstallation`, `CantonInstallations`, `CantonBuiltinConf`, `Edition`, `InstallException`, `InstallSource`, `PqsInstallation`, `PqsInstallations`, `PqsSource`, `ScribeBanner`, `ScribeProbe`, `VersionId` |
| `canton/pqs/` | `PqsSpec`, `ScribeProcess`, `ScribeTables` |
| `canton/dar/` | `CantonBuiltinDars`, `DarCatalog`, `DarException`, `DarInfo` |
| `canton/topology/` | `CantonLine`, `Canton2xConfig`, `Canton3xBootstrap`, `Canton3xDaemonBootstrap`, `AuthOverlay`, `AdminTokenOverlay`, `DaemonPortsOverlay`, `RemoteConsoleOverlay`, `StorageOverlay`, `SandboxPorts`, `Sandbox2xPorts` |
| `canton/process/` | `CantonProcess`, `Canton2xProcess`, `Canton3xDaemonProcess`, `CantonConsoleProcess` |

**Nothing here starts a node, and `canton/install/` is why this module
exists.** Which Canton and which PQS are on this machine is asked by callers
that start nothing at all, and asking a module called SANDBOX to find out
would be the wrong question. Nothing about those types is sandbox-shaped.

**What is deliberately NOT here.** `CantonSandboxProcess` launches Canton's
`sandbox` subcommand and takes a `SandboxSpec`; `SandboxLauncher` chooses
between the subcommand and the daemon. Both stayed in `raposza-sandbox`, and
`CantonSandboxProcess` extends `CantonProcess` across the module boundary -
which is the direction the enforcer rules hold, not a violation of them.

**Two names in `canton/topology/` are known to be wrong and were NOT changed
on the way in.** `SandboxPorts` and `Sandbox2xPorts` are records of
ledger-api, admin-api, sequencer and mediator ports - a Canton NODE shape,
which is what they are. Only the names say sandbox.
Renaming is a separate decision from moving, exactly as it was for
`SandboxPostgres` in `raposza-runtime`.

**`topology` is a loaded word in this domain and the package name is
provisional.** Canton uses "topology" for identity: namespace delegations,
party-to-participant mappings, topology transactions. What is in this package
is node CONFIGURATION - HOCON overlays, a 2.x config file, console bootstrap
scripts and port records.

## Dependencies

`raposza-runtime` since increment 6, `raposza-auth` since increment 8.
Neither was declared before something imported it.

| module | why |
| --- | --- |
| `raposza-runtime` | `ManagedProcess` for scribe, `PostgresCoordinates` for storage overlays, `PortGuard` behind the port records |
| `raposza-auth` | `AuthPlan`, which `AuthOverlay` renders into a participant's `auth-services` block |

`raposza-auth` is a CONFIGURATION dependency and not a credential one. This
module writes the block that tells Canton where to find a key; it does not mint
a token and does not verify one.

## DARs

`dar/` reads Daml archives without decoding them. `DarInfo` and `DarCatalog`
open the zip and read its manifest; `CantonBuiltinDars` extracts the archives
shipped inside the Canton jar; `DarException` is the one failure type.

**This is not a Daml-LF reader and must not become one.** S-1 from the parent
bans a Daml-LF dependency here, and nothing in this package needs one: a
manifest is text in a zip entry. Decoding package payloads is `raposza-lf`'s
job, in a module where that dependency is allowed.

## Configuration and the console

`AuthOverlay`, `StorageOverlay` and `AdminTokenOverlay` write the PARTICIPANT's
configuration, under the bundled node name `sandbox`. `RemoteConsoleOverlay`
writes the CONSOLE's and binds the same node as `raposza`, because
`sandbox-console` generates a `remote-participants.sandbox` entry from its port
flag defaults and that entry shadows a configured one of the same name.

Three overlays go to Canton as separate `-c` files, so a stack that will not
start is diagnosed by removing one at a time. That has paid repeatedly: every
configuration rejection so far named a path, a file and a line.

**The admin token has to be pinned when auth is on.** Canton mints one
otherwise; it rotates every five minutes and carries neither `admin-claim` nor
`act-as-any-party-claim`. Without `admin-claim` the console's admin API calls
succeed and its Ledger API calls are refused, which looks like success until
something allocates a party. Pinning also needs
`canton.parameters.non-standard-config = yes`, which `AdminTokenOverlay`
renders alongside; the flag is global, so this overlay is not free.

`Canton3xBootstrap` renders the console script: a marker before any console
call, the participant id after one, and optionally a party and a user. The two
sentinels separate "the console never got here" from "a console call failed",
which have opposite fixes. WHEN that script runs, and which launcher runs it,
is `raposza-sandbox`'s question rather than this module's.

## PQS

Optional, and owned by the stack when it is asked for: `stack.usePqs(spec)`
before `start()`. It comes up after Canton is serving, because it connects to
the Ledger API, and goes down before Canton does, because a client left
streaming from a participant that is being taken away reports the shutdown as a
fault. The stack that owns that ordering is `raposza-sandbox`'s; what lives here
is the process, the spec and the schema.

`ScribeProcess` runs REAL or MOCK, and the mock is a mode rather than a lesser
version: with a binary it runs scribe, without one it provisions the schema
in-process, and the command is rendered either way so a mock run still yields a
line to paste in on a licensed machine. What the mock measures is the argument
vector, the database, the schema, the token and the lifecycle. It measures
nothing about scribe, and `isReal()` is how a caller keeps that distinction.

`PqsSpec` decides the rest. Its database name is DERIVED from the binary -
`pqs_3_4_3`, and `pqs_mock` when none resolved - because 3.4.1 writes schema
revision 034 and 3.4.3 writes 035, so one database for two binaries is a
migration failure waiting to be misread. `PqsSpec.resolveFor(installation)`
gives the real one where the line has a staged binary and the mock where it has
none, which is the normal answer for every 2.x line.

The token is supplied, never minted here: scribe takes a STATIC access token on
its command line, so nothing renews it and a restart is the renewal mechanism.

## Installations

`install/` answers what Canton and PQS are present on this machine.

Two installation models exist and they share no layout: the Daml Assistant puts
a version under `~/.daml/sdk/<version>/canton/`, DPM puts one under
`~/.dpm/cache/components/<component>/<version>/`. Neither is derived from the
other, and a DPM SDK bundle version is not a Canton version.

Three rules the code enforces rather than documents:

* **A version does not identify an installation.** The same version string can
  name an open-source build and an enterprise one, installed side by side.
  Edition is carried, and it is never defaulted.
* **PQS availability is an independent axis.** A Canton installation says
  nothing about whether a PQS exists for it. Finding none is a normal answer.
* **PQS resolves through the line alias**, `~/.pqs/line/<canton-line>/`, never
  a patch version written into code. The schema revision moves at patch level,
  so two binaries on one line still need two databases.

## Processes

`process/` launches a Canton NODE and watches it come up. `CantonProcess` is
the base - a working directory, a log file, and the problem-line extraction
that turns a failed start into a diagnosis. `Canton2xProcess` runs
`daemon -c conf --bootstrap script`; `Canton3xDaemonProcess` runs the 3.x
daemon; `CantonConsoleProcess` runs `sandbox-console`, which is a second,
short-lived process because the `sandbox` subcommand refuses `--bootstrap`.

**Readiness is asked two ways and that is not redundancy.** Canton prints a
ready line and writes its port file only once the stack is serving. The file
survives a rewording of the log; the line arrives first.
## The rule

`pom.xml` refuses a declared dependency on `raposza-sandbox` or
`raposza-localnet`. The parent's S-1 rule still applies and
is NOT skipped here: this module launches processes and writes HOCON text, and
never needs a Canton, Daml-LF, protobuf or gRPC artefact on its own classpath.
If an increment appears to need one, that is a finding rather than an exemption.
