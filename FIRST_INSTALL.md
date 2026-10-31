<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Author Claude/bentzn  Generated 2026-10-12T07:03:15Z  Updated 2026-10-26T18:45:00Z -->
# First installation — Linux and Windows

From a clean machine to a running Sandbox and Workbench. You install Java and download Raposza; the Sandbox downloads and installs DPM, the Daml SDK and Splice itself. The versions below are the ones this release is built and tested with:

| | version |
| --- | --- |
| Java | 21 or newer |
| Daml SDK | 3.6.3 |
| Splice | 0.9.4 |
| Raposza | 0.6.3 |

Linux commands run in a shell; Windows commands run in PowerShell. Nothing asks for administrator rights, on either platform.

If you already have DPM installed in an alternative directory, you can tweak the config in Sandbox 'Settings'.

If your network blocks downloads, fetch the files by hand and install them from file: the DPM archive with **Install DPM from file...** (step 3), and a Splice version with **Install from file...** in the **Install Splice** dialog (step 5). The Splice file must keep the vendor's name, `<version>_splice-node.tar.gz`.

---

## 1. Verify Java

Java 21 or newer must be installed and available on `PATH`. If it is not, download it from [oracle.com/java/technologies/downloads](https://www.oracle.com/java/technologies/downloads/).

Linux and Windows:

```
java -version
```

---

## 2. Download Raposza

Download 3 files: `sandbox-0.6.3-app.jar`, `workbench-0.6.3-app.jar` and `SHA256SUMS` from the [Raposza 0.6.3 release](https://github.com/raposza/tools/releases/tag/v0.6.3) into your Downloads folder.

On Windows, Edge warns about each jar: choose **Keep file**.

Check the hashcode:

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

Both jars must report `OK`.

---

## 3. Start the Sandbox with one participant

Linux:

```
cd ~/Downloads
java -jar sandbox-0.6.3-app.jar
```

Windows - PowerShell:

```
cd $env:USERPROFILE\Downloads
java -jar sandbox-0.6.3-app.jar
```

1. Choose **Single participant**.
2. The first time, the Sandbox checks the machine and shows what it found, starting with **First start - this is what Raposza found on this machine.** On a clean machine it finds no DPM, no Daml SDK and no Splice, and offers **Download DPM**, **Install DPM from file...** and **Close**.

   Press **Download DPM**. The Sandbox downloads DPM, Digital Asset's package manager, and installs it with the Daml SDK it comes with. The download is about 1.2 GB and takes a while. When the log says **Installed DPM with DAML SDK**, followed by the version, the **SDK** box shows that version.

   If your network blocks the download, download `dpm-3.6.3-linux-amd64.tar.gz` or `dpm-3.6.3-windows-amd64.zip` from `https://get.digitalasset.com/install/dpm-sdk/` on a machine that can, and press **Install DPM from file...** instead. It installs with no network.

   The check can be run again at any time: **Settings** tab, **Check this machine**.
3. Open the **Settings** tab and press **Create Aviation test data**.

   The Sandbox builds the test fixture, takes a founding snapshot, and starts with the Aviation data. It starts and stops a few times. This is expected.
4. All lights will turn green. But wait until the log says **Aviation data created, system running**.

The ledger now holds the Aviation data.

---

## 4. Start the Workbench

In a second terminal or PowerShell window.

Linux:

```
cd ~/Downloads
java -jar workbench-0.6.3-app.jar
```

Windows - PowerShell:

```
cd $env:USERPROFILE\Downloads
java -jar workbench-0.6.3-app.jar
```

1. Choose **a local Sandbox**, leave the default port and press **OK**. It takes a few seconds for the data to load.
2. **work as** shows `participant_admin`, a user that can see users and parties.
3. Select **superuser** in **work as**. It shows no users and parties, but the templates and contracts are visible: the aircraft, their components and their defects.
4. Open the **CaQL** tab. **Ctrl+Enter** runs one line. To create and list parties and users, work as `participant_admin` again. For everything else, work as `superuser`, or as another user to test its privileges.
5. **Skills** is a reference of CaQL that you can give to an LLM to help it write CaQL statements.

---

## 5. Start Sandbox with LocalNetND

ND = No Docker

Prerequisite: DPM must be installed. See [3. Start the Sandbox with one participant](#3-start-the-sandbox-with-one-participant).

1. Close both windows.
2. Start the Sandbox again, as in step 3. The check does not come up again.
3. Choose **LocalNetND**.
4. The **Splice** box is empty. Press **Install** beside it. The **Install Splice** dialog takes a moment to load the Splice versions; it lists 0.8 and later.
5. Press **Install** on 0.9.4, about 800 MB. The dialog closes when Splice is installed, and the **Splice** box shows the version.

   Splice 0.9.4 runs on the Canton 3.6 that DPM installed in step 3. A Splice version whose Canton line is not installed offers it when it starts: **Download** installs it through DPM, **Install from file...** from a DPM archive.
6. Open the **Settings** tab and press **Create Pharma test data**.

   The Sandbox builds the test fixture and starts LocalNetND with the Pharma data.
7. Wait until the log says `HH:mm:ss  Pharma data created, system running`.

Start the Workbench again, in a second terminal or PowerShell window.

Linux:

```
cd ~/Downloads
java -jar workbench-0.6.3-app.jar
```

Windows - PowerShell:

```
cd $env:USERPROFILE\Downloads
java -jar workbench-0.6.3-app.jar
```

Connect as in step 4. It opens one tab per participant, `app-provider`, `app-user` and `sv`, and each tab shows what that participant sees.

---

## If something goes wrong

Every Sandbox window writes one debug log with everything it does. The console prints its path when the window opens, as `debug log:`. The files are in `~/.raposza/logs` on Linux and `%APPDATA%\raposza\logs` on Windows; the newest ten are kept.

Attach that file to a bug report at [github.com/raposza/tools/issues](https://github.com/raposza/tools/issues). It holds the Sandbox's test credentials and your proxy and Java environment variables as they are set; if a proxy password is among them, remove it before you send the file.

---

Building from source: [BUILD_AND_INSTALL.md](BUILD_AND_INSTALL.md). Where Raposza keeps its files: [BUILD_AND_INSTALL.md](BUILD_AND_INSTALL.md), section 6. Before running the Sandbox on a shared machine, read [SECURITY.md](SECURITY.md).
