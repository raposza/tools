<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# raposza-target-3x

Translation implementation for Canton 3.4 and 3.5, over Ledger API v2.

`Target3x` is discoverable through `ServiceLoader`, and its `connect()`
returns `Lapi2Client`, the Ledger API v2 client. What a 3.x participant cannot
do is refused by name - `UnsupportedCapability` - rather than answered with an
empty result, because an empty result is indistinguishable from a ledger with
nothing on it. The module has no tests.

SDK types permitted.
