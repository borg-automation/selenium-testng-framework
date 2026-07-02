package io.github.borgautomation.tests;

import io.github.borgautomation.utils.ConfigReader;
import io.github.borgautomation.utils.DriverFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;

public abstract class BaseTest {

    private static final Logger log = LogManager.getLogger(BaseTest.class);

    protected WebDriver driver;

    @BeforeMethod
    public void setUp(Method method) {
        log.info("Starting test '{}'", method.getName());
        DriverFactory.setDriver();
        driver = DriverFactory.getDriver();
        driver.manage().window().maximize();
        driver.get(ConfigReader.getInstance().getBaseUrl());
    }

    @AfterMethod
    public void tearDown(Method method) {
        DriverFactory.quitDriver();
        log.info("Finished test '{}'", method.getName());
    }
}
