<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# raposza-model

Project-owned domain types: the LF and contract semantic core.

No SDK types. No dependency on any target module, and none on
`raposza-spi` either - the direction is one way, and that is what the
split is for. This is the type vocabulary everything above the
translation layer speaks.

`LedgerClient_i` and `LfDecoder_i` live in `raposza-spi` rather than here.
They are contracts a translation target implements, not types the domain
speaks, and holding them here would make this module - which must never change
when a Canton version is added - look like a module that would.

Interfaces that stayed, and why: `Resolver_i`, `RefProbe_i`,
`TypeRegistry_i` and `PackageCache_i` are domain services. Their
implementations differ per generation; their contracts do not. Where a
generation disagrees, that is a declared capability, never a branch.
