package io.micronaut.function.executor;

import javax.inject.Singleton;

@Singleton
public class TestFunctionExitHandler implements FunctionExitHandler {
    static Exception lastError;
    @Override
    public void exitWithError(Exception error, boolean debug) {
        lastError = error;
    }

    @Override
    public void exitWithSuccess() {

    }

    @Override
    public void exitWithNoData() {
        throw new RuntimeException("no data");
    }
}

