# Metrics

Two sets of numbers. The first describes this repository, and you can reproduce all of it. The
second describes the private platform the harness was extracted from, which you cannot check, so
every figure is given with the exact command that produced it and with what the command actually
counts.

Measured 2026-09-07. Java 21 (Temurin/Oracle 21), Maven 3.9.11, Windows 11.

---

## Part 1: this repository

| Metric | Value | How |
|---|---|---|
| Modules | 3 | `harness-rules`, `sample-order-service`, `sample-ledger-service` |
| Production lines | 728 | `find . -path '*/src/main/java/*' -name '*.java' -exec cat {} + \| wc -l` |
| Test lines | 867 | same, `src/test` |
| Test methods | 51 | Surefire totals: 26 + 10 + 15 |
| Line coverage | 100%, enforced per package | `mvn verify`, JaCoCo `check` with `COVEREDRATIO` floor 1.00 and `haltOnFailure` |
| Mutation score | 100% (53/53 killed) | `mvn -Pmutation verify`; PIT reports 33 + 11 + 9 mutations, all killed, test strength 100% |
| Checkstyle violations | 0 | reported per module during `validate` |
| SpotBugs findings | 0 | max effort, medium threshold, FindSecBugs included, at `verify` |
| Architecture rules | 7, adopted by 2 services via 2 tests each | `WireContractRules` (4) + `LayeringRules` (3) |
| `mvn verify` | ~18 s | wall clock, warm local repository |
| `mvn -Pmutation verify` | ~35 s | same |

Reproduce:

```bash
mvn -B verify                # expect BUILD SUCCESS
mvn -B -Pmutation verify     # expect BUILD SUCCESS, "Killed 33/11/9 (100%)"
```

To see the gates actually bite, break something and rerun: delete an assertion, return
`OrderRecord` instead of `OrderResponse` from `OrderController.place`, add a `public static class`
inside a controller, or remove one `check()` call from either `checkAll`. Each of those turns the
build red, and the last one is red only because of the delegation tests that mutation testing
demanded.

### The two findings

Both are described in the README and are the reason the repo exists in this shape.

1. **Coverage 0.99, one line short.** The uncovered line was the exhausted-loop path in
   `WireContractRules.findEntityInTypeTree`: a generic type descended into and found clean. Nothing
   proved the rule would not false-positive on every `List<Record>` endpoint.
2. **Mutation score 79% at 100% line coverage.** Seven survivors, all `VoidMethodCallMutator`
   removals of individual `check()` calls inside `checkAll`. Deleting a rule from either aggregator
   left the entire suite green.

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
| Test files | 1,404 | `find . -name '*Test.java' -o -name '*Tests.java' -o -name '*IT.java'` | |
| `@Test` annotations | 16,494 | `grep -rho '@Test\b' --include=*.java . \| wc -l` | **annotations, not executions** |
| `@ParameterizedTest` | 68 | same pattern | each expands to several executions at run time |
| Architecture test classes | 82 | `find . -name '*Arch*Test*.java' \| wc -l` | per-service adopters of shared packs |
| `ArchRule` declarations | 32 | `grep -rho 'ArchRule ' --include=*.java . \| wc -l` | the shared rules those 82 classes apply |
| Files referencing Testcontainers | 77 | `grep -rl 'Testcontainers\|testcontainers' --include=*.java --include=*.yml .` | includes compose files and base classes |
| `@Testcontainers` classes | 20 | `grep -rho '@Testcontainers' --include=*.java .` | actual container-backed test classes |
| Kubernetes manifests | 38 | `find k8s infrastructure -name '*.yaml' -o -name '*.yml'` | |
| Line coverage floor | 1.00 per package | parent POM, `COVEREDRATIO` / `haltOnFailure` | ratcheted from 0.95 on 2026-06-22 |
| Commits | 1,144 | `git log --oneline \| wc -l` | |
| Active period | 2025-08-30 to 2026-08-03 | `git log --format=%ad --date=short` | ~49 calendar weeks |
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
