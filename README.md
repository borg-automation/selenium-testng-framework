# selenium-testng-framework

[![CI](https://github.com/borg-automation/selenium-testng-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/borg-automation/selenium-testng-framework/actions/workflows/ci.yml)
<!-- Badge is a placeholder until the ci.yml workflow is added (tracked as a follow-up session);
     it will start rendering pass/fail automatically once that workflow exists. -->

A Selenium + TestNG + Maven UI automation framework: ThreadLocal-safe parallel execution
across local browser instances (no Grid/Docker), Page Object Model, Log4j2 logging,
ExtentReports with screenshot-on-failure, and a retry analyzer for flaky tests.

## Stack

- Java 21, Maven
- Selenium 4.33.0, TestNG 7.11.0
- WebDriverManager 6.1.0 (no manual driver binaries)
- Log4j2 2.26.0
- ExtentReports 5.1.2 (Spark reporter)

## Project structure

```
src/main/java/io/github/borgautomation/
  pages/        Page Object classes (BasePage, LoginPage, InventoryPage)
  utils/        DriverFactory, ConfigReader, ExtentManager, ScreenshotUtil

src/test/java/io/github/borgautomation/
  tests/        BaseTest, SauceDemoLoginTest
  listeners/    TestListener, RetryAnalyzer, AnnotationTransformer

src/test/resources/
  config.properties   browser, baseUrl, retryCount, etc.
  testng.xml          suite + parallel config + listener registration
  log4j2.xml          console + rolling file logging
```

## Running the suite

```
mvn test
```

Runs `src/test/resources/testng.xml`, which executes the SauceDemo login/cart tests with
`parallel="methods"` — real browser windows launch concurrently, not sequentially.

Switch browser without touching any files:

```
mvn test -Dbrowser=firefox
```

## Reports & logs

- `test-output/ExtentReports/Report_<timestamp>.html` — one entry per test, screenshots
  embedded inline on failure, retry attempts logged onto the same node (not shown as
  separate/duplicate entries).
- `logs/automation.log` — rolling log file, thread-tagged so concurrent test threads can be
  told apart.

Neither directory is committed (see `.gitignore`); both are regenerated on every run.

## Configuration

`src/test/resources/config.properties`:

| Key | Default | Notes |
|---|---|---|
| `browser` | `chrome` | `chrome` or `firefox`; overridable via `-Dbrowser=...` |
| `baseUrl` | `https://www.saucedemo.com/` | |
| `implicitWaitSeconds` | `0` | Reserved; the framework uses explicit waits only |
| `retryCount` | `2` | Max retries per test via `RetryAnalyzer` |

## Status / roadmap

See `PROGRESS.md` for what's been verified so far, known environment caveats, and deviations
from the original session plans. Not yet implemented: GitHub Actions CI, data providers,
Cucumber.
