<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Author Claude/bentzn  Generated 2026-10-12T07:03:15Z  Updated 2026-10-12T10:45:00Z -->
# First installation — Linux and Windows

From a clean machine to a running Sandbox and Workbench. The versions below are the ones this release is built and tested with:

| | version |
| --- | --- |
| Java | 21 or newer |
| Daml SDK | 3.6.0 |
| Splice | 0.8.3 |
| Raposza | 0.6.0 |

Linux commands run in a shell; Windows commands run in PowerShell. On Windows the DPM installer asks for administrator rights; nothing else does, on either platform.

---

## 1. Verify Java

Java 21 or newer must be installed and available on `PATH`.

Linux and Windows:

```
java -version
```

---

## 2. Install DPM and the Daml SDK

DPM is Digital Asset's package manager for the Daml SDK. It installs the Daml SDK with it. It is a command-line tool, and there is no daemon to start.

Linux:

```
curl -fsSL https://get.digitalasset.com/install/install.sh -o install-dpm.sh
sh install-dpm.sh
export PATH="$HOME/.dpm/bin:$PATH"
dpm version
```

Windows:

1. Open [docs.canton.network/sdks-tools/cli-tools/dpm](https://docs.canton.network/sdks-tools/cli-tools/dpm).
2. Under **Windows Installation**, click the **windows installer** link. It downloads `dpm-3.6.0-windows-amd64.exe`, about 1.2 GB.
3. Edge warns that the file is not commonly downloaded. Open the arrow next to **Delete** and choose **Keep anyway**.
4. Run `dpm-3.6.0-windows-amd64.exe` from your Downloads folder and answer **Yes** when Windows asks for administrator rights.
5. Open a **new** PowerShell window, so that it picks up DPM on `PATH`, and check:

```
dpm version
```

---

## 3. Install Splice

Splice 0.8.3 from [Digital Asset's Splice releases](https://github.com/digital-asset/decentralized-canton-sync/releases), unpacked where the Sandbox looks for it: `<splice directory>/<version>/splice-node`. The download is about 800 MB.

Linux, into `~/.splice/0.8.3/splice-node`:

```
SPLICE_VERSION=0.8.3
mkdir -p "$HOME/.splice/$SPLICE_VERSION"
curl -fL -o "/tmp/${SPLICE_VERSION}_splice-node.tar.gz" \
  "https://github.com/digital-asset/decentralized-canton-sync/releases/download/v${SPLICE_VERSION}/${SPLICE_VERSION}_splice-node.tar.gz"
tar -xzf "/tmp/${SPLICE_VERSION}_splice-node.tar.gz" -C "$HOME/.splice/$SPLICE_VERSION"
```

Windows, into `%USERPROFILE%\.splice\0.8.3\splice-node`:

```
$ProgressPreference = 'SilentlyContinue'
$s = "0.8.3"
New-Item -ItemType Directory -Force "$env:USERPROFILE\.splice\$s" | Out-Null
Invoke-WebRequest "https://github.com/digital-asset/decentralized-canton-sync/releases/download/v$s/${s}_splice-node.tar.gz" -OutFile "$env:TEMP\${s}_splice-node.tar.gz"
tar -xzf "$env:TEMP\${s}_splice-node.tar.gz" -C "$env:USERPROFILE\.splice\$s"
```

---

## 4. Download Raposza

Download `sandbox-0.6.0-app.jar`, `workbench-0.6.0-app.jar` and `SHA256SUMS` from the [Raposza 0.6.0 release](https://github.com/raposza/tools/releases/tag/v0.6.0) into your Downloads folder. On Windows, Edge warns about each jar: choose **Keep file**.

Verify them.

Linux:

```
cd ~/Downloads
sha256sum -c SHA256SUMS
```

Windows:

```
cd $env:USERPROFILE\Downloads
Get-Content SHA256SUMS | ForEach-Object { $h, $f = $_ -split '\s+', 2; if ((Get-FileHash $f -Algorithm SHA256).Hash -eq $h) { "${f}: OK" } else { "${f}: FAILED" } }
```

Both jars must report `OK`.

---

## 5. Start the Sandbox with the Aviation fixture

From the Downloads folder, Linux and Windows:

```
java -jar sandbox-0.6.0-app.jar
```

1. Choose **Single participant**.
2. Answer **Yes** to **Install Aviation test fixture?**

   The Sandbox builds the test fixture, takes a founding snapshot, and starts with the Aviation data.
3. Wait until the log says **Aviation data created, system running**. Then press **Save snapshot** and name it `Aviation`.

   The Sandbox stops and saves the snapshot.
4. Select **Aviation** under **Snapshots**, if it is not already selected, and press **Start**.

The ledger now holds the Aviation data, and every start from the `Aviation` snapshot gives you the same ledger again.

---

## 6. Start the Workbench

In a second terminal or PowerShell window, from the Downloads folder:

```
java -jar workbench-0.6.0-app.jar
```

1. Choose **a local Sandbox**, leave the default port and press **OK**.
2. **work as** shows `participant_admin`, a user that can see users and parties.
3. Select **superuser** in **work as**. It shows no users and parties, but the templates and contracts are visible: the aircraft, their components and their defects.
4. Open the **CaQL** tab. **Ctrl+Enter** runs one line. To list parties and users, work as `participant_admin` again. For everything else, work as `superuser`, or as another user to test its privileges.
5. **Skills** is a reference of CaQL that you can give to an LLM to help it write CaQL statements.

Seeing the Aviation contracts confirms more than two windows opening: it confirms DAR loading, party allocation, contract creation and Ledger API access.

---

## 7. Run LocalNetND

1. Close the Sandbox window. Closing it stops the stack.
2. Start the Sandbox again, as in step 5.
3. Choose **LocalNetND**.
4. Choose Splice **0.8.3** in the version box.
5. Press **Start**.
6. Answer **Yes** to **Install Pharma test fixture?**

It is ready when it reports **Pharma data created, system running**. Reconnect the Workbench as in step 6. It opens one tab per participant, `app-provider`, `app-user` and `sv`, and each tab shows what that participant sees.

---

Building from source: [BUILD_AND_INSTALL.md](BUILD_AND_INSTALL.md). Where Raposza keeps its files: [BUILD_AND_INSTALL.md](BUILD_AND_INSTALL.md), section 6. Before running the Sandbox on a shared machine, read [SECURITY.md](SECURITY.md).
