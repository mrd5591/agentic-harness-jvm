# CLAUDE.md

Instructions for a coding agent working in this repository. Rename this file for whichever tool you
use; nothing in it is tool-specific except the filename.

Written in the imperative, addressed to the agent. Most of it restates a rule the build already
enforces, so the agent does not have to discover each one by failing. Some of it does not, and the
Conventions section marks which. A convention nothing checks is still binding on you, but it will
not stop you, so it is the one a reviewer has to read for. "The build enforces this" is a claim
worth keeping true: a file that overstates its own enforcement teaches the reader to trust the
wrong things.

## What this repository is

A quality harness. The `harness-rules` module holds reusable ArchUnit rule packs; the two sample
services exist to exercise the gates. Changes that add business features to the samples are almost
certainly wrong. Changes that make a gate sharper, or that add a rule with fixtures proving it
fires, are the point.

## Build and test

```bash
mvn verify                    # every gate except mutation
mvn -Pmutation verify         # all of them: adds PIT
mvn -pl harness-rules test    # one module
mvn -Pmutation verify -pl sample-ledger-service -am   # one module's mutation score, plus what it depends on
```

JDK 21. No Docker required.

**`mvn verify` is not all of them.** Mutation testing sits behind the `mutation` profile because it
is slow, which means rule 2 below does not run unless you ask for it. Run `mvn -Pmutation verify`
before reporting done on anything that touched an invariant or a test. CI runs the mutation profile
on every push, so a change that satisfies `mvn verify` and fails PIT fails there instead — later,
and further from the change that caused it.

## The rules you cannot route around

These fail the build. Do not attempt to satisfy them by weakening the gate.

1. **Line coverage is 100% per package.** If a line cannot be covered, the answer is to remove the
   line, not to lower the floor and not to write a test that executes it without asserting anything.
   `coverage.line.minimum` moves upward only.
2. **Mutation score is at least 85%.** A test that executes code without constraining its behaviour
   will pass coverage and fail here. Assert on exact values and exact messages.
3. **Checkstyle and SpotBugs are clean.** Adding an entry to `spotbugs-exclude.xml` is a change to
   the gate itself. Propose it, with the reason inline, rather than doing it silently.
4. **Architecture rules hold.** Wire types live in the contract package. Controllers neither return
   nor accept persistence entities, at any generic depth. Event publishers do not accept them. The
   domain layer does not depend on the delivery layer. No package cycles. No field injection.
5. **A service's architecture test imports something.** `WireContractRules.importService` refuses an
   empty import rather than returning one. Every rule allows an empty match, so before this guard a
   mistyped base package produced two green tests that checked nothing, permanently. If you add a
   service by copying `ArchitectureTest`, a typo in the one string you change now fails loudly.

## Conventions

Each is tagged with what would actually stop you: **[gated]** means a gate fails the build,
**[review]** means nothing checks it and a human has to notice.

**Money is a `long` of minor units. [review]** Never `double`, never `float`. If a calculation can
overflow, use `Math.multiplyExact` / `Math.addExact` and let it throw. No gate knows a `long` is
money, so a `double` here dies in review or not at all.

**An amount is positive; direction is carried separately. [gated for ledger entries]** A signed
amount alongside a `Side`/type field encodes direction twice, and the two encodings then disagree:
a negative debit used to buy headroom for an oversized one, passing both ledger guards while moving
the wrong amount. `EntryResponse` now refuses a non-positive amount in its compact constructor.
Prefer that shape — reject at construction — over a check in each consumer.

**Wire types are records, and they live in the contract package. [package gated, shape review]**
One file per service holds them (`OrderContracts`, `LedgerContracts`). Never declare a `Request`,
`Response`, or `Dto` outside it, and never as a nested class inside a controller. The rules enforce
*where* such a type lives and that a controller declares no public nested class; that it is a
`record` rather than a class, and that there is one file per service, are review matters.

**Entities never cross a boundary. [gated at controllers and publishers]** Project to a record
through a named method, so there is one place to look when a field drifts. The rules cover
controller parameters, controller return types, and event-publisher parameters. A service or
repository method handing an entity to another layer is not covered — that one is review.

**Records with collection components copy in the compact constructor. [review]** `List.copyOf(entries)`.
A record with a mutable component is not a value. SpotBugs catches the common shape of this, not
every shape; and note that copying is not validating — if you validate a collection and then copy
it, validate the *copy*, or a list that answers differently on the second read slips through.

**Constructor injection only. [gated]** If the constructor is getting long, that is information, not
an inconvenience.

**Tests use AssertJ and `@DisplayName`. [review]** Assert exact messages, not substrings:
`hasMessage(...)` rather than `hasMessageContaining(...)` when the exact text is knowable. A
substring assertion on a number also matches its negation, which is how sign errors survive.

**Every rule needs a fixture that violates it. [review]** A rule with only passing cases is a rule
nobody has proven fires. Add both a violating fixture and a clean one — the clean one matters just
as much, because a rule that fires on healthy input makes the gate permanently red, and a gate that
is always red is a gate people start bypassing.

**Prefer interfaces to utility classes. [gated, indirectly]** A `final class` with a private
constructor leaves an uncovered line under the coverage gate. An interface with static methods has
no constructor.

## How to work

**Read the invariant before the code.** For any change touching the ledger, the invariant is that a
posting balances and moves the event's amount, and it is checked on the value the strategy returned
rather than assumed from the strategy's shape. Preserve that property; the implementation underneath is yours to change.

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
2. Copy `ArchitectureTest`, change the one base-package string.
3. Put wire types in `<service>.contract`, the model in `<service>.domain`, delivery in
   `<service>.api`, publishers in `<service>.events`.

The rules then apply to the new module with no further work, which is the property the whole design
exists to produce.
