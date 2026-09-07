# CLAUDE.md

Instructions for a coding agent working in this repository. Rename this file for whichever tool you
use; nothing in it is tool-specific except the filename.

Written in the imperative, addressed to the agent. Everything here is a convention the build already
enforces, restated in prose so the agent does not have to discover each rule by failing.

## What this repository is

A quality harness. The `harness-rules` module holds reusable ArchUnit rule packs; the two sample
services exist to exercise the gates. Changes that add business features to the samples are almost
certainly wrong. Changes that make a gate sharper, or that add a rule with fixtures proving it
fires, are the point.

## Build and test

```bash
mvn verify                    # all gates
mvn -Pmutation verify         # adds PIT
mvn -pl harness-rules test    # one module
mvn -Pmutation verify -pl sample-ledger-service   # one module's mutation score
```

JDK 21. No Docker required.

## The rules you cannot route around

These fail the build. Do not attempt to satisfy them by weakening the gate.

1. **Line coverage is 100% per package.** If a line cannot be covered, the answer is to remove the
   line, not to lower the floor and not to write a test that executes it without asserting anything.
   `coverage.line.minimum` moves upward only.
2. **Mutation score is at least 85%.** A test that executes code without constraining its behaviour
   will pass coverage and fail here. Assert on exact values and exact messages.
3. **Checkstyle and SpotBugs are clean.** Adding an entry to `spotbugs-exclude.xml` is a change to
   the gate itself. Propose it, with the reason inline, rather than doing it silently.
4. **Architecture rules hold.** Wire types live in the contract package. Controllers do not return
   persistence entities at any generic depth. Event publishers do not accept them. The domain layer
   does not depend on the delivery layer. No package cycles. No field injection.

## Conventions

**Money is a `long` of minor units.** Never `double`, never `float`. If a calculation can overflow,
use `Math.multiplyExact` and let it throw.

**Wire types are records, and they live in the contract package.** One file per service holds them
(`OrderContracts`, `LedgerContracts`). Never declare a `Request`, `Response`, or `Dto` outside it,
and never as a nested class inside a controller.

**Entities never cross a boundary.** Project to a record through a named method, so there is one
place to look when a field drifts.

**Records with collection components copy in the compact constructor.** `List.copyOf(entries)`.
A record with a mutable component is not a value.

**Constructor injection only.** If the constructor is getting long, that is information, not an
inconvenience.

**Tests use AssertJ and `@DisplayName`.** Assert exact messages, not substrings: `hasMessage(...)`
rather than `hasMessageContaining(...)` when the exact text is knowable. A substring assertion on a
number also matches its negation, which is how sign errors survive.

**Every rule needs a fixture that violates it.** A rule with only passing cases is a rule nobody has
proven fires. Add both a violating fixture and a clean one.

**Prefer interfaces to utility classes.** A `final class` with a private constructor leaves an
uncovered line under the coverage gate. An interface with static methods has no constructor.

## How to work

**Read the invariant before the code.** For any change touching the ledger, the invariant is that a
posting balances, and it is checked on the value the strategy returned rather than assumed from the
strategy's shape. Preserve that property; the implementation underneath is yours to change.

**Run `mvn verify` before reporting done.** Not `mvn test`. The architecture rules, SpotBugs, and
the coverage check all run in phases that `test` alone does not reach.

**When a gate fails, fix the cause.** The failure modes to avoid, in order of how tempting they are:
deleting an assertion, adding a SpotBugs exclusion, lowering a threshold, marking a test
`@Disabled`, and catching an exception to make a test pass. All five are detectable in review and
all five are worse than saying the change is not working.

**When you cannot satisfy a gate, say so.** An honest "this rule and this requirement conflict, here
is the trade-off" is a useful message. A green build achieved by weakening a gate is not.

## Adding a service

1. Copy a sample module's POM and change the artifact id.
2. Copy `ArchitectureTest`, change the two package strings.
3. Put wire types in `<service>.contract`, the model in `<service>.domain`, delivery in
   `<service>.api`, publishers in `<service>.events`.

The rules then apply to the new module with no further work, which is the property the whole design
exists to produce.
