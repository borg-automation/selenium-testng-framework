package io.github.borgautomation.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import io.github.borgautomation.utils.ExtentManager;
import io.github.borgautomation.utils.ScreenshotUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.Arrays;

public class TestListener implements ITestListener {

    private static final Logger log = LogManager.getLogger(TestListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        // TestNG re-invokes the whole method (including onTestStart) for every retry attempt,
        // but only calls onTestFailure/onTestSuccess once, for the final outcome. Reuse the
        // existing node on a retry re-invocation instead of creating a duplicate that would
        // orphan the first attempt's node and hide it from the final pass/fail.
        IRetryAnalyzer analyzer = result.getMethod().getRetryAnalyzer(result);
        boolean retryReinvocation = analyzer instanceof RetryAnalyzer retryAnalyzer
                && retryAnalyzer.getRetryCount() > 0
                && ExtentManager.getTest() != null;

        if (retryReinvocation) {
            log.info("Re-invoking '{}' for retry attempt {}", buildTestName(result),
                    ((RetryAnalyzer) analyzer).getRetryCount());
            return;
        }

        ExtentTest test = ExtentManager.getInstance().createTest(buildTestName(result));
        if (result.getParameters() != null && result.getParameters().length > 0) {
            test.info("Parameters: " + Arrays.toString(result.getParameters()));
        }
        ExtentManager.setTest(test);
        log.info("Test started: {}", buildTestName(result));
    }

    // Every row from a @DataProvider invokes the same method name, so without this all rows
    // would render as identical, indistinguishable report entries. Appending the first
    // parameter's toString() (username for the JSON provider, product name for the CSV one)
    // gives each row a name a reviewer can actually tell apart.
    private static String buildTestName(ITestResult result) {
        String baseName = result.getMethod().getMethodName();
        Object[] params = result.getParameters();
        if (params != null && params.length > 0 && params[0] != null) {
            return baseName + " [" + params[0] + "]";
        }
        return baseName;
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ExtentTest test = ExtentManager.getTest();
        if (test != null) {
            test.pass("Test passed");
        }
        log.info("Test passed: {}", buildTestName(result));
        ExtentManager.removeTest();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        ExtentTest test = ExtentManager.getTest();
        String screenshot = ScreenshotUtil.captureBase64();
        if (test != null) {
            if (screenshot != null) {
                test.fail(result.getThrowable(),
                        MediaEntityBuilder.createScreenCaptureFromBase64String(screenshot).build());
            } else {
                test.fail(result.getThrowable());
            }
        }
        log.error("Test failed: {}", buildTestName(result), result.getThrowable());
        ExtentManager.removeTest();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        // TestNG reports every failed-but-about-to-be-retried attempt through onTestSkipped
        // (not onTestFailure) - confirmed empirically for TestNG 7.11.0, see PROGRESS.md. The
        // RetryAnalyzer itself already logged the failure/retry detail onto the live node, so
        // this intermediate skip must NOT tear the node down, or the next attempt's
        // onTestStart would find no node to reuse and start a disconnected duplicate.
        IRetryAnalyzer analyzer = result.getMethod().getRetryAnalyzer(result);
        boolean pendingRetry = analyzer instanceof RetryAnalyzer retryAnalyzer
                && retryAnalyzer.getRetryCount() > 0
                && retryAnalyzer.getRetryCount() <= retryAnalyzer.getMaxRetryCount();

        if (pendingRetry) {
            log.info("Intermediate retry skip for '{}' - another attempt follows", buildTestName(result));
            return;
        }

        ExtentTest test = ExtentManager.getTest();
        if (test != null) {
            Throwable throwable = result.getThrowable();
            test.log(Status.SKIP, throwable != null ? throwable.getMessage() : "Test skipped");
        }
        log.warn("Test skipped: {}", buildTestName(result));
        ExtentManager.removeTest();
    }

    @Override
    public void onFinish(ITestContext context) {
        ExtentManager.getInstance().flush();
    }
}
