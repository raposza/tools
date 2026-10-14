<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Changelog

## 0.6.1 - 2026-10-14

* **Snapshots wait for PostgreSQL to exit.** The founding snapshot and Save
  snapshot copy the database cluster only once the server that held it has
  exited, and wait up to 60 seconds for it; if it is still running then,
  nothing is copied and the window says so. Stopping returned before the
  server had always exited, so on Windows the founding snapshot could fail on
  `postmaster.pid`, and elsewhere a copy could catch a cluster mid-shutdown.
* **`FIRST_INSTALL.md`** no longer downloads DPM and Splice by hand: the
  Sandbox installs them from the window, and the fixtures are created from the
  Settings tab. The README's first line points to it.

## 0.6.0 - 2026-10-14

* **LocalNetND on Windows.** The Splice bundle carries one launcher, a Unix
  shell script, so on Windows the Sandbox starts Splice as the JVM that script
  runs, and a Splice release counts as installed there when
  `lib/splice-node.jar` is present. PostgreSQL for Windows refuses a setting
  Canton and Splice send, and LocalNetND answers it the way the single
  participant already did. Linux starts Splice as before.
* **LocalNetND behind a proxy.** Both of its process groups start with
  `-Dhttp.nonProxyHosts=localhost|127.*|[::1]` added to `JAVA_TOOL_OPTIONS`,
  unless that already names a list. On a machine whose JVMs carry a proxy, the
  validators' calls to scan on loopback went to the proxy and LocalNetND never
  came up.
* **DPM from the window.** On a single participant with no DPM the SDK box is
  empty, and Install asks **Install DPM?**. It installs DPM into your home
  directory, with no administrator rights, and selects the SDK DPM brought.
  The Aviation offer needs an SDK. Install is off while DPM installs.
* **Splice versions.** The Splice install dialog says it is loading until the
  list arrives. LocalNetND with no Splice installed offers no Pharma data.
* **DARs on the discovery document.** It lists the DARs - `selected` for the
  next start, `uploaded` once a participant has them - and the snapshot a
  running stack was restored from. `GET /dars` on the same port describes
  every template, choice, field and type in them.
* **LF 2.3 in the Workbench.** The Workbench and `GET /dars` read packages with
  the Daml-LF reader of the newest open-source Canton 3.5.1 or later on the
  machine, which reads what the participant accepts. With none installed, the
  Workbench uses its built-in reader and, when that refuses a package, says
  what to install. The status bar names the packages it could not read. A
  contract id keeps the template it refers to.
* **Pharma.** `participant_admin` no longer keeps the act-as rights the
  population used, so on LocalNetND it sees users and parties and no
  contracts, as on a single participant. Create Pharma test data is offered
  again when the Splice box moves to another network.
* **The starter scripts** fetch Pharma's receipt and lot by lot number when the
  selected user cannot read them, and run the reads of users and parties as
  `participant_admin`. They are meant to be run a statement at a time.
* **The window.** Closing it during a start or a stop no longer leaves Canton,
  Splice and PostgreSQL running. Save snapshot waits until the Aviation data is
  created. A stack stopped straight after it started no longer reports a failed
  user registration. Every Sandbox dialog carries the Sandbox's icon.
* **The README** is a front page, and `FIRST_INSTALL.md` takes a clean Linux or
  Windows machine to a running Sandbox and Workbench.

## 0.5.0 - 2026-10-05

* **Raposza OIDC 0.5.1.** The Sandbox carries and starts 0.5.1, whose settings
  are `raposza.oidc.*`. `-Draposza.jwtmint.jar` is refused at start; name the
  jar with `-Draposza.oidc.jar`.
* **CaQL.** `FETCH T WHERE <clause> SINGLE`; `EXERCISE ... VIA <interface>` for
  a choice an interface declares; `$party.asText`, a party's id as text;
  `WHERE <field> = null` tests an Optional for None. `Run` shows each
  statement's progress as it goes, and the CaQL tab's Skills page carries all
  of it.
* **A Web tab, at the top level, on both topologies.** Endpoints lists every
  page the window serves - LocalNetND's web UIs and each participant's JSON
  Ledger API - with its name, its node and its URL; a click copies the URL. Log
  shows each request to them. It replaces OIDC's Web UIs tab. LocalNetND's
  wallet and name service are named for their node -
  `app-provider.wallet.localhost` - and the old names still answer.
* **OIDC > Users and OIDC > Access log.** Users lists who can sign in at the
  window's own provider: username, node and password. On the single
  participant every ledger user is now registered there, with LocalNetND's
  password - before, nobody could sign in on that topology. The Access log
  shows each request to the provider: from where, what was asked, the Origin
  and whether CORS allowed it.
* **A Ledger log on the Sandbox tab**, beside Main: contracts created, choices
  exercised and contracts archived, per node, read from the participant's
  database.
* **The window.** Upload DAR is enabled only while a participant runs and its
  chooser opens in the folder last used; a participant's refusal of a DAR is
  worded; the settings that cannot change while a stack runs are locked; an
  OIDC lamp after PQS; Logs is now Debug; the topology question is an
  application window; the window opens faster and exits at once.
* **Dependencies raised for published advisories:** Jackson 2.21.7, Nimbus
  JOSE+JWT 10.10, Protocol Buffers 3.25.8, PostgreSQL JDBC 42.7.13, and, over
  what the Daml artefacts bring, Bouncy Castle 1.85, Guava 33.3.0-jre and Scala
  2.13.16. The build refuses a declared repository, an unversioned plugin and a
  dynamic or SNAPSHOT dependency version, and compiles with `--release 21`.
* **The release workflow** names its actions by commit and checks the Maven it
  runs against Apache's SHA-512.
* **The provider's access log** is kept under
  `~/.raposza/oidc-access/<port>/`, owner-only. An Aviation or Daml Script run
  that hangs is ended at its timeout.

## 0.4.1 - 2026-09-27

* **Installations on the Settings tab.** Three directories at the bottom of the
  Sandbox's Settings tab point it at a Daml Assistant, a DPM and Splice bundles
  that already exist - your own, or a corporate install directory. Blank means
  the default; a directory set there wins over `DPM_HOME` and `PATH`. The DPM
  row also accepts the directory the `dpm` launcher sits in.
* **Release jars.** Publishing a GitHub Release builds the tagged commit and
  attaches `sandbox-<version>-app.jar`, `workbench-<version>-app.jar` and
  `SHA256SUMS`.
* **The README** is rewritten.

## 0.4.0 - 2026-09-26

* The first public release: Raposza Sandbox, with Sandbox Simple and
  LocalNetND, and Raposza Workbench.
