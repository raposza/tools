<!-- Copyright (c) 2026 bentzn -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# caql-core

CaQL parser, AST and semantics. A language, not a tool.

S-2: syntax and AST are major-version neutral; version-specific validation
happens after parse, never in the grammar.

S-3: **only scalar things bind, and reads bind nothing** - except `GET LEDGER
END`, which answers with a scalar. A `LIST` holds a collection, a collection
needs indexing, and indexing is an expression. Binding one is a syntax error
rather than a discarded binding.

S-4: **a comparison takes a literal on one side and nothing computed on the
other.** `ASSERT ... COUNT 3` is the whole of it: no operator, no arithmetic,
no bound operand. Without this written down the next request is `COUNT > $n`
and the one after that is `COUNT > $n + 1`.

S-5: **the dot is a projection, not an operator.** `$r.owner.party` walks
record fields and does nothing else - no index, no call, no arithmetic, and
records only. It is also what makes the grammar predictable enough to
autocomplete: every position resolves from what precedes it.

`EXERCISE ON <target> <choice>` is in that order for the same reason. The
contract gives the template, the template gives the choice set, the choice
gives the type of the `WITH` object - so the statement resolves left to right.
Naming the choice first, as this did until 2026-08-25, means offering choices
before anything knows which template they belong to.
