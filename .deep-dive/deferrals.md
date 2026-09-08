# Deferred findings

Active ledger. Closed items move to `resolved.md`; they are not archived here.

Ceiling on `## Open`: 20. Classes: `money` > `step` > `hours`. `hygiene` is never filed — it is
fixed in the pass that finds it, or dropped.

This ledger was created by the first deep-dive pass (2026-09-08), so its opening balance is zero and
everything below is intake from that pass. Later passes drain to a net intake of zero or less.

## Open

### 1. A generic base controller defeats the entity rules
- **Severity** High · **Class** `step` · **Effort** `own-pr` · **Surfaced** 2026-09-08 · **Run count** 1
- `WireContractRules.noEntityInControllerReturnType` and `noEntityInControllerParameters` both scope
  with `areDeclaredInClassesThat(controller())`, which keys on the class that *declares* the method.
  Given `abstract class BaseCrudController<T> { public T get() }` and
  `@RestController class OrderController extends BaseCrudController<OrderEntity> {}`, javac emits no
  bridge method, so `get()` is declared only in the un-annotated base and neither rule sees it. The
  generic-CRUD-base pattern is common in exactly the multi-service codebases these packs target, so
  this is a real hole, not a curiosity. Verified by probe during the 2026-09-08 pass.
- **Defer reason**: design decision. The obvious fix is
  `controller().or(JavaClass.Predicates.assignableFrom(controller()))` — "a class some controller
  extends" — but that widens the element set to every supertype of a controller, including
  interfaces a controller happens to implement, and the false-positive surface needs its own fixture
  matrix before it can be trusted. A rule that fires on healthy input is worse than the gap.
- **Default if nobody rules**: implement the `assignableFrom` widening with fixtures covering an
  abstract generic base, an interface-implementing controller, and a controller whose supertype is
  outside the imported package set. **Applies at run count 3.**
- **Next action**: write the fixture matrix first, then the predicate.

### 2. `-DskipTests` still yields a green build with no coverage check
- **Severity** High · **Class** `step` · **Effort** `small` · **Surfaced** 2026-09-08 · **Run count** 1
- The 2026-09-08 pass closed `-Djacoco.skip`, `-Dcheckstyle.skip`, `-Dcheckstyle.skipExec`,
  `-Dspotbugs.skip` and `-DskipPitest` by writing explicit `<skip>false</skip>` into each sensor.
  `-DskipTests` remains: with no tests run, JaCoCo finds no execution data and its `check` goal
  *skips rather than fails*, so `mvn -DskipTests verify` is BUILD SUCCESS with the coverage gate
  silently absent. A `.mvn/maven.config` carrying that flag would apply in CI too and appear in no
  diff anyone reads — and `.mvn/` already exists here (an empty `jvm.config`).
- **Defer reason**: design decision. `<skipTests>false</skipTests>` on surefire closes it, but also
  removes a legitimate developer convenience (`mvn -DskipTests package` to build a jar). That
  trade-off is the owner's, not a review pass's.
- **Default if nobody rules**: close it. This repo's whole claim is that its gates cannot be routed
  around, and the convenience is worth less than the claim. **Applies at run count 3.**
- **Next action**: also consider failing JaCoCo `check` on missing execution data, which fixes the
  vacuity directly rather than by blocking one route to it.

### 3. Nothing makes a new module adopt the architecture rules
- **Severity** High · **Class** `step` · **Effort** `own-pr` · **Surfaced** 2026-09-08 · **Run count** 1
- README's headline property is "a rule written once cannot be forgotten in module nineteen", and
  within an adopting module that holds. But adoption itself is a copy-paste convention documented in
  CLAUDE.md: the parent POM adds neither the `harness-rules` test dependency nor any check that an
  `ArchitectureTest` exists. A new module inherits Checkstyle, SpotBugs, JaCoCo and PIT, has no
  architecture test at all, and passes. `importService`'s new empty-import guard (added 2026-09-08)
  catches a *mistyped* base package but not an *absent* test.
- **Defer reason**: design decision. Several mechanisms work and they differ materially: inheriting
  the test-scoped dependency from the parent (removes one copy-paste step, forces nothing); an
  enforcer rule asserting the file exists (brittle, path-shaped); a reactor-level test that walks
  sibling modules (real coverage, but a test that reaches outside its own module).
- **Default if nobody rules**: add the `harness-rules` test dependency to the parent `<dependencies>`
  so it is inherited, and document the remaining gap honestly rather than pretending it is closed.
  **Applies at run count 3.**

### 4. Record compact constructors are invisible to the mutation gate
- **Severity** Medium · **Class** `hours` · **Effort** `small` · **Surfaced** 2026-09-08 · **Run count** 1
- PIT's default `+frecord` filter excludes record-generated members, and it takes the *whole* record
  with it: `LedgerContracts` contributes **0 mutants**, before and after the 2026-09-08 pass added
  real invariants to `EntryResponse`'s compact constructor. Proven causal that pass: with
  `-Dfeatures=-frecord`, `EntryResponse.<init>` yields `ConditionalsBoundary` and `NegateConditionals`
  mutants and both are killed by the new tests. So the guards are mutation-proof but get no credit,
  and the next invariant put in a compact constructor will get no scrutiny from the gate at all.
  This matters here specifically because CLAUDE.md now *recommends* that shape ("reject at
  construction").
- **Defer reason**: design decision. Disabling `frecord` wholesale also surfaces ~10 `NO_COVERAGE`
  mutants on record accessors and one genuine survivor (`equals` → `true`), so it cannot simply be
  turned off without deciding what to do about those.
- **Default if nobody rules**: leave `frecord` on and record the blind spot in README's mutation
  section, so nobody reads "100% mutation score" as covering record invariants. **Applies at run
  count 3.**

### 5. `main` has no branch protection, so a red build blocks nothing
- **Severity** High · **Class** `step` · **Effort** `small` · **Surfaced** 2026-09-08 · **Run count** 1
- Verified 2026-09-08: `gh api repos/mrd5591/agentic-harness-jvm/branches/main/protection` → 404
  "Branch not protected"; `.../rulesets` → `[]`. Anyone, including an agent holding a token, can
  push a red build straight to `main`. Every other gate's authority rests on this one setting.
- The 2026-09-08 pass corrected `build.yml`'s comment, which had asserted the opposite ("if this job
  is green the change is mergeable, and if it is red it is not"). The comment is now true; the
  setting is still absent.
- **Defer reason**: blocked on external input. This is a repository setting, not a file — a review
  pass should not be changing a public repo's protection rules.
- **Next action** (owner): make the `gates` check required on `main`.

### 6. The OWASP gate has never produced a signal, and will run unauthenticated
- **Severity** Low · **Class** `hours` · **Effort** `small` · **Surfaced** 2026-09-08 · **Run count** 1
- `owasp-weekly.yml` is `cron: '0 7 * * 1'` (Monday 07:00 UTC). The repo was created 2026-09-07
  23:39Z, so the first slot had already passed; the first real run is 2026-09-14. **This is not a
  defect** — a note only, so a later pass does not re-report "gate 8 has never run" as a finding.
- The real item: `gh api .../actions/secrets` → `{"total_count":0}`, so `NVD_API_KEY` is unset and
  the scan will fall back to unauthenticated NVD access, which is heavily rate-limited and can take
  20+ minutes or fail outright. A scheduled workflow failing is also nearly silent.
- **Defer reason**: blocked on external input — needs an NVD API key from the owner.
- **Next action** (owner): request a key at https://nvd.nist.gov/developers/request-an-api-key and
  add it as the `NVD_API_KEY` repository secret. Then confirm the 2026-09-14 run went green.

### 7. SpotBugs runs at max effort but a medium threshold, discarding the Low band
- **Severity** Low · **Class** `hours` · **Effort** `small` · **Surfaced** 2026-09-08 · **Run count** 1
- `<effort>Max</effort>` widens the analysis and `<threshold>Medium</threshold>` then throws away the
  low-confidence half of what it found, including FindSecBugs detectors that only ever report at Low.
- **Tried and reverted in the 2026-09-08 pass.** At a real `Low` threshold the tree is *not* clean:
  `sample-ledger-service` reports `MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR` against
  `this(LedgerService::saleEntries)` in the no-arg constructor. That is a false positive —
  `saleEntries` is `public static`, and a static method cannot be overridden. Going green would
  require either a `spotbugs-exclude.xml` entry (a gate change, and the exact move CLAUDE.md forbids)
  or restructuring production code around a detector bug. Reverted; the reason is now a comment in
  `pom.xml` so the next pass does not re-try it blind.
- **Cautionary note on how this was nearly shipped**: the first probe ran
  `mvn -B clean verify -Dspotbugs.threshold=Low` and reported `BugInstance size is 0` three times.
  That probe was **vacuous** — this pass had just added explicit `<skip>false</skip>`-style POM
  configuration hardening, and POM `<configuration>` beats a `-D` user property, so the probe
  measured Medium while appearing to measure Low. The finding only appeared once the value was
  edited in the POM itself. Verify a threshold change by editing the POM, never by `-D`.
- **Defer reason**: design decision. Reaching a clean Low band needs either an upstream SpotBugs fix
  or a code change made purely for a tool.
- **Default if nobody rules**: leave it at Medium. README's own position is that sensors should be
  loud and rare, and a gate that reports a false positive on every build is one people learn to
  ignore. **Applies at run count 3.**

## Accepted / won't-action

- **Spotless applies rather than checks, so committed formatting drift is never failed.** Raised
  2026-09-08 and rejected: README designs Spotless as a *guide*, not a sensor, deliberately
  ("Nothing. It applies formatting so drift never becomes a conversation"). The tree self-heals on
  every build. Do not re-file this as a gate gap.
- **Checkstyle's `UnusedImports` is unreachable because Spotless `removeUnusedImports` runs first in
  the same phase.** Raised 2026-09-08 and rejected: it is defence in depth for the case where
  Spotless is skipped, and it costs nothing.
- **JaCoCo's per-package floor does not constrain packages that emit no lines** (both `contract`
  packages, `sample-order-service...events`). Inherent to how JaCoCo counts; those packages hold
  only interfaces and records. Noted so it is not re-filed as a scope hole.
