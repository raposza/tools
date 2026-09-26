<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Platform

Shared modules, released together under one version.

## Boundary rule S-1

Vendor SDK types - Canton, Daml-LF, protobuf and gRPC - are confined to the
modules whose job is translating between a vendor API and this project's own
model. They must not leak into the core model or into any ordinary
application-facing API.

The modules where an SDK dependency is intentionally allowed, and why:

| Module | Why |
| --- | --- |
| `raposza-lf` | reads the Daml-LF archive and maps its AST |
| `raposza-wire` | protobuf `Value` conversion, both directions; no transport |
| `raposza-admin` | the Canton admin API, spoken through descriptors; wire plus one transport |
| `raposza-target-2x` | Ledger API v1 client |
| `raposza-target-3x` | Ledger API v2 client |

Every other module here depends on `raposza-model` and `raposza-spi` and
never on an SDK type.

This is a build failure and not a convention. maven-enforcer
`bannedDependencies` in `platform/pom.xml` bans the four groups, and each of the
five modules above skips the rule in its own `pom.xml`, so the exemption list is
visible where it applies rather than gathered in one place nobody reads. The
rule does not search transitively; an application depends on a target at runtime
scope and `bannedDependencies` has no scope filter.
