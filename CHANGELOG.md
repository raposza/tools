<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Changelog

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
