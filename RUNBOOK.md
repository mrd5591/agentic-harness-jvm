# Runbook: directing an agent through a cross-module change

The gates say what is allowed. This says how to actually use them, and what goes wrong.

The worked example throughout is a real shape of change: **add a currency to the order and ledger
flow.** It touches two modules, a wire contract, an invariant, and a persistence entity, which is
enough to hit every failure mode worth naming.

---

## Before the agent starts

**Write the invariant down first, in one sentence.** For this change: *a posting still balances, and
every entry in a posting shares one currency.* If you cannot state the invariant in a sentence, you
are not ready to delegate, because you have nothing to check the result against.

**Decide where the invariant is enforced.** Ideally on the value, in one function, not spread across
the callers. Here it belongs next to `requireBalanced`, because that is where postings are already
validated.

**Decide what must not change.** Existing wire fields, the meaning of `totalCents`, the two account
names. Say so explicitly. An agent asked to "add currency" will otherwise cheerfully rename
`totalCents` to `amount`, and every consumer breaks.

**Write the failing test yourself, or review it before implementation starts.** This is the single
highest-leverage thing on the list. The test is the specification; if you delegate writing it, you
have delegated the specification.

## The prompt shape that works

State it in this order, because it matches the order the gates run:

1. The invariant, in one sentence.
2. The files that may change, and the ones that may not.
3. The gates that will judge the result (`mvn verify`, then `mvn -Pmutation verify`).
4. What "done" means, concretely: which new test exists, and what it asserts.

What does not work: "add multi-currency support to the ledger." That is a goal, not a
specification, and the result will be a plausible-looking diff that changes a wire contract you did
not intend to change.

## During

**Let it run the build.** The agent should be running `mvn verify` itself and iterating on real
failures. Reviewing a diff that has not been through the gates wastes the gates.

**Watch for the five evasions.** In roughly the order they show up:

| Evasion | What it looks like | Response |
|---|---|---|
| Deleted assertion | A test still exists and asserts less | Checkstyle catches the empty block; mutation score catches the hollow test |
| Weakened threshold | `coverage.line.minimum` edited, `mutationThreshold` lowered | Reject on sight. The floor moves one way. |
| New SpotBugs exclusion | A fresh `<Match>` in the exclude file | Treat as a gate change: it needs a reason and a reviewer |
| Disabled test | `@Disabled`, or a test quietly renamed out of the pattern | Grep the diff for it |
| Swallowed exception | `catch (Exception e) { }` around the failing path | Checkstyle's `EmptyCatchBlock` |

All five are cheap to spot in a diff *if you know to look*. That list is most of what code review is
for when the code was generated.

**Watch for the drift the rules catch, and let them catch it.** Do not pre-emptively correct a
contract violation you notice in progress. Let the architecture test fail, so you learn whether the
rule actually fires. A rule you never see fire is a rule you do not know you have.

## After

**Read the test diff before the implementation diff.** The tests are where a bad change hides. An
implementation that is wrong in an interesting way usually arrives with a test that is wrong in an
obvious one.

**Run the mutation profile on the modules that changed.** `mvn -Pmutation verify -pl <module> -am`. This
is the check that catches the tests-that-assert-nothing failure, and it is worth the extra minute on
any change that touched an invariant.

**Ask what the agent decided that you did not specify.** There is always something. In this example
it will be whether currency lives on the entry or the posting. Both are defensible; only one matches
the model in your head, and if you do not ask, you find out two changes later.

## Failure modes actually seen

**The plausible-but-wrong patch.** Passes every gate, does the wrong thing. Gates do not catch this
and are not supposed to. This is what reading the diff is for, and it is the reason the human owns
the invariant.

**The gate-shaped hole.** A change that satisfies each rule individually while defeating the intent,
such as adding a new wire type in the contract package that duplicates an existing one. Rules
constrain shape, not judgment.

**The silent aggregator.** The one this repository found in itself: a rule removed from a `checkAll`
while every test stayed green. Anything that fans out to many callers needs a test per branch of the
fan-out, and mutation testing is what tells you that you are missing them.

**Coverage-driven test padding.** A 100% floor invites accessor tests that assert nothing. They are
visible in the mutation score, which is the argument for running both gates rather than either.

**Context loss on long changes.** Across a multi-module change an agent will re-derive a convention
it already followed in the first module, differently. The convention file exists to shorten that,
but the reliable fix is to keep changes small enough that this does not happen.
