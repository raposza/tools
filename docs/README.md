<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# Documentation

`architecture.md` is the reference for maintainers: the module map, the two
version axes, the semantic model, the capability declaration, and the
invariants that must not be broken silently.

Start there. Each module also carries a README describing what it holds and
the rules its `pom.xml` enforces.

`security-review.md` is the security posture: what executes, what is
downloaded, what listens, what is written and held, what is logged, what leaves
the machine, and the known limitations. `../SECURITY.md` is its short form and
the reporting channel.
