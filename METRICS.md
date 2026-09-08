# Metrics

Two sets of numbers. The first describes this repository, and you can reproduce all of it. The
second describes the private platform the harness was extracted from, which you cannot check, so
every figure is given with the exact command that produced it and with what the command actually
counts.

Measured 2026-09-08. Java 21 (Temurin/Oracle 21), Maven 3.9.11, Windows 11.

---

## Part 1: this repository

| Metric | Value | How |
|---|---|---|
| Modules | 3 | `harness-rules`, `sample-order-service`, `sample-ledger-service` |
| Production lines | 583 | `find . -path '*/src/main/java/*' -name '*.java' -exec cat {} + \| wc -l` |
| Test lines | 1,361 | same, `src/test` |
| Test methods | 77 | `grep -rho '@Test\b\|@ParameterizedTest\b' --include=*.java */src/test \| wc -l`: 44 + 10 + 23 |
| Test executions | 83 | Surefire totals: 44 + 10 + 29. Higher than the method count because one `@ParameterizedTest` carries seven values |
| Line coverage | 100%, enforced per package | `mvn verify`, JaCoCo `check` with `COVEREDRATIO` floor 1.00 and `haltOnFailure` |
| Mutation score | 100% (66/66 killed) | `mvn -Pmutation verify`; PIT reports 45 + 11 + 10 mutations, all killed, test strength 100% |
| Checkstyle violations | 0 | reported per module during `validate` |
| SpotBugs findings | 0 | max effort, medium threshold, FindSecBugs included, at `verify` |
| Architecture rules | 8, adopted by 2 services via 2 tests and one base-package string each | `WireContractRules` (5) + `LayeringRules` (3) |
| `mvn clean verify` | 20.9 s | wall clock, warm local repository |
| `mvn -Pmutation verify` | 38.3 s | same tree |
| `mvn clean -Pmutation verify` | 42.2 s | cold |

The method count and the execution count are reported separately on purpose. They differ here by
six, which is small enough to be tempting to ignore, and ignoring it is exactly the conflation the
honesty note in Part 2 is about. Surefire counts executions; `@Test` counts methods; neither counts
assertions or distinct behaviours.

Reproduce. All three were run consecutively on a clean tree and all three reported BUILD SUCCESS:

```bash
mvn -B clean verify                # BUILD SUCCESS
mvn -B -Pmutation verify           # BUILD SUCCESS, "Killed 45/11/10 (100%)"
mvn -B clean -Pmutation verify     # BUILD SUCCESS, same mutation counts
```

To see the gates actually bite, break something and rerun. These four turn the build red, and each
is red for a different reason:

- Return `OrderRecord` instead of `OrderResponse` from `OrderController.place` — the entity-leak
  rule, via `ArchitectureTest.wireContractHolds`.
- Take an `OrderRecord` as a parameter of a controller method — the same rule in the inbound
  direction.
- Add a `public static class` inside a controller — the nested-type rule, same test.
- Remove one `check()` call from either `checkAll` — red only because of the delegation tests that
  mutation testing demanded.

**"Delete an assertion" is not on that list, and the reason is worth more than the list.** It was,
until we tried it: deleting `assertThat(response.sku()).isEqualTo("SKU-3")` from
`OrderControllerTest` leaves `mvn -B -Pmutation verify` fully green — BUILD SUCCESS, 100% coverage,
66/66 mutants still killed. The assertion is redundant, because `OrderServiceTest` already pins that
projection, and no gate can distinguish a redundant assertion from a load-bearing one. Deleting a
*load-bearing* assertion does turn the build red, and that is what the mutation score buys. Stated
precisely: mutation testing tells you which assertions are load-bearing. It does not tell you that
every assertion is.

### The findings

All three are described in the README and the write-up, and are the reason the repo exists in this
shape.

1. **Coverage 0.99, one line short.** The uncovered line was the exhausted-loop path in
   `WireContractRules.findEntityInTypeTree`: a generic type descended into and found clean. Nothing
   proved the rule would not false-positive on every `List<Record>` endpoint.
2. **Mutation score 79% at 100% line coverage.** Seven survivors in `harness-rules`, all
   `VoidMethodCallMutator` removals of individual `check()` calls inside `checkAll`. Deleting a rule
   from either aggregator left the entire suite green.
3. **A guard no test could trip.** In the ledger, PIT deleted `requireBalanced` entirely and every
   test stayed green, because the only posting strategy in the codebase was structurally incapable
   of producing an unbalanced result. Unlike the seven above, this one could not be fixed with a
   test: the strategy had to become a constructor parameter first, so a test could inject a broken
   one. The tool asked for a design change, not more coverage.

---

## Part 2: the source platform

A private Java 21 / Spring Boot fintech monorepo, implemented near-fully by directing Claude Code
behind the gates generalized here. Repository not public; figures below are what the named commands
report against the working tree.

| Metric | Value | Command | What it actually counts |
|---|---|---|---|
| Maven modules | 21 | `grep -c '<module>' pom.xml` | 18 services plus shared libraries |
| Production files | 1,614 | `find . -path '*/src/main/java/*' -name '*.java' \| wc -l` | files, not classes |
| Production lines | 189,714 | `... -exec cat {} + \| wc -l` | includes comments and blanks |
| Test lines | 422,685 | same for `src/test` | 2.2 lines of test per line of production |
| Test files | 1,404 | `find . \( -name '*Test.java' -o -name '*Tests.java' -o -name '*IT.java' \) \| wc -l` | |
| `@Test` annotations | 16,494 | `grep -rho '@Test\b' --include=*.java . \| wc -l` | **annotations, not executions** |
| `@ParameterizedTest` | 68 | same pattern | each expands to several executions at run time |
| Architecture test classes | 82 | `find . -name '*Arch*Test*.java' \| wc -l` | per-service adopters of shared packs |
| `ArchRule` declarations | 32 | `grep -rho 'ArchRule ' --include=*.java . \| wc -l` | the shared rules those 82 classes apply |
| Files referencing Testcontainers | 77 | `grep -rl 'Testcontainers\|testcontainers' --include=*.java --include=*.yml . \| wc -l` | includes compose files and base classes |
| `@Testcontainers` classes | 20 | `grep -rho '@Testcontainers' --include=*.java . \| wc -l` | actual container-backed test classes |
| Kubernetes manifests | 38 | `find k8s infrastructure \( -name '*.yaml' -o -name '*.yml' \) \| wc -l` | |
| Line coverage floor | 1.00 per package | parent POM, `COVEREDRATIO` / `haltOnFailure` | ratcheted from 0.95 on 2026-06-22 |
| Commits | 1,144 | `git log --oneline \| wc -l` | |
| Active period | 2025-08-30 to 2026-08-03 | `git log --format=%ad --date=short` | 338 days, ~48 calendar weeks |
| Weeks with commits | 22 | `git log --format=%ad --date=format:%Y-%W \| sort -u \| wc -l` | development was bursty, not steady |
| Commits per active week | ~52 | 1,144 / 22 | ~23 per calendar week across the whole span |
| Revert commits | 4 | `git log --oneline --grep='^Revert' \| wc -l` | **0.35% revert rate** |
| Merge commits | 24 | `git log --merges --oneline \| wc -l` | most work landed directly on trunk |

### Honesty notes on these numbers

**"16,494 tests" is a count of annotations.** It is not a count of assertions, of executions, or of
distinct behaviours. Parameterized tests execute more times than they are annotated; some of those
16,494 are single-assertion accessor tests that a 100% line floor made necessary and that prove
close to nothing. The right way to describe this platform is "roughly 16,500 test methods," and the
mutation-score finding in Part 1 is precisely why no stronger claim is made from a coverage number.

**Commit velocity is not throughput.** 52 commits per active week says how the work was chunked, not
how much value shipped. It is reported because it is measurable, not because it is meaningful on its
own.

**The 0.35% revert rate is the most interesting number here** and also the softest. It counts commits
whose message begins with "Revert", which undercounts fixes that were made forward rather than
reverted. Roughly 626 of 1,144 commit messages contain "fix" case-insensitively, though on a
trunk-based history that conflates genuine defect repair with normal incremental work, so it is
reported as a raw count rather than a defect rate.

**Not computed here:** defect-escape rate (needs an issue tracker join, which is not public), agent
turns per merged change (session transcripts were not retained for the full period), and the
companion web repository's test count. Where a number was not computed it is not estimated.

**Mutation score for the platform is not reported.** PIT was not run across 189k lines of production
code for this write-up; the honest statement is that its 100% line coverage carries the same caveat
Part 1 demonstrates, which is that line coverage alone does not establish that tests assert
anything. That caveat is the finding, not a footnote to it.
