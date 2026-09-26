<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Applications

Three applications - `sandbox`, `jwtmint` and `workbench`. There is no unified
launcher and no application containing all three.

**Today they build as one reactor.** The root `pom.xml` is a development
aggregator and lists `platform`, `apps/sandbox` and `apps/workbench`; each
inherits its dependency management from there. Nothing has been released, and
an application cannot import a bill of materials that has never been published.

**The intended release structure is different.** Each application becomes an
independent Maven build with its own version and its own release schedule,
drops the platform parent, and consumes released platform artefacts through
`raposza-bom`. That has not happened; see `platform/raposza-bom/README.md`
for what the BOM does and does not do today.
