# Session Brief: Logging + ExtentReports + Screenshot-on-Failure + Retry Analyzer

## Project coordinates
- GroupId: `io.github.borgautomation`
- ArtifactId: `selenium-testng-framework`
- Java package root: `io/github/borgautomation/`

## Prerequisite
Session 1 must already be complete and working: `DriverFactory` (ThreadLocal `WebDriver`),
`ConfigReader`, `BasePage`, `BaseTest`, SauceDemo page objects and tests, parallel
`testng.xml` proven to run concurrently. This session builds on top of that — do not
modify `DriverFactory`'s ThreadLocal pattern, only read from it.

## Scope

**In:** Log4j2 setup, ExtentReports with thread-safe parallel logging, screenshot capture
on failure embedded in the report, a listener that ties it all together, retry analyzer
for flaky tests, and retry-aware reporting so retried tests don't look like unrelated runs.

**Out:** data providers, GitHub Actions, README polish — separate sessions.

---

## 1. pom.xml additions

- `org.apache.logging.log4j:log4j-core` + `log4j-api` (latest 2.x)
- `com.aventstack:extentreports` (latest 5.x)

## 2. Folder structure additions

```
src/main/java/io/github/borgautomation/
  utils/
    ExtentManager.java
    ScreenshotUtil.java

src/test/java/io/github/borgautomation/
  listeners/
    TestListener.java
    RetryAnalyzer.java
    AnnotationTransformer.java

src/test/resources/
  log4j2.xml
```

---

## 3. Log4j2

- `log4j2.xml`: console appender (pattern with timestamp, thread name, level, logger,
  message — thread name matters here since we're running parallel, you need to be able
  to tell which log line came from which test thread) + rolling file appender to
  `logs/automation.log`, max file size 10MB, keep last 5.
- Add a `private static final Logger log = LogManager.getLogger(ClassName.class);` pattern
  to `BaseTest`, `DriverFactory`, and the new listener classes — log driver
  creation/teardown, test start/end, and retry attempts at INFO; exceptions at ERROR with
  stack trace.
- `.gitignore` the `logs/` folder.

## 4. ExtentManager — thread-safe report generation

- Single `ExtentReports` instance (the report *file* is shared across all threads —
  this is correct and required, do not create one report per thread).
- `ExtentTest` node held in `ThreadLocal<ExtentTest>` — this is the part that must be
  thread-safe, since each parallel test thread needs its own node in the same report
  without cross-writing into another thread's test entry.
- Report initialized once via a static block or double-checked singleton, output to
  `test-output/ExtentReports/Report_<timestamp>.html`.
- Use the Spark reporter (`ExtentSparkReporter`), not the older HTML reporter — it's the
  current supported one for ExtentReports 5.x and gives a cleaner dashboard.

## 5. ScreenshotUtil

- Takes the current thread's `WebDriver` from `DriverFactory`, casts to
  `TakesScreenshot`, captures as `OutputType.BASE64` (not `FILE`) — embedding the
  screenshot directly in the HTML report as base64 means the report is self-contained
  and portable (can be zipped/emailed/attached to a GitHub Actions artifact later
  without broken image links).

## 6. TestListener implements ITestListener

- `onTestStart`: create the `ExtentTest` node for the current thread, log test name and
  parameters if any.
- `onTestSuccess`: mark PASS on the Extent node.
- `onTestFailure`: capture screenshot via `ScreenshotUtil`, attach as base64 to the
  Extent node, mark FAIL, log the exception via Log4j2 at ERROR.
- `onTestSkipped`: mark SKIP, log why.
- Register in `testng.xml` as a `<listener>` element — not via `@Listeners` annotation on
  test classes — so it applies suite-wide without touching every test class.

## 7. Retry Analyzer

- `RetryAnalyzer implements IRetryAnalyzer`: max retry count read from
  `config.properties` (key `retryCount`, default `2`), per-thread retry counter (a plain
  instance field is fine here — TestNG creates a new `IRetryAnalyzer` instance per test
  method invocation context, this does not need ThreadLocal, but confirm this assumption
  against the TestNG version in use and note it in PROGRESS.md rather than assuming).
- `AnnotationTransformer implements IAnnotationTransformer`: auto-applies
  `RetryAnalyzer` to every `@Test` method's annotation at runtime so nobody has to hand-add
  `retryAnalyzer = RetryAnalyzer.class` on each test. Register in testng.xml as a
  `<listener>` alongside `TestListener`.
- Retry attempts must be visible in the ExtentReport: extend `TestListener`'s
  `onTestFailure`/`onTestSuccess` to check if the test was a retry (TestNG's
  `ITestResult` exposes method invocation count) and log "Retry attempt N" into the
  Extent node rather than letting a retried-then-passed test look like a plain pass with
  no explanation, or a retried-then-failed test look like N separate unrelated failures.

## 8. Deliberate scope boundary — document, don't fix

Add a note to PROGRESS.md: retry analyzer masks environment/timing flakiness (slow
network, animation delays), it is not a substitute for fixing a genuinely broken locator
or a real application bug. If a test fails consistently across all retries, that's a
signal to investigate the test/locator, not raise `retryCount` further.

---

## Acceptance criteria

- Run the full parallel suite (`mvn test`). Confirm:
  - `logs/automation.log` contains interleaved but correctly thread-tagged log lines from
    concurrent test threads.
  - `test-output/ExtentReports/Report_<timestamp>.html` opens and shows one entry per
    test, correctly attributed (no cross-thread bleed — a common bug here is two parallel
    tests' logs/screenshots ending up on the wrong Extent node; explicitly verify this by
    running with thread-count ≥ 3 and checking each node's content matches its test name).
  - Deliberately fail one test (e.g. temporarily assert something false) and confirm a
    screenshot appears embedded in that test's report node.
  - Deliberately make one test fail on first attempt and pass on retry (e.g. a
    time-based flake) and confirm the report shows the retry, not a silent pass or a
    confusing duplicate.
- PROGRESS.md updated with: the TestNG retry-analyzer-instance-per-method confirmation
  from step 7, any deviations, and a screenshot of the ExtentReport dashboard for later
  README use.

## Ground rules

- Do not modify `DriverFactory`'s ThreadLocal pattern from Session 1.
- No `Thread.sleep` anywhere, including in any flaky-test demo — use a real timing
  condition or explicit wait mismatch to demonstrate retry behavior, not a sleep hack.
- Keep GitHub Actions, data providers, and README out of this session.
