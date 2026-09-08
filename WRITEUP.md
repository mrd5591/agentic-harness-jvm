# Draft: "100% coverage told me nothing. The mutation score told me my gates were fake."

*Draft blog post. Target: personal blog, then Hacker News and the Claude Code community. ~2,000
words. Talk abstracts at the end.*

---

I spent the last year building an 18-service fintech platform in Java by directing a coding agent,
behind a build that enforces 100% line coverage per package. About 16,500 test methods, 82
architecture-test classes, no exceptions to the coverage floor.

When I extracted the gate configuration into a public template, the first thing the template did was
prove that the number I had been quoting does not mean what people assume it means.

This is what happened, and what I changed.

## Why gate at all

An agent optimizes for the build passing. That is the objective, and it is narrower than "write good
code" in a way that matters.

Given a failing test, it will sometimes fix the bug and sometimes delete the assertion. Given a
missing type, it will sometimes find the canonical one and sometimes declare a local copy with three
of the seven fields. Given a layering rule that exists only in your README, it will import whatever
makes the compiler stop complaining. None of this is malice or even error, exactly. It is a system
doing what it was asked, where what it was asked is measurably less than what you meant.

So the working model I settled on is: **the human owns the invariants and the gates, the agent owns
the keystrokes.** Every constraint that lives only in your head is a constraint the agent does not
have. Every constraint in the build is one it cannot route around. The interesting engineering moves
from writing the code to choosing invariants that are cheap to check and expensive to violate.

That model is why the coverage floor got ratcheted from 95% to 100% partway through the project.
Not because 100% is magic, but because a floor with exceptions is a floor an agent will find the
exceptions in.

## Guides and sensors

One distinction is worth borrowing before anything else. Birgitta Böckeler splits harness components
into *guides*, which shape work before it happens, and *sensors*, which detect that something is
already wrong.

Formatting is a guide. Spotless applies google-java-format at `validate` and the tree self-heals.
Nobody should ever see a build fail over import order, and on this project nobody does.

Coverage, static analysis, and the architecture rules are sensors. They fail the build.

Getting this backwards is the most common way a harness becomes something people route around. A
build that fails on formatting trains everyone, human and agent, to treat red as noise, and then the
red that matters gets treated as noise too. Guides should be silent and automatic. Sensors should be
loud and rare.

## The rules that actually paid

Of everything in the harness, the architecture rules earned their keep most clearly, and not for the
reason I expected.

The expensive recurring failure in a multi-service codebase built this way is not a wrong algorithm.
It is the same concept acquiring three different shapes in three services. An agent that cannot find
the canonical `OrderResponse` will declare a local one. Nobody catches it in review because each
diff looks fine in isolation. The merged API spec grows duplicate schemas, and eventually the
frontend papers over the difference with a chain of null-coalescing operators.

So the rules are mostly about wire contracts:

- Any class named `*Request`, `*Response`, or `*Dto` must live in the one contract package.
- No public nested classes inside controllers, which is the fastest way to invent a second shape.
- Controllers must not return persistence entities, **at any generic depth**. A bare `OrderEntity`
  and a `ResponseEntity<Page<OrderEntity>>` leak identically, and only a recursive walk over the
  type tree catches both.
- Event publishers must not accept entities, because an event payload has no compile-time contract
  on the far side and the method signature is the only place the shape can be pinned.

The property that makes these worth writing is adoption cost. Each service adopts the whole pack
with one file and one string:

```java
class ArchitectureTest {
  private static final String BASE = "com.example.orders";
  private static final JavaClasses CLASSES = WireContractRules.importService(BASE);

  @Test void wireContractHolds() {
    WireContractRules.checkAll(CLASSES, BASE);
  }

  @Test void layeringHolds() {
    LayeringRules.checkAll(CLASSES, BASE);
  }
}
```

When modules are being created faster than anyone can review their structure, a rule written once
cannot be forgotten in module nineteen. That is the whole value proposition.

## What 100% coverage forced

A 100% line floor is a design constraint, not only a testing one. Three concrete effects.

**Utility classes became interfaces.** A `final class` with a private constructor has an uncovered
line. The standard workaround is a reflective test that invokes the constructor and asserts nothing,
which is a test written to satisfy a tool. An `interface` with static methods has no constructor to
cover. Both rule packs are interfaces now, the call sites read identically, and the fake test does
not exist.

**Every branch became somebody's decision.** A defensive `if` no test can reach is either dead code
or a missing test, and the gate makes you say which. Where the answer was genuinely "the framework
requires this," as with the no-arg constructor JPA needs, the honest response is a real test that
documents why the line exists.

**It caught a real gap in the template.** The first green-looking build sat at 0.99 in one module.
The uncovered line was the exhausted-loop path in that recursive type walk: the case where a generic
return type like `List<OrderResponse>` gets descended into and found clean. Nothing proved the rule
would decline to fire there. A false-positive architecture rule is worse than no rule, because the
first thing anyone does with a noisy gate is switch it off.

So far this all reads as a defence of the coverage floor. Here is the part that is not.

## The mutation score

I added PIT to the template mostly for completeness. With 100% line coverage and every test green,
it reported **79%**.

Seven mutants survived, and all seven were the same shape. PIT deletes an individual `check()` call
from inside a `checkAll` aggregator, and nothing observable changes.

The reason is embarrassing once you see it. My tests asserted that `checkAll` failed when run against
a fixture set that violated *several* rules at once. Remove any one rule and it still fails, for the
other reasons. The aggregate test never distinguished them.

Read as a defect this is serious. Every service adopts these packs through `checkAll`. A future edit
drops the entity-leak rule from the aggregator, every test in the repository stays green, and
nineteen services silently lose a gate they believe they have. That is not a hypothetical failure
mode for a codebase built by an agent; it is close to the *characteristic* one, because an agent
reconciling a merge conflict in an aggregator has no idea which line carries which guarantee.

The fix was one delegation test per rule, each against a class set that violates exactly that rule
and nothing else. Now deleting any single `check()` call turns the suite red.

Then the ledger module told a related story. Its balance guard, `requireBalanced`, could not be
tripped by any test, because the only posting strategy in the codebase was structurally incapable of
producing an unbalanced result. PIT deleted the guard entirely and every test stayed green. A guard
no test can trip is decoration.

Fixing that one required a design change rather than a test change: the posting strategy became a
constructor parameter, so a test can inject a deliberately broken strategy and prove the guard
guards. That is a better design on its own merits, and I would not have written it without the tool
telling me the old one was untestable.

Both modules sit at 100% mutation score now. The build enforces a floor of 85%.

## What I actually believe now

**Line coverage tells you code ran. That is all it tells you.** I had been describing the platform in
terms of a coverage number, and the number was true, and it was not evidence of what I was implying.
Böckeler found the same thing from the other direction, thirteen surviving mutants under 100%
statement coverage. If you are going to enforce a coverage number, enforce a mutation number next to
it, or the first one is theatre.

**A gate you have not watched fail is not a gate.** Both findings here are the same lesson. The
uncovered line meant a rule had never been proven to *not* fire. The surviving mutants meant an
aggregator had never been proven to actually call its parts. In both cases the artifact looked
finished and the evidence was missing.

**Coverage floors change designs, and that is the point.** Interfaces over utility classes, seams
that make guards testable, defensive branches justified or deleted. These are not concessions to a
tool. They are what the tool is for.

**None of this decides whether the software is worth building.** Gates constrain how code is allowed
to be wrong. What to build, and whether the thing that passed is the thing anyone wanted, stays with
a person, and that is the part of the job that does not delegate.

The template is public, with both findings written into the README and reproducible from a clean
checkout: `mvn verify`, then `mvn -Pmutation verify`. Break something and watch it go red. That is
the only way to know a gate works.

---

# Talk abstracts

## 1. Philadelphia / NYC JUG, 30 minutes

**Title:** Harness engineering for a JVM monorepo: what 100% coverage does not tell you

**Abstract:** I built an 18-service Java platform by directing a coding agent behind a build that
enforces 100% line coverage per package, ArchUnit rules in every module, and static analysis at max
effort. Then I ran mutation testing on the extracted template and scored 79%, because an aggregator
could silently drop a rule and no test noticed.

This talk is the practical version of that story. We will look at the actual gate configuration
(JaCoCo ratchet, ArchUnit rule packs adopted per service in one file, Spotless as a guide rather
than a sensor), the two findings that only appeared under mutation testing, and the design changes a
100% floor forces, including why both rule packs are interfaces rather than utility classes.

You will leave with a runnable template and a clear sense of which gates are worth their cost.
Suitable for anyone maintaining a multi-module JVM codebase, whether or not agents are involved.

## 2. Fintech and testing meetups, 25 minutes

**Title:** Invariants over implementations: gating money code an agent wrote

**Abstract:** When the implementation is cheap to regenerate, the durable artifact is the invariant.
A posting balances. Money is a long of minor units. An entity never crosses the wire.

Using a double-entry ledger as the worked example, this talk shows how to express invariants so a
build can enforce them on the *value* rather than on the code shape that produced it, why that
distinction is what makes generated code reviewable, and how mutation testing revealed that our
balance guard could not be tripped by any test we had. We will cover property-style tests over
example-based ones, exact-message assertions and the sign errors that substring assertions hide, and
the seam that made an unreachable guard testable.

## 3. Durable-execution and platform communities, 20 minutes

**Title:** The build as the harness: quality gates as the contract between humans and agents

**Abstract:** Agent harness conversations usually focus on tools, memory, and context. The less
discussed half is the feedback loop, and on a JVM codebase the build already is one.

This talk frames the Maven build as the harness: guides that shape work silently (formatting),
sensors that stop it loudly (coverage, architecture, static analysis, mutation), and the ratchet
pattern that lets a floor rise and never fall. I will show a template where the whole per-service
adoption cost is one file with one string, walk through two defects the gates found in their own
implementation, and argue that the reviewable unit for generated code is the invariant rather than
the diff.
