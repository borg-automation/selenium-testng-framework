package io.github.borgautomation.models;

public class LoginTestData {

    public enum Outcome {
        SUCCESS, ERROR
    }

    public String username;
    public String password;
    public Outcome expectedOutcome;
    public String expectedMessage;

    // Jackson deserializes into these public fields directly - toString() below is what
    // TestListener uses to give each data row a distinct, readable name in the report
    // (instead of every row rendering as the same bare method name).
    @Override
    public String toString() {
        return username;
    }
}
