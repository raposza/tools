<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# raposza-target-3x

Translation implementation for Canton 3.4 and 3.5, over Ledger API v2.

**A registered stub.** `Target3x` is discoverable through `ServiceLoader` and
its `connect()` throws, so a Canton 3.x participant is out of reach of the
client stack. The methods behind it throw rather than returning an empty result,
because an empty result is indistinguishable from a ledger with nothing on it.
The Sandbox reaches 3.x by other means and does not go through this module.

SDK types permitted.
