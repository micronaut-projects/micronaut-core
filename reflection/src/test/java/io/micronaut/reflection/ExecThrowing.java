package io.micronaut.reflection;

import java.io.IOException;

/**
 * A type whose methods throw, to tell what an invocation lets through.
 */
public class ExecThrowing {

    public void unchecked() {
        throw new IllegalStateException("unchecked");
    }

    public void checked() throws IOException {
        throw new IOException("checked");
    }
}
