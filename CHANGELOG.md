<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Changelog

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
