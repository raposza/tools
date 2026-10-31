<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Author Claude/bentzn  Generated 2026-10-16T08:55:00Z  Updated 2026-10-26T18:45:00Z -->
# Raposza Tools 0.6.3 — skill document for an AI assistant

This document is written for an AI assistant, such as Claude, that guides a person through installing and using Raposza Sandbox and Raposza Workbench. It replaces the READMEs for that purpose. Everything here describes Raposza Tools 0.6.3, the release at <https://github.com/raposza/tools/releases/tag/v0.6.3>.

---

## 0. How to use this document — for the assistant

* **Start with section 1, every time: give the person the choice, and recommend path A.** Path A is clone the source, audit it, build it locally, run. Path B is download the released jars and run. Recommend A before anything else, and most of all when the person is on a restricted computer in a corporate network: security is a high priority for Raposza. Say why in one or two sentences (section 1), then let the person choose, and follow the path they choose without arguing it again.
* **Ask before you guide.** You need to know the operating system (Linux or Windows), whether the machine may reach the internet (GitHub, Maven Central, `get.digitalasset.com`), whether Java 21 or newer is installed, and whether corporate policy restricts what may be downloaded or run.
* **Do not invent.** Every argument, button, tab, path and port in this document was read from the 0.6.3 source. If the person asks for something not described here, say so. Do not guess a flag or a menu item. The source tree is the authority: point to the file that settles the question.
* **One step at a time.** Give one command or one action, then wait for the result. On Linux commands run in a shell; on Windows they run in PowerShell. Nothing in Raposza needs administrator rights.
* **Ask for evidence.** When something fails, ask for the exact text: the terminal output, or the window's **Debug** tab (section 4.9). The Sandbox reports what it is doing in its log, line by line.
* **Say what the person is exposing.** Raposza is developer tooling for one workstation and is not hardened (section 12). Before the person runs it on a shared machine, make sure they have read that section.

---

## 1. First: choose how to get the tools

There are two paths. Offer both, and recommend path A.

| path | what the person does | |
| --- | --- | --- |
| **A - recommended** | clone the source, security audit, build locally, run | sections 1.1 to 1.5 |
| **B** | download the released jars, check their hashes, run | section 1.6 |

**Why A.** The released jars on GitHub are built by a GitHub workflow and are not signed. `SHA256SUMS` proves a download is intact. It cannot prove which source the jars were built from, because whoever can replace the jars can also replace that file (`docs/security-review.md` section 10). Building from audited source removes that question. On a restricted computer in a corporate network it is the expected path.

**Why B may still be the person's choice.** It needs no JDK, Maven or Git, and no route to Maven Central: only Java 21 and the three files of the release. The choice is theirs.

### 1.1 Path A - what the person needs

| | check with |
| --- | --- |
| JDK 21 or newer | `java -version` and `javac -version` |
| Maven | `mvn -version` |
| Git | `git --version` |

The build fetches its dependencies and plugins from Maven Central, so it needs a route there, or a corporate mirror the person's Maven settings already point at. No pom names any other repository, and the build fails at `validate` if one is added (`docs/security-review.md` section 2).

### 1.2 Path A - get the source of the release

```
git clone https://github.com/raposza/tools.git
cd tools
git checkout v0.6.3
```

The Sandbox embeds a second product, **Raposza OIDC**, the local OpenID Provider. Its source is a separate repository, <https://github.com/raposza/OIDC>. Tools 0.6.3 uses Raposza OIDC **0.5.1**, the value of `raposza.oidc.version` in `platform/pom.xml`. A plain Tools build takes that jar from Maven Central. To audit and build it as well, clone that repository, check out the tag for 0.5.1 and follow its own build instructions. Installing it into the local Maven repository (`mvn install`) makes the Tools build below use that build instead of the copy from Central.

### 1.3 Path A - what to audit

Suggest the person, or their security team, read at least:

* `SECURITY.md` — the reporting channel and the short statement of the security model.
* `docs/security-review.md` — the posture document. It covers what executes, what is downloaded and how its integrity is checked, what listens on which interface with which authentication, what is written to disk, what key material is held, what leaves the machine, and the known limitations. It is a review by the authors, not a third-party audit, and says so.
* Every `pom.xml` — the dependencies and plugins, and the `supply-chain` enforcer rules in `platform/pom.xml`.
* The shell scripts at the root: `build.sh`, `test.sh`, `run-sandbox.sh`, `run.sh`, `clean.sh`.
* `.github/workflows/release.yml` — how the published jars are built.

Points an auditor will want to know (all from `docs/security-review.md`):

* **At run time only the Sandbox downloads, and only when the person presses a button.** It can fetch DPM, a Daml SDK, Canton components and Splice bundles from Digital Asset and GitHub. These downloads are checked by TLS only, and then executed. On a locked-down machine, the person can provide DPM and Splice themselves (sections 4.4 and 4.5).
* **The Workbench downloads nothing and listens on nothing.**
* **Everything the Sandbox serves is bound to `127.0.0.1`**, except a Canton **2.x** participant's Ledger API and Admin API, which bind every interface (section 12).

### 1.4 Path A - build and test

Linux:

```
./build.sh
./test.sh
```

Windows - PowerShell:

```
mvn install -DskipTests
mvn install -Papp
```

`build.sh` is `mvn install -DskipTests`. `test.sh` is `mvn install -Papp`, plus a check that every module with tests actually ran them. The suite is headless and needs no Canton. `-Papp` builds the two application jars, which carry their dependencies:

```
apps/sandbox/target/sandbox-0.6.3-app.jar
apps/workbench/target/workbench-0.6.3-app.jar
```

From here on, wherever this document says `sandbox-0.6.3-app.jar` or `workbench-0.6.3-app.jar`, use these files.

### 1.5 Path A - run

Section 3. On Linux the repository root also has launchers: `./run-sandbox.sh` (the Sandbox) and `./run.sh` (the Workbench). `--build` as the FIRST argument rebuilds the jar first; everything after it is passed through. Without `--build` they start the jar already built, which does not change when the sources change.

### 1.6 Path B - download and run

1. Verify Java: `java -version` must show 21 or newer. If it does not, Java is available from <https://www.oracle.com/java/technologies/downloads/>.
2. Download 3 files, `sandbox-0.6.3-app.jar`, `workbench-0.6.3-app.jar` and `SHA256SUMS`, from <https://github.com/raposza/tools/releases/tag/v0.6.3> into the Downloads folder. On Windows, Edge warns about each jar: choose **Keep file**.
3. Check the hashes. Both jars must report `OK`.

Linux:

```
cd ~/Downloads
sha256sum -c SHA256SUMS
```

Windows - PowerShell:

```
cd $env:USERPROFILE\Downloads
Get-Content SHA256SUMS | ForEach-Object { $h, $f = $_ -split '\s+', 2; if ((Get-FileHash $f -Algorithm SHA256).Hash -eq $h) { "${f}: OK" } else { "${f}: FAILED" } }
```

Then run them: section 3.

---

## 2. What the tools are

| | |
| --- | --- |
| **Raposza Sandbox** | Starts a local Canton development stack as ordinary operating-system processes: no Docker, no containers. Two topologies: **Single participant** (one Canton participant with its sequencer and mediator, and an embedded PostgreSQL) and **LocalNetND** (a complete Splice LocalNet, run natively: `sv`, `app-provider` and `app-user`). ND = No Docker. A window by default, or the terminal with `--cli`. |
| **Raposza Workbench** | Connects to a participant and shows what is on the ledger: parties, users, packages, templates, contracts and transactions, with real field names read from the participant's Daml-LF (no codegen). One tab per participant. Its **CaQL** tab runs CaQL statements against the ledger. It also runs CaQL scripts headless. |
| **CaQL** | Raposza's statement language for a Canton ledger: allocate parties, create users, create contracts, exercise choices, query with `WHERE`. Not Daml and not a Daml Script: no SDK, no DAR, no compile step. Section 9 is the complete reference. |
| **Raposza OIDC** | A local OpenID Provider the Sandbox starts and stops itself, so authenticated ledgers and the Splice web UIs work from the first start. Its own repository, <https://github.com/raposza/OIDC>. |

Canton, the Daml SDK, DPM and Splice are Digital Asset's. Raposza does not redistribute them: the person obtains them, and the Sandbox can install them from its window.

This release is tested with Java 21 or newer, Daml SDK 3.6.0, and Splice 0.8.3 and 0.9.1. What is supported on which Canton line - 2.8, 2.9, 2.10, 3.4 and 3.5 - is the table in `README_LONG_TIME.md`. The default line is 3.5.

---

## 3. Starting the applications

Linux and Windows, from the folder holding the jars - `apps/sandbox/target` and `apps/workbench/target` on path A, the Downloads folder on path B:

```
java -jar sandbox-0.6.3-app.jar
java -jar workbench-0.6.3-app.jar
```

Each in its own terminal or PowerShell window. Arguments go after the jar name (sections 5 and 7).

---

## 4. Sandbox — the window

### 4.1 The first question

The window opens with **What should this Sandbox start?**: **Single participant** ("one Canton, one database, the released Sandbox") or **LocalNetND** ("the Splice network natively: sv, app-provider and app-user"). Neither is preselected. The choice holds until the window is closed; to switch, close the window and start it again.

**On a first start, the machine check comes next.** A first start is one with no settings file or no Raposza directory. The dialog opens with **First start - this is what Raposza found on this machine.** and lists DPM and the Daml Assistant (launcher, directory and where it comes from, SDKs), Splice (the directory, and per version the Canton it starts on or what it needs), installations elsewhere that the window does not use, the `java` on `PATH` the Sandbox starts Canton with, and **Can start now** for the Sandbox and LocalNetND. It offers only what is missing: **Download DPM** and **Install DPM from file...** when there is no DPM, **Install Splice...** on LocalNetND when no Splice 0.8 or later is installed, **Use the one found** when an installation elsewhere has what the one in use lacks, and **Close**. After a DPM install the fixture offer does not follow; the fixtures are on the Settings tab. **Check this machine** on the Settings tab shows the same dialog at any time.

### 4.2 Help on every field

Hovering over a field's label shows one line about it. **Clicking the label opens the full help** for that field, and for the fields Digital Asset documents it also shows the vendor's page from `docs.canton.network`, read into the dialog when the machine can reach it. When the person asks what a field does, tell them to click its label; the text there is the authority for that version.

### 4.3 The Sandbox tab

On the left the card **Sandbox** holds the form, four buttons and the snapshot list. On the right the card **Status** holds the lamps and the status table, and below it two logs: **Main**, what the window did, and **Ledger**, one line per contract created, choice exercised and contract archived, such as `app-provider  created  Main:Aircraft  #81` (Splice's own contracts are left out).

**The form.** On LocalNetND the first row is **Splice** instead of **SDK**; the other rows are the same.

| row | what it is |
| --- | --- |
| **SDK** + **Install** | Which installed SDK, and so which Canton, the stack runs. Rows read `SDK 3.5.5   Canton 3.5.12`; `SDK -` is a Canton no SDK on the machine ships; a row marked `[no runtime jar]` is greyed out and cannot start; `[unmeasured]` marks a version not yet measured. The newest version that can start is selected when the window opens. Changing it swaps the whole profile (section 4.11). **Install** is section 4.4 |
| **Splice** + **Install** | LocalNetND only: which installed Splice bundle the network runs. **Install** is section 4.5 |
| **PQS** | `OFF` or `ON`. Whether to run PQS (scribe), which streams the ledger into PostgreSQL for SQL queries. Starts OFF every time and is never saved. What runs is the vendor scribe for the Canton line: one staged under `.pqs` in the home directory, else the one DPM installed, found in DPM's cache whatever the jar is called, else the jar **PQS scribe.jar** on the Settings tab names; a stand-in on the 2.10 line; an in-process mock otherwise. When it is the mock, the main log says why: "The Sandbox is not able to start PQS: ... The PQS mock is running instead." With auth on, scribe reads as the user the form names, or on Canton 3.x as `raposza-pqs`, which the start creates with `readAsAnyParty` when the participant lacks it; nothing has to be filled in. On a first start with no parties, scribe waits with "No parties found matching *" until a party exists; that is a wait, not a hang |
| **First port** | The lowest port of the stack; every other port follows it in a fixed order. In the 30xxx range |
| **PostgreSQL port** | Where the embedded PostgreSQL listens. Canton's databases and PQS's are all inside it |
| **Ready timeout s** | How long each component may take to report ready, in whole seconds. About 45 s for a participant on a fast workstation, nearer three minutes on a modest machine, longer on a first 2.x start. Raise it before concluding a start is broken |
| **Auth** | What the participant checks on the Ledger API: `none (wildcard)` checks nothing; `unsafe-jwt-hmac-256` a shared secret, test only; `jwt-rs-256-crt`, `jwt-es-256-crt`, `jwt-es-512-crt` a public key from an X.509 certificate; `jwt-jwks` a key set fetched from a URL — the default, against the window's own Raposza OIDC |
| **Token shape** | `AUDIENCE` (the `aud` claim names the participant; the default) or `SCOPE` (the `scope` claim names a purpose, default `daml_ledger_api`) |
| **Audience** / **Scope** | The value the token must carry. Empty takes the vendor default: `https://daml.com/jwt/aud/participant/<node>` for audience, `daml_ledger_api` for scope. A mismatch does not fail the start; it fails the first call with PERMISSION_DENIED or INVALID_TOKEN |
| **Secret** | `unsafe-jwt-hmac-256` only: the plaintext HS256 secret, with a minimum length. Anyone holding it can mint a token for any user |
| **Certificate** + **...** | The certificate modes only: the X.509 file, PEM or DER, holding the public key. Read once at start. A self-signed pair is enough: `openssl req -nodes -new -x509 -keyout s.key -out s.crt` |
| **JWKS URI** | `jwt-jwks` only: where the participant fetches the key set. Leave it on the built-in provider's `/oauth2/jwks` unless reproducing an external provider. An unreachable URL fails the start |

Rows that do not apply to the selected mode are hidden.

**The buttons.** **Start**, **Stop**, **Reset** and **Save snapshot**. Section 4.6 and 4.8.

**The lamps.** Single participant: **PostgreSQL**, **Participant**, **JSON API**, **PQS**, **OIDC**, and **Test fixture** while a fixture runs. LocalNetND: **PostgreSQL**, **Splice**, **Web**, **PQS**, **OIDC**. Clicking **Participant** shows the exact command line the participant was started with, with **Copy**; clicking **PQS** shows scribe's.

**The status table.** While a stack runs: every port, every JDBC URL, user and password, the participant id and the path of the status file. **Clicking a row copies its value** to the clipboard.

**The footer.** The last thing that happened, and on the right the stack's state.

### 4.4 Getting Canton — single participant

* On a machine with no DPM the **SDK** box is empty. **Download DPM** on the machine check (section 4.1), or **Install** beside the box and **Yes** to **Install DPM?**, downloads DPM, Digital Asset's package manager, about 1.2 GB, installs it with the Daml SDK it comes with, and selects that SDK. The log says **Installed DPM with DAML SDK**, followed by the version. A failed download offers the file instead.
* **Downloads blocked:** fetch `dpm-<version>-linux-amd64.tar.gz` or `dpm-<version>-windows-amd64.zip` from `https://get.digitalasset.com/install/dpm-sdk/` on another machine and use **Install DPM from file...** on the machine check. The archive installs itself with no network. The chooser opens in the home directory.
* With DPM present, **Install** opens **Install Daml SDK**: the published SDKs, newest first, those already installed marked, with **Install** per row and **Close**.
* **DPM already installed somewhere else:** set **DPM directory** on the Settings tab (section 4.10). Installs from the window still land in the default location.
* The box is read when the window opens. After installing outside the window, close and reopen it.

### 4.5 Getting Splice — LocalNetND

* LocalNetND needs DPM too. Without it a banner at the top says "No Canton found. Raposza can install the Digital Asset Package Manager and the newest Canton it publishes." with **Install**.
* **Install** beside the **Splice** box opens **Install Splice**: every published Splice version from 0.8 on, newest first, with its size (around 800 MB down, 1.5 GB on disk) and what is already installed marked. 0.8 is the lowest LocalNetND runs. With downloads blocked the dialog says why the list could not be read. "Loading the Splice versions. Give it a moment." while it reads them. A row's **Install** downloads that version into the Splice directory; the dialog closes when it is installed.
* **A Splice version already downloaded as a tar.gz file:** **Install from file...** in the same dialog. The file must keep the vendor's name, `<version>_splice-node.tar.gz`; a file named otherwise is refused, because the version is read from the name.
* **Splice bundles already unpacked somewhere else:** set **Splice directory** on the Settings tab: `<directory>/<version>/splice-node`.
* **The Canton a Splice version runs on** is read from the bundle: the newest installed patch of the Canton line the release names. Splice 0.9.4 names Canton 3.6.4 and runs on 3.6.3 when that is the newest 3.6 installed. The console and the debug log say which one, as `Splice 0.9.4 on Canton 3.6.3 open-source - released on 3.6.4; the newest 3.6 installed.` When no Canton of the line is installed, Start offers one: **Download** installs the newest Daml SDK of that line through DPM, and DPM first if it is missing; **Install from file...** takes a DPM archive of that line.

### 4.6 Starting and stopping

* **Start** starts the stack. The lamps turn green as the parts come up; the log says `Sandbox started in`, followed by the number of seconds. **Stop** asks **Stop the sandbox?**; during a start it asks **Cancel the start and take down what is up?**
* The form is disabled while a stack is up.
* Closing the window stops the stack first: **The sandbox is running. Stop it and close?**, or during a start **A start is in progress. Cancel it and close?**
* The window publishes what it runs on `127.0.0.1:32001` for the Workbench and for scripts (section 8.1). It answers from the moment the window opens, saying `STOPPED` until a stack runs.
* **Every start begins from scratch.** The first start of a version founds the ledger once and takes a **founding snapshot**; later starts restore it and are faster. The **Founding snapshot** card on the Settings tab shows its state, and **Reset founding** discards it.

### 4.7 Test data — the fixtures

* **Single participant: Aviation. LocalNetND: Pharma.** Section 10 describes both.
* **Create Aviation test data** or **Create Pharma test data** on the Settings tab builds the fixture and starts the stack with it. The Aviation start stops and starts a few times; that is expected. Wait for **Aviation data created, system running**, or `Pharma data created, system running`.
* A start can also offer it: **Install Aviation test fixture?** (or **Use the Aviation test fixture built earlier?**), and the same for Pharma, with **Yes**, **Not now** and **Never**. **Never** switches the offer off; **Offer to create it on a start** on the Settings tab switches it back on.
* The data is removed when the window is closed, unless a snapshot was taken.

### 4.8 Snapshots and Reset

* **Save snapshot** stops the stack and saves its state under a name. The window may not respond until the copy is done; wait for it.
* **Snapshots** lists the saved states, with `(none - empty ledger)` at the top. Select one and press **Start** to start from it. Right-click one for **Delete snapshot**.
* **Reset** asks "Reset stops the sandbox and starts it again. Everything the running ledger holds is discarded.", then starts again from the snapshot selected in **Snapshots**, or from an empty ledger when `(none - empty ledger)` is selected.
* Snapshots belong to the version that took them. There is no cross-version restore.

### 4.9 The other tabs

**Debug** — one log per process: single participant **PostGres**, **Participant**, **PQS**, **OIDC**; LocalNetND **PostGres**, **Canton**, **Splice**, **Web**, **PQS**, **OIDC**. Every log has **Follow**, **Wrap**, **Copy** and **Clear**. Ask for these when something fails.

**JSON API** — single participant only. The HTTP JSON Ledger API's port, **Check** (a TCP connect: is anything listening) and its log. On 3.x it is the participant's own API and the log stays empty; on 2.x it is a separate process and the log is its own.

**DARs** — single participant: two tabs.

* **Store**: the `*.dar` files in `<run directory>/dars`, with file, package, version and the first twelve characters of the package id. The ticked ones are uploaded at the next **Start**. **All**, **None**, **Import...** (copies a DAR of the person's own into the store; it refuses a DAR whose manifest cannot be read, a package id already in the store under another file name, and never overwrites a different file of the same name), **Refresh**.
* **Participant**: what the running participant holds, in the participant's own columns, plus **Vetted** (`?` when it cannot be read). **Upload DAR**, **Remove DAR** (asks first), **Vet**, **Unvet**, **Refresh**. These act on the running participant over its Admin API.

LocalNetND: one tab per participant, `app-provider`, `app-user` and `sv`, each like **Participant** above.

**Web** — **Endpoints**: the table "Web pages and endpoints", with name, node and URL — each participant's **JSON Ledger API**, the Splice web UIs on LocalNetND (section 8.6) and the RAWAR pages (section 8.5) on both topologies. **Clicking a row copies its URL**; paste it into a browser. **Open** is disabled. **Refresh** re-reads the list. **Log**: one line per request to those pages; **Hide repeats** hides a page that keeps polling.

**OIDC** — two tabs.

* **Provider**, with two tabs. **Service**: whether the provider is up, **Swagger** (copies the URL of the provider's API page, `http://127.0.0.1:32002/swagger-ui.html` by default, and opens it in a browser: every endpoint, with runnable examples), **External OIDC url** with **Apply** (section 8.4), a **User** box filled from the participant's own users with **Refresh users**, **Mint JWT** (a token for that user, shown below it and copied to the clipboard; it lasts 24 h, or less where an older Canton caps it), and **Audience** with **Copy**. Against an external provider **Swagger** reads **Open provider**. **Access log** shows every request to the provider, with the origin and the CORS verdict of a cross-origin one. Mint JWT and Refresh users are off against an external provider, because they use Raposza OIDC's own `/mint.txt`.
* **Users**: everyone who can sign in at the provider, with **Username**, **Node** and **Password**. The window registers them: on LocalNetND each role's users at every start; on a single participant every ledger user, after the fixture and every 3 s. The password is read from the provider's `users.json` — `123456` for the users the window registers — or `not readable`. A user the window did not register, such as one added through the provider's own pages, shows node `-`. It re-reads every 3 s; there is no Refresh button.

**Settings** — section 4.10.

### 4.10 The Settings tab

Global settings, not per version. Edits save themselves 600 ms after the last keystroke; a field that does not parse is not saved. While a stack runs the fields and **Restore defaults** are locked: "Locked while the stack is running. Stop it to change this." **Restore defaults** is the one destructive control, and **Undo** beside it puts back what was there. Stored in `~/.raposza/settings.properties` (`%APPDATA%\raposza\settings.properties` on Windows).

| card | setting | key | default |
| --- | --- | --- | --- |
| Directory | Raposza directory | `dir.home` | `~/.raposza`, `%APPDATA%\raposza` |
| Ports and defaults | Discovery port | `port.discovery` | 32001 |
| | RAWAR port | `port.rawar` | 31100 |
| | JWT mint port | `port.mint` | 32002 |
| | PostgreSQL port | `default.port.postgres` | 32101 |
| | First stack port | `default.port.first` | 30010 |
| | First web UI port | `default.port.ui.first` | 31000 |
| | Ready timeout s | `default.timeout.ready.seconds` | 300 |
| Test data | Aviation: Offer to create it on a start, Create Aviation test data, Aviation documentation | `fixture.aviation.offer` | on |
| | Pharma: Offer to create it on a start, Create Pharma test data, Pharma documentation | `fixture.pharma.offer` | on |
| Founding snapshot | its state, and Reset founding | | |
| Installations | DAML Assistant directory | `dir.daml` | the root of `daml` on `PATH`, else `~/.daml` or `%APPDATA%\daml` |
| | DPM directory | `dir.dpm` | `DPM_HOME`, then the root of `dpm` on `PATH`, else `~/.dpm` or `%APPDATA%\dpm` |
| | Splice directory | `dir.splice` | `~/.splice` or `%USERPROFILE%\.splice` |
| | PQS scribe.jar | `file.pqs` | blank: the DPM cache only |

What to know about them:

* **Raposza directory** holds the provider's keys, the fixtures, the package cache, the saved ledger hosts and the audit log. Moving it does not move what is there, and the provider makes new keys at the new place, so every token issued before stops verifying.
* The three **ports** in 32xxx (discovery, mint, PostgreSQL) are administrative; nodes are 30xxx and web UIs 31xxx. Everything stays below 32768, because Linux hands out ephemeral ports from there. A port already held gives "Failed to bind to address" from Canton; `lsof -n -i` names the holder.
* **JWT mint port**: changing it changes the JWKS URL a participant is configured with, so it takes effect on the next start.
* **PostgreSQL port**, **First stack port** and **Ready timeout s** are the defaults a version gets before it has a saved profile. LocalNetND has no profile, so for it they ARE the values. Moving LocalNetND's first port moves its founding snapshot, so the next start founds again.
* **First web UI port**: LocalNetND only. sv, app-provider and app-user are on it and the next two ports ten apart.
* **DPM directory** takes DPM's root (holding `bin` and `cache`), or the directory the `dpm` launcher sits in. A root set here wins over `DPM_HOME` and `PATH`. The installers do not follow the DAML Assistant and DPM directories; a Splice install does land in the Splice directory. LocalNetND's run directory stays `~/.splice/native-localnet` whatever is set.
* **PQS scribe.jar** takes a scribe jar by its full path, for a Canton line DPM has no scribe for; its file name does not matter. The jar is asked for its version and used only for the Canton line it belongs to. Started on another line, the main log says "PQS mismatched: ..." and the PQS mock runs.
* **The external provider URL** is on the OIDC tab (section 8.4) and stored as `oidc.url`.

### 4.11 The SDK box decides everything below it

The form's values are remembered per version and edition, in `~/.raposza/profiles/` (`%APPDATA%\raposza\profiles` on Windows): written before a selection change, at Start, and at close. The run directory is `~/.raposza/sandbox/<version>-<edition>`, and the DAR store is its `dars` directory. Snapshots belong to the selected version. A control the selected version is known not to have is hidden; an unmeasured version keeps every control.

---

## 5. Sandbox — the command line

Without `--cli` the window opens, and every other argument pre-fills its form. With `--cli` the stack runs in the terminal, touches no window code, and Ctrl-C stops it. `--help` and `--list` print and stop either way.

```
java -jar sandbox-0.6.3-app.jar --help
java -jar sandbox-0.6.3-app.jar --list
java -jar sandbox-0.6.3-app.jar --cli
java -jar sandbox-0.6.3-app.jar --cli --canton 3.5.14
java -jar sandbox-0.6.3-app.jar --cli --auth JWKS --discovery 32001
java -jar sandbox-0.6.3-app.jar --cli --fixture --auth JWKS --discovery 32001
```

On Linux in a clone, `./run-sandbox.sh` takes the same arguments.

The complete argument list, as `--help` prints it:

| argument | meaning |
| --- | --- |
| `--canton <version>` | exact version, e.g. 3.5.11 |
| `--line <line>` | newest installed of a minor line (default 3.5). Not together with `--canton` |
| `--edition <edition>` | open-source, enterprise, unknown or any (default any) |
| `--list` | print what is installed and stop |
| `--port-offset <n>` | move Canton's 6864-6869 block by n (default 0) |
| `--pg-port <n>` | the embedded PostgreSQL port (default 32101) |
| `--work-dir <path>` | overlays, logs, status file (default `~/.raposza/sandbox`) |
| `--data-dir <path>` | a PostgreSQL cluster that outlives the process |
| `--db-prefix <name>` | prefix for the node databases (default none) |
| `--dars <dir>` | upload every `*.dar` in a directory at start |
| `--dar <file>` | upload one `*.dar`; repeatable, and combines with `--dars` |
| `--party <hint>` | allocate a party (needs `--user`) |
| `--user <id>` | create a ledger user (needs `--party`) |
| `--ping` | ping the participant from itself once it is up |
| `--launcher <name>` | subcommand or daemon (default subcommand) |
| `--dev` | the development protocol version |
| `--static-time` | time advances only through the time service |
| `--heap-mb <n>` | Canton's JVM heap |
| `--pqs <mode>` | off or on (default off) |
| `--timeout <s>` | how long to wait for the stack (default 300) |
| `--cli` | start in the terminal |
| `--fixture` | build the Aviation fixture for the selected Canton, upload it at start and run its setup script; needs a toolchain that builds for that version |
| `--help` | the usage text |
| `--auth <mode>` | terminal only: authenticate the Ledger API, e.g. `JWKS`; starts the local OpenID Provider first |
| `--token-shape <shape>` | terminal only: `AUDIENCE` or `SCOPE`, with `--auth` |
| `--discovery <port>` | terminal only: publish the discovery document on `127.0.0.1` |

Things to know:

* **Without `--auth` the Ledger API is UNAUTHENTICATED.**
* **2.x and 3.x are different stacks.** 3.x is a participant, a sequencer and a mediator; 2.x is a participant and a domain. `--launcher`, `--dev`, `--static-time`, `--party`, `--user` and `--ping` belong to the 3.x launchers and are reported as ignored on 2.x.
* **A `--data-dir` stack cannot be restarted on the default launcher.** `--launcher daemon` can: it starts again against the kept cluster as the same participant, with the same party and the same packages. PQS has never been run on it.
* **The status file.** While the stack runs, `sandbox.properties` in the work directory carries every port, the JDBC urls and the participant id. It is removed when the stack stops.

---

## 6. Workbench — the window

### 6.1 Connecting

The window opens on the connection dialog, **Connect to**:

* **a local Sandbox** — **Discovery port**, 32001 by default: where the Sandbox's discovery document is. It carries the host, the ports, the version and where tokens come from, so nothing else is needed. Press **OK**. The Workbench opens one tab per participant the document lists: one for a single participant, three on LocalNetND (`app-provider`, `app-user`, `sv`), each showing what that participant sees.
* **a standalone ledger** — **Name**, **Host**, **Ledger API port**, **JSON API port**, **Generation** (`Ledger API v2  (Canton 3.x)`, the default, or `Ledger API v1  (Canton 2.x)`), **Audience**, **Scope**, **Credential** (section 6.5), **TLS**, and **Allow writes** ("lets CaQL and the examples write to this ledger; off, the tab only reads"). Allow writes is off by default.

**OK** or **Cancel**. A tab's own **Connect...** button opens the dialog again; choosing a Sandbox there rebuilds every tab from its document.

From the command line:

```
java -jar workbench-0.6.3-app.jar --discovery http://127.0.0.1:32001/
java -jar workbench-0.6.3-app.jar <path-to-ledger_hosts>
```

The first opens directly on a Sandbox. The second pre-fills the standalone form from a participant catalogue; without an argument the catalogue is `~/.raposza/ledger_hosts`, and a missing one is not an error. The format is in `ledger_hosts.example` at the repository root:

```
# name...  protocol  host  ledger-port  json-port  scope  audience  [mode  colour]
Local Sandbox      http   localhost      6865  7575  -   -   rw  #2e7d32
```

`-` means absent, and the dashes are not optional. `mode` is `ro` or `rw`, and a missing mode reads as read-only. The colour shows on the tab's status bar, so a shared participant can be told apart at a glance.

### 6.2 A participant tab

Across the top: **Connect...**, **Session** with the **work as** box, and **Short ids**. **Back** sits in the bar above the navigator and the detail.

* **work as** — the ledger user the tab acts as. Choosing another reconnects as that user. `participant_admin` sees users and parties but not contracts. On the Aviation fixture `superuser` sees every template and contract; any other user shows exactly that user's view.
* **Short ids** — on by default: ids shown as head[...]tail, a party as hint::[...]tail. CaQL accepts the short form (section 9).
* **Back** — back to the pane the last link was followed from.
* **The status bar** — in the profile's colour: its name, `connected on` and the host and port, or `connected, READ CAPPED at <n>, on` when the contract read stopped at its cap. Hovering over it shows the credential in use.

**The navigator**, on the left: **Users**, **Parties**, **Templates seen** and **Contracts**. It is read once on connect and again after a CaQL run that committed something; the contract read is capped, and the status bar says when the cap was hit.

**Detail** — the selected object: a user, a party, a template with its fields and choices, a contract with its payload and its **activity** (its create, each exercise on it and what those produced, as seen by the working user), or a transaction as a tree with **Expand all**, **Collapse** and **Show JSON**. Every id in it is a link: click to follow it; right-click it for **Copy**, which copies the full id.

**CaQL** — section 6.3.

### 6.3 The CaQL tab

* The editor, coloured by the language: comments blue, keywords bold dark green, identifiers in the dark blue of a link. On a ledger holding the Aviation or the Pharma fixture it opens with a working example script — reads, then writes chosen to succeed.
* **Ctrl+Enter** runs the statement the caret is in; **Run** runs the whole editor, top to bottom, as the **work as** user, and stops at the first failure. **Ctrl+Z** and **Ctrl+Y** undo and redo.
* **Results** shows progress while a run is in flight, then the transcript of that run. A failure with a line number puts the caret on that line.
* **Parameters** holds what the runs have bound — name, type, value — and keeps it across runs, so a party allocated by one Ctrl+Enter can be used by the next. Right-click an entry to drop it.
* **Skills** shows the complete CaQL reference, the same text as section 9, ready to copy. It is compiled into the jar, so it always matches the version running.
* To create and list parties and users, work as `participant_admin`. For everything else, work as `superuser`, or as another user to test its rights.
* A write needs a connection with writes allowed: always on a local Sandbox, **Allow writes** or `rw` on a standalone ledger.
* Every statement run is appended to the audit log, `~/.raposza/audit.jsonl`, which never holds a payload.

### 6.4 What the Workbench does not have

No search box, no Act form for a choice (choices are exercised with CaQL), no saving of scripts, and no completion in the editor.

### 6.5 Credentials against a standalone ledger

The **Credential** row of the connection dialog is a button reading **auth: none** until a token is set. It opens **Token for this participant**: paste a JWT, optionally tick **save to this profile's settings file**, then **Apply**. **Clear pasted token** drops the session's token; **Delete stored token** removes it from the settings file after asking. **Cancel** closes.

Or a settings file `~/.raposza/profiles/<profile>.properties`, named after a lower-case slug of the profile's display name:

```
auth           = none | token | jwks
token          = the bearer token                 auth=token
jwks.file      = /path/to/private-jwks.json       auth=jwks
jwks.kid       = key id, else the first private key
subject        = the sub claim, normally the ledger user id
scope          = overrides the catalogue's scope
audience       = overrides the catalogue's audience
act.as         = comma separated, custom-claim tokens only
read.as        = comma separated, custom-claim tokens only
admin          = true | false, custom-claim tokens only
application.id = the applicationId claim, custom-claim tokens only
ledger.id      = the ledgerId claim, custom-claim tokens only
participant.id = the participantId claim, custom-claim tokens only
ttl.seconds    = lifetime of a MINTED token, default 1000 years
```

Scope and audience are mutually exclusive; a profile carrying both is refused. The file holds a credential: keep it out of every repository. Against a local Sandbox none of this is needed, because the discovery document hands the Workbench a token URL.

---

## 7. Workbench — CaQL without a window

The Workbench jar runs a CaQL script headless:

```
java -jar workbench-0.6.3-app.jar --script FILE --transcript FILE [--profile NAME] [--validate] [--catalogue FILE]
```

* The participant comes from the catalogue (section 6.1): `--catalogue`, else `~/.raposza/ledger_hosts`. `--profile` names the line, and is required when the catalogue has more than one. The line must say `rw` for a script that writes.
* Authentication comes from that profile's settings file (section 6.5).
* **For a script that writes, run it against a stack without authentication** - `--cli` without `--auth`. The headless runner submits under the application id `workbench-caql`, and an authenticated Canton 3.x participant refuses that (`platform/caql-core/.../RunConfig.java`). Reads are unaffected. In the window, the CaQL tab submits as the user picked in **work as** and has no such limit.
* `--transcript` is required and has no default: the transcript carries the payloads the script wrote, so the tool does not choose where it lands. It is written even when the run fails.
* `--validate` resolves and checks the script without submitting.
* Exit code: `0` every statement reached a state the run could continue past; `1` the run stopped (a rejection, a local failure, an unobserved outcome); `2` nothing ran (bad arguments, missing script, unreadable catalogue). The script is parsed before anything connects.

To find the ports for a catalogue line against a running Sandbox, read `port.ledger-api` and `port.json-api` from the discovery document (section 8.1).

---

## 8. The APIs, the identity provider and the web pages

Everything in this section is served on `127.0.0.1` while the Sandbox window (or a `--cli --discovery` stack) is up. Ports are the defaults; the Settings tab moves them.

### 8.1 The discovery document — `GET http://127.0.0.1:32001/`

One JSON document describing the Sandbox. Read-only: `GET /` and `GET /dars`, nothing else. It answers while the window is open, whether a stack runs or not.

```
curl http://127.0.0.1:32001/
```

PowerShell: `Invoke-RestMethod http://127.0.0.1:32001/`

| key | content |
| --- | --- |
| `product` | `raposza-sandbox` |
| `schema` | `2` |
| `state` | `STOPPED`, `STARTING`, `RUNNING`, `STOPPING`, `FAILED`, or `UNKNOWN` |
| `topology` | `sandbox` (single participant) or `localnet` (LocalNetND) |
| `discovery.port` | the port this answered on |
| `oidc` | `running`, `mode` (`embedded` when the window runs the provider, `external` when it was pointed at one), `discovery`, `issuer`, `jwks`, `token_endpoint`, `grant_type` |
| `canton` | `version`, `edition` — while running only |
| `run` | `dir`, `log` — while running only |
| `nodes` | one entry per node — below. Absent when nothing runs |
| `dars` | every DAR: `name`, `version`, `packageId`, `origin` (`user` or `splice`), `status` (`selected` for the next start, or `uploaded`), `file` |
| `snapshot` | the snapshot the running stack was restored from, or null |

Each entry in `nodes` carries `name`, `role`, and `auth` — `mode`, `type` (Canton's name for it), and for a token mode `shape`, `jwks` (the key set the participant was configured with), `token` (a URL that returns a token for this node) and `oidc` — followed by the node's report, whose keys exist only where they apply:

| key | content |
| --- | --- |
| `host` | the host every port is on, `127.0.0.1` |
| `port.discovery` | the discovery port |
| `port.ledger-api`, `port.admin-api`, `port.json-api` | the participant's gRPC Ledger API, Admin API and HTTP JSON Ledger API |
| `port.sequencer-public`, `port.sequencer-admin`, `port.mediator-admin` | 3.x |
| `port.domain-public`, `port.domain-admin` | 2.x |
| `port.postgres` | the embedded PostgreSQL |
| `db.names` | the databases, comma separated — `participant,sequencer,sequencer_driver,mediator` on 3.x, `participant,domain` on 2.x |
| `url.jdbc.<name>`, `user.jdbc.<name>`, `password.jdbc.<name>` | for each name in `db.names` |
| `participant.id`, `party.id`, `user.id` | the participant, and the party and user `--party`/`--user` created |
| `pqs`, `url.jdbc.pqs`, `user.jdbc.pqs`, `password.jdbc.pqs` | PQS, when it runs |
| `dir.work`, `dir.data`, `pid` | the work and data directories and the process id |
| `canton.version`, `canton.edition` | the Canton the node runs |

Read ports from here rather than assuming them: they depend on the settings and the topology.

### 8.2 `GET http://127.0.0.1:32001/dars`

Every template, choice, field and type in the DARs the document lists, as one JSON document written for someone about to write CaQL against them. It decodes the files, so it takes a moment. Top level: `schema`, `topology`, `reader`, `typeSpelling`, `controllers` (null), `controllersNote`, `packages` and `refused`. Each package has its id, its role and whether it is `described`, the `files` that carry it, its `dependencies`, and `modules` with `templates` (each with `name`, `ref` — the exact name to write in CaQL — `fields`, `implements`, `choices` with `name`, `consuming`, `argument`, `returns`), `interfaces` (`requires`, `choices`), `records`, `variants` and `enums`. Types are spelt as a Daml developer writes them: `Party`, `ContractId Main:Aircraft`, `[Party]`, `Optional (...)`. The main package of each DAR is described in full; the others only as far as a main package references them. Choice controllers are not in the decode, and the document says so once. A DAR that cannot be read lands in `refused` with its reason.

### 8.3 Getting a token

Each node's `auth.token` in the discovery document is a URL that returns a token for that participant with one GET. Its `sub` parameter is the ledger user the token speaks for; replace that one parameter to get a token for another user (for example `superuser` on the Aviation fixture).

```
curl "<auth.token from the discovery document>"
```

The window's **OIDC → Provider → Mint JWT** does the same for the user in its **User** box. Send the token as `Authorization: Bearer <token>`.

### 8.4 Raposza OIDC — the identity provider

The window starts Raposza OIDC itself at `http://127.0.0.1:32002` (the **JWT mint port**), bound to `127.0.0.1`, with that address as its issuer and the admin credential `admin` / `123456`, and stops it when the window closes. `--cli --auth JWKS` starts it the same way. It keeps its keys, `users.json` and `clients.json` in `~/.raposza/jwtmint/keys` (under the Raposza directory). It is a mock: it publishes its own private keys on request and mints a token for any subject with any claims.

| endpoint | what it does |
| --- | --- |
| `GET /.well-known/openid-configuration`, `GET /.well-known/oauth-authorization-server` | the provider configuration |
| `GET /` | a plain-text index of the endpoints |
| `GET /oauth2/jwks`, `GET /jwks.json`, `GET /.well-known/jwks.json` | the public key set — what a participant is pointed at |
| `GET /oauth2/jwks-private`, `GET /jwks-private.json` | the full key set, private members included |
| `GET /keys`, `GET /keys.txt` | algorithm, kid and key size per key |
| `GET`/`POST /oauth2/authorize` | the login page, then a code |
| `POST /oauth2/token` (also `/oauth/token`, `/token`) | `authorization_code`, `refresh_token`, `client_credentials`; form-encoded only |
| `GET`/`POST /oauth2/userinfo` | the signed-in user and the claims the scope releases |
| `GET`/`POST /oauth2/logout` | back to `post_logout_redirect_uri`, when registered for the client |
| `POST /mint`, `GET /mint`, `GET /mint.txt` | a token from an explicit request; `/mint.txt` returns the bare token |
| `GET /admin/status`, `POST`/`GET /admin/reload` | where the keys are and when they loaded; re-read them from disk |
| `GET /ui/` | the web UI, below |
| `/api/ui/*` | what the UI calls; a script may too |
| `GET /swagger-ui.html` | every endpoint, with runnable examples |

**The mint's parameters** (`GET /mint`, `GET /mint.txt`, by query string): `sub`; `alg` — `NONE`, `HS256`, `HS384`, `HS512`, `RS256`, `RS384`, `RS512`, `PS256`, `PS384`, `PS512`, `ES256`, `ES384`, `ES512`; `kid`; `iss` (blank omits it); `aud` (comma separated); `scope`; `ttlSeconds` (0 or less omits `exp`); `shape` — `AUDIENCE`, `SCOPE`, `CUSTOM` or `RAW`; for `CUSTOM` also `actAs`, `readAs` (comma separated), `admin`, `applicationId`, `ledgerId`; `participantId` (for `CUSTOM`, and the participant `AUDIENCE` builds its audience from); and `secret` for an HS algorithm. `POST /mint` takes the same as JSON, plus `claims`, merged last.

```
curl "http://127.0.0.1:32002/mint.txt?sub=superuser&aud=https://daml.com/jwt/aud/participant/<participant>&ttlSeconds=3600"
```

The document's `auth.token` URL already carries the audience or scope, the lifetime and the algorithm the participant expects; use it rather than building one by hand.

**The web UI, `http://127.0.0.1:32002/ui/`** — six pages:

| page | what it does |
| --- | --- |
| Overview | the issuer, where the keys, users and clients are, and every URL a consumer is given |
| Keys | every key with its algorithm, type and thumbprint; rotate, add, delete, reload |
| Users | who can sign in, and their standard claims; add, change, delete |
| Clients | registered `client_id`s and their redirect URIs; register, replace, remove. No secret is ever returned |
| Mint | a form over `/mint`, with the token decoded beside it |
| Inspect | paste a token: header, claims, expiry, whether this key set signed it — and, with a JWKS pasted, whether that one does |

Key, user and client management needs the admin credential, `admin` / `123456` as the window starts it. With PQS on, when the provider already has a client registered, the window registers scribe as a confidential client - the PQS user as its `client_id`, a secret made for that start - and says so on **OIDC → Provider**; a provider with no clients checks none, and nothing is registered. **Inspect** is the page for an authentication failure: an issuer, an audience or a key that does not match is refused by a participant with no detail, and Inspect shows which.

**An external provider.** A base URL in **OIDC → Provider → External OIDC url**, with **Apply**, starts nothing: the window reads that provider's discovery document, configures the participant with the `jwks_uri` it publishes, and reports `mode: external`. It need not be Raposza OIDC. **Mint JWT** and **Refresh users** are off against it, because `/mint.txt` is Raposza OIDC's own. Below Canton 3.5.6 the participant caps token lifetimes itself, so a provider that cannot issue short tokens will have every token refused there. The change takes effect on the next start.

Raposza OIDC's own repository, <https://github.com/raposza/OIDC>, documents running it standalone, behind a reverse proxy, and its settings `raposza.oidc.*`.

### 8.5 RAWAR — local web pages on `http://127.0.0.1:31100`

The Sandbox serves web pages a developer writes against it, from `rawars` under the Raposza directory (`~/.raposza/rawars`, `%APPDATA%\raposza\rawars` on Windows). One directory is one site; its name is lower case, digits and `-`. It is mounted at the `mount` its `rawar.json` names, or at `/` without one.

```
{ "rawar": 1, "name": "my-site", "version": "1.0", "mount": "/my-site/" }
```

`rawar.json` may also carry `treeSha256`, `builtWith` (`sdk`, `canton`, `splice`, `host`, `at`), `dars` (`name`, `version`, `packageId`) and `commit`; these record what the site was built and tested against.

Under every mount:

* `_env.json` — how to sign in and where the ledger is: `env` (1), `host` (`sandbox` or `sandbox-localnetnd`), `mount`, `running`, `ledgerBase` (`./_ledger/`), `auth` (Canton's mode name, or `none`), `issuer`, `clientId` (`rawar`), `redirectUri`, and `audience` or `scope`. No secret.
* `_ledger/...` — forwarded to the stack's JSON Ledger API with the path below `_ledger` unchanged and the page's own bearer token, on the page's own origin, so the page needs no address and meets no CORS. It answers 503 when no stack runs. On LocalNetND it reaches the `app-provider` participant.
* every other path — a file from the site's directory. Pages are read from disk on every request, so an edit shows on the next reload.

A path under no mount is a 404 that names the mounts there are. The **Web** tab lists the sites and logs every request.

### 8.6 LocalNetND — the Splice web pages

LocalNetND serves the Splice bundle's own web UIs natively. Each role has its own port, the **First web UI port** and the next two ten apart:

| page | URL |
| --- | --- |
| Super Validator Operations | `http://sv.localhost:31000` |
| Amulet Scan — read-only, no sign-in | `http://scan.localhost:31000` |
| Amulet Wallet - sv | `http://sv.wallet.localhost:31000` |
| Amulet Wallet - app-provider | `http://app-provider.wallet.localhost:31010` |
| Amulet Name Service - app-provider | `http://app-provider.ans.localhost:31010` |
| Amulet Wallet - app-user | `http://app-user.wallet.localhost:31020` |
| Amulet Name Service - app-user | `http://app-user.ans.localhost:31020` |

Names under `.localhost` resolve to `127.0.0.1` in the browser itself; nothing has to be added to a hosts file. The **Web** tab lists these with their node; **OIDC → Users** lists who signs in to each, with the node and the password `123456`. The window registers every page's origin at its own provider at each start, so sign-in works with nothing done by hand. Each role's port also answers `canton.localhost` as a forward to that participant's JSON Ledger API — on the sv port only `/v2` and `/docs/openapi`, on the app-provider and app-user ports everything — and the app-provider and app-user ports answer `json-ledger-api.localhost`, forwarding everything to it. The vendor's own default, which the bundle ships, is HS256 over the secret `unsafe`; the window starts LocalNetND on JWKS against its provider.

### 8.7 The Ledger API and the Admin API

Each participant's gRPC Ledger API is at `host`:`port.ledger-api`, its Admin API at `port.admin-api`; on Canton 3.x the participant also serves the HTTP JSON Ledger API at `port.json-api`. These are Canton's own APIs, documented by Digital Asset. Send the token from section 8.3 as `Authorization: Bearer <token>` when the stack runs with authentication.

```
curl -H "Authorization: Bearer <token>" http://127.0.0.1:<port.json-api>/v2/authenticated-user
```

The Admin API has no authentication configured by the Sandbox.

### 8.8 The databases and PQS

The embedded PostgreSQL runs trust authentication; every JDBC URL, user and password is in the discovery document per node, and any password is accepted. With **PQS** on, the PQS database is `url.jdbc.pqs`, which scribe fills with the ledger for SQL queries.

### 8.9 The status file

A `--cli` stack writes `sandbox.properties` in its work directory while it runs, with the same keys as a node's report in section 8.1, and removes it when it stops.

---

## 9. CaQL — complete reference

This is the text of the Workbench's **Skills** page in 0.6.3, unchanged. For any other version, the Skills page of the running Workbench is the authority.

```
CaQL - COMPLETE REFERENCE
=========================

CaQL is a small statement language for working a Canton ledger:
allocate parties, create users, create contracts, exercise
choices, read them back. It runs against the participant this
window is connected to, as the user named in `work as` above,
and it works on both the 2.x and 3.x Ledger API generations.

It is NOT Daml and it is NOT a Daml Script. There is no SDK, no
DAR and no compile step: a statement is parsed, resolved against
the packages the participant carries, and sent.


RUNNING
-------

Run           runs the whole editor, top to bottom.
Ctrl-Enter    runs ONLY the statement the caret is in, however
              many lines it is laid out over.

A run stops at the first statement that fails. Statements after
it are not sent. The transcript below the editor names each
statement by its line number, so the line you ran and the line
reported are the same line.

While a run is in flight, Results shows its PROGRESS, one line
per statement: where the run is, the statement's line in the
editor, how it ended, how long it took and its first line.

  [ 3/122]  line 26    running                  treasury = ...
  [ 3/122]  line 26    committed         0.21 s treasury = ...

The running line is replaced by the outcome when the statement
ends; the full transcript replaces the progress when the run
is over.

Identifiers in the editor may be written SHORT, as the panes
show them - `PlantOperator-d4d95138::` followed by the cut
marker and the last six characters of the fingerprint. They are
put back to their full form against what this window has read
before anything is sent. A short id that matches nothing, or
matches more than one thing, stops the run and names itself.


THE STATEMENTS
--------------

Reads - allowed on a read-only connection:

  AS <party> QUERY <template> [WHERE <clause>];
  AS <party> FETCH <contractId>;
  AS <party> FETCH <template> [WHERE <clause>] SINGLE;
  LIST PARTIES;
  LIST USERS;
  LIST PACKAGES;
  GET USER "<userId>";
  GET LEDGER END;
  ASSERT <query> COUNT <n>;

A QUERY may be filtered on payload contents:

  AS $plant QUERY Main:Batch WHERE weight > 10.0;
  AS $plant QUERY Main:Batch WHERE holder.name = "acme" AND made >= "2026-01-01";
  AS $plant QUERY Main:Batch WHERE NOT (open = true OR note = null);
  AS $plant QUERY Main:Batch WHERE owner = $plant;
  ASSERT AS $plant QUERY Main:Batch WHERE cases >= 3 COUNT 2;

A comparison is <field> <op> <value>: the field is a name,
dotted into nested records; the operator is one of = < <= > >=
(there is no !=, write NOT field = value); the value is quoted
text, a number, true, false, null or a $binding, never another
field. NOT binds tightest, then AND, then OR; parentheses
regroup. The value is coerced against the field's declared
type, so "2026-01-01" is a date on a Date field and $plant is
checked to hold a party. Int64, Numeric, Text, Date and
Timestamp order; party, contract id, Bool and enum compare
for equality only. A field a contract lacks, or an Optional
holding None, is a non-match rather than a failure; a field
the template does not declare stops the statement.

The filter runs in the Workbench over what the read returned -
neither Ledger API filters by payload - so it never widens what
the parties can see, and the read is capped at 1000 before the
filter sees it. The transcript records countRead before the
filter, count after it, and truncated when the cap was hit.

FETCH ... SINGLE takes the same WHERE, and binds the ONE contract
that survives it - none, more than one, or one out of a read
that hit the 1000 cap are each refused, the last because a
match beyond the cap cannot be ruled out:

  h = AS $treasury FETCH Utility.Registry.Holding.V0.Holding:Holding
        WHERE owner = $treasury AND lock = null AND amount = 60000000.0
        SINGLE;

`= null` asks whether an Optional is None, whatever it holds - an
Optional of a RECORD included, which no other comparison reaches.
`null` takes `=` only, and only on an Optional field.

Writes - refused on a read-only connection:

  AS <party> CREATE <template> WITH <json>;
  AS <party> EXERCISE ON <contractId> <Choice> [VIA <interface>] [WITH <json>];
  AS <party> EXERCISE ON KEY <template> <json> <Choice> [WITH <json>];
  ALLOCATE PARTY "<hint>";
  CREATE USER "<userId>" WITH <json>;
  DELETE USER "<userId>";
  GRANT  canActAs|canReadAs <party> TO   USER "<userId>";
  REVOKE canActAs|canReadAs <party> FROM USER "<userId>";
  PRUNE TO <offset>;

And one wrapper:

  EXPECT <status> <statement>;

Party allocation and user management are administrative calls
rather than ledger submissions, and they count as writes: a
read-only connection is a promise not to change the participant.

<party> may be a LIST, comma separated:

  AS $operator, $provider
     CREATE Utility.Registry.App.V0.Service.Provider:ProviderService
     WITH { "operator": "$operator", "provider": "$provider" };

Every party named acts - actAs on CREATE and EXERCISE - or reads
- readAs on QUERY and FETCH. A contract two parties must sign is
created by naming both.


INTERFACE CHOICES - VIA
-----------------------

A choice a template inherits from an interface it implements is
exercised like its own. Which one is sent:

  * the template's own choice of that name, when it has one;
  * else the one implemented interface that declares it;
  * else - MORE THAN ONE interface declares it - the statement is
    refused, naming every one of them. Say which with VIA:

  AS $treasury, $registrar
     EXERCISE ON $factory TransferFactory_Transfer
     VIA Splice.Api.Token.TransferInstructionV1:TransferFactory
     WITH { ... };

The interface is written Module:Entity, like a template.

VIA comes after the choice, so completion still offers the
choices once the contract is known. The transcript records
interfaceId whenever the target is not the template.


BINDINGS
--------

A statement that produces ONE thing can name it:

  alice = ALLOCATE PARTY "Alice";
  acct  = AS $bank CREATE Main:Account WITH { "owner": "$alice" };
  off   = GET LEDGER END;

Binding forms: FETCH, FETCH ... SINGLE, CREATE, EXERCISE,
EXERCISE ON KEY, ALLOCATE PARTY, GET LEDGER END.

Not binding, by grammar rather than by omission: QUERY, LIST,
CREATE USER, DELETE USER, GRANT, REVOKE, ASSERT, EXPECT, PRUNE.
QUERY answers with a collection; a collection would need
indexing, indexing is an expression, and this language has none.

A binding is single-assignment. Rebinding a name is refused when
the script is parsed, with the whole script in view.

A binding holds a VALUE AND ITS TYPE. That is why it exists: a
contract id carries no template, a decimal carries no scale, and
an empty list carries no element type.

A field of a bound record is reachable with a dot:

  $r.owner.party

Records only. No index, no operator, no arithmetic, no call.

ONE CONVERSION, asked for by name: `.asText` on a PARTY is that
party's id, typed TEXT.

  "subject": "$treasury.asText"

A party is NOT turned into text on its own - a Text field given a
party binding is refused - because an implicit rule would let a
party land in any label without a word. On a record, `asText` is
an ordinary field name; on anything else it is refused, naming
what it reached.

A binding to a contract that a later consuming choice archived
is STALE, and using it fails locally rather than on the wire.
One exception: EXERCISE ON KEY stales nothing, because the
participant picks which contract the key resolves to and that
id never reaches the run.


VALUES
------

`WITH` takes the same JSON the detail pane renders a payload in,
so a payload can be copied out, edited and submitted.

JSON alone does not say enough - "10.0" is a Numeric of some
scale or an Int64, and a string is a party, a text or a contract
id depending on context. So it is coerced against the type the
package registry gives for that template or choice. A field that
does not exist, a missing field and a wrong shape are all caught
BEFORE anything is sent, and the error names the path to the
field.

Encoding, which is the Daml JSON API's own:

  Int64, Numeric     strings, so no precision is lost
  Text, Party        strings
  ContractId         a string
  Timestamp, Date    ISO strings
  Bool               true / false
  Optional           null, or the bare value
  List               an array
  Record             an object of its field names
  Variant            { "tag": ..., "value": ... }
  Enum               its constructor name, as a string
  TextMap            an object
  GenMap             an array of [key, value] pairs
  Unit               an empty object

CREATE USER ... WITH is the one exception: a user is not a Daml
value, so its payload is checked against a fixed schema -
`primaryParty` and `rights`, nothing else - and its errors read
differently from every other WITH.

  CREATE USER "alice-app" WITH {
    "primaryParty": "$alice",
    "rights": [ { "canActAs": "$alice" },
                { "canReadAs": "$bank" } ]
  };


SUBSTITUTION
------------

A `$name` inside a JSON string is replaced when the WHOLE string
is the reference:

  { "owner": "$alice" }        the binding, with its type
  { "label": "owner-$alice" }  literal text, no interpolation

A string that must begin with a literal dollar is written `$$`.

Substitution happens inside coercion, so the binding's type is
checked against the type expected at that point. A field wanting
a party and a binding holding a contract id are both strings in
JSON; nothing further down would have noticed.


SYNTAX
------

Keywords are upper case and reserved.
Comments run from `--` to the end of the line.

EVERY STATEMENT ENDS WITH `;`. There is no exception, not even
for the last statement in the editor - a script whose final
statement carries none is refused before anything is sent. A
newline is whitespace wherever it falls, so a statement may be
laid out over as many lines as it reads well on, and two
statements may share one line.

Ctrl-Enter runs the statement the caret is in, cut at the first
`;` outside a text literal and outside a WITH payload, so a
statement laid out over several lines runs from any of them.

A `;` inside a text literal or inside a WITH payload is not a
terminator; it is part of the value.

A template is written `Module:Entity`, or
`packageId:Module:Entity` when two packages on the ledger carry
the same module and entity name.

A PARTY MAY BE WRITTEN WITHOUT QUOTES. These are the same
statement:

  AS Plant-1::1220abcd QUERY Main:Batch;
  AS "Plant-1::1220abcd" QUERY Main:Batch;

What makes the bare form a party is the `::` a party id
carries. A party HINT, a user id and a contract id have none,
so `ALLOCATE PARTY "Alice"`, `GET USER "alice-app"` and
`FETCH "0076..."` still take their quotes - and inside a WITH
payload everything stays strict JSON, so what a detail pane
renders pastes straight back.

AS maps to a right by statement: readAs for QUERY and FETCH,
actAs for CREATE and EXERCISE. ALLOCATE PARTY and CREATE USER
take no AS.


LIMITS WORTH KNOWING BEFORE YOU HIT THEM
----------------------------------------

* AS names the parties that act, or that read. A submission
  needing actAs AND an extra readAs cannot be written.
* There are no disclosed contracts. A contract a choice needs to
  see - a registry's configuration, a rule, a credential - has
  to be visible to one of the AS parties: name its stakeholder
  among them.
* WITH on a QUERY and on FETCH ... SINGLE is refused; WHERE, on
  a QUERY, is the filter. FETCH ... SINGLE only succeeds on a
  template with exactly one visible contract.
* ASSERT compares against a literal count. There is no operator
  and neither side may be computed.
* PRUNE is irreversible and its outcome is a property of the
  participant's pruning rules, not of this language.
* There is no EXECUTE. CaQL runs CaQL; it does not run a Daml
  Script.


A WHOLE SCRIPT
--------------

  alice = ALLOCATE PARTY "Alice";
  bank  = ALLOCATE PARTY "Bank";

  CREATE USER "alice-app" WITH {
    "primaryParty": "$alice",
    "rights": [ { "canActAs": "$alice" } ]
  };

  acct = AS $bank CREATE Main:Account WITH {
    "owner": "$alice",
    "bank": "$bank",
    "balance": "0.0"
  };

  AS $bank EXERCISE ON $acct Deposit WITH { "amount": "10.0" };
  AS $alice QUERY Main:Account;
  ASSERT AS $alice QUERY Main:Account COUNT 1;
```

---

## 10. The fixtures

### 10.1 Aviation — Single participant

An aircraft maintenance model: aircraft, configurations, serialized components and their installation and removal, maintenance events, tasks, inspections, defects, investigations, engineering decisions, corrective actions, operational tests, shop visits, certifications and releases. The model is `Main` (`Main:Aircraft`, `Main:Defect`, `Main:Release` and so on); `apps/sandbox/src/main/resources/fixtures/aviation/daml/Main.daml` is its source, and **Aviation documentation** on the Settings tab describes it.

Three airframes, the components fitted to them, the defects found on them and everything done about those defects. **Story A** is the troublesome aircraft, MSN-4711: a scheduled inspection finds a hydraulic defect, DEF-104, which is investigated, deferred, recurs, has a transducer replaced, fails its test, gets a wiring repair, passes an independent inspection and is closed; beside it DEF-091 closes in four steps. **Story B** is the travelling engine, SN-88321: removed from MSN-5822, through a shop visit and a certificate, and fitted to MSN-4711, so the two stories meet. A third airframe, MSN-6190, is grounded with a defect still open. About 35 contracts are active and as many archived. The data is nested - records inside lists inside records, optionals, variants - and chosen so that `WHERE` has known answers: the cycle counts sit at 11999, 12000 and 12001. Only the Technician can perform maintenance, only the Inspector can sign the independent inspection, only Engineering can record a disposition, and only ReleaseAuthority can release an aircraft, so each party sees a different part of the ledger.

| user | party | rights |
| --- | --- | --- |
| `maintenancecontrol` | MaintenanceControl | acts as it |
| `technician` | Technician | acts as it |
| `inspector` | Inspector | acts as it |
| `engineering` | Engineering | acts as it |
| `quality` | Quality | acts as it |
| `configurationcontrol` | ConfigurationControl | acts as it |
| `releaseauthority` | ReleaseAuthority | acts as it |
| `auditor` | — | reads as all seven, acts as none |
| `superuser` | — | acts and reads as all seven |

`participant_admin` is the participant's own administrator: it sees users and parties, not contracts.

```
LIST PARTIES;
AS MaintenanceControl::<fingerprint> QUERY Main:Defect WHERE grounded = true;
```

`LIST PARTIES` gives the full party ids, as `participant_admin`; the second statement, as `superuser`, lists the grounded defects.

### 10.2 Pharma — LocalNetND

Two organizations on two participants, so the boundary in the story is a real boundary in the network: the producer, **Kestrel**, on `app-provider`, and the supplier, **Vinterberg**, on `app-user`. Each person is a party on their own organization's participant, with a ledger user of their own:

| organization | parties |
| --- | --- |
| Kestrel, `app-provider` | `Kestrel-Procurement`, `Kestrel-QualityAssurance`, `Kestrel-Receiving`, `Kestrel-Manufacturing` |
| Vinterberg, `app-user` | `Vinterberg-Sales`, `Vinterberg-Production`, `Vinterberg-QualityAssurance`, `Vinterberg-Shipping` |

Each participant also has its own `superuser`, which still cannot read the other organization's private contracts.

* **Story 1, the clean lot.** Quality Assurance records the specification and Procurement the order for 12000 vials; Sales accepts, Production makes lot LOT-88213, supplier QA tests it and issues a Certificate of Analysis, Shipping sends it; Receiving takes it in, and the producer's Quality Assurance runs its own incoming tests and releases it.
* **Story 2, the problem lot.** A second order, made and certified the same way, where the incoming test disagrees with the certificate: the producer quarantines the lot - its own record - and raises a deviation, which is where the supplier hears of it; then the supplier's response, the retest, the disposition and the quarantine closed.

Every choice is controlled by one organization and submitted from that organization's participant, so the Workbench shows a different ledger from each side. A list of quality tests, each with a measured value and a permitted range, is there for `WHERE` to reach with a dotted path. The source is `apps/sandbox/src/main/resources/fixtures/pharma/daml/Main.daml`, and **Pharma documentation** on the Settings tab describes it.

---

## 11. Files and directories

| | Linux | Windows |
| --- | --- | --- |
| settings, profiles, run directories, snapshots, fixtures, RAWARs | `~/.raposza` | `%APPDATA%\raposza` |
| Workbench profiles and catalogue, the CaQL audit log | `~/.raposza` | `%USERPROFILE%\.raposza` |
| Splice bundles | `~/.splice` | `%USERPROFILE%\.splice` |
| LocalNetND's run directory | `~/.splice/native-localnet` | `%USERPROFILE%\.splice\native-localnet` |
| DPM, unless set elsewhere | `~/.dpm` | `%APPDATA%\dpm` |
| the debug logs, the newest ten | `~/.raposza/logs` | `%APPDATA%\raposza\logs` |

To remove Raposza, delete the jars or the clone and those directories. DPM, the SDK and Canton are managed by DPM's own tools.

---

## 12. Security — what the person must know

**Raposza is developer tooling for one workstation, and it is not hardened.** It starts a Canton stack, a database and an identity provider as ordinary processes of the user who runs it, with test credentials. Nothing it starts is meant to be reachable from a network anyone else is on. From `SECURITY.md`:

* **Every credential the Sandbox creates is a published test credential.** Web UI users and the identity provider's admin use `123456`; the embedded PostgreSQL runs trust authentication as `postgres`/`postgres`; LocalNetND signs its tokens with HS256 over the literal secret `unsafe`, with no expiry.
* **The provider mints a token for any subject on request, without authentication**, on `127.0.0.1` only. Anything that can open a connection to that port on the machine — another local account, a browser page the person visits — can get a token the participant accepts.
* **The Ledger API is unauthenticated unless authentication is asked for.** The window's default is JWKS; `--cli` without `--auth` is unauthenticated. A Canton **2.x** participant started by the Sandbox binds its Ledger API and Admin API on EVERY interface, and no authentication is configured on its Admin API. The RAWAR server on `127.0.0.1:31100` forwards `_ledger/` to the JSON Ledger API with whatever credential the caller sends.
* **Downloads are checked by TLS and nothing else, and then executed**: DPM, the SDK installer and Splice bundles. No digest or signature is verified.
* **Test data, tokens and database passwords are written to disk in clear**, under `~/.raposza` and `~/.splice`, mostly with the user's umask.
* **An archive installed from file is not checked at all**: the DPM archive and the Splice bundle are trusted as the person's own.
* **The debug log collects all of it.** Every window writes one file with every log line, the settings file and the proxy and Java environment variables as they are set. It carries the test credentials above, LocalNetND's token that never expires, and any password a proxy variable holds. Tell the person to read it before attaching it anywhere.
* **The discovery document on `127.0.0.1:32001` publishes every JDBC password and a token URL per node**, to anything on the machine that can reach it.

Vulnerabilities are reported to **info@raposza.com**, not in a public issue.

---

## 13. When something goes wrong

* **Read the debug log first.** Every window writes one file with everything: the console prints its path as `debug log:` when the window opens, in `~/.raposza/logs` (`%APPDATA%\raposza\logs` on Windows). Ask the person for that file rather than for lines copied from the panes. The Sandbox tab's log says what the window is doing; the **Debug** tab has one log per process.
* **What is installed and what is missing:** **Settings** tab, **Check this machine** (section 4.1).
* **The SDK or Splice box is empty.** Nothing is installed where the window looks. Install from the window (sections 4.4, 4.5), or set the directory on the Settings tab.
* **A version is greyed out** in the SDK box: it has no runtime jar.
* **PQS runs the mock.** The main log says why, in a line beginning "The Sandbox is not able to start PQS:". Install the SDK through DPM (section 4.4), or set **PQS scribe.jar** on the Settings tab.
* **Restarting a stack straight after stopping it**: the window waits for its own PostgreSQL port, which the stopped server holds for about a minute.
* **The Workbench cannot connect to a local Sandbox**: the Sandbox window must be open, and the port in the dialog must match the **Discovery port** on the Sandbox's Settings tab. `curl http://127.0.0.1:32001/` shows what the Sandbox publishes.
* **Windows, the embedded PostgreSQL does not unpack**: the Sandbox retries in `raposza-tmp` beside the jar by itself.
* **A CaQL write is refused on a read-only connection**: connect with **Allow writes**, or use `rw` in the catalogue.
* **`--canton` and `--line` together** is refused: they are alternatives.

---

## 14. Where to read more, in the source tree

| file | what |
| --- | --- |
| `README.md` | the front page |
| `FIRST_INSTALL.md` | the first installation, step by step, for a person |
| `BUILD_AND_INSTALL.md` | building from source |
| `README_LONG_TIME.md` | architecture, tested versions, known limitations |
| `SECURITY.md`, `docs/security-review.md` | the security model and the full posture |
| `docs/architecture.md` | the reference for contributors |
| `apps/sandbox/README.md`, `apps/workbench/README.md` | the two applications in more depth |
| <https://github.com/raposza/OIDC> `README.md` | Raposza OIDC standalone: settings, the client registry, users and claims, behind a reverse proxy |
