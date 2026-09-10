package io.micronaut.context.python;

import io.micronaut.context.ApplicationContext;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application runtime owns the primary context, the pool and the offload executor of one
 * application; generated code resolves the runtime installed last.
 */
final class PythonApplicationRuntimeTest {

    @Test
    void theRuntimeBeanIsBoundToThePrimaryContextAndThePoolRegistersThere() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PythonApplicationRuntime runtime = applicationContext.getBean(PythonApplicationRuntime.class);
            assertSame(runtime, PythonApplicationRuntime.current());
            assertTrue(runtime.owns(applicationContext.getBean(Context.class)));
            assertSame(applicationContext.getBean(PythonPool.class), runtime.pool());
            assertNotNull(runtime.pooledExecutorServiceProvider());
            assertSame(runtime.context(), PythonContextRuntime.getContext());
        }
        assertNull(PythonApplicationRuntime.current(), "closing the application uninstalls its runtime");
        assertFalse(PythonContextRuntime.isInitialized());
    }

    @Test
    void aDisabledPoolLeavesTheRuntimeWithoutPool() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of("micronaut.python.pool.enabled", false))) {
            PythonApplicationRuntime runtime = applicationContext.getBean(PythonApplicationRuntime.class);
            assertNull(runtime.pool());
            assertSame(applicationContext.getBean(PythonPool.class), applicationContext.getBean(PythonPool.class));
        }
    }

    @Test
    void aReplacedRuntimeCannotUninstallItsSuccessor() {
        try (Context first = Context.newBuilder(PYTHON).allowAllAccess(true).build();
             Context second = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonApplicationRuntime old = PythonContextRuntime.setContext(first, null);
            assertTrue(PythonContextRuntime.isCurrentContext(first));
            assertTrue(PythonContextRuntime.isCurrentContext(first.eval(PYTHON, "object()").getContext()), "a context view matches the primary context");

            PythonApplicationRuntime replacement = PythonContextRuntime.setContext(second, null);
            assertFalse(PythonApplicationRuntime.uninstall(old), "the old runtime is no longer installed");
            assertSame(replacement, PythonApplicationRuntime.current());
            assertTrue(PythonContextRuntime.isCurrentContext(second));
            assertFalse(PythonContextRuntime.isCurrentContext(first));

            PythonContextRuntime.resetContext();
            assertNull(PythonApplicationRuntime.current());
            assertFalse(PythonContextRuntime.isCurrentContext(second));
            assertTrue(PythonContextRuntime.isCurrentContext(null));
        } finally {
            PythonContextRuntime.resetContext();
        }
    }
}
