<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Security

## Reporting a vulnerability

Write to **info@raposza.com**. Say what you found, how to reproduce it, and
what you think it lets an attacker do. You will get an acknowledgement; if the
finding is valid you will be told what is being done about it and when the fix
lands.

Please do not open a public issue for a vulnerability before it is fixed.

There is no bug bounty.

## What is in scope

This repository: Raposza Sandbox (`apps/sandbox`), Raposza Workbench
(`apps/workbench`), the libraries under `platform/`, and the shell scripts at
the root.

**Not in scope:**

* **Canton, scribe, the Daml SDK, DPM and the Splice bundle.** They are Digital
  Asset's, you obtain them, and nothing from them is redistributed here. Report
  anything you find in them to their maintainers.
* **Raposza OIDC**, the identity provider the Sandbox starts. It has its own
  repository, `github.com/raposza/OIDC`, its own `SECURITY.md` and its own
  review. The Sandbox carries a copy of its jar and decides how it is started;
  that part IS in scope here and is described in `docs/security-review.md`.

## Supported versions

Only the current tip of the default branch. There are no maintenance branches
and no backports; a fix arrives as a new version.

## When this was last reviewed

**Reviewed 2026-09-27 for 0.4.1**, against the tree as it then stood -
`docs/security-review.md` carries the result. The first review was
2026-09-25, the second 2026-09-26 for 0.4.0.

## The short statement of the model

**Raposza is developer tooling for a workstation and it is not hardened.** The
Sandbox starts a Canton stack, a database and an identity provider as ordinary
processes of the user who runs it, with test credentials, so that a developer
can build against them. Nothing it starts is meant to be reachable from a
network anyone else is on. Five consequences you should read before running it:

* **Every credential the Sandbox creates is a published test credential.** Web
  UI users and the identity provider's admin use the password `123456`; the
  embedded PostgreSQL runs trust authentication as `postgres`/`postgres`;
  LocalNetND signs its tokens with HS256 over the literal secret `unsafe`, with
  no expiry. None of them is a secret and none of them should guard anything
  you care about.
* **The Sandbox window starts an identity provider that mints a token for any
  subject on request, without authentication.** It listens on `127.0.0.1` only.
  Anything that can open a connection to that port on your machine - another
  local account, a browser page you visit - can obtain a token the participant
  accepts.
* **The Ledger API is unauthenticated unless you ask for authentication.** The
  window's default is JWKS against that provider; `--cli` without `--auth` is
  unauthenticated. A Canton 2.x participant started by the Sandbox binds its
  Ledger API and its Admin API on EVERY interface, and nothing here configures
  authentication on its Admin API.
* **Artefacts the Sandbox downloads are checked by TLS and nothing else, and
  then executed.** The toolchain installer, the SDK installer and the Splice
  bundle are fetched on a button press, unpacked and run. No digest or
  signature is verified.
* **Test data, tokens and database passwords are written to disk in clear**,
  under `~/.raposza` and `~/.splice`, mostly with your umask's permissions.

`docs/security-review.md` is the full posture document: what executes, what is
downloaded and how its integrity is established, what listens and with what
authentication, what is written to disk, what key material is held, what
leaves the machine, and the limitations that follow.
