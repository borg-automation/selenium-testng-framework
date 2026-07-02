package io.github.borgautomation.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import io.github.borgautomation.utils.ConfigReader;
import io.github.borgautomation.utils.ExtentManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger log = LogManager.getLogger(RetryAnalyzer.class);

    // TestNG instantiates one IRetryAnalyzer per @Test method and reuses that same instance
    // across all of that method's retry attempts (confirmed for TestNG 7.11.0 by logging
    // System.identityHashCode(this) across attempts - see PROGRESS.md), so a plain instance
    // field is sufficient here without ThreadLocal.
    private int retryCount = 0;
    private final int maxRetryCount = ConfigReader.getInstance().getRetryCount();

    @Override
    public boolean retry(ITestResult result) {
        String methodName = describeMethod(result);
        log.info("RetryAnalyzer instance {} evaluating '{}' after attempt {} (max retries {})",
                System.identityHashCode(this), methodName, retryCount + 1, maxRetryCount);

        // retry() runs on the same thread that just ran the test method, so the ThreadLocal
        // ExtentTest node for this test is still the active one - this is the only place a
        // failed-but-retried attempt is visible at all, since TestNG suppresses
        // onTestFailure/onTestSuccess for every attempt except the final one.
        ExtentTest test = ExtentManager.getTest();

        if (retryCount < maxRetryCount) {
            retryCount++;
            String message = String.format("Attempt %d failed (%s). Retrying - attempt %d of %d.",
                    retryCount, describe(result), retryCount, maxRetryCount);
            log.warn(message);
            if (test != null) {
                test.log(Status.WARNING, message);
            }
            return true;
        }

        String message = String.format("All %d retries exhausted for '%s'.", maxRetryCount, methodName);
        log.warn(message);
        if (test != null) {
            test.log(Status.WARNING, message);
        }
        return false;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    private static String describe(ITestResult result) {
        Throwable throwable = result.getThrowable();
        return throwable != null ? throwable.getClass().getSimpleName() : "unknown reason";
    }

    // Mirrors TestListener.buildTestName() - a data-driven method's rows all share one
    // method name, so the first parameter (username / product name) is what actually
    // identifies which row this retry decision belongs to.
    private static String describeMethod(ITestResult result) {
        String baseName = result.getMethod().getMethodName();
        Object[] params = result.getParameters();
        if (params != null && params.length > 0 && params[0] != null) {
            return baseName + " [" + params[0] + "]";
        }
        return baseName;
    }
}
