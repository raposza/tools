<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# workbench

Explore and act on a Canton participant node from one window.

Confirming that something happened on a participant, or trying out a choice,
normally costs a chain of tool switches: the Canton console for admin state,
PQS and SQL for history, grpcurl or curl for the Ledger API, a JWT minted
somewhere else first, and a script for anything that submits.

Raposza Workbench collapses that into two panes.

**Explore.** One search box. Paste a contract id, update id, command id, party
id, template name or ledger offset; the tool works out what it is and shows the
object, its payload with field names, and the transaction that produced it.

**Act.** Pick a contract, see its choices, fill the arguments in typed widgets,
submit as a chosen party, and see the resulting transaction tree in the same
window. A ledger rejection is shown with its Canton error code, because that is
usually the answer you came for.

## Status

Explore works against a Canton 2.x participant over Ledger API v1, authenticated
or not. It reads the participant's version and identity, its parties and users,
the active contract set, single contracts and transaction trees, with field
labels at every nesting depth. The window lists users, parties, the templates
seen on the ledger and the contracts under them, and resolves a pasted contract
id, transaction id or party id - selecting what it found in the navigator and in
the contract table.

The contract table shows template, contract id, signatories and observers for the
contracts that were read, with cumulative multi-column sorting, a filter per
column and a search box. Party columns show display names where the participant
reports one and match on either the name or the party id. Every control there
operates on what was loaded and nothing in it calls the participant; the row
count and the read cap are stated above the rows.

Daml-LF decoding works. On connect the tool fetches every package the
participant hosts, decodes it and caches it, so it holds the template
signatures, the choices on each template - including the ones a template gains
from the interfaces it implements - and the data types behind them. The status
bar states how many templates are KNOWN alongside how many were SEEN: seen comes
from the contracts that were read, known from the packages, and the two are
different claims. Packages are cached under `~/.raposza/packages/`, keyed by a
content hash, so only the first connect pays for the fetch.

Choice CONTROLLERS are not decoded. On Canton 2.x the controller expressions are
not present in the archive at all, so who may exercise a choice is a question
only the participant can answer.

Submission is BUILT and runs, and it is not in the window. The execution layer
commits a command, keeps the Canton error code on a rejection rather than the
gRPC one, and reports an outcome as unknown when the deadline expires before
the completion arrives. CaQL drives it headless today. What does not exist is
the Act PANE: the choice list is not on screen, so the signatures are held and
not shown, and there is no form to fill in or submit from.

Ledger API v2 IS there: `raposza-target-3x` resolves to `Lapi2Client` and a
Canton 3.x participant is reached the same way a 2.x one is. Two capabilities
are declared unsupported on 3.4.4 - 3.5.12 - `UPDATE_BY_EVENT_ID` and
`INTERFACE_FILTER` - and the methods behind those throw rather than returning
an empty result, because an empty result is indistinguishable from a ledger
with nothing on it.

## The headless CaQL runner

The same jar runs a CaQL script without the window:

```
workbench --script FILE --transcript FILE [--profile NAME] [--validate]
        [--catalogue FILE]
```

`--transcript` has no default on purpose: it carries the payloads the script
wrote, so the tool does not choose where it lands.

## Authentication

A participant that requires a JWT is reached in one of three ways, chosen per
profile:

* **Nothing.** No authorization header is sent. This is the default and what a
  local sandbox wants.
* **A pasted token.** Press the auth button, paste a JWT, connect. It lasts for
  the session, and for that participant only - selecting another profile drops
  it.
* **A settings file.** `~/.raposza/profiles/<profile>.properties`, named after a
  lower-case slug of the profile's display name:

```
auth           = none | token | jwks
token          = the bearer token                 auth=token
jwks.file      = /path/to/private-jwks.json       auth=jwks
jwks.kid       = key id, else the first private key
subject        = the sub claim, normally the ledger user id
scope          = overrides the catalogue's scope
audience       = overrides the catalogue's audience
act.as         = comma separated, custom-claim tokens only
read.as        = comma separated, custom-claim tokens only
admin          = true | false, custom-claim tokens only
application.id = the applicationId claim, custom-claim tokens only
ledger.id      = the ledgerId claim, custom-claim tokens only
participant.id = the participantId claim, custom-claim tokens only
ttl.seconds    = lifetime of a MINTED token, default 1000 years
```

**`application.id` matters only when you submit, and then it is mandatory.**
Canton will not default a command's application id from a claim that does not
carry one, and says so as an argument error on the submission rather than as
anything about the token. Reads never touch it, so a profile that works all day
can fail on its first command.

Scope and audience are mutually exclusive; a profile carrying both is refused
rather than resolved by precedence, because a participant accepts one shape and
presenting the wrong one fails in a way that reads like a key problem.

The settings file holds a credential. Keep it out of any repository, and note
that saving a token from the window sets owner-only permissions where the
filesystem supports it. The identity in use, and its expiry, are always shown on
the status bar.

**Removing a credential.** The auth dialog clears either one. *Clear pasted
token* drops the session token only. *Delete stored token* removes it from the
settings file, after naming the file and asking: the mode reverts to `none`,
because a file declaring `auth=token` without a token is an error rather than a
fall back to anonymous; other settings are kept; and a file left holding nothing
but `auth=none` is deleted outright. This is not a secure erase, and a token
that has been on disk should be revoked at its issuer rather than merely
deleted.

**What one token can do.** `admin` reaches the party list, the user list and the
participant id. `readAs` and `actAs` reach contracts. They are separate rights
and one credential rarely holds both, so sections of the window are expected to
refuse individually and say why on the section rather than emptying the tree.
When a participant will not list its parties, the picker is populated from the
token's own `readAs` and `actAs` instead - the window says so when it does.

## Live tests

The default suite is headless. A live subset is opt-in:

```
RAPOSZA_IT_PORT=6865 ./test.sh
```

**Those tests need a participant that demands no token.** They establish their
own fixture by shelling out to `daml`, which is given no credential, so a
participant configured with `auth-services` refuses the setup and the run fails
while building the fixture - which reads as a broken test rather than as the
wrong sandbox.

They do not need a fresh one. The fixture allocates its own parties per run and
every assertion looks at one contract of the expected shape rather than at the
size of the active contract set, so a ledger it does not own is fine.

The environment variable still carries the old project name. Renaming it is a
change to anyone's shell profile, so it is deliberate and pending, not
forgotten.
