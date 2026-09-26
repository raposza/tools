<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# raposza-spi

The translation service-provider interface, and the machine-readable
capability declarations.

`LedgerClient_i` and `LfDecoder_i` live here because a translation target
implements them. `raposza-model` holds what the domain speaks; this holds
what a target must supply. Both forbid SDK types equally - the split is
about direction, not about permission.

A target declares, per capability, one of SUPPORTED, UNSUPPORTED or
UNMEASURED, with the version and date behind any measured claim. That is
what lets a caller tell 'correctly refused because unsupported' from
'supported and broken', and what stops the hand-maintained status table in
a hand-maintained status table being rebuilt in Java. UNMEASURED never
blocks: the gate reports rather than refuses.

Adding Canton 3.6 or 4.x is a new target module plus one line in
`META-INF/services`. It is never a change here or in `raposza-model`.
