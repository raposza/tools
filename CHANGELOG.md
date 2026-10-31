<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Changelog

## 0.6.3 - 2026-10-31

* **A first-start check.** The first time the Sandbox runs on a machine - no
  settings file, or no Raposza directory - it shows what it found before
  anything else: DPM and the Daml Assistant with their directories and SDKs,
  the Splice versions and the Canton each would start on, installations
  elsewhere that it does not use, and the `java` on `PATH` it starts Canton
  with. It offers only what is missing: **Download DPM**, **Install DPM from
  file...**, **Install Splice...**, and **Use the one found** for an
  installation elsewhere. **Settings > Check this machine** runs it again. A
  blank DAML Assistant, DPM or Splice directory on the Settings tab shows the
  default it stands for.
* **DPM from file.** Where downloads are blocked, the DPM archive fetched by
  hand installs with **Install DPM from file...**, with no network. A failed
  DPM download offers the file instead of ending there, and the file chooser
  opens in the home directory.
* **LocalNetND runs Splice on the Canton its release names.** The Sandbox
  reads the Canton version from the Splice bundle and starts the newest
  installed patch of that line - Splice 0.9.4 runs on Canton 3.6.3 when 3.6.4
  is not installed. Before, it took the newest Canton of any line. When no
  Canton of the line is installed, the start offers one: **Download** installs
  the newest Daml SDK of that line through DPM, **Install from file...** takes
  the archive. Splice 0.8 is the lowest LocalNetND runs, and **Install
  Splice** lists 0.8 and later.
* **One debug log.** Every window writes `logs/sandbox-<time>-<pid>.log` under
  the Raposza directory: a header with the jar, Java, the operating system,
  the settings file and the environment variables a toolchain or a proxy is
  found through, then every line of every log pane and of the console, each
  tagged with where it came from. The newest ten are kept, and the console
  prints the path. It carries the Sandbox's test credentials - `SECURITY.md`.
* **Start answers at once** (#5). Wiping the database, restoring a snapshot
  and restarting the identity provider run after the controls are disabled
  and the first line is written, off the window's event thread.
* **Install Splice with downloads blocked** says why the list could not be
  read instead of showing an empty dialog (#8). A failure that carries no
  message is named by its type instead of "null".
* **The Workbench's Aviation CaQL script on Canton 2.x** (#7). The starter
  script finds a 2.x party by its display name, and is offered as soon as the
  parties are read, before the packages decode. On 2.x the Sandbox reads the
  ledger's users from `GET /v1/users`. The **Test fixture** lamp shows that the
  fixture is starting while it runs.
* **Documentation** (#3). `apps/sandbox/README.md` gives the embedded
  PostgreSQL's default port as 32101, and `BUILD_AND_INSTALL.md` asks for
  Maven 3.8.7 or later, the version the release is built with.
  `FIRST_INSTALL.md` covers the first-start check and the installs from file,
  tested with Daml SDK 3.6.3 and Splice 0.9.4.
* **Guava 33.7.2-jre**, from 33.3.0-jre, in both jars: CVE-2026-102554,
  GHSA-xxph-c9ww-hj94. Nothing in Raposza deserializes Java objects.
* **Build.** The jsoup the build forces into maven-site-plugin is 1.23.2,
  and the release workflow's maven-help-plugin runs with jsoup 1.23.2,
  XStream 1.4.21 and plexus-utils 4.0.3 forced into it.

## 0.6.2 - 2026-10-26

* **PQS from DPM, on every platform.** With PQS on, the Sandbox runs the
  scribe DPM installed for the Canton line being started, found in DPM's own
  cache - `%APPDATA%\dpm` on Windows - whatever the jar is called. On a
  machine where nobody had staged scribe by hand, every PQS start ran the mock.
  Where DPM has none, the new **PQS scribe.jar** row on the Settings tab names
  a scribe jar by its full path; it is used only for the Canton line its own
  `--version` names, and on another line the Sandbox says PQS is mismatched.
  When PQS cannot start, the main log says why and that the PQS mock is
  running instead.
* **PQS authenticated, with nothing to fill in.** On Canton 3.x, when the form
  names no user, scribe reads as the ledger user `raposza-pqs`, which the start
  creates with the right to read every party when the participant does not
  have it yet. Where the window's identity provider checks clients, scribe is
  registered there as a client with a secret made for that start; before,
  scribe's token was refused and PQS stopped at start.
* **Save snapshot no longer freezes the window.** Save snapshot and the
  founding snapshot copy the database off the window's event thread; Start,
  Save, the snapshot list and Close wait until the copy ends.

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
