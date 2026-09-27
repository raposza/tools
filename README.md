<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Raposza Tools

Developer tooling for Canton and Splice.

Raposza provides a local environment for running, inspecting and testing Canton applications — including a complete Splice LocalNet without Docker or other containers.

It currently consists of three closely integrated tools:

* **Sandbox Simple** — runs a local Canton stack as ordinary processes, with a participant, PostgreSQL and supporting services.
* **Sandbox LocalNetND** — runs a complete Splice LocalNet as ordinary processes, without containers.
* **Workbench** — explores a running ledger and executes CaQL, Raposza's declarative language for creating, exercising and querying ledger data.

Raposza is intended for local development, integration testing and experimentation, including restricted enterprise environments where Docker is unavailable, inconvenient or prohibited.

> **Status:** Early-stage software. Capabilities are documented together with what has actually been tested and against which Canton/Splice versions. See [README_LONG_TIME.md](README_LONG_TIME.md) for the full technical description.

---

## You need

* Java 21
* A Canton/Daml installation
* For LocalNetND: the corresponding Splice distribution and its runtime dependencies

Raposza does not require Docker for Sandbox Simple or LocalNetND.

---

## Download

Download a release from the [GitHub Releases page](https://github.com/raposza/tools/releases) and unpack it.

Each release carries `sandbox-<version>-app.jar`, `workbench-<version>-app.jar` and `SHA256SUMS`. Start either with `java -jar`.

Or clone the repository:

```
git clone https://github.com/raposza/tools.git
cd tools
```

---

## Build

Build the project using the build instructions for your platform: [BUILD_AND_INSTALL.md](BUILD_AND_INSTALL.md), section 3.

See [README_LONG_TIME.md](README_LONG_TIME.md) for detailed build, configuration and compatibility information.

---

## Try it

### Sandbox Simple

Start a local Canton development environment:

```
./run-sandbox.sh --build
```

Choose **Single participant**, then press **Start**.

Sandbox starts the required processes and gives you a single place to see what is running, inspect logs and manage the local ledger environment.

A start runs in this order:

1. Create the test fixture, if selected.
2. Create the founding snapshot, which restarts the stack.
3. Save the snapshot, if selected.

Use Simple when you want a fast local Canton participant without bringing up an entire Splice network.

![The Sandbox running one participant with the Aviation test fixture](docs/images/sandbox-running-aviation.png)

### Sandbox LocalNetND

Start a complete local Splice network:

```
./run-sandbox.sh --build
```

Choose **LocalNetND**, then press **Start**.

LocalNetND means LocalNet — No Docker.

The Splice components run as ordinary local processes rather than containers.

This is particularly useful when developing in environments where Docker cannot be installed or used.

![LocalNetND web UIs and endpoints](docs/images/sandbox-oidc-web-uis.png)

### Workbench

Start Workbench against the running ledger:

```
./run.sh --build
```

Choose **a local Sandbox**.

Workbench lets you inspect parties, packages, contracts and ledger activity.

It also includes CaQL.

![Connect to a local Sandbox](docs/images/workbench-connect.png)

---

## CaQL

CaQL is Raposza's declarative ledger fixture and query language.

It can be used to allocate parties, create contracts, exercise choices and query ledger state without writing a Daml Script or application solely to populate a development ledger.

For example, abridged from the script the Workbench writes for the Aviation test fixture:

```
trainee = ALLOCATE PARTY "Trainee";

AS <maintenance control> QUERY Main:Defect WHERE grounded = true;

defect = AS <maintenance control>
    CREATE Main:Defect WITH { ... };

withEvidence = AS <technician>
    EXERCISE ON $defect AddEvidence WITH { "evidenceNew": ... };
```

CaQL is intended for repeatable development fixtures, demonstrations, integration tests and ledger experimentation.

![CaQL on LocalNetND, Pharma fixture](docs/images/workbench-caql-pharma.png)

---

## Snapshots

Sandbox can capture and restore complete local development environments.

A snapshot allows you to return to a known ledger state instead of rebuilding the environment and replaying setup steps every time. A snapshot can only be restored by the version that took it.

This is useful for:

* reproducible demonstrations
* test fixtures
* debugging
* regression testing
* switching between development scenarios

---

## Multiple Canton versions

Raposza is designed to work across multiple Canton/Daml versions rather than assuming a single installed SDK.

The exact versions and capabilities that have been exercised are documented in [README_LONG_TIME.md](README_LONG_TIME.md).

The distinction is deliberate: supported means tested, not merely expected to work.

---

## Screenshots

| Sandbox | Workbench |
| --- | --- |
| [![What the Sandbox starts](docs/images/sandbox-topology.png)](docs/images/sandbox-topology.png)<br>**Sandbox Simple** — what the Sandbox starts | [![A contract and its activity](docs/images/workbench-contract-detail.png)](docs/images/workbench-contract-detail.png)<br>**Workbench** — a contract and its activity |
| [![The Aviation test fixture offered](docs/images/sandbox-fixture-prompt.png)](docs/images/sandbox-fixture-prompt.png)<br>**Test fixtures** — Aviation test fixture offered at startup | [![A template seen on the ledger](docs/images/workbench-template-detail.png)](docs/images/workbench-template-detail.png)<br>A template seen on the ledger |
| [![DARs uploaded at start](docs/images/sandbox-dars.png)](docs/images/sandbox-dars.png)<br>**DAR management** — DARs uploaded at start | |
| [![LocalNetND running](docs/images/sandbox-localnetnd-running.png)](docs/images/sandbox-localnetnd-running.png)<br>**LocalNetND** — a complete Splice LocalNet running without containers | |

---

## What Raposza is — and isn't

Raposza is development and testing tooling around Canton and Splice.

It does not replace Canton, Splice or Daml tooling supplied by their respective maintainers. It provides a development environment around them intended to make local execution, inspection, testing and experimentation easier.

Raposza does not redistribute Canton or Splice.

---

## More

[README_LONG_TIME.md](README_LONG_TIME.md) contains the detailed documentation, including:

* architecture
* configuration
* tested Canton/Daml versions
* Splice compatibility
* Sandbox capabilities
* LocalNetND
* Workbench
* CaQL
* known limitations

Before you run it on a shared machine, read [SECURITY.md](SECURITY.md).

---

## License

See [LICENSE](LICENSE).
