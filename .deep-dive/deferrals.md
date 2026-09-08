# Deferred findings

Active ledger. Closed items move to `resolved.md`; they are not archived here.

Ceiling on `## Open`: 20. Classes: `money` > `step` > `hours`. `hygiene` is never filed — it is
fixed in the pass that finds it, or dropped.

## Open

_(none — the 2026-09-08 debt pass drained the ledger. See `resolved.md`.)_

## Accepted / won't-action

- **SpotBugs runs at max effort but a medium threshold, discarding the Low band.** Ruled 2026-09-08
  and moved here from Open rather than left to age out. At a real `Low` threshold the tree is not
  clean: `sample-ledger-service` reports `MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR` against
  `this(LedgerService::saleEntries)`, which is a false positive — `saleEntries` is `public static`,
  and a static method cannot be overridden. Going green would need either a `spotbugs-exclude.xml`
  entry, which is the exact move CLAUDE.md forbids, or production code restructured around a
  detector bug. README's own position is that sensors should be loud and rare, and a gate that
  reports a false positive on every build is one people learn to ignore. The reason is a comment in
  `pom.xml` so the next pass does not re-try it blind.

  **Verify a threshold change by editing the POM, never by `-D`.** The first probe ran
  `mvn -B clean verify -Dspotbugs.threshold=Low` and reported `BugInstance size is 0` three times.
  That probe was vacuous: POM `<configuration>` beats a `-D` user property, so it measured Medium
  while appearing to measure Low. Re-confirmed during the 2026-09-08 debt pass, from the other
  direction — `-Djacoco.skip=true` likewise does not skip JaCoCo here.

- **Record compact constructors are invisible to the mutation gate.** Ruled 2026-09-08: leave PIT's
  `+frecord` filter on and state the blind spot instead of removing it. Disabling it wholesale also
  surfaces ~10 `NO_COVERAGE` mutants on record accessors and one genuine survivor (`equals` →
  `true`), so it cannot be turned off without deciding what to do about those, which is a separate
  piece of work with its own trade-offs. The blind spot is now named in README under "What 100%
  here does not cover" and in METRICS.md next to the mutation score, so nobody reads "100% mutation
  score" as covering record invariants. Do not re-file as a scope hole; re-file only as a proposal
  to deal with the accessors and the `equals` survivor.

- **Spotless applies rather than checks, so committed formatting drift is never failed.** Raised
  2026-09-08 and rejected: README designs Spotless as a *guide*, not a sensor, deliberately. The
  tree self-heals on every build. Do not re-file this as a gate gap.

- **Checkstyle's `UnusedImports` is unreachable because Spotless `removeUnusedImports` runs first in
  the same phase.** Raised 2026-09-08 and rejected: it is defence in depth for the case where
  Spotless is skipped, and it costs nothing.

- **JaCoCo's per-package floor does not constrain packages that emit no lines** (both `contract`
  packages, `sample-order-service...events`). Inherent to how JaCoCo counts; those packages hold
  only interfaces and records. Noted so it is not re-filed as a scope hole.

- **The parent POM cannot declare the `harness-rules` test dependency.** Attempted during the
  2026-09-08 debt pass and rejected by Maven itself, not by judgement: every module inherits the
  parent's `<dependencies>`, so `harness-rules` would inherit a dependency on itself —
  `'dependencies.dependency.[io.harness:harness-rules]' for io.harness:harness-rules is referencing
  itself`. An intermediate service-parent POM would work but only removes a copy-paste step;
  `ModuleAdoptionTest` is what actually forces adoption. Do not re-propose the parent-dependency
  form.
