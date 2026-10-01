package io.micronaut.context.python;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Drops the registry states of contexts a test closed without unregistering them, so a suite of
 * hundreds of raw contexts does not keep every closed context's heap alive.
 */
public final class ForgetClosedContextsExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        PythonContextRegistry.forgetClosedContexts();
    }
}
