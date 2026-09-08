# Agentic Harness (JVM)

A build-enforced quality harness for directing coding agents across a multi-module JVM codebase.

The gates are the product. The two sample services exist only so the gates have something to bite
on. If you take one thing from this repo, take the parent POM and the `harness-rules` module.

```
mvn verify                # every gate: coverage ratchet, style, static analysis, architecture
mvn -Pmutation verify     # adds mutation testing
```

Companion to [agent-eval-mcp](https://github.com/mrd5591/agent-eval-mcp), which measures the agent's
own behaviour (loops, tool failure rates, cost per change) from its session transcripts. This repo
gates the code an agent writes; that one gates how it works.

Both are green on a clean checkout. Numbers in [METRICS.md](METRICS.md), all reproducible with the
commands printed there.

---

## The problem this solves

An agent will make the build pass. That is the whole of its objective, and it is a much narrower
target than "write good code." Given a failing test it will sometimes fix the bug, and sometimes
delete the assertion. Given a missing type it will sometimes find the canonical one, and sometimes
declare a local copy with three of the seven fields. Given a layering constraint that exists only in
the README, it will import whatever makes the compiler stop complaining.

None of that is a reason to avoid agents. It is a reason to be precise about what "pass" means. Every
constraint you hold in your head is a constraint the agent does not have. Every constraint in the
build is one it cannot route around.

So the working model here is: **the human owns the invariants and the gates; the agent owns the
keystrokes.** The interesting engineering is in choosing invariants that are cheap to check and
expensive to violate.

## Guides and sensors

Two kinds of thing live in this repo, and conflating them is the most common way a harness becomes
annoying instead of useful. The distinction and the naming come from Birgitta Böckeler's 2026
writing on agent harnesses.

A **guide** shapes work before it happens and costs nothing to be wrong about. Formatting is a
guide: Spotless applies google-java-format at `validate` and the tree self-heals. Nobody should ever
see a build fail over an import order.

A **sensor** detects that something is already wrong and stops it. Coverage, style violations that
formatting cannot express, static analysis, and the architecture rules are sensors. They fail the
build.

Guides should be silent and automatic. Sensors should be loud and rare. A harness where every gate
is a sensor trains everyone, human and agent alike, to treat red as noise.

## The gates

| # | Gate | Kind | Phase | What it catches |
|---|------|------|-------|-----------------|
| 1 | JaCoCo line coverage, per package, floor 1.00 | sensor | `test` | Code nobody exercised. Per-package, so one well-tested module cannot subsidize a bare one. |
| 2 | Spotless (google-java-format) | guide | `validate` | Nothing. It applies formatting so drift never becomes a conversation. |
| 3 | Checkstyle, deliberately small | sensor | `validate` | Empty catch blocks, star imports, `==` on strings, missing switch defaults. The shortcuts an agent takes when a test will not go green. |
| 4 | SpotBugs at max effort, plus FindSecBugs | sensor | `verify` | Real defects. It found a mutable-record leak in this repo on the first run; see below. |
| 5 | ArchUnit rule packs | sensor | `test` | Wire-contract drift and layering inversions, enforced identically in every module. |
| 6 | PIT mutation testing, floor 85% | sensor | `verify` (opt-in) | Tests that execute code without asserting anything about it. |
| 7 | CodeQL, weekly plus per-PR | sensor | CI | Security patterns, including in code that has not changed. |
| 8 | OWASP dependency-check, weekly | sensor | CI | Dependency risk, which arrives on the calendar rather than on your commit schedule. |

### The ratchet

The coverage floor is a property, `coverage.line.minimum`, and it moves in one direction. The
platform this came from started at 0.95, ran there until the real number sat at ~100%, then set the
floor to 1.00 and never moved it back. That is the only honest way to adopt a high floor: reach it
first, then lock the door behind you.

A floor you lower under deadline pressure is not a gate. It is a suggestion with extra steps.

### Why these rules and not others

The expensive recurring failure in a multi-service codebase built this way is not a wrong
algorithm. It is one concept acquiring three shapes in three services, because an agent that cannot
find the canonical type will declare a local one. Each diff looks reasonable in isolation, nobody
catches it in review, the merged API spec grows duplicate schemas, and eventually the frontend
papers over the difference with a chain of null-coalescing operators.

So the wire-contract pack is four rules:

- Types named `*Request`, `*Response` or `*Dto` must live in the one contract package.
- No public nested classes inside controllers, which is the fastest way to invent a second shape
  for a concept that already has one.
- Controllers must not return persistence entities **at any generic depth**. A bare `OrderEntity`
  and a `ResponseEntity<Page<OrderEntity>>` leak identically, so the check is a recursive walk over
  the type tree rather than a type comparison.
- Event publishers must not accept entities, because an event payload has no compile-time contract
  on the far side and the method signature is the only place the shape can be pinned.

The layering pack is smaller and more conventional: the domain does not depend on the delivery
layer, no package cycles, no field injection. Those catch the shortcut an agent takes when the
cheapest import is the one that inverts a dependency.

### Adopting the architecture rules

The whole cost, per service, is one file:

```java
class ArchitectureTest {
  private static final JavaClasses CLASSES =
      WireContractRules.importService("com.example.orders");

  @Test void wireContractHolds() {
    WireContractRules.checkAll(CLASSES, "com.example.orders.contract..");
  }

  @Test void layeringHolds() {
    LayeringRules.checkAll(CLASSES, "com.example.orders.(*)..");
  }
}
```

Copy it into a new module, change two strings. That property is what matters when modules are being
created faster than anyone can review their structure: a rule written once cannot be forgotten in
module nineteen.

## What the ratchet forced

A 100% line floor is a design constraint, not just a testing one. Three things it changed here:

**Interfaces instead of utility classes.** A `final class` with a private constructor has an
uncovered line, and the standard fix is a reflective test that asserts nothing. Both rule packs are
`interface`s with static methods instead. No constructor, nothing to fake-cover, and the call sites
read the same.

**Every branch has to be somebody's decision.** A defensive `if` that no test can reach is either
dead code or a missing test, and the gate makes you say which. Where the answer was "genuinely
required by a framework contract," as with the persistence no-arg constructor in `OrderRecord`, the
honest move is a real test that documents why the line exists.

**It caught something real.** The first green-looking build sat at 0.99 in `harness-rules`. The
missing line was the exhausted-loop path in the recursive type walk: the case where a generic return
type like `List<OrderResponse>` is descended into and found clean. No test covered it, which means
nothing proved the rule would not fire on every list-returning endpoint in the codebase. A
false-positive architecture rule is worse than no rule, because the first thing anyone does with a
noisy gate is disable it.

## What mutation testing found

This is the part worth reading if you already run coverage gates.

With 100% line coverage and every test green, PIT reported a **79%** mutation score. Seven mutants
survived, and all seven were the same shape: deleting an individual `check()` call from a `checkAll`
aggregator changed nothing observable. The aggregate tests asserted that `checkAll` failed on a
fixture set that violated *several* rules, so removing any one rule still left it failing for the
other reasons.

Read that as a defect and it is a serious one. Every service adopts these packs through `checkAll`.
A future edit could silently drop the entity-leak rule from the aggregator, every test in the
repository would stay green, and nineteen services would quietly lose a gate they believed they had.

The fix was one delegation test per rule, each against a class set that violates exactly that rule
and nothing else. Now deleting any single `check()` call turns the suite red.

The ledger service told a related story. Its balance guard could not be tripped by any test, because
the only posting strategy in the codebase was incapable of producing an unbalanced result. A guard
no test can trip is decoration. The fix was a seam: the posting strategy became a parameter, so a
test can inject a broken one and prove the guard actually guards. That is a better design, and
mutation testing is what asked for it.

Both modules now sit at 100% mutation score. The general lesson is the one Böckeler reported from
the other direction, finding surviving mutants under 100% statement coverage: **line coverage tells
you code ran, and nothing else.** If you are going to enforce a coverage number at all, enforce a
mutation number next to it, or the first one is theatre.

## What is deliberately not here

- **No business logic.** The sample services are an order and a ledger because those are the
  smallest domains where an invariant worth enforcing exists. They are not a reference application.
- **No Spring Boot runtime.** The modules depend on `spring-web` and `jakarta.persistence-api` for
  annotations only, so the rules have real targets while the build stays a few seconds long. Adding
  Boot changes nothing about the gates.
- **No Testcontainers.** The source platform uses it heavily for integration tests; it is omitted
  here so a clean checkout builds without Docker. The patterns transfer unchanged.
- **No opinion about which agent.** Nothing in the gates knows or cares. `CLAUDE.md` documents the
  conventions in the dialect one tool reads; rename it for another.

## Limitations, honestly

- **The mutation floor is 85% and both modules exceed it.** On a large real codebase, PIT run over
  everything is slow enough that you will want it scoped or nightly rather than per-PR.
- **100% line coverage is not free and not always right.** It cost real design changes here, and it
  works because this codebase is small and pure. On code that is mostly glue to external systems the
  same floor buys much less and costs much more. The ratchet is the mechanism; 1.00 is not
  automatically the correct setting of it.
- **Architecture rules encode one opinion.** Wire types in one package, no entities over the wire,
  no field injection, no cycles. These are defensible, not universal. The packs are parameterized so
  the opinion is yours.
- **None of this checks whether the software is worth building.** Gates constrain how code is
  allowed to be wrong. Deciding what to build, and whether the thing that passed is the thing anyone
  wanted, stays with a person.

## How this was built

The gate configuration and the architecture-rule design were extracted from a private 18-service
Java 21 platform (~16,500 test methods, 100% enforced line coverage, 82 architecture-test classes)
that was implemented near-fully by directing Claude Code behind exactly these gates. The
generalization, the rules' semantics, the invariants, and the review are mine. The code in this
repository was written the same way the platform was: I specified the invariants and the gates, an
agent produced candidate implementations, and nothing merged that the gates rejected.

The two findings written up above, the uncovered false-positive path and the seven surviving
mutants, are both cases where the gates caught something I had not noticed. That is the argument for
the approach, and it is more convincing than any claim about throughput.

## License

MIT. See [LICENSE](LICENSE).
