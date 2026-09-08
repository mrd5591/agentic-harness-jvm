# Deep-dive resolved

Closed findings, newest first. Historical audit trail: this file is **not**
loaded at the start of a run and must never be pasted into an agent prompt.

## 2026-09-08 (debt pass)

All five open entries closed: three fixed, two ruled and moved to Accepted. Every claim below was
verified by running the build, including the two "this cannot work" claims, which were tested rather
than asserted.

### `step` · FIXED — A generic base controller defeats the entity rules

The filed entry proposed one mechanism: widen the scope with
`controller().or(assignableFrom(controller()))`. Probing first showed that **the proposed fix does
not close the case the entry describes.** With `@RestController class Sub extends
BaseCrudController<OrderEntity>`, the inherited `get()` returns the type variable `T`, whose erasure
is `Object` — there is no entity anywhere on the method to find, however the method scope is
widened. Two probes, both `PASSED (rule saw nothing)` before the change.

So it needed two mechanisms:

1. `controllerOrItsSupertype()` widens the method rules, which closes the **non-generic** half: a
   base class whose own method returns an entity, inherited by a controller.
2. `noEntityInControllerTypeArguments()` is a new class rule reading the controller's superclass and
   interfaces as *generic* types, which closes the **generic** half. The entity lives on the extends
   clause, so that is where it is read. The effect on the wire is identical either way — the
   controller serialises `OrderEntity` — so it is the same violation found somewhere else.

The false-positive matrix the entry asked for exists as `ControllerInheritanceTest`, eight tests in
two nested groups: three that the hole is closed, five that healthy input stays quiet — a generic
base bound to a contract record, a controller implementing an interface, a base no controller
extends, a supertype outside the imported set, and an ordinary clean controller.

The widening is bounded by the import, which is the answer to the entry's false-positive worry:
`importService` imports the service's own packages and the rules run against that set, so a shared
base in another module is not pulled in, and `Object` — a supertype of everything — is never a
candidate. A test pins that.

### `step` · FIXED — `-DskipTests` still yields a green build with no coverage check

Ruled as the entry's default said: close it. Surefire now carries explicit `<skipTests>false</skipTests>`,
`<skip>false</skip>` and `<testFailureIgnore>false</testFailureIgnore>`, matching how the previous
pass hardened the other sensors. Three properties because they are three routes to the same place:
skip running, skip compiling, or run and ignore the verdict.

Verified by execution, both ways: `mvn -pl harness-rules test -DskipTests` and
`-Dmaven.test.skip=true` now both **run the tests**. Before, `mvn -DskipTests verify` was BUILD
SUCCESS with the coverage gate silently absent, because JaCoCo's `check` goal *skips rather than
fails* when it finds no execution data — and `.mvn/` already exists here, so a `maven.config`
carrying the flag would have applied in CI and appeared in no diff anyone reads.

The accepted cost, stated in the POM: `mvn -DskipTests package` no longer builds a jar quickly.

### `step` · FIXED — Nothing makes a new module adopt the architecture rules

The entry's default was to add the `harness-rules` test dependency to the parent's `<dependencies>`.
**That is impossible, and it was tested rather than assumed:** every module inherits the parent's
dependencies, so `harness-rules` inherits a dependency on itself and Maven refuses to build the
reactor — `'dependencies.dependency.[io.harness:harness-rules]' for io.harness:harness-rules is
referencing itself`. Filed under Accepted so it is not re-proposed.

Of the three mechanisms the entry listed, the one with real coverage was chosen.
`ModuleAdoptionTest` reads the reactor's own `<module>` list and fails for any non-exempt module
with no `ArchitectureTest`, or one that does not reference both rule packs. It reaches outside its
own module, which is unusual and deliberate: the claim is about the reactor, and there is nowhere
inside a single module to make it from.

Proven able to go red, which is the whole point in this repo: deleting
`sample-ledger-service`'s `ArchitectureTest.java` produces `these modules have no ArchitectureTest,
so they inherit the sensors but none of the architecture rules and pass while checking nothing:
[sample-ledger-service]`, and BUILD FAILURE. That deletion was green before. Recorded in METRICS.md
alongside the other break-it-and-rerun scenarios.

### `hours` · RULED — Record compact constructors are invisible to the mutation gate

Ruled as the default said: leave `+frecord` on, document the blind spot. README gains a "What 100%
here does not cover" section and METRICS.md says it next to the score, so "100% mutation score" is
not read as covering record invariants. Moved to Accepted with the condition for re-opening: a
proposal that also deals with the ~10 `NO_COVERAGE` accessor mutants and the genuine `equals` → `true`
survivor that disabling the filter surfaces.

### `hours` · RULED — SpotBugs runs at max effort but a medium threshold

Ruled as the default said: leave it at Medium. Already tried and reverted in the previous pass with
the reason recorded in `pom.xml`; this pass moved it out of Open, where it had no selection path
out, and into Accepted with the full reasoning. The vacuous-probe warning it carries was
independently re-confirmed this pass from the other direction: `-Djacoco.skip=true` does not skip
JaCoCo either, because POM configuration beats a `-D` user property.

### Verification for this pass

`mvn -B clean verify` and `mvn -B clean -Pmutation verify` both BUILD SUCCESS.

The mutation gate caught a regression this pass, which is the gate doing its job: the new
`checkAll` delegation was initially killable — PIT reported 51/52, a `VoidMethodCallMutator`
removing the new `check()` call with nothing noticing. A `checkAllInvokesTypeArgumentRule` test was
added, following the one-delegation-test-per-rule pattern the earlier pass established, and the
score returned to **100% (75/75)**.

METRICS.md was re-measured rather than edited by hand: production lines 583 → 662, test lines
1,361 → 1,719, test methods 77 → 87, executions 83 → 93, mutants 66 → 75, architecture rules 8 → 9,
and all three wall-clock timings. Note the sample services' mutant counts moved too (11/10 → 11/12)
without their code changing, because pitest was bumped 1.17.0 → 1.30.0 since the figures were first
taken.

Not verified locally: nothing. Every gate in this repo runs on this machine.

### `step` · FIXED — `main` has no branch protection, so a red build blocks nothing

Both filings were correct, including the defer reason: a review pass should not
change a public repo's protection rules on its own initiative. The owner
authorised it directly, which is what unblocked it.

`main` now requires a pull request and every required check (`gates`, `analyze`)
green, strict, **enforced for administrators**, with force-push and deletion
blocked. Verified through the API rather than the UI.

Required approvals are deliberately **0**, not 1. With admin enforcement on and
a single maintainer there is no second account able to approve, so 1 would be a
permanent self-lockout. Zero still mandates a PR and still mandates green
checks. Raising it to 1 is one API call once a second reviewer exists.

Consequence worth carrying: direct pushes and `gh pr merge --admin` are both
refused, so the deep-dive skill's Step 7d cannot land here. `.deep-dive/repo-notes.md`
records the PR flow it must use instead.

### `hours` · FIXED — The OWASP gate has never produced a signal, and will run unauthenticated

`NVD_API_KEY` is set. The wiring was verified against the plugin itself rather
than assumed: `nvdApiKeyEnvironmentVariable` is a real user property and is the
form the plugin's own docs recommend for CI, because `-DnvdApiKey=<value>` can
expose the key through Maven debug logging (GHSA-qqhq-8r2c-c3f5).

The gate did not wait for the 2026-09-14 cron. Dispatched manually, it pulled
387,445 NVD records with no 403 or 429 -- and failed, correctly, on
`spring-core-6.1.14`: 29 CVEs, 12 at CVSS >= 7, four at 9.8. Spring is now on
7.0.9 and the scan passes.

That first run also exposed a defect the workflow had carried since it was
written: `dependency-check:check` as a bare plugin goal builds nothing, so
`sample-ledger-service` could not resolve its dependency on
`sample-order-service` and died in 12ms without being scanned. It had stayed
invisible because an earlier module always failed on CVEs first. The job now
runs `package` in the same reactor session, and has completed end to end -- all
four modules SUCCESS -- for the first time in its existence.

Follow-up landed alongside: that complete run took 1h06m, nearly all of it
rebuilding the NVD database, so it is now cached in its own directory outside
the Maven cache that keys on `pom.xml`.
