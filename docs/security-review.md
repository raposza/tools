<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Security posture

**This is a REVIEW, not an audit.** It was written by the people who wrote the
code, from a static reading of this tree on 2026-09-25, read again on
2026-09-26 against the tree released as 0.4.0, on 2026-09-27 for 0.4.1, on
2026-10-05 for 0.5.0, on 2026-10-06 for 0.5.0 with Raposza OIDC 0.5.1, and on
2026-10-14 for 0.6.0, on 2026-10-14 for 0.6.1, on 2026-10-26 for 0.6.2, and
on 2026-10-31 for 0.6.3.
Nothing in it was
established by a third party, and no third party has signed anything about it.
When an external audit exists it will be published beside this document,
unedited.

**Reviewed 2026-10-31 for 0.6.3.** The changes since 0.6.2 were read against
the tree, and `SECURITY.md` with them; Raposza OIDC stays at 0.5.1. **ADDED:**
the debug log every window writes - in section 4, in section 6 and as L-18:
one file with every log line, the settings file and the proxy and JVM
environment variables, which a user is asked to send; DPM installed from an
archive the user chooses, and a Canton installed for a Splice release through
DPM or from file, in sections 1 and 2; the `install` directory, in section 4.
**ONE OMISSION WAS STALE and is corrected:** Install Splice from file predates
0.6.2 and was in neither document - section 2 and L-1. The first-start check
reads the disk and the environment and writes only the settings file; choosing
a Splice release's Canton reads a page inside the bundle; neither starts a
process or opens a port. ONE DEPENDENCY OF THE SHIPPED JARS MOVED: Guava
33.3.0-jre to 33.7.2-jre, for CVE-2026-102554, GHSA-xxph-c9ww-hj94, published
by the Guava project 2026-09-29 and in GitHub's advisory database 2026-10-27 -
Java deserialization of a Guava collection allocates from a size
the stream declares and runs out of memory. No code in this tree reads a Java
serialized stream; the version is raised regardless. At build time - section
9: the jsoup forced into maven-site-plugin, 1.23.1 to 1.23.2, and the fixed
jsoup 1.23.2, XStream 1.4.21 and plexus-utils 4.0.3 forced into
maven-help-plugin 3.5.1, which the release workflow runs. The rest stands as
reviewed for 0.6.2.

**Reviewed 2026-10-26 for 0.6.2.** The changes since 0.6.1 were read against
the tree, and `SECURITY.md` with them; Raposza OIDC stays at 0.5.1 and no
dependency moved. **ONE STATEMENT WAS STALE and is corrected:** section 1 said
scribe is a staged `scribe.jar`; it is now also taken from the DPM cache or
from the jar the settings name. **ADDED:** each candidate scribe jar is run
with `--version` before it is chosen, in section 1 and L-16; `file.pqs` in
`settings.properties`, scribe's client in the provider's store and the
temporary file the version is read from, in section 4; the `raposza-pqs`
ledger user, which reads every party, and scribe's client secret, made per
start, in section 5; that secret on scribe's command line, in section 6 and
L-8. Save snapshot and the founding copy moving off the window's event thread
open no port, write nothing new and change no permission. **SECTION 9's
STATEMENT ON KNOWN VULNERABILITIES WAS INCOMPLETE and is replaced:** it named
one carried advisory; every known-vulnerable version this repository's build
carries is now listed there with the reason it stays, the Netty advisories
sorted by whether a client reaches them, and the one a peer can reach is
L-17. The rest stands as reviewed for 0.6.1.

**Reviewed 2026-10-14 for 0.6.1.** The change since 0.6.0 was read against
the tree, and `SECURITY.md` with it: a snapshot waits until the PostgreSQL
server holding the cluster has exited before it copies it, reading the
server's own `postmaster.pid` in the cluster directory and asking the
operating system whether the process it names is alive. It opens no port,
writes nothing new and changes no permission. No statement changed; the rest
stands as reviewed for 0.6.0. Raposza OIDC stays at 0.5.1.

**Reviewed 2026-10-14 for 0.6.0.** The changes since 0.5.0 were read against
the tree, and `SECURITY.md` with them; Raposza OIDC stays at 0.5.1. **TWO
STATEMENTS WERE STALE and are corrected:** section 3 said the discovery
document answers `GET /` only, and section 2 that a Splice bundle is started
through `bin/splice-node`, which on Windows it no longer is. **ADDED:** how
Splice is started on each platform, in section 1; both applications load the
Daml-LF reader from an installed Canton's jar - section 1 and L-15;
the discovery document's DARs and `GET /dars` in section 3; the data source
classes put first on Canton's and Splice's classpath on Windows, in sections 1
and 4 - on the single participant that predates 0.5.0 and was not described;
the DPM install of the single participant in section 2; the Pharma
population's grants in section 5; LocalNetND's proxy bypass for loopback in
section 7; the Workbench's built-in reader in section 9. The rest stands as
reviewed for 0.5.0.

**Reviewed 2026-10-06 for 0.5.0.** Raposza OIDC moved to 0.5.1, its code
unchanged, and the dependencies were reviewed again; `SECURITY.md` was read
with it. **TWO STATEMENTS WERE STALE and are corrected:** section 9 had
Raposza OIDC at 0.5.0 and said the dependencies are looked up in OSV.dev
alone, and section 13 said the same. Section 9 gains the one advisory the
carried provider brings, CVE-2026-47884 in Spring Framework 6.2.19, and why
its path is not reached.

**Reviewed 2026-10-05 for 0.5.0.** The changes since 0.4.1 were read against
the tree, and `SECURITY.md` with them. **ONE LISTENER WAS MISSING:** the
window's page server on `127.0.0.1:31100`, which forwards to the stack's JSON
Ledger API - section 3, section 4's `rawars/`, and L-14. **FOUR STATEMENTS
WERE STALE and are corrected:** section 9 had Jackson at 2.17.2 and Raposza
OIDC at 0.4.0; section 10 said the release workflow names its actions by tag;
sections 9 and 13 said no dependency has been checked for known
vulnerabilities. Added from the tree: the enforcer's supply-chain rules in
section 2, the versions this build sets over what the Daml artefacts bring in
section 9, the refused `-Draposza.jwtmint.jar` in section 1, every ledger user
of the single participant registered at the provider in section 5, and the
OIDC tab's Users and Access log and the Web tab's Log in section 6. The rest
stands as reviewed for 0.4.1.

**Reviewed 2026-09-27 for 0.4.1.** The changes since 0.4.0 were read against
the tree: section 4 now carries the three installation directories the
Settings tab writes, and section 10 the release workflow and its
`SHA256SUMS`; section 10's statement that the tree's version is a `-SNAPSHOT`
between releases was false at 0.4.0 and is removed. The rest stands as
reviewed for 0.4.0.

**Reviewed 2026-09-26 for 0.4.0.** The first review of this repository was
2026-09-25; the second reading, for the release, corrected section 9's
Raposza OIDC version and added the build plugin, added the 2.x help
links to section 7, and removed two scripts that no longer ship from
section 8. The first review's findings stand. Read in the same pass: `SECURITY.md`, this document, and the
statements about authentication in `apps/sandbox/README.md` and the Sandbox's
own `--help`. Two things the review found were FIXED in the same change rather
than recorded: the identity provider the Sandbox starts now binds `127.0.0.1`
instead of every interface, and the command line logged for scribe no longer
carries its token, its shared secret or its database password. Everything else
it found is below, most of it in section 11.

It is organised as the questions a reviewer asks, in the order they get asked.
Read it with `SECURITY.md`, which carries the short statement of the model and
the reporting channel.

**What Raposza is, in one paragraph.** Two desktop applications and the
libraries under them. **Raposza Sandbox** starts a local Canton stack - a
single participant, or LocalNetND, a Splice network of three participants run
natively - as ordinary processes of the user, on an embedded PostgreSQL, with an
identity provider beside it, and publishes how to reach all of it. **Raposza
Workbench** is a client: it connects to a participant over the Ledger API,
browses its packages and contracts, and runs CaQL against it. Neither is a
server anyone else is meant to reach, and neither is hardened. The sections
below say exactly where that shows.

Paths in this document are relative to the repository root; `apps/sandbox/...`
and `platform/...` are Java packages under `src/main/java/com/raposza/`.


## 1 - What executes, and how the command line is built

`java` to run both applications, `mvn` to build them. Beyond that, the Sandbox
starts other programs, and every one of them is started from an ARGUMENT LIST
through `ProcessBuilder`. There is no `sh -c`, no `bash -c`, no `cmd /c`, no
`Runtime.exec` and no shell string anywhere in the main source.

What the Sandbox runs:

| program | how it is found | started by |
| --- | --- | --- |
| Canton, 2.x and 3.x | the installation the user selects | `CantonSandboxProcess`, `Canton2xProcess`, `Canton3xDaemonProcess`, `CantonConsoleProcess` |
| scribe (PQS) | for the Canton line: the staged `~/.pqs/line/<line>/scribe.jar`, else the newest of the line in the DPM cache, whatever the jar is called, else the jar `file.pqs` names in the settings; `java` from `PATH` | `ScribeProcess` |
| the 2.x JSON API | the SDK installation | `JsonApiProcess` |
| Raposza OIDC | see below | `JwtMintProcess` |
| `dpm`, `daml` | the installed toolchain | `ToolchainInstall`, `AviationRun`, `DamlScriptRun` |
| `tar` | `PATH` | `Extract`, `SpliceAcquire` |
| a downloaded installer: `dpm bootstrap`, `install.sh` / `install.bat`; and `dpm bootstrap` from a DPM archive the user chooses | the archive just unpacked - section 2 | `ToolchainInstall` via `InstallRunner` |
| `splice-node` | the Splice bundle: `bin/splice-node` on Linux; on Windows, where that shell script cannot run, `lib/splice-node.jar` under `java` from the Sandbox's own runtime (`SpliceLaunch`) | `LocalNetRunner`, `SpliceCheck` |
| `xdg-open` | `PATH`, only when `Desktop.browse` fails | `JwtPane` |

**Raposza OIDC is not built here.** `JwtMintProcess.fileJarFound()` takes the
jar named by `-Draposza.oidc.jar` if set; otherwise the NEWEST
`raposza-oidc-server` jar in the local Maven repository, by modification time,
across every version directory; otherwise the copy the Sandbox jar carries,
unpacked under `~/.raposza/raposza-oidc/`. The chosen jar is run without a
digest check. Anything that can write to `~/.m2` therefore chooses what the
Sandbox runs as the identity provider - which is the same account.
`-Draposza.jwtmint.jar`, the property's name until 0.4.x, is refused at start
rather than ignored, so the jar a developer names is the jar that runs.

**Scribe is run once to ask its version before it is chosen.** A jar in the
DPM cache, and the jar `file.pqs` names, is started as
`java -jar <jar> --version` with `java` from `PATH`, given 30 s and then
killed, and its answer is cached per path, size and modification time
(`PqsInstallations`). A jar whose banner names another Canton line is not
started for this one. The banner says which version the jar claims to be; it
does not establish what the jar is, and no digest is checked. Anything that
can write the DPM cache or the settings file chooses code that runs as scribe,
and runs at that check - L-16.

**Windows.** `dpm` and `daml` are `.cmd` files there and the SDK installer is
`install.bat`, and the JDK starts those through `cmd.exe`, which parses its
arguments by its own rules. The arguments passed are version strings, file
paths, a script name and a ledger host; whether any of them can carry a
metacharacter taken from user input was NOT established by this review.

**Windows: classes of the Sandbox's own ahead of Canton's jar.** PostgreSQL for
Windows refuses a setting Canton and Splice send on their connections, so on
Windows the Sandbox copies two class files out of its own jar,
`WinPgDataSource`, before each start - into the work directory's `pgshim/` for
the single participant, into `<state root>/localnet/pgshim/` for LocalNetND -
and puts that directory first on the classpath of Canton and, on LocalNetND,
of Splice (`PgShim`, `LocalNetRunner.useDataSource`). Anything that can write
that directory chooses code that runs inside Canton - the same account.

The Workbench starts no process.

**Both applications load code from an installed Canton.** The Daml-LF reader -
for the Workbench's package reads and for the Sandbox's `GET /dars` - is taken
from the jar of the newest open-source Canton 3.5.1 or later the machine has,
through a class loader of its own, and run without a digest check
(`LfReaderSource`, `LfReaderLoader`). With none, the Workbench uses the reader
built into its jar (`LfReaderChoice`). Anything that can write the toolchain
directories chooses that code - L-15.

Configuration - `~/.raposza/settings.properties`, the per-version profiles,
`~/.raposza/ledger_hosts`, and the command line - is TRUSTED INPUT in the
ordinary sense: it chooses ports, directories and what is started. It is your
own file.


## 2 - What is downloaded, from where, and how integrity is established

**At BUILD time**, from Maven Central, or from the local repository: the
dependencies in section 9 and the Maven plugins the poms name. No pom declares
a `<repositories>`, `<pluginRepositories>` or `<distributionManagement>`
element, and the enforcer's supply-chain rules make that a build failure
rather than a convention: a declared repository, a plugin of the clean or
default lifecycle without a stated version, or a dynamic or SNAPSHOT
dependency version stops the build at `validate`. Integrity is Maven's: the
checksums the repository serves, verified by the resolver.

**At RUN time, only the Sandbox downloads, and every download starts from
something the user does in the window.** Nothing is fetched at application
start. The Workbench downloads nothing.

| what | from | when |
| --- | --- | --- |
| the newest DPM version string | `https://get.digitalasset.com/install/latest` | the toolchain banner's Install on LocalNetND; on the single participant, the SDK row's Install on a machine with no DPM, after **Install DPM?** |
| the DPM archive | `https://get.digitalasset.com/install/dpm-sdk/dpm-<v>-<os>-<arch>.tar.gz` or `.zip` | the same |
| a Daml SDK archive | `https://github.com/digital-asset/daml/releases/download/v<v>/...` | an Install in the SDK dialog, only where no DPM is present |
| Canton and the SDK components | `oci://europe-docker.pkg.dev/da-images/public-all/components/...`, fetched by `dpm`, not by this code | an Install in the SDK dialog; `dpm version --all` also runs when that dialog opens |
| the Splice release list | `https://api.github.com/repos/digital-asset/decentralized-canton-sync/releases`, up to ten pages, plus one `HEAD` per release for its size | AUTOMATICALLY when the Splice install dialog opens |
| a Splice bundle, about 1.5 GB | `https://github.com/digital-asset/decentralized-canton-sync/releases/download/v<v>/<v>_splice-node.tar.gz` | a row's Install in that dialog |
| a help page as Markdown | `https://docs.canton.network/...` | AUTOMATICALLY when a help dialog opens |
| `daml-script` and its dependencies | resolved by `dpm build` / `daml build` | the first fixture build for a Canton version |
| the newest Daml SDK of the Canton line a Splice release names, and DPM first where it is missing | the DPM rows above, and `dpm` itself | **Download** in the dialog a LocalNetND start raises when no Canton of that line is installed |

**TLS IS THE ONLY INTEGRITY CONTROL, AND WHAT IS DOWNLOADED IS THEN EXECUTED.**
`platform/raposza-canton/.../install/Download.java` checks the HTTP status and,
where the server sends one, the `Content-Length`, and writes through a `.part`
file that is moved into place on success. It computes no digest and checks no
signature. The DPM archive is unpacked and its `bin/dpm bootstrap` run; the SDK
archive is unpacked and its `install.sh` or `install.bat` run; a Splice bundle
is unpacked and started once with `daemon` to prove it starts - section 1 says
how on each platform. That is L-1.

`SpliceAcquire` does compute a SHA-256 of the kept archive - and writes it into
`SOURCE.txt` beside the bundle as provenance. Nothing reads it back and nothing
compares it with a published value. It records what was downloaded; it does not
check it.

**FROM FILE, NOTHING IS CHECKED AT ALL.** Where downloads are blocked the user
fetches the archive elsewhere and picks it in a file chooser. **Install DPM
from file...** - on the first-start check, after a failed DPM download, and as
**Install from file...** for a Splice release's missing Canton - unpacks it
with `tar` and runs its `bin/dpm bootstrap`
(`ToolchainInstall.versionInstallDpmFromFile`). **Install from file...** in the
Install Splice dialog copies a `<version>_splice-node.tar.gz` into the Splice
directory, unpacks it and starts it once, as a download is
(`SpliceAcquire.install`). No TLS is involved and no digest is compared: the
file is trusted as the user's own. That is L-1 too.

The version string from `.../install/latest` is trimmed and used in a URL and a
file name without further validation. The Splice version is taken through the
pattern `v(\d+\.\d+\.\d+)` and is constrained.

Every HTTP client here follows redirects with `Redirect.NORMAL`, which does not
follow an HTTPS-to-HTTP redirect. `Download` has a 30 s connect timeout and, by
design and with the reason at the site, no read timeout: a stalled transfer
holds its thread until the window is closed.


## 3 - What listens, on which interface, with what authentication

**Every port is above 1024.** The defaults sit in three classes - 30000-30999
for nodes, 31000-31999 for web UIs, 32000-32767 for administrative endpoints -
`platform/raposza-runtime/.../settings/PortClass.java`. Each block's first port
is a setting.

| listener | default port | bound to | authentication |
| --- | --- | --- | --- |
| discovery document (Sandbox) | 32001 | `127.0.0.1` - `DiscoveryServer`, no setting changes it | **none**; `GET /` and `GET /dars` only, anything else 404 or 405 |
| the window's page server, `RawarServer` | 31100, a setting | `127.0.0.1` - `InetAddress.getLoopbackAddress()`; up while the window is open, with or without a stack | **none of its own** - below |
| Raposza OIDC, started by the Sandbox | 32002 | **`127.0.0.1`** - `JwtMintProcess.lstArgSpring()` passes `--server.address=127.0.0.1` | `/mint` **none**; admin paths `admin` / `123456` - see Raposza OIDC's own review |
| embedded PostgreSQL | 32101 | PostgreSQL's default, `localhost`: the embedded server sets no `listen_addresses` | **trust** - `initdb -A trust`; any password is accepted |
| Canton 3.x participant, sequencer, mediator: Ledger, Admin, JSON, public ports | 30010-30015 in the window; 6864-6869 with `--cli` | not set here, so Canton's default, which Canton documents as `127.0.0.1` | Ledger API as configured - section 5; Admin API: none configured here |
| **Canton 2.x participant: Ledger API and Admin API** | 6865, 6866 | **`0.0.0.0`** - `Canton2xConfig.java`, written explicitly | Ledger API as configured; **Admin API: none configured here** |
| Canton 2.x domain, public and admin | 6867, 6868 | not set here; Canton's default | none configured here |
| 2.x JSON API | 6864 | not set here; the JSON API's default was NOT established | `--allow-insecure-tokens` |
| scribe health endpoint (PQS on) | the stack's highest port plus one | not set here; scribe's default was NOT established | not established |
| LocalNetND: three participants, two sequencers, two mediators, three validator apps, scan, the SV app | 30010 upwards, stride 10 | not set here; the Splice bundle's configuration applies and was NOT established | HS256 over `unsafe` by default; JWKS against Raposza OIDC from the window |
| LocalNetND web UIs and their reverse proxy | 31000, 31010, 31020 | `127.0.0.1` - `LocalNetRunner.STR_HOST` | none at the proxy; the UIs sign in against the configured provider |

**The page server is a forward, not a gate.** It serves the files under
`~/.raposza/rawars`, refusing a path that leaves a site's directory and any
hidden file, and `_env.json`: the issuer, client id, redirect URL and audience
a page signs in with, and no secret. Under `_ledger/` it FORWARDS the method,
path, query, body and headers - the caller's bearer among them, never read
here - to the stack's JSON Ledger API, and passes the answer back with the
ledger's own headers; it adds no CORS header of its own. So it is exactly as
authenticated as that API: anything that can reach `127.0.0.1:31100` reaches
the ledger with the credential it brings, and with none when the stack runs
without authentication. Each request is shown on the Web tab's Log - method,
path without its query, and status; no header is shown or kept.

**The Workbench listens on nothing.** It is a client of whatever its profile
names.

**What the discovery document publishes, to anyone who can reach
`127.0.0.1:32001`.** The stack's state; the provider's discovery, issuer, JWKS
and token URLs; and per node its host and ports, participant, party and user
ids, the work and data directories, the process id, and every JDBC URL, user
and PASSWORD - `ReadyReport`'s `password.jdbc.*` keys, `postgres` under trust
authentication. Per node it also publishes a `token` URL on the Sandbox's own
provider, one `GET` away from a token for that participant. **In
`unsafe-jwt-hmac-256` mode that URL carries the participant's shared secret as
`&secret=`** - `JwtMintProcess`, because the provider has to sign with the key
the participant verifies. It publishes no token and no admin token.

**It also lists the DARs** - name, version, package id, origin, `selected` or
`uploaded`, and the file's path on disk - and the snapshot a running stack was
restored from. **`GET /dars`** answers with every template, choice, field and
type in those DARs, decoded from the files on the request's own thread, two
requests at a time; a description that fails answers with its reason.

**LocalNetND's reverse proxy answers the JSON API hosts with
`Access-Control-Allow-Origin: *`** and GET, POST, PUT, DELETE and OPTIONS, as
the vendor's own proxy does - `LocalNetWeb`, `LocalNetUi`. In the default HS256
mode, the `/config.js` it serves to the browser carries `secret: 'unsafe'`,
because that is how the vendor's UIs sign their own tokens.

**Outbound connections to a participant** - the Workbench over the Ledger API,
the Sandbox's DARs pane over the Admin API - are plaintext unless the profile
asks for TLS; the Admin API channel is always plaintext and always `127.0.0.1`
(`AdminChannels`). A bearer token on a plaintext channel is on the wire.


## 4 - What is written to disk, and with what permissions

The state root is `~/.raposza`, or `%APPDATA%\raposza` on Windows
(`RaposzaSettings`). The Workbench and the CaQL audit log use
`<user.home>/.raposza` on every platform.

| path | contents | permissions |
| --- | --- | --- |
| `settings.properties` | ports, directories - its own, and the Daml Assistant, DPM and Splice installations when they are set - the scribe jar `file.pqs` when it is set, the external provider's URL | umask |
| `profiles/<version>-<edition>.properties` (Sandbox) | the form, INCLUDING the HMAC shared secret, `auth.secret`, in clear | umask |
| `profiles/<name>.properties` (Workbench) | the auth mode and, ONLY if the user ticks "save", a bearer token in clear | **`rw-------`**, set after the write |
| `audit.jsonl` | one line per CaQL submission | **`rw-------`** at creation |
| `sandbox/<version>/work/` | the generated Canton configuration - which holds the admin token, the HMAC secret and the database password - logs, `sandbox.properties`, `admin-token.txt`, and on Windows `pgshim/` - section 1 | umask |
| `sandbox/<version>/data/` | the PostgreSQL cluster; wiped at every start unless `--data-dir` is given | as PostgreSQL creates it |
| `snapshots/` | whole copies of a stopped PostgreSQL data directory, per version and per LocalNetND key | the data files keep their mode; the directories and the meta file take the umask |
| `fixtures/aviation`, `fixtures/pharma` | Daml project sources | umask |
| `rawars/` | the pages the window's page server serves, put there by the user; read, never written | the user's |
| `jwtmint/keys` | Raposza OIDC's key set, private members included, and its users and clients - among them scribe's client and its secret, registered by the window again at each start and never removed - section 5 | written by that service; see its review |
| `raposza-oidc/raposza-oidc-app.jar` | the carried provider, unpacked | umask; `.part` then move |
| `oidc-access/<port>/access.log` | the provider's access log, one line per request WITH ITS QUERY - so a mint for an HMAC participant leaves that participant's secret here. In `java.io.tmpdir` until 2026-10-05 | **`rwx------`** on both directories where the file system has POSIX permissions; the file takes the umask |
| `packages/` | cached Daml-LF packages | umask; temporary file then atomic move |
| `localnet/pgshim/` (Windows) | the data source classes put first on LocalNetND's classpaths - section 1 | umask; rewritten before each start |
| `install/` | the DPM archive the window downloads and the trees it and a chosen archive are unpacked into; nothing removes them | umask |
| `logs/sandbox-<UTC stamp>-<pid>.log` | the debug log, one per window - section 6; the newest ten are kept and older ones deleted when a window opens | umask, the directory too |

The embedded PostgreSQL's binaries are unpacked into `java.io.tmpdir`, or,
when that fails, into `raposza-tmp` beside the Sandbox jar (`SandboxPostgres`).

`~/.splice/` holds the downloaded Splice bundles - or the Settings tab's
Splice directory does, when it is set - and, by default, LocalNetND's run
directory `~/.splice/native-localnet`, which does not move with that setting:
its PostgreSQL cluster, the staged
configuration - which carries `secret = "unsafe"` - per-process logs,
`web.log` rotated at 10 MB, and while it runs `localnet.properties`, which
carries the database password, the HS256 secret and a signed token with no
expiry.

**Temporary files.** The fixture writes each user's bearer token to a
`Files.createTempFile` file - owner-only on POSIX by the JDK's own default -
and deletes it in a `finally`. `PqsInstallations` writes a scribe jar's
`--version` output to a `Files.createTempFile` file and deletes it once
read. `LocalNetProbe` uses the FIXED directory
`${java.io.tmpdir}/localnet-probe`, whose name another local account can
predict.

**Almost nothing is written atomically or narrowed.** The two exceptions are
named in the table. Everything else is truncated and rewritten in place with
the process umask's permissions. On a file system without POSIX permissions the
narrowing is skipped and the Workbench reports that it was.


## 5 - What credential or key material is held, where, and for how long

**Passwords, all published test values.** `123456` for every web UI user the
Sandbox registers - on the single participant that is every ledger user, written
to the window's provider at each start and whenever the window finds one the
provider lacks (`SandboxUsers`) - and for Raposza OIDC's admin
(`JwtMintProcess.STR_PASSWORD`);
`postgres` / `postgres` for the embedded PostgreSQL, under trust
authentication, so the password is not checked.

**Shared secrets.** The Sandbox's HMAC default is
`raposza-sandbox-unsafe-shared-secret`, held in clear in the profile
(`AuthSettings`). LocalNetND's is `unsafe` (`LocalNetToken`).

**The Ledger API's authentication.**

* The window defaults to JWKS against Raposza OIDC; `none (wildcard)`,
  `unsafe-jwt-hmac-256` and certificate modes are selectable.
* `--cli` is UNAUTHENTICATED unless `--auth <mode>` is given.
* LocalNetND defaults to HS256 over `unsafe` when run from the command line,
  and to JWKS against Raposza OIDC from the window.

**The admin token.** A random UUID per start, configured into Canton as the
Ledger API's `fixed-admin-token` and valid while the participant runs. It is written to
`admin-token.txt` and into the Canton configuration in the work directory, and
stays there after the stack stops.

**The Pharma population's grants.** To submit as each organization's parties,
the population grants `participant_admin` act-as rights over them and revokes
those grants as its last step (`PharmaSession`, `PharmaLedger.revokeActAs`). A
population that fails part-way leaves them in place.

**Scribe's reader and its client.** On Canton 3.x, when the form names no
user, scribe reads as `raposza-pqs`, a ledger user the start fetches or else
creates with `readAsAnyParty` - the right to read every party on the
participant (`PqsReaderScript`). It stays on the participant and in every
snapshot taken after it; a user of that id is taken to be this one, and its
rights are not read back. With PQS on and the window's own provider already
checking clients, the window registers scribe there as a confidential client
under that user's id, with a random 24-byte secret made for the start
(`MintRegistration.strRegisterScribe`); a provider that checks no clients is
left as it is, because registering one client makes it check every caller.

**Token lifetimes.** 24 h for a Sandbox-minted token on Canton 3.5.6 and later,
and 240 s below that, where the participant refuses longer ones. A token the
Workbench mints from a JWKS file is valid for a thousand years, by design and
with the reason at `ProfileAuth.TTL_DEFAULT`. LocalNetND's HS256 tokens carry
NO expiry. There is no revocation anywhere: a token is valid until it expires.

**Private keys.** Raposza OIDC's signing keys are under `jwtmint/keys` and are
that service's. A Workbench profile may point at a JWKS file holding private
keys; that file is the user's. The Sandbox's own code describes its PostgreSQL
cluster as holding the participant's crypto store (`SandboxPostgres`); whatever
Canton keeps there is in every snapshot of it. Where exactly Canton keeps its
node keys was NOT established by this review.


## 6 - What is logged, and whether a secret can reach a log line

Logging is slf4j-simple to standard error. In the Sandbox window, `LogTee`
copies standard output and standard error into the log pane, unfiltered.

**The command line of every process the Sandbox starts is logged**, and shown
in the pane, by `ManagedProcess.start`. It is logged through
`lstCommandForLog`, and `ScribeProcess` overrides that to elide the access
token, the client secret, the shared secret in the token endpoint's query and
the target database password. Canton's command lines carry no secret: its
credentials are in the configuration files, not on the command line. Raposza
OIDC's command line, which carries its admin password, is not logged.

**What still reaches the log, knowingly.**

* LocalNetND prints, at start, its database user and password, the HS256
  secret, and one complete `curl` line with a signed token that does not
  expire (`LocalNetRunner`). They are test values and the output is how a
  developer is told how to call the stack.
* The Canton configuration dialog shows every generated configuration file
  whole, including the admin token and the HMAC secret.
* The web UI table and the OIDC tab's Users table show the password `123456`;
  Users reads the provider's own `users.json`, passwords included, every 3 s
  (`ProviderUsers`).
* The OIDC tab's Access log shows each request to the provider with only `sub`
  and `client_id` from its query and no Authorization header; the file behind
  it keeps the whole query - section 4.

**The debug log - all of it in one file, made to be sent.** Every window
writes `logs/sandbox-<UTC stamp>-<pid>.log` under the state root (`DebugLog`,
`LogTee`, `LogPane.useDebugLog`). It receives every line the console and every
log pane receive, unfiltered and untrimmed, so everything this section says
reaches a log reaches that file too: LocalNetND's database password, its HS256
secret and its token with no expiry, the identity provider's admin user and
password as its log pane states them at start, and every command line as
logged. Its header adds the Sandbox's command line, the JVM's arguments, the
settings file verbatim, and the environment variables `JAVA_HOME`,
`JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `DPM_HOME`, `DAML_HOME`, `APPDATA`,
`LOCALAPPDATA`, `TMP`, `TEMP`, `HTTPS_PROXY`, `HTTP_PROXY`, `NO_PROXY` and
`PATH`, verbatim: a proxy URL or a JVM option carrying a password is written as
it is. The file is what a developer is asked to attach to a report, and
whoever receives it receives all of the above - L-18.

**On the process list.** A command-line argument is readable by any account
that can list processes: scribe's token, client secret and database password,
and Raposza OIDC's admin password, are visible there while those processes
run. The line the registration of scribe's client leaves on the OIDC tab names
the client and never the secret.

What Canton, scribe, Raposza OIDC and `splice-node` log themselves was NOT
established; their output is copied into the panes and the run directories.


## 7 - What leaves the machine

**Nothing without a user action, and no telemetry.** There is no update check
and no analytics.

What does leave:

* the downloads in section 2, each on a button press or on opening a dialog;
* the help dialog's request to `docs.canton.network`;
* when the Sandbox is pointed at an EXTERNAL identity provider (`oidc.url`),
  a request for `<oidc.url>/.well-known/openid-configuration`. The `jwks_uri`
  and `token_endpoint` in the answer are used as returned; its `issuer` is read
  and not compared with `oidc.url`;
* the Workbench's connections to the hosts its profiles name, and to the token
  URL a discovery document publishes - which need not be on loopback.

LocalNetND's own calls on loopback are kept away from a proxy the machine sets
for every JVM: both of its process groups start with
`-Dhttp.nonProxyHosts=localhost|127.*|[::1]` added to `JAVA_TOOL_OPTIONS`,
unless that already names a list, which is then left as it is
(`LocalNetRunner.strToolOptions`).

The LocalNetND web UIs' configuration names a favicon on `www.hyperledger.org`,
copied from the vendor's; the BROWSER fetches it, not this process. On
Canton 2.x the help dialog has no in-window page: its button hands a
`docs.daml.com` URL to the desktop's browser (`HelpDialog`, `FieldHelp`).


## 8 - What privileges are needed

None beyond an ordinary user account. Nothing needs root, `sudo` or an
elevated Windows prompt - the DPM bootstrap is an unprivileged install into the
user's home - and no port below 1024 is bound. `/etc/hosts` is never written;
LocalNetND prints the lines it would like there and leaves them to the user.

`build.sh` and `test.sh` write into `target/` and the local Maven repository.
The Sandbox writes `~/.splice` when LocalNetND is chosen, and `~/.splice` or
the Settings tab's Splice directory when a Splice bundle is installed -
section 4.


## 9 - What third-party code is present

Nothing is vendored: no jar, archive, font, script or style sheet from a third
party is checked in. Everything is a declared Maven dependency, versions pinned
in `platform/pom.xml`:

| what | version | why |
| --- | --- | --- |
| Jackson (`jackson-databind`, `jackson-datatype-jdk8`) | 2.21.7 | JSON |
| Nimbus JOSE+JWT | 10.10 | tokens, in `raposza-auth` |
| gRPC (`grpc-netty`, `grpc-protobuf`, `grpc-services`) | 1.60.0 | the Ledger and Admin APIs |
| Protocol Buffers | 3.25.8 | the same |
| `com.daml:bindings-java` | 2.9.6 | Ledger API v1 |
| `com.daml:daml-lf-archive-reader_2.13` | 3.4.11 | reading DARs: the Workbench's built-in reader, used when no installed Canton lends one - section 1. The Sandbox's pom excludes it from its `raposza-lf` dependency |
| Bouncy Castle (`bcprov-jdk18on`) | 1.85 | not declared here: it arrives through the Daml artefacts, and the version is managed so that it is ours |
| Guava | 33.7.2-jre | the same |
| Scala library | 2.13.16 | the same - the Daml-LF reader's runtime |
| embedded PostgreSQL (`io.zonky.test:embedded-postgres`) | 2.2.2 | the Sandbox's database |
| PostgreSQL JDBC | 42.7.13 | the same |
| FlatLaf | 3.5.1 | the Swing look and feel |
| SLF4J | 2.0.16 | logging |
| JUnit Jupiter | 5.11.3 | tests only |
| `com.raposza.oidc:raposza-oidc-core`, `raposza-oidc-server` (app) | 0.5.1 | tokens; the provider the Sandbox carries |
| `com.raposza.design:raposza-design` | 0.4.0 | tokens, the Inter and Hack fonts, the logo |
| `com.raposza.design:raposza-design-maven-plugin` | 0.4.0 | BUILD TIME only: renders the application icons from SVG. Its SVG library stays in the plugin's classpath and reaches no application jar |

**The launcher jars are shaded** (`-Papp`): each one carries its dependencies,
and the Sandbox's also carries the whole Raposza OIDC server jar as a resource.
The shade step strips the dependencies' own jar signatures.

**Nothing is loaded from a CDN or a website at run time.** The fonts come from
the classpath.

**Known vulnerabilities.** Before each release every dependency and every
plugin of the build, their own dependencies included, is listed with the hash
of its bytes and checked against OSV.dev's advisories and this project's own
record of them. A version above that is raised over what a library brings
says which advisory in `platform/pom.xml`.

**What is carried, and why - read 2026-10-26 for 0.6.2.** Where a version
with a known advisory stays, it stays on a recorded decision, and every one
that reaches this repository's build is below. Each says what the advisory
needs and whether this code gives it that. The sorting is read from each
advisory's own description and from what this code builds and runs; the
dependencies' code was not read - section 13.

**1. Netty 4.1.100.Final - in both applications, at run time.** gRPC 1.60.0
is the generation `com.daml:bindings-java` 2.9.6 resolves, and it declares
Netty 4.1.100.Final. No gRPC release is paired with a fixed Netty 4.1, and
Netty raised out of step with gRPC corrupted HTTP/2 framing when it was tried,
so Netty stays at the version gRPC was built against. Neither application runs
a gRPC or Netty server. Both are gRPC CLIENTS: the Sandbox of its own
participant's Admin and Ledger APIs on `127.0.0.1`, a process it started
itself; the Workbench of whatever participant its profile names, on loopback
or not. TLS, where a profile asks for it, is the JDK's: gRPC's
`useTransportSecurity()` with the JDK's default trust store, and no
netty-tcnative in the build. OSV.dev records 38 advisories against six of its
Netty jars:

| advisories | what they need | here |
| --- | --- | --- |
| HTTP/1.1 request parsing and smuggling - GHSA-38f8-5428-x5cv, GHSA-m4cv-j2px-7723, GHSA-pwqr-wmgm-9rr8, GHSA-fghv-69vj-qj49, GHSA-xxqh-mfjm-7mv9; `HttpPostRequestDecoder` GHSA-5jpm-x58v-624v; `HttpContentEncoder` GHSA-q4f6-jm68-57ww; CORS GHSA-6cqp-g7gg-8hr5, GHSA-8c42-7qj2-3j46; the WebSocket handshaker GHSA-4mp9-239f-g9hg; `HttpContentDecompressor` GHSA-f6hv-jmp6-3vwv | an HTTP/1.1 server, or that handler in the pipeline | **not reached.** gRPC speaks HTTP/2 and puts none of these handlers in a client's pipeline |
| HTTP/2 on the side that accepts streams - reset floods GHSA-563q-j3cm-6jxm and GHSA-prj3-ccx8-p6x4, `MAX_CONCURRENT_STREAMS` GHSA-5x3r-wrvg-rp6q, HTTP/2 to HTTP/1.x translation GHSA-c69g-56f8-xwqj | a server | **not reached.** Nothing here accepts a stream |
| HTTP/2 decompression - GHSA-93wv-jw9v-4972, GHSA-c2gf-v879-257j | `DelegatingDecompressorFrameListener` | **not reached.** gRPC decompresses its messages itself |
| Netty's compression decoders - GHSA-3p8m-j85q-pgmj, Bzip2 GHSA-558v-64gr-wgg4, Lz4 GHSA-mj4r-2hfc-f8p6 | those decoders in the pipeline | **not reached**, for the same reason |
| SPDY - GHSA-6jqx-86gh-f27w, GHSA-mvh2-crg5-v77c, GHSA-jppx-w49h-x2qq | SPDY | **not reached.** Nothing here speaks it |
| TLS and filtering on a server - SNI GHSA-c4c3-7fpv-j4q5, GHSA-fccg-mwvh-qqg4, GHSA-x4gw-5cx5-pgmh; `IpSubnetFilter` GHSA-3qp7-7mw8-wx86 | a server's `SniHandler` or subnet filter | **not reached** |
| `SslHandler` with a native engine - GHSA-4g8c-wm8x-jfhw | netty-tcnative | **not reached.** The build carries none |
| the multipart encoder - GHSA-gcjf-9mgh-3p7g | `HttpPostRequestEncoder` | **not reached.** Nothing here sends a form |
| the HTTP/1.1 client and proxy handlers - GHSA-57rv-r2g8-2cj3, GHSA-84h7-rjj3-6jx4, GHSA-v8h7-rr48-vmmv, GHSA-hvcg-qmg6-jm4c, GHSA-45q3-82m4-75jr | an HTTP/1.1 exchange, which for gRPC is only the `CONNECT` to an HTTP proxy | **reached only through a proxy the JVM is given for the participant's host.** The `CONNECT` carries the host and port of the user's own profile; what answers it is the user's own proxy. A hostile proxy can desynchronise that exchange - GHSA-57rv-r2g8-2cj3 |
| a file Netty reads on Windows - GHSA-xq3w-v528-46rv, GHSA-389x-839f-4rhx | a large file planted at that path | **reached on Windows by whoever can write that path**: a local account, inside the boundary of section 12. It crashes the application |
| the HTTP/2 `CONTINUATION` flood - GHSA-w9fj-cfpg-grvv | a peer sending header frames | **REACHED.** A participant the Workbench connects to can make it spend CPU and memory on headers it never completes - a denial of service of the Workbench by the server it was pointed at. The Sandbox's peers are its own processes. L-17 |
| a plain trust manager wrapped by Netty - GHSA-c653-97m9-rcg9 | a plain `X509TrustManager` handed to Netty | **NOT ESTABLISHED.** No profile hands Netty a trust manager of its own; whether gRPC's default path wraps one was not read |

**2. Spring Framework 6.2.19 - inside the Raposza OIDC jar the Sandbox
carries.** Two advisories. CVE-2026-47884, server-side request forgery and
remote code execution through `XsltView`, needs that view rendering behind a
`/**` mapping under an implicitly derived view name. CVE-2026-47890, stream
corruption, needs view fragments sent over Server-Sent Events with data an
attacker controls. The provider's own source has no `XsltView`, no view
resolver, no `ModelAndView`, no template engine and no Server-Sent Events, and
every one of its controllers returns a body. Spring Boot 3.5's MVC
auto-configuration defines three view resolvers - `InternalResourceViewResolver`,
`BeanNameViewResolver` and `ContentNegotiatingViewResolver` - and no XSLT one;
that is read from Spring Boot's source on its 3.5 branch, not from a running
provider, whose registered resolvers were not read. The fixed 6.2 release,
6.2.20, is for Spring's commercial support customers only; the open-source fix
is Spring Framework 7.0.9, which is Spring Boot 4. Nothing in this
repository's own code uses Spring.

**3. Logback 1.5.34, Log4j API 2.24.3 and Tomcat 10.1.55 - in the dependency
list, not in what ships.** The Sandbox declares the provider's jar `provided`,
so Maven lists that jar's graph at the versions Spring Boot 3.5.16 manages.
The jar itself - the bytes on Maven Central, sha256
`2da23d4e0019471c09f9c3851dd6f9bc4ff1256782a7655bde3c4551ec476dc7` - carries
logback-core and logback-classic 1.5.38, log4j-api 2.25.5 and
tomcat-embed-core 10.1.60, each at or above its fix: CVE-2026-13006 in
Logback 1.5.37, CVE-2026-49844 in Log4j API 2.25.5, CVE-2026-65905,
CVE-2026-65182 and CVE-2026-68525 in Tomcat 10.1.58. Raising the versions in
this tree would change the list and no byte the Sandbox ships.

**4. Build time only - what maven-site-plugin 3.22.0 brings: commons-beanutils
1.9.4, Jetty 9.4.58 and jsoup 1.23.2.** The poms state the site plugin's
version because the enforcer's plugin-version rule checks the site lifecycle
too; without it Maven falls back to 3.3, which brought commons-collections
3.2.1, commons-beanutils 1.7.0 and Velocity 1.5. No
build path here runs `site`: not `build.sh`, not `test.sh`, not the release
workflow. The advisories: CVE-2025-48734 in commons-beanutils; GHSA-355h-qmc2-wpwf,
GHSA-qh8g-58pp-2wxh, GHSA-2fvj-hgj9-j2gr and GHSA-7p3p-8qv8-m2vh in Jetty 9.4,
whose line has no fixed release. jsoup carries none: the build forces 1.23.2,
which fixes CVE-2026-71497 (fixed in 1.23.1) and CVE-2026-75140 (fixed in
1.23.2, released 2026-08-26 by jsoup's own changelog), since 0.6.3.

**5. Plugins listed and not run by the build - and one the release workflow
runs.** The list also holds plugins that are named and not executed: the
versions Maven 3.8.7's own super-POM binds for maven-antrun-plugin 1.3,
maven-assembly-plugin 2.2-beta-5, maven-dependency-plugin 2.8 and
maven-release-plugin 2.5.3, with advisories in their old dependencies
(commons-collections 3.2.1, commons-beanutils 1.7.0, plexus-utils 1.5.6 to
3.0.15, Velocity 1.5, JSch, Ant 1.7.1, Groovy 1.7.6, JDOM 1.1 and others).
That is a statement about `build.sh`, `test.sh` and the release workflow
only: a goal invoked by hand - `mvn site`, `mvn dependency:tree` on the
aggregator - runs that plugin with what it brings. **maven-help-plugin 3.5.1
IS RUN**, by the release workflow's `help:evaluate`, which reads the version to
check against the tag. Its own POM brings jsoup 1.17.2 (CVE-2026-75140,
CVE-2026-71497), XStream 1.4.20 (CVE-2024-47072) and plexus-utils 4.0.1
(CVE-2025-67030); since 0.6.3 the build forces the fixed versions into it -
jsoup 1.23.2, XStream 1.4.21 and plexus-utils 4.0.3 - and those are what it
loads. Its only input is this repository's own POM at the tagged commit, which the same
run then builds: whoever could hand it hostile input could change the build
itself.


## 10 - How a release is built, and how a consumer verifies it

This repository is released as a tagged source tree on GitHub. From 0.4.1,
publishing a GitHub Release on a tag runs `.github/workflows/release.yml` from
that tagged commit: on a GitHub-hosted runner it checks that the tag names the
pom version, runs `test.sh`, and attaches `sandbox-<version>-app.jar`,
`workbench-<version>-app.jar` and `SHA256SUMS` to the Release. The workflow
holds `contents: write` and the run's own token, nothing else. It names
`actions/checkout` and `actions/setup-java` by commit, not by tag, so a tag
moved by their owners does not move what runs; and it runs Maven 3.8.7
downloaded from `archive.apache.org`, checked against Apache's SHA-512 before
use - a mismatch stops the release.

**No artefact of it is deployed to a Maven repository, and nothing is signed**
- there is no release profile and no signing plugin in any pom, the tag is
not a signed tag, and the jars carry no signature. `SHA256SUMS` is written by
the same run that uploads the jars, so it shows that a download is intact; it
cannot show that the jars on the Release are the ones that run built, because
whoever can replace the jars can replace the file. A consumer who needs more
builds from source, which is what `BUILD_AND_INSTALL.md` describes.

What a consumer can check is therefore the source and the build's own inputs:
every dependency is resolved by Maven with the repository's checksums, and the
Raposza dependencies and the build plugin are published under `com.raposza` with their own
signatures.


## 11 - Known limitations

These are the things a reviewer would otherwise have to find. Every one is
deliberate for a developer tool on a workstation, except where it says it is
owed.

* **L-1 Downloads are verified by TLS only, and then executed.** The DPM
  archive, the SDK archive and the Splice bundle are fetched, unpacked and run
  with no digest and no signature check - section 2. An archive installed
  from a file the user chooses - DPM, or a Splice bundle - is not checked at
  all. **OWED:** an expected
  SHA-256 per artefact, checked before the `.part` file is moved into place,
  failing closed; and where the vendor publishes no digest, a statement that it
  does not.
* **L-2 The Sandbox's identity provider mints for anyone who can reach it.**
  `/mint` has no authentication by design. It is bound to `127.0.0.1`, so
  "anyone" is every process and every account on this machine - and, because
  Raposza OIDC answers every origin, any web page open in a browser on it.
* **L-3 Every credential is a published test value** - `123456`, `postgres`
  under trust authentication, `unsafe`, `raposza-sandbox-unsafe-shared-secret`.
* **L-4 A Canton 2.x participant binds its Ledger API and Admin API on every
  interface**, and nothing here configures authentication on its Admin API.
  Authentication on its Ledger API is written and has never been measured -
  `README_LONG_TIME.md`'s capability table. Run 2.x on a network you trust.
* **L-5 The discovery document hands out the way in.** It is on loopback and
  unauthenticated, and it publishes the database passwords, a token URL per
  node and, in HMAC mode, the participant's shared secret.
* **L-6 Credentials are written to disk in clear, mostly with the umask's
  permissions** - the admin token, the HMAC secret in the profile, the
  LocalNetND secret and token, a saved Workbench token (owner-only). Section 4.
* **L-7 Plaintext channels by default.** The Workbench uses TLS only when the
  profile asks for it; the Admin API channel is plaintext on loopback.
* **L-8 Some secrets are visible on the process list** - scribe's token, client
  secret and database password, Raposza OIDC's admin password.
* **L-9 LocalNetND's tokens never expire, and its secret is served to the
  browser** in `/config.js` in HS256 mode, as the vendor's UIs require.
* **L-10 Snapshots copy the whole database cluster**, including whatever keys
  Canton keeps in it, unencrypted.
* **L-11 The identity provider the Sandbox runs is chosen by modification
  time** from the local Maven repository, without a digest check - section 1.
* **L-12 `LocalNetProbe` uses a predictable temporary directory**,
  `${java.io.tmpdir}/localnet-probe`.
* **L-13 An external provider's metadata is trusted as returned**: its
  `issuer` is not compared with the URL it was fetched from.
* **L-14 The window's page server forwards to the JSON Ledger API for
  anything that reaches it** on `127.0.0.1:31100`, with the caller's own
  credential - section 3. With the stack unauthenticated, that is an
  unauthenticated ledger on a second port.
* **L-15 The Daml-LF reader is loaded from an installed Canton's jar**, the
  newest open-source 3.5.1 or later on the machine, into both applications,
  without a digest check - section 1.
* **L-16 Scribe is chosen from the DPM cache or the settings and run without a
  digest check**, first with `--version` to read the line it claims, then as
  PQS - section 1.
* **L-17 Netty 4.1.100.Final stays, paired with gRPC 1.60.0**, and one of its
  advisories is reached from the network: a participant the Workbench
  connects to can deny it service with an HTTP/2 `CONTINUATION` flood,
  GHSA-w9fj-cfpg-grvv. Point the Workbench at participants you trust -
  section 9.
* **L-18 The debug log gathers every log line into one file meant to be
  sent** - the test credentials, LocalNetND's token with no expiry, the
  settings file and the proxy and JVM environment variables verbatim, written
  with the umask's permissions - section 6. Read it before sending it.


## 12 - The trust boundary, stated plainly

**INSIDE the boundary - trusted completely, and no control here limits them.**

* The user who runs the Sandbox, and every other process and account on the
  same machine. Loopback is the only boundary the Sandbox draws, and a local
  account crosses it: the discovery document, the identity provider, the
  database under trust authentication, the Canton APIs and the page server in
  front of the JSON Ledger API are all open to it.
* Anyone who can write the state root, the local Maven repository or the
  toolchain directories. They choose what runs - in the processes the Sandbox
  starts and, through the Daml-LF reader, inside both applications.
* The vendor's download hosts and the network path to them, up to TLS.
* On Canton 2.x, anyone who can reach the machine's network interfaces.

**OUTSIDE the boundary - what the controls in this tree actually stop.**

* A machine on the same network reaching the Sandbox's identity provider, its
  discovery document, its page server, its database or a Canton 3.x node -
  each is on loopback,
  by this code or by Canton's and PostgreSQL's own defaults.
* A token or secret reaching the logs through scribe's command line - section 6.
* Another local account reading a Workbench profile that holds a saved token,
  or the CaQL audit log - both owner-only where the file system allows.

**WHAT THIS MEANS FOR A DEPLOYMENT.** There is no deployment. Raposza runs on a
developer's own workstation, as that developer, against test data. Do not put a
Sandbox where another person has an account, do not expose its ports, and do
not point it at a credential you would mind publishing.


## 13 - What this review did not establish

* **Nothing was measured at run time.** No listener enumeration on a running
  stack, no capture of file-system writes across a start and stop, no
  observation of egress. The bind addresses marked "not established" in
  section 3 are the gap that matters most.
* **The dependency check is a lookup, not an analysis.** Each coordinate in
  section 9's transitive closure is looked up in OSV.dev's advisories and in
  this project's own record of them; nothing reads the dependencies' code.
  Whether this code reaches an advisory, in section 9, is read from the
  advisory's own description and from what this code builds; Netty's code,
  the plugins' code and the running provider's registered view resolvers
  were not read.
* **No scanner output exists** - no static analysis, no secret scan of the
  build output.
* **No third party** has reviewed any of it.
