# Deep-dive resolved

Closed findings, newest first. Historical audit trail: this file is **not**
loaded at the start of a run and must never be pasted into an agent prompt.

## 2026-09-08

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
