# Progress Log

## Session 4 — Data providers (JSON + CSV)

Implemented per `Claude/BRIEF_data_providers.md`: `jackson-databind` for JSON, plain
`BufferedReader` for CSV (no CSV library, per the brief's ground rule), `LoginTestData` POJO,
`LoginDataProvider` (JSON) and `AddToCartDataProvider` (CSV) under a new `dataproviders`
package.

### Second scenario choice: add-to-cart with different products, not checkout

Section 4 asked for "whichever is already closest to what Session 1's InventoryPage/checkout
coverage supports." Session 1 only built `InventoryPage` — no `CheckoutPage` exists, and the
brief's "Out" section explicitly excludes new page objects. Checkout field validation would
have needed a new page object; add-to-cart with different products needed nothing beyond
`InventoryPage.addItemToCart(String)` and `getCartBadgeCount()`, both already there. Chose
4 real SauceDemo products (`testdata/add-to-cart-products.csv`: productName,
addToCartButtonId, expectedCartBadgeCount) over checkout for that reason alone.

### Login tests: rewritten, not added alongside

The brief left this as a choice ("Rewrite (or add, if the Session 1 test still needs to
stand alone)"). Session 1's three hardcoded tests (`validLogin_inventoryLoads`,
`invalidLogin_showsError`, `lockedOutUser_showsError`) covered exactly the same three
scenarios the JSON provider now covers, plus the JSON provider adds a fourth
(`problem_user`) for free. Keeping both would have meant duplicate coverage of identical
scenarios and contradicted the brief's own stated goal ("the data drives the assertion, the
test method stays generic"), so the three were replaced by one `login(LoginTestData)` test.
`validLogin_addToCart_cartBadgeUpdates` (Session 1, single hardcoded product) was left
untouched — it isn't a login-outcome test and the brief didn't ask for it to be touched.

### Parallelism decision (Section 5)

Left `@DataProvider(parallel = true)` off, per the brief's explicit direction. Verified
concretely rather than just trusting the reasoning on paper: `logs/automation.log` shows all
9 test/row combinations (4 login rows + 1 static add-to-cart + 4 CSV rows) dispatched across
exactly 3 `TestNG-test-SauceDemoTests-N` threads — the same thread-pool that Session 1's
`thread-count="3"` already governs, no extra threads spawned by the data providers. Total
concurrent browser count stayed bounded at 3, not 3×4.

### A correctness question the brief didn't ask, but the report-naming fix in Session 2 made relevant: is `RetryAnalyzer`/`ExtentTest`-node state shared across data-provider rows?

Not assumed — checked directly, since Session 2's report-node-reuse logic
(`onTestStart`/`onTestSkipped` in `TestListener`) keys off `RetryAnalyzer.getRetryCount()` on
the instance returned by `result.getMethod().getRetryAnalyzer(result)`. If TestNG reused one
`ITestNGMethod`/retry-analyzer across all rows of a data-driven method, a retry on row 2 would
corrupt row 3's report node (wrong reuse-vs-create decision) or bleed retry-state into it. In
a run where only one row (`addToCart_variousProducts [Sauce Labs Bolt T-Shirt]`) needed
retries (2 of them, same `RetryAnalyzer` instance both times, consistent with the Session 2
finding), the very next row dispatched on that same thread
(`addToCart_variousProducts [Sauce Labs Fleece Jacket]`) rendered as a clean, separate report
node with zero "Retrying"/"exhausted" text bleeding into it. TestNG clones the test method
(and therefore the cached retry analyzer) per data-provider row — confirmed empirically for
TestNG 7.11.0, not just assumed from documentation.

### Report naming fix (applies beyond this session's tests)

`TestListener` created every `ExtentTest` node from the bare method name, so every row of a
data-driven method rendered as identical, indistinguishable entries (e.g. four nodes all
named `login`). Fixed by appending the first parameter's `toString()` to the node name
(`login [standard_user]`, `addToCart_variousProducts [Sauce Labs Backpack]`, etc.) — this is
why `LoginTestData.toString()` returns `username` rather than the default `Object.toString()`.
`RetryAnalyzer`'s log lines got the same treatment so a retry on a specific row is
identifiable in `logs/automation.log`, not just "retrying login" with no row indicated.

### Known environment flakiness, amplified

Session 2 already documented this machine needing 1-2 retries per parallel run under 3-way
Chrome contention. Adding 5 more test/row invocations (9 total vs. 4) increased contention
enough that one run fully exhausted retries on `validLogin_addToCart_cartBadgeUpdates`
(2 `TimeoutException`s, then a real failure) before an immediate re-run passed clean. This is
the same documented cause, not a new bug — see Session 2's note. Not treated as a signal to
change `thread-count` or `retryCount`, per that same note and the brief's explicit ground
rule not to revisit the parallelism decision without documenting new reasoning first.

## Session 3 — GitHub Actions CI

No brief document for this one (no `Claude/BRIEF_*.md`); scoped from the "Out" notes in
Sessions 1 and 2, which both explicitly deferred GitHub Actions.

- `.github/workflows/ci.yml`: runs on push/PR to `main`/`master`, JDK 21 (temurin), Maven
  dependency cache, `mvn test -Dheadless=true`. Uploads `test-output/ExtentReports/`,
  `logs/`, and `target/surefire-reports/` as artifacts on every run (`if: always()`), so a
  failed CI run is debuggable without rerunning locally.
- Added headless support, which Session 1 explicitly deferred as "a CI-session concern":
  `ConfigReader.isHeadless()` (system property `headless`, default `false` from
  `config.properties`) and `DriverFactory` now adds `--headless=new --no-sandbox
  --disable-dev-shm-usage --window-size=1920,1080` (Chrome) / `-headless --width=1920
  --height=1080` (Firefox) when enabled. Default stays `false` so local `mvn test` still
  shows real browser windows, unchanged from Session 1; CI passes `-Dheadless=true`
  explicitly.
- Verified locally with `mvn test -Dheadless=true` before pushing (no visible Chrome windows,
  `logs/automation.log` confirms `headless=true` on every driver creation, suite passed).
  `ubuntu-latest` ships `google-chrome-stable` preinstalled, so WebDriverManager resolves a
  matching chromedriver the same way it does locally — no extra browser-install step needed.
- README's CI badge (added in the prior commit as a placeholder pointing at `ci.yml`) now
  resolves to a real workflow.

## Session 1 — Core framework + parallel execution

Implemented per `Claude/BRIEF_selenium_core_parallel.md`: `DriverFactory` (ThreadLocal
`WebDriver`), `ConfigReader` (singleton, `-Dbrowser` override), `BasePage`, `BaseTest`,
`LoginPage`/`InventoryPage` (plain `By` locators), 4 independent SauceDemo tests, and a
`testng.xml` running `parallel="methods"`.

### Deviations from the brief

- **`thread-count` is 3, not left at the brief's suggested value untouched** — see the two
  bugs below; both had to be fixed before 3 concurrent Chrome sessions were stable.
- **`DriverFactory.setDriver()` now serializes the actual `new ChromeDriver(...)` /
  `new FirefoxDriver(...)` call** behind a `synchronized (DriverFactory.class)` block, and
  `WebDriverManager.xxxdriver().setup()` is called exactly once per browser per JVM (cached in
  a `Set<String>`), not once per thread. Two real bugs were found and fixed here, both
  environment/library issues rather than test-code bugs:
  1. **WebDriverManager cache race**: calling `setup()` concurrently from every parallel
     thread races on WebDriverManager's shared driver-binary cache and can hand back a
     corrupted binary → intermittent crashes. Fixed by resolving each browser's driver
     binary exactly once (double-checked locking), before any threads construct a driver.
  2. **DriverService port-allocation race**: Selenium's `DriverService` picks a free local
     port by probing then releasing it, then hands that port to the chromedriver/geckodriver
     process it launches. Two threads probing at (almost) the same instant can be handed the
     same "free" port (an OS-level TOCTOU race), so two unrelated `ChromeDriver` Java objects
     end up talking to *one* physical browser process — confirmed by seeing identical
     `processID`/port in two different threads' error output. Fixed by serializing only the
     driver **construction** call; the actual test execution afterward still runs fully in
     parallel.
- **`ChromeOptions`/`FirefoxOptions` explicitly set `webSocketUrl=false`.** Selenium 4's
  default JDK HTTP client shares a JVM-wide cleanup executor for the CDP/BiDi websocket
  across all driver instances (see `SeleniumHQ/selenium#14269`, closed as "not planned"). One
  thread's `driver.quit()` was shutting that executor down mid-request for every other
  parallel session, surfacing as `UnreachableBrowserException` / `RejectedExecutionException`
  on unrelated threads. Disabling the (unused, opt-in-only-when-needed) BiDi websocket avoids
  the shared executor entirely.
- **`InventoryPage.addItemToCart` now waits (`ExpectedConditions.elementToBeClickable`)
  instead of calling `driver.findElement(...).click()` directly.** The original version
  worked serially but flaked under parallel load because it clicked before the inventory page
  had finished rendering — this was a genuine test-code bug the brief's "no code changes
  beyond the plan" scope didn't anticipate.
- The temporary `System.out.println(Thread.currentThread().getId())` proof-of-parallelism
  statements were added, used to visually confirm distinct thread IDs during a parallel run,
  and then removed, per the brief.

### Timing (measured in Session 2, after the above fixes were in place; Session 1 itself
never reached a clean parallel run to time — that's what the two bugs above were)

| Mode | `thread-count` | Surefire-reported elapsed | Result |
|---|---|---|---|
| Serial | 1 | 15.83 s | 4/4 passed, no retries |
| Parallel | 3 | 23.22 s (incl. 1 real retry) | 4/4 passed after retry |

Parallel is **not** reliably faster than serial on this specific dev machine — see the
"Known environment flakiness" note under Session 2. The proof-of-parallelism requirement
(distinct thread IDs, concurrent browser windows) was satisfied; the wall-clock speedup was
not, because of genuine resource contention on this machine, not a framework defect.

---

## Session 2 — Logging, ExtentReports, screenshot-on-failure, retry analyzer

Implemented per `Claude/BRIEF_logging_reporting_retry.md`: Log4j2 (`log4j2.xml`, console +
rolling file), `ExtentManager` (shared `ExtentReports` instance, `ThreadLocal<ExtentTest>`
node), `ScreenshotUtil` (base64 capture), `TestListener` (`ITestListener`), `RetryAnalyzer`
(`IRetryAnalyzer`), `AnnotationTransformer` (`IAnnotationTransformer`), both registered in
`testng.xml` as `<listener>` elements (not `@Listeners` on the test class).

### Step 7 confirmation: is one `IRetryAnalyzer` instance really reused across all retries of a method?

Confirmed empirically for **TestNG 7.11.0** (not assumed): `RetryAnalyzer.retry()` logs
`System.identityHashCode(this)` on every call. In a forced-full-exhaustion run (a test made
to always fail via an unreachable locator), the same identity hash appeared on attempt 1,
attempt 2, and the final exhausted attempt 3, all on the same TestNG worker thread name. A
plain instance field for the retry counter is correct; `ThreadLocal` is not needed, because
TestNG never runs two attempts of the same method concurrently.

### A second thing that needed empirical confirmation, not in the brief's step 7 but discovered while building it

TestNG does **not** call `onTestFailure` for an intermediate attempt that's about to be
retried — it calls **`onTestSkipped`**, and only calls `onTestFailure`/`onTestSuccess` once,
for the method's final outcome. The first implementation of `TestListener.onTestSkipped`
called `ExtentManager.removeTest()` unconditionally, which tore down the thread's
`ExtentTest` node after the *first* failed attempt. The next attempt's `onTestStart` then had
no node to reuse and created a brand-new top-level node — exactly the "N separate unrelated
failures" bug the brief warned against. Verified visually (3 top-level report entries for one
method) and fixed by having `onTestSkipped` check whether the skip is retry-pending
(`retryAnalyzer.getRetryCount() > 0 && <= maxRetryCount`) and, if so, leave the node alone and
return — the retry/failure detail for that attempt is logged directly onto the live node by
`RetryAnalyzer.retry()` itself (it runs on the test's own thread, so the ThreadLocal lookup is
still correct there). Re-verified after the fix: exactly one top-level node per method, with
both retry attempts' `WARNING` log lines and the screenshot visible inside that single node.
A test that passes after retrying now renders with an overall **Warning** status (not a plain
green Pass) in the Spark report, because Extent rolls a node's status up to the highest
severity among its child log entries — this was left as-is since it's a more honest signal
than a silent pass, not a bug.

### Verification performed (acceptance criteria)

- Ran the full parallel suite (`thread-count=3`) — `logs/automation.log` shows interleaved
  lines correctly tagged by TestNG worker thread name (`TestNG-test-SauceDemoTests-1/2/3`).
- Forced a deterministic full-retry-exhaustion failure (temporarily pointed a wait at a
  nonexistent locator) — confirmed a screenshot (base64 `data:image`) is embedded in that
  test's report node, confirmed the same `RetryAnalyzer` instance across all 3 attempts, then
  reverted the temporary change.
- A **real** flake-then-pass happened organically during verification runs (not staged) —
  `validLogin_addToCart_cartBadgeUpdates` hit `TimeoutException`/`UnreachableBrowserException`
  on this machine under 3-way parallel load, retried, and passed; confirmed in the report as
  a single node with both retry warnings visible and an overall Warning status, not a silent
  pass or a duplicate entry.
- Dashboard screenshot saved to `Claude/extent-report-dashboard.png` for later README use.

### Known environment flakiness (not a framework bug — see Section 8 of the brief)

This dev machine has ~4 GB free RAM out of ~20 GB (IntelliJ alone holds ~2.6 GB). Running 3
simultaneous Chrome instances reliably produces 1–2 genuine `TimeoutException` /
`UnreachableBrowserException` retries per parallel run, consistently on
`validLogin_addToCart_cartBadgeUpdates` or `validLogin_inventoryLoads` (both navigate straight
into an assertion right after login, leaving the least margin before their explicit waits are
checked). The retry analyzer absorbs this correctly every time (0 final failures across ~6
verification runs this session). Per the brief's Section 8: this is exactly what retries are
for (environment/timing flakiness), and it is **not** a signal to raise `retryCount` further
or to "fix" these two tests — the same tests pass cleanly and immediately in serial mode. If
this were to fail consistently across all retries (as the forced-locator demo did), that
would be the real signal to go investigate the locator or the app, not the retry count.

### Housekeeping

- `logs/` and `test-output/` added to `.gitignore`.
- `retryCount` added to `config.properties` (default `2`), read via `ConfigReader.getRetryCount()`.
