<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Build and install

From a clone to a running Sandbox and Workbench, on Linux and on Windows. Every
command runs from the repository root.

Nothing here needs root or an elevated prompt, and nothing is installed outside
your home directory. `SECURITY.md` says what the Sandbox starts and what it
leaves open. Read it before you run the Sandbox on a machine other people use.


## 1 - What you need

| | check with |
| --- | --- |
| JDK 21 | `java -version` |
| Maven 3.9 or later | `mvn -version` |
| Git | `git --version` |

The build resolves its dependencies from Maven Central, so it needs a route
there. The Sandbox runs Canton, which you obtain from Digital Asset, and none of
it is included in this repository. Section 4 covers how the window installs it
for you.


## 2 - Download

```
git clone https://github.com/raposza/tools.git
cd tools
```


## 3 - Build and test

Linux:

```
./build.sh
./test.sh
```

Windows, in PowerShell:

```
mvn install -DskipTests
mvn install -Papp
```

`build.sh` is `mvn install -DskipTests`. `test.sh` is `mvn install -Papp`,
followed by a check that every module with tests actually ran them. The suite
is headless and needs no participant and no Canton.

`-Papp` also builds the two launcher jars, which carry their dependencies:

```
apps/sandbox/target/sandbox-<version>-app.jar
apps/workbench/target/workbench-<version>-app.jar
```


## 4 - Raposza Sandbox

### Start the window

Linux:

```
./run-sandbox.sh
```

`--build` rebuilds the launcher jar first and must be the FIRST argument. The
jar is built once and is NOT rebuilt when the sources change, so pass `--build`
after you change code.

Windows, in PowerShell, after section 3:

```
java -jar (Get-ChildItem apps\sandbox\target\sandbox-*-app.jar).FullName
```

The window first asks what to start:

* **Single participant** - one Canton, one embedded PostgreSQL.
* **LocalNetND** - a Splice network natively: sv, app-provider and app-user.

### Get Canton

A machine with no toolchain shows a banner at the top of the window. Its
**Install** fetches Digital Asset's package manager, DPM, into your home
directory. The **Install** button beside the SDK box then opens a dialog that
installs further SDKs, newest first. On LocalNetND that box lists Splice
bundles, and the same button lists Splice releases and downloads the one you
choose into `~/.splice`.

These downloads are checked by TLS only, and the installers they contain are
then run. `docs/security-review.md` section 2 says exactly what is fetched and
from where.

### Start a stack

Choose an SDK and press **Start**. Each row names the SDK first - the
`sdk-version` of your `daml.yaml` - and then the Canton it runs, which from
SDK 3.5.2 on is a different number. Once the stack is up, the tabs show its state and logs, and it is published for the Workbench on
`127.0.0.1:32001`. Closing the window stops the stack.

### In the terminal instead

```
./run-sandbox.sh --cli                      the defaults
./run-sandbox.sh --cli --canton 3.5.14      that Canton
./run-sandbox.sh --cli --auth JWKS          with an authenticated Ledger API
./run-sandbox.sh --list                     what Canton this machine has
./run-sandbox.sh --help                     every argument
```

On Windows, put the same arguments after the `java -jar` line above. Ctrl-C
stops the stack.

### Windows: where PostgreSQL unpacks

The embedded PostgreSQL unpacks into the temporary directory. If that fails,
the Sandbox tries again in `raposza-tmp` beside the jar. Nothing needs to be
set.


## 5 - Raposza Workbench

Linux:

```
./run.sh
```

`--build` works as it does for the Sandbox.

Windows, in PowerShell, after section 3:

```
java -jar (Get-ChildItem apps\workbench\target\workbench-*-app.jar).FullName
```

The connection dialog offers two things:

* **a local Sandbox** reads the Sandbox's discovery document on `127.0.0.1`,
  port 32001 by default, and opens one tab per participant it lists.
* **a standalone ledger** takes a host, a port, **TLS** and **Allow writes**.
  Allow writes is off by default.


## 6 - Where things are kept

| | Linux | Windows |
| --- | --- | --- |
| settings, profiles, run directories, snapshots, fixtures | `~/.raposza` | `%APPDATA%\raposza` |
| Workbench profiles, the CaQL audit log | `~/.raposza` | `%USERPROFILE%\.raposza` |
| Splice bundles | `~/.splice` | `%USERPROFILE%\.splice` |
| LocalNetND's run directory | `~/.splice/native-localnet` | `%USERPROFILE%\.splice\native-localnet` |

The Sandbox's Settings tab has three more directories at the bottom, for
installations that already exist somewhere else - your own, or a corporate
install directory. Blank means the default:

| setting | points at | default |
| --- | --- | --- |
| DAML Assistant directory | the Daml Assistant's root, holding `bin` and `sdk` | the root of `daml` on `PATH`, else `~/.daml` or `%APPDATA%\daml` |
| DPM directory | DPM's root, holding `bin` and `cache` - or the directory the `dpm` launcher sits in | `DPM_HOME`, then the root of `dpm` on `PATH`, else `~/.dpm` or `%APPDATA%\dpm` |
| Splice directory | where the Splice bundles are, `<directory>/<version>/splice-node` | `~/.splice` or `%USERPROFILE%\.splice` |

A root set there wins over `DPM_HOME` and over `PATH`; a DPM launcher
directory decides only which `dpm` runs. The installers in the window do not follow the DAML Assistant and DPM
directories; a Splice install does land in the Splice directory. The run
directory stays where the table above says.

To remove Raposza, delete the clone and those directories. DPM, the SDK and
Canton are managed by DPM's own tools.
