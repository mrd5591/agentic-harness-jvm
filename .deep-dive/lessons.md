# Project-specific verification lessons

How to verify a finding in *this* repo. Consulted at verification time, never at discovery — every
pass approaches the tree with fresh eyes.

### A rule that "has a violating fixture" may still be blind to most of its own population
Every rule here is paired with a violating fixture and a clean one, and that pairing proves the rule
*fires*, not that it fires on everything it claims to cover. Before accepting a rule as sound, ask
what its scope predicate keys on and enumerate what that predicate structurally cannot reach.
`areDeclaredInClassesThat(controller())` keys on the *declaring* class, so a method inherited from
an un-annotated generic base is invisible to it no matter how many fixtures exist. Verify by writing
the shape you suspect and running the rule against it, not by reading the fixture list.

### `allowEmptyShould(true)` is on nearly every rule, so "the test is green" can mean "nothing was
### imported"
Both sample `ArchitectureTest`s derive their whole class set from one base-package string. Before
2026-09-08 a typo in that string produced two permanently green, entirely vacuous tests.
`WireContractRules.importService` now refuses an empty import, which closes the single-package
adoption path — but any claim of the form "service X is gated" should still be verified by checking
that its import is non-empty and that the specific rule you care about matched something. Note which
rules use `allowEmptyShould(true)` (all of them except `noCyclesBetweenSlices`) when reasoning about
whether a green run means anything.

### PIT reports 100% while whole classes contribute zero mutants
PIT's default `+frecord` filter removes record-generated members and takes the entire record with
it. `OrderContracts` and `LedgerContracts` produce **0 mutants**. So "100% mutation score" here is a
statement about non-record production code only, and an invariant living in a record's compact
constructor — which CLAUDE.md now recommends — gets no mutation scrutiny at all. Before citing the
mutation score as evidence that a guard is pinned, check whether the guard is inside a record; if it
is, prove it with a red-then-green test instead, or re-run with `-Dfeatures=-frecord` to see the
mutants that are normally hidden.

### JaCoCo's per-package floor says nothing about packages that emit no lines
The floor is `<element>PACKAGE</element>` at 1.00, which reads as total coverage but only constrains
packages JaCoCo emits a row for. Interface-and-record-only packages (both `contract` packages,
`sample-order-service...events`) produce no rows and are therefore unconstrained. Do not treat "100%
line coverage, enforced per package" as covering them.

### Verify a documented claim by running it, not by reading the code that should support it
This repo's docs make precise, checkable claims, and they are the most rewarding review surface
because they can be *executed*. METRICS.md said deleting an assertion turns the build red; deleting
`assertThat(response.sku()).isEqualTo("SKU-3")` from `OrderControllerTest` and running
`mvn -B -Pmutation verify` returned BUILD SUCCESS with 63/63 mutants still killed. Reading the
mutation config would never have found that, because the config was correct — the *claim* was
over-broad. Re-run each documented command and each "break it and rerun" instruction literally.

### Distinguish Surefire executions from `@Test` methods before quoting a test count
The ledger suite carries a `@ParameterizedTest` with seven values, so Surefire's per-module totals
exceed the method count by six. Both numbers are legitimate and they are not interchangeable;
METRICS.md now reports them separately. Count methods with
`grep -rho '@Test\b\|@ParameterizedTest\b' --include=*.java */src/test | wc -l`, and read executions
off Surefire.

### The samples are fixtures — "a production service would have X" is not a finding
`sample-order-service` and `sample-ledger-service` exist so the gates have something to bite on.
CLAUDE.md states that changes adding business features to them are almost certainly wrong. Findings
about missing persistence, validation frameworks, REST plumbing, or error-handling layers should be
rejected at triage. What *is* in scope: arithmetic that is wrong, an invariant the file exists to
demonstrate that it fails to demonstrate, and a violation of the repo's own stated conventions.

### The gate config's off-switches are the highest-value config surface
Sensors here now carry explicit `<skip>false</skip>`, but the user property names are not guessable
and getting one wrong produces a `<configuration>` element Maven silently ignores. PIT's is
`${skipPitest}`, not `pitest.skip`; Checkstyle has two (`checkstyle.skip` and `checkstyle.skipExec`).
Always read the mojo descriptor (`plugin.xml` in the plugin jar) rather than assuming the property
follows the plugin name, and prove a closure by running the build *with* the flag set and showing
the gate still ran.
