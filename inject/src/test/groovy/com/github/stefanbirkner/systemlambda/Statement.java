package com.github.stefanbirkner.systemlambda;

/**
 * Code that should be executed by on of the methods of {@link SystemLambda}.
 * This code may throw an {@link Exception}. Therefore we cannot use
 * {@link Runnable}.
 */
public interface Statement {
    /**
     * Execute the statement.
     *
     * @throws Exception the statement may throw an arbitrary exception.
     */
    void execute() throws Exception;
}
