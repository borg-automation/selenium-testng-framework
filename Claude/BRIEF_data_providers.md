# Session Brief: Data Providers (JSON + CSV)

## Project coordinates
- GroupId: `io.github.borgautomation`
- ArtifactId: `selenium-testng-framework`

## Prerequisite
Session 1 complete (DriverFactory, ConfigReader, BasePage, BaseTest, SauceDemo
LoginPage/InventoryPage, parallel testng.xml). Sessions 2-3 (reporting, retry, CI) are
independent of this one and don't need to be touched, but if they're already done,
the new data-driven tests should get the same ExtentReport/screenshot/retry treatment
as everything else — verify they show up correctly in the report, don't special-case them.

## Scope

**In:** JSON-based `@DataProvider` for login testing (multiple SauceDemo users, expected
outcomes), a CSV-based `@DataProvider` for a second scenario, explicit decision on
data-provider parallelism vs. method-level parallelism from Session 1.

**Out:** new page objects beyond what Session 1 already built, GitHub Actions changes
(the existing workflow will pick up new tests automatically since it just runs the suite).

---

## 1. pom.xml additions

- `com.fasterxml.jackson.core:jackson-databind` (latest) for JSON parsing.
- Nothing needed for CSV beyond `java.io`/`java.nio` — don't pull in a CSV library for
  something this simple (OpenCSV etc. is overkill for a handful of rows); a manual
  `BufferedReader` + `split(",")` is fine and one less dependency to explain in the README.

## 2. Folder structure additions

```
src/test/resources/
  testdata/
    login-users.json
    search-terms.csv   (or whatever second scenario is chosen, see step 4)

src/test/java/io/github/borgautomation/
  dataproviders/
    LoginDataProvider.java
    <SecondScenario>DataProvider.java
  models/
    LoginTestData.java   (POJO for Jackson deserialization)
```

## 3. JSON data provider — login scenarios

`login-users.json` — array of objects, each with: `username`, `password`,
`expectedOutcome` (`"SUCCESS"` / `"ERROR"`), `expectedMessage` (nullable, only for error
cases). Cover SauceDemo's actual test users:

- `standard_user` / valid password → SUCCESS
- `locked_out_user` / valid password → ERROR, "Sorry, this user has been locked out."
- `problem_user` / valid password → SUCCESS (this user has UI bugs on other pages, not
  login — fine to just prove login succeeds here, don't scope-creep into testing
  `problem_user`'s known UI quirks in this session)
- invalid username / invalid password → ERROR, generic credentials mismatch message

`LoginTestData.java`: simple POJO matching the JSON shape, Jackson `ObjectMapper` reads
the file once in the `@DataProvider` method (not per-invocation) and returns an
`Object[][]`.

Rewrite (or add, if the Session 1 test still needs to stand alone) a
`@Test(dataProvider = "loginData")` method: attempts login with each row's credentials,
asserts against `expectedOutcome`/`expectedMessage` rather than hardcoding pass/fail
logic per user — the data drives the assertion, the test method stays generic.

## 4. CSV data provider — second scenario

Pick something with real input variation on SauceDemo beyond login — e.g. add-to-cart
with different product names (from the inventory page), or checkout form field
validation (empty first name / empty postal code / all fields valid). Choose whichever
is already closest to what Session 1's `InventoryPage`/checkout coverage supports so
this session doesn't require building a substantial new page object; note the choice
and reasoning in PROGRESS.md.

`search-terms.csv` (or equivalently named for whatever scenario is chosen): header row +
data rows, read via `BufferedReader`, split, converted to `Object[][]` in the
`@DataProvider` method.

## 5. Parallelism decision — do not skip this

`@DataProvider(parallel = true)` is available and will run each data row on its own
thread *in addition to* the method/class-level parallelism already configured in Session
1's testng.xml. Running both without checking actual concurrent browser count risks an
uncontrolled multiplication of simultaneous browser instances (e.g. thread-count 3 ×
4 data rows = up to 12 concurrent browsers, not 3).

Decision for this session: **leave `@DataProvider(parallel = true)` OFF.** Data rows
within one `@Test` method run sequentially; parallelism stays exactly as configured by
Session 1's `testng.xml` (`parallel="methods"`, thread-count as already set — each data
row still counts as a separate method invocation dispatched to the thread pool, so
you still get real parallel execution across rows, just governed by the one thread-count
knob instead of two independent ones).

Document this reasoning in PROGRESS.md rather than just setting the flag silently — this
is exactly the kind of decision a client reviewing the code will want to see was made
deliberately.

---

## Acceptance criteria

- `mvn test` runs the full suite including both new data-driven tests, all data rows
  execute, correct pass/fail per row matches the expected outcome in the data file.
- Confirm via console/log thread IDs (same technique as Session 1) that data-driven test
  rows are still running in parallel via the existing thread pool, not serialized.
- If Session 2 is already done: open the ExtentReport, confirm each data row shows as
  its own distinctly-named test entry (e.g. parameterized test name includes the
  username or CSV row identifier, not just "loginTest" four times indistinguishably) —
  add a descriptive test name via TestNG's parameter-based naming if it doesn't already
  render usefully.
- PROGRESS.md updated: second scenario chosen and why, parallelism decision, any
  deviations.

## Ground rules

- Don't add a CSV library dependency for this scale of data — plain Java I/O is enough.
- Don't turn on `@DataProvider(parallel = true)` without updating this document's
  reasoning first, if a future session decides to revisit that decision.
- Keep test method bodies generic (assert against the data row's expected values) rather
  than writing one branch per user/row inside the test method — that defeats the purpose
  of a data provider.
