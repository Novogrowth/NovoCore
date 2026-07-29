# ADR 0014 — Property-based tests written against our own harness, not jqwik

**Date:** 2026-07-29
**Status:** Accepted

## Decision

Express the property-based half of the test suite through a small in-repo harness —
`Gen`, `Property` and `ValueGenerators` in `gr.novotrade.novocore.core.api.testsupport`,
published from `core-api` as a test-jar — rather than depending on **jqwik**.

The harness provides what property-based testing is actually for: generated input,
reproducible runs, and **shrinking** a failure down to the smallest value that still
breaks it. It deliberately does not attempt to be a general-purpose property library.

## Context

Step 13 called for property-based tests on `Money` and on FIFO consumption. jqwik is the
obvious library for that on the JVM, and it cannot be used here.

Spring Boot 4.1.0 manages JUnit Jupiter 6.0.3, whose platform artifacts are versioned
`6.x`. jqwik is a JUnit Platform **test engine**: `net.jqwik:jqwik-engine:1.10.1` — the
newest release as of this step — declares `org.junit.platform:junit-platform-engine:1.14.4`,
and there is no jqwik 2. Verified against Maven Central rather than assumed.

**This is the third time the same situation has arisen and it is being resolved the same
way each time.** ADR 0002 rejected `archunit-junit5` for exactly this reason and uses the
plain `archunit` library. Step 11 rejected `greenmail-junit5` for exactly this reason and
uses plain `greenmail`. The pattern is now explicit: *take the idea, not the artifact,
when the artifact is a JUnit Platform 1.x engine.*

The difference here is that jqwik has no "plain library" form to fall back on — the engine
**is** the product. So the choice was between pinning the whole reactor's test platform
backwards to accommodate one library, or writing the part of it we need. The part we need
is a few hundred lines.

## What the harness does and does not do

- `Gen<T>` is `sample(RandomGenerator)` plus `shrink(T)`. Every generator states its own
  shrinks; there is deliberately **no `map` combinator**, because a mapped value cannot be
  turned back into what it came from and `map` would therefore silently produce generators
  that cannot shrink.
- `Property.forAll` runs 500 cases; `Property.forAllScenarios` runs 20, for properties whose
  each case is a database round trip. A failure is shrunk greedily and reported with the
  smallest failing value, the value originally generated, and the seed to reproduce it.
- A `Throwable` that is not an `AssertionError` counts as a failure. **Consequence: a JUnit
  assumption must never be used inside a property**, since `TestAbortedException` is
  indistinguishable from a real failure. Narrow the input with a generator instead.
- No parallel execution, no statistics reporting, no `@Provide`/`@ForAll` annotations, no
  edge-case exhaustion, no stateful/model-based testing. If any of those turn out to be
  needed, that is the moment to revisit this decision rather than to grow the harness.

## The seed is fixed by default, and that is the load-bearing trade-off

`Property` runs from a constant seed unless `-Dnovocore.property.seed=<n>` says otherwise.
So the same 500 cases run on every machine and every CI run, and **a red build always means
a real defect** rather than today's dice.

The cost is real: a fixed sample explores less over time than a reseeding one, and is
closer to 500 parameterised tests than to property testing in the purest sense. It is
accepted for the reason `CLAUDE.md` already gives about the self-invocation rules — *a
check that cries wolf is one somebody eventually deletes* — and because a build gate that
can go red on a commit that changed nothing destroys trust in the whole suite, not just in
the property that flickered.

What buys the breadth back is the **generators**, not the runner: `ValueGenerators` draws
roughly a third of every sample from a hand-written edge list (zero, one cent, the scale
limit, a rounding midpoint, a value one unit either side of it) and picks a fresh order of
magnitude per sample rather than sampling uniformly from one huge range. Everything that
has ever gone wrong with these types lives at those boundaries, and a uniformly random
twelve-digit decimal essentially never lands on one.

Exploring with a new seed is a deliberate act. **Anything a new seed finds should be pinned
here as a named example-based test**, not left to be rediscovered by luck.

## The harness is itself proven to fail

`PropertyTest` is not optional decoration. Everything else in this package checks that
other code is correct, so a broken harness produces a suite that passes and proves nothing
— the exact shape of the `..core.web..` ArchUnit rule that carried `allowEmptyShould(true)`
and checked nothing for two build steps. `PropertyTest` proves that a false property is
reported, that a non-assertion exception counts as a failure, that the value reported is
the *shrunk* one, and that generation is reproducible.

Writing it caught a real weakness immediately: the first shrinker offered "half" and "one
unit closer to zero" and nothing between, which is a binary search that gives up after one
step. A property failing above 1000 shrank 12345 to 1543 in four rounds and then crawled
down by 0.01 until the round limit stopped it, reporting a number nobody would recognise as
the boundary. The halving *ladder* that replaced it converges in tens of rounds.

## Consequences

- `core-api` now publishes a test-jar, as `core` already did. The harness is defined once,
  and the FIFO properties in `core` use the same generators and the same shrinking as the
  `Money` properties in `core-api`.
- The harness lives in `core-api` rather than `core` because that module deliberately has
  no Spring on its test classpath, which keeps the harness free of it too.
- Property tests and example tests coexist and have different jobs. `MoneyTest` states what
  `Money` does, one worked example at a time, and stays the right place to read.
  `MoneyPropertiesTest` states what must remain true of every amount. Neither replaces the
  other, and a finding from the second belongs in the first.
- Upgrading JUnit or Spring Boot cannot be blocked by this, which is the same benefit
  ADR 0002 bought.
