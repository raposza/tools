<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# sandbox

A stable local Canton development stack with deterministic configuration and
optional persistent storage.

Canton nodes, the JSON API, PQS, embedded PostgreSQL, DARs, parties, users,
auth. All ordinary OS processes - no container runtime, ever.

The scope is a standalone ledger development stack. Not a Canton Network
environment: anything requiring amulet is out of scope.

This module is the DELIVERABLE APPLICATION: the window, and the terminal entry
point behind `--cli`. Everything it drives lives in `raposza-sandbox`, and a
project must be runnable from here with no window open.

## In the terminal - `--cli`

`run-sandbox.sh` lives at the REPOSITORY ROOT, not in this module, so every
command below runs from `raposza/` rather than from `raposza/apps/sandbox/`.
Stated because it is not stated by the path: a `./` in a module README reads as
local, and on 2026-08-14 it was run here and failed with "No such file or
directory".

```
./run-sandbox.sh --build --cli      build the launcher jar, then start
./run-sandbox.sh --cli              start from the jar already built
./run-sandbox.sh --list             what Canton and scribe this machine has
./run-sandbox.sh --help             every argument

./run-sandbox.sh --cli --launcher daemon --data-dir ~/.raposza/pgdata
                                    a stack that can be stopped and started again
```

`--build` must be the FIRST argument; everything after it is passed through.

**Both generations.** `--canton 2.10.4` starts the 2.x stack - a participant and
a DOMAIN, no sequencer/mediator split - and the options belonging to the 3.x
launchers are reported as ignored there rather than dropped in silence, which is
the shape of defect measured on scribe's own flag. PQS resolves per line:
vendor scribe where one is staged, the 2.x stand-in on `line/2.10`, and the
in-process mock otherwise.

It starts the newest installed Canton on line 3.5 by default, on Canton's own port block,
with an embedded PostgreSQL on 33321 and a temporary cluster. The status file
`sandbox.properties` in the work directory carries every port, the JDBC url and
the participant id while the stack runs, and is removed when it stops.

Three things it deliberately does not do, each recorded rather than discovered:

* **The Ledger API is unauthenticated unless `--auth` is given.** `--auth JWKS`
  starts the local OpenID Provider, Raposza OIDC, on `127.0.0.1` before Canton
  and points the participant at its key set. The window has its own auth
  settings and does not need the switch.
* **A `--data-dir` stack cannot be restarted ON THE DEFAULT LAUNCHER.** Canton's
  own generated bootstrap re-proposes a synchronizer trust certificate that
  already exists and the stack exits. `--launcher daemon` runs a
  bootstrap this project writes and guards, and a persistent stack on it starts
  again as the same participant with the same party and the same packages -
  live-verified on 3.5.11. Which of the two you are on is
  printed at start, along with what it means for a second start.
* **No init scripts and no `parties.txt`.** Both are named work items;
  `--party` and `--user` cover the one case the console script already has.

## The window - the default

```
./run-sandbox.sh                                the same defaults, in a form
./run-sandbox.sh --launcher daemon              opens with the launcher already set
```

`--cli` is read BEFORE the arguments are parsed and is removed from what the
parser sees, so a terminal run touches no Swing class at all - on a machine
with no display, initialising a toolkit would be the failure rather than
anything about the stack. Without it, everything on the line pre-fills the form
through the same parser the terminal uses, so there is no second dialect.
`--help` and `--list` print and stop, so they run in the terminal either way.

What the window adds over the terminal is a form, Start and Stop, the state as
a chip, the ready report as a table, and the log. What it does NOT add is a
second way of starting a stack: `SandboxService` holds the sequence and both
entry points call it, because two copies would drift on the first flag either
one gained and the drift would only show on a live run.

Three consequences worth knowing before the first start:

* **The SDK box lists what is installed, SDK first.** Each row reads
  `SDK 3.5.5   Canton 3.5.12`: the SDK is the bundle whose manifest
  names that Canton, and `SDK -` is a Canton no SDK on this machine ships.
  Anything without a runtime jar is shown DISABLED rather than hidden. "My 2.10 is not in the list" is a
  worse question than seeing it there greyed out. Both generations are
  startable from the window.
* **The form is disabled while a stack is up.** A field that can be edited
  under a running participant is a field that disagrees with what is listening.
* **Closing the window stops the stack first.** A disposed window over a live
  Canton would leave four node processes and a PostgreSQL behind with only the
  shutdown hook to stop them.

### Everything below the SDK box belongs to the selected version

Changing the SDK box is not a small edit to one row. It changes which
settings apply, what their values are, and which tabs there are.

* **Settings are remembered per version and edition**, in one properties file
  each under `~/.raposza/profiles/`: the first port, the PostgreSQL port, the
  run directory, the ready timeout, the DAR directory and selection, and what
  the Scripts tab runs. They are written before a selection change, at Start,
  and at close.
* **Whether PQS runs is not remembered.** It is a decision about this run and
  it starts OFF every time, because a window that quietly starts a scribe
  because of something done last week is worse than one that asks.
* **The run directory defaults per version**, to
  `~/.raposza/sandbox/<version>-<edition>`. `<run>/dars` is uploaded at
  start and a DAR is compiled against an LF version, so one directory shared by
  a 2.x and a 3.x would offer each of them the other's DARs.
* **Which DARs a start uploads is a per-version selection.** The DARs tab lists
  the `*.dar` files in a directory - `<run>/dars` unless the profile names
  another - with a tick each, and shows the package name, version and the first
  twelve of the package id read from each DAR's own manifest. The ticks are read
  when Start is pressed, so they are a property of the next start rather than a
  button, and both the directory and the ticks are remembered per version and
  edition. A profile with no selection recorded uploads everything in the
  directory, which is what this application did before the tab existed. The
  package id column is not decoration: twelve builds of one fixture are twelve
  files carrying one name and one version, and nothing else tells them apart.
* **A Daml Script is run per version too, from the Scripts tab.** It runs
  `daml script` out of the Daml assistant on PATH, invoked INSIDE a project
  directory whose `daml.yaml` names the SDK - which is what makes one binary
  name drive a 2.8 ledger and a 3.5 one. Three fields, all remembered per
  version and edition: the project, the DAR and the script name, plus whether
  the run uploads the DAR as well. Empty means follow rather than none: the
  project defaults to the staging project the pet shop fixture store built for
  this version, and the DAR to the one the next start uploads. The three
  installs with no compiler beside them have no staging project of their own, so
  the tab falls back to the project of the version that BUILT the DAR in their
  entry - `dar.json` records it, and it is the same donor the fixture sweep drove
  them with. That is read, never chosen: where the record names a version that
  two projects carry, or none, the tab refuses and lists the projects that exist
  instead. Run is a button rather than a property of the next start, because a
  script changes what is on a ledger that is already up, and its outcome goes to
  the Sandbox tab's log beside the start it ran against.
* **Snapshots belong to the version that took them.** One root per version and
  edition under `~/.raposza/snapshots/`, and the window lists and restores
  only the one that is selected. There is no migration and no cross-version
  restore: a snapshot must be created by the version that will use it.
* **A setting a version has no use for is not shown.** The JSON API tab and its
  lamp are 2.x only - on 3.x the participant serves the HTTP Ledger API itself,
  so there is no separate process to watch and the lamp was a second name for
  the participant lamp beside it. What each version supports is answered from
  the command lines this project has read off the binaries, extrapolated from
  the nearest one that was read when a version is new, and a control is hidden
  only when the feature is KNOWN to be absent rather than merely unknown.

The edition is part of the key, not decoration: 3.4.4 was released under both
licences and sits in two caches under one version string.

A stack stopped and started again inside a minute waits for its own PostgreSQL
port rather than refusing: the server that just stopped holds it for about that
long.
