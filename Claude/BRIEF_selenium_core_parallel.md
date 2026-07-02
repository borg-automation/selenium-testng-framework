# Session Brief: Selenium + TestNG + Maven — Core Framework with Parallel Execution (No Grid)

## Project coordinates (already created in IntelliJ)

- GroupId: `io.github.borgautomation`
- ArtifactId: `selenium-testng-framework`
- Java package root: `io/github/borgautomation/`
- Java target: 21 (JDK 21 installed, use it — not 17)

Goal: a working, demoable framework by end of session — Maven build, Page Object Model,
ThreadLocal-safe parallel execution across multiple test classes/methods, running against
local browser instances (no Selenium Grid, no Docker, no reporting/CI yet — those are
separate follow-up sessions).

## Scope

**In:** pom.xml, folder structure, DriverFactory (ThreadLocal), ConfigReader, BasePage,
BaseTest, one real page + one real test (against a public demo site), testng.xml configured
for parallel execution, proof that parallelism actually works (not just configured).

**Out:** ExtentReports, Log4j2, screenshot-on-failure, retry analyzer, data providers, CI,
Grid/Docker, Cucumber. Do not scaffold folders for these yet — keep the repo lean; we add
structure when each feature lands, not before.

---

## 1. pom.xml dependencies

- `selenium-java` (latest 4.x)
- `testng` (latest 7.x)
- `io.github.bonigarcia:webdrivermanager` (latest) — no manual driver binaries
- Java 21 target (`maven-compiler-plugin` source/target 21)
- `maven-surefire-plugin` configured to run via `testng.xml`, not default TestNG discovery —
  this is required for parallel settings in testng.xml to actually take effect

## 2. Folder structure

```
src/main/java/io/github/borgautomation/
  pages/
    BasePage.java
  utils/
    DriverFactory.java
    ConfigReader.java

src/test/java/io/github/borgautomation/
  tests/
    BaseTest.java
    <FirstFeature>Test.java

src/test/resources/
  config.properties
  testng.xml
```

## 3. DriverFactory — the critical piece

- `WebDriver` held in `ThreadLocal<WebDriver>`, not a static field or instance field.
- `getDriver()` / `setDriver()` / `quitDriver()` (removes from ThreadLocal after quit —
  a leaked ThreadLocal entry across TestNG's thread-pool reuse is the #1 cause of
  "works serially, breaks in parallel" bugs).
- Browser selection via a `browser` param read from `ConfigReader`, default `chrome`.
  Support at minimum: chrome, firefox (edge can come later, not blocking parallel proof).
  Use WebDriverManager's `setup()` per browser branch.
- No headless flag yet — that's a CI-session concern; local runs should show real browser
  windows so you can visually confirm N browsers open simultaneously.

## 4. ConfigReader

- Singleton, loads `config.properties` once.
- Keys: `browser`, `baseUrl`, `implicitWaitSeconds` (keep small/explicit-wait-first —
  don't actually use implicit wait in tests, just have the config key for later).
- Must support override via `-Dbrowser=firefox` system property taking precedence over the
  properties file value — this is what lets testng.xml or CLI drive browser choice per run
  without editing files, and it's what CI will use later.

## 5. BaseTest

- `@BeforeMethod` → `DriverFactory.setDriver(...)`, navigate to `baseUrl`.
- `@AfterMethod` → `DriverFactory.quitDriver()`.
- Do NOT use `@BeforeClass`/`@AfterClass` for driver setup — per-method setup is what
  makes method-level parallelism safe. Note this tradeoff in PROGRESS.md: per-method setup
  costs browser-launch time per test but is what allows `parallel="methods"` later; if launch
  time becomes a problem we revisit with a pooled-driver strategy, not before.

## 6. First real page + test

Target site: **saucedemo.com** (chosen for stability, multiple independent flows, and
built-in test users that will feed Session 4's data providers later).

- `LoginPage` — username/password fields, login button, error message locator.
- `InventoryPage` — product list (proves login succeeded), add-to-cart button(s).
- Plain `By` locators, not PageFactory `@FindBy` — PageFactory's lazy proxy pattern has
  known sharp edges with ThreadLocal drivers; plain `By` + `driver.findElement()` per
  action is simpler and safer here.
- 3-4 independent TestNG test methods: valid login → inventory loads; invalid login →
  error shown; locked-out user → specific error shown; valid login → add item to cart →
  cart badge count updates. Each self-contained (own login in `@BeforeMethod`, no shared
  state, no dependency on execution order) — this is what actually proves parallel-safety.

## 7. testng.xml — parallel execution

- `parallel="methods"` at the `<suite>` level, `thread-count="3"` (or higher — pick a number
  you can visually verify by watching browser windows open).
- Also show `parallel="classes"` commented out as an alternative with a one-line note on the
  difference, so the choice is documented, not silently picked.

## 8. Proof of parallelism (required before calling this session done)

- Run `mvn test`. Confirm multiple browser windows launch and run concurrently, not
  sequentially — visually watch it once.
- Add a `System.out.println(Thread.currentThread().getId())` (temporary, remove after
  verifying) in each test method at start, confirm distinct thread IDs in console output
  during a parallel run.
- Record actual wall-clock time for the parallel run vs. same suite with `thread-count="1"`
  in PROGRESS.md — this number is useful later for the portfolio README ("4x faster with
  parallel execution").

## Acceptance criteria

- `mvn test` runs the full suite with no manual driver management.
- Switching `-Dbrowser=firefox` on the command line runs the same suite in Firefox with zero
  code changes.
- Parallel run is demonstrably faster than serial and multiple browsers visibly run at once.
- No `Thread.sleep` anywhere; no implicit wait relied upon (explicit `WebDriverWait` for any
  wait that's actually needed).
- PROGRESS.md updated: parallel timing numbers, any deviations from this brief.

## Ground rules

- Keep it minimal — resist the urge to add reporting, retries, or Cucumber scaffolding this
  session even if it seems easy to bolt on. Each feature gets its own session per your
  usual workflow.
- No `Legacy` renames needed — this is a from-scratch scaffold.
