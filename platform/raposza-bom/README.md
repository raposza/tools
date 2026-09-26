<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# raposza-bom

Bill of materials. Applications will import this to get a coherent set of
platform versions without pinning each one.

**NOT ACTIVE.** The module carries a pom with no `dependencyManagement` and no
source, it is not in the `platform` reactor's `<modules>` list, and no
application imports it. The applications are built by the root development
aggregator today and take their versions from `platform/pom.xml` as their
parent.

It becomes the authoritative statement of which platform component versions go
together on the day the first platform release exists and the applications begin
releasing on their own schedules. Until then it is a placeholder: documentation
describing applications as consuming the platform through the BOM is describing
that day and not this tree.
