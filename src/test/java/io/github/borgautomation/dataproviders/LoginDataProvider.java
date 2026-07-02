package io.github.borgautomation.dataproviders;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.borgautomation.models.LoginTestData;
import org.testng.annotations.DataProvider;

import java.io.IOException;
import java.io.InputStream;

public class LoginDataProvider {

    private LoginDataProvider() {
    }

    // TestNG calls a @DataProvider method exactly once to build the full Object[][] before
    // dispatching any test invocations, so this file read happens once per suite run, not
    // once per row.
    @DataProvider(name = "loginData")
    public static Object[][] loginData() {
        try (InputStream input = LoginDataProvider.class.getClassLoader()
                .getResourceAsStream("testdata/login-users.json")) {
            if (input == null) {
                throw new IllegalStateException("Unable to find testdata/login-users.json on the classpath");
            }
            LoginTestData[] rows = new ObjectMapper().readValue(input, LoginTestData[].class);
            Object[][] data = new Object[rows.length][1];
            for (int i = 0; i < rows.length; i++) {
                data[i][0] = rows[i];
            }
            return data;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load login-users.json", e);
        }
    }
}
