package io.micronaut.context.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application runtime owns the primary context, the pool and the offload executor of one
 * application; generated code resolves the runtime installed last, and a nested application that
 * closes hands the enclosing one back.
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
    void installedRuntimesNestAndUninstallingTheInnerOneRestoresTheOuterOne() {
        try (Context first = Context.newBuilder(PYTHON).allowAllAccess(true).build();
             Context second = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonApplicationRuntime outer = PythonContextRuntime.setContext(first, null);
            assertTrue(PythonContextRuntime.isCurrentContext(first));
            assertTrue(PythonContextRuntime.isCurrentContext(first.eval(PYTHON, "object()").getContext()), "a context view matches the primary context");

            PythonApplicationRuntime inner = PythonContextRuntime.setContext(second, null);
            assertSame(inner, PythonApplicationRuntime.current());
            assertTrue(PythonContextRuntime.isCurrentContext(second));
            assertFalse(PythonContextRuntime.isCurrentContext(first));

            assertTrue(PythonApplicationRuntime.uninstall(inner), "the inner runtime was installed");
            assertSame(outer, PythonApplicationRuntime.current(), "the outer runtime is resolved again");
            assertTrue(PythonContextRuntime.isCurrentContext(first));
            assertFalse(PythonApplicationRuntime.uninstall(inner), "an uninstalled runtime is not installed");

            PythonApplicationRuntime replacement = PythonContextRuntime.setContext(second, null);
            assertTrue(PythonApplicationRuntime.uninstall(outer), "an enclosing runtime can be uninstalled while a nested one is installed");
            assertSame(replacement, PythonApplicationRuntime.current(), "uninstalling the enclosing runtime leaves the nested one in place");

            PythonContextRuntime.resetContext();
            assertNull(PythonApplicationRuntime.current());
            assertFalse(PythonContextRuntime.isCurrentContext(second));
            assertTrue(PythonContextRuntime.isCurrentContext(null));
        } finally {
            PythonContextRuntime.resetContext();
        }
    }

    @Test
    void resetContextUninstallsEveryRuntime() {
        try (Context first = Context.newBuilder(PYTHON).allowAllAccess(true).build();
             Context second = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonContextRuntime.setContext(first, null);
            PythonContextRuntime.setContext(second, null);

            PythonContextRuntime.resetContext();

            assertNull(PythonApplicationRuntime.current());
            assertFalse(PythonContextRuntime.isInitialized());
        } finally {
            PythonContextRuntime.resetContext();
        }
    }

    @Test
    void aNestedApplicationOwnsItsOwnContextAndRestoresTheEnclosingRuntimeWhenItCloses() {
        try (ApplicationContext outer = ApplicationContext.run()) {
            PythonApplicationRuntime outerRuntime = outer.getBean(PythonApplicationRuntime.class);
            Context outerContext = outer.getBean(Context.class, Qualifiers.byName(PYTHON));
            Context nestedContext;
            try (ApplicationContext nested = ApplicationContext.run()) {
                PythonApplicationRuntime nestedRuntime = nested.getBean(PythonApplicationRuntime.class);
                nestedContext = nested.getBean(Context.class, Qualifiers.byName(PYTHON));
                assertNotSame(outerRuntime, nestedRuntime);
                assertNotSame(outerContext, nestedContext, "a nested application builds its own primary context");
                assertSame(nestedRuntime, PythonApplicationRuntime.current(), "generated code resolves the nested application while it runs");
                assertSame(nestedContext, PythonContextRuntime.getContext());
            }
            assertSame(outerRuntime, PythonApplicationRuntime.current(), "closing the nested application restores the enclosing runtime");
            assertSame(outerContext, PythonContextRuntime.getContext());
            assertEquals(3, outerContext.eval(PYTHON, "1 + 2").asInt(), "the enclosing context is still open");
            assertClosed(nestedContext, "the nested context is closed");
        }
        assertNull(PythonApplicationRuntime.current());
        assertFalse(PythonContextRuntime.isInitialized());
    }

    @Test
    void aNestedApplicationClosedInsideAnExecutionOfTheEnclosingContextIsClosedOnceThatExecutionEnds() {
        try (ApplicationContext outer = ApplicationContext.run()) {
            PythonApplicationRuntime outerRuntime = outer.getBean(PythonApplicationRuntime.class);
            Context outerContext = outer.getBean(Context.class, Qualifiers.byName(PYTHON));
            // a Python test method of the enclosing application runs ApplicationContext.run(...) and closes it
            Context nestedContext = PythonContextRegistry.withExecutionFrame(outerContext, () -> {
                ApplicationContext nested = ApplicationContext.run();
                Context context = nested.getBean(Context.class, Qualifiers.byName(PYTHON));
                nested.close();
                assertSame(outerRuntime, PythonApplicationRuntime.current(), "the enclosing runtime is resolved as soon as the nested application closes");
                assertEquals(3, context.eval(PYTHON, "1 + 2").asInt(), "the nested context stays open until the enclosing execution ends");
                return context;
            });
            assertClosed(nestedContext, "the nested context is closed after the execution");
            assertSame(outerRuntime, PythonApplicationRuntime.current());
            assertEquals(3, outerContext.eval(PYTHON, "1 + 2").asInt(), "the enclosing context is still open");
        }
        assertNull(PythonApplicationRuntime.current());
    }

    /**
     * A platform entry point can reach generated Python code before any application context exists:
     * {@code TestPropertyProvider.getProperties()}, the {@code contextBuilder} of {@code @MicronautTest}
     * and a reflective no-arg instantiation all run before the application starts. The runtime
     * bootstraps a default context for them, and the application that starts next adopts it, so the
     * Python objects created before it are the ones the application sees.
     * <p>
     * The entry point is the one of an application that is on its way: Micronaut Test creates the
     * context builder first and calls {@code TestPropertyProvider.getProperties()} while it fills it
     * in. A call that arrives when no application is starting and none is recorded is a leftover of
     * the application that shut down and gets nothing, which the first half of this test pins.
     */
    @Test
    void anEntryPointOutsideAnApplicationBootstrapsAContextTheApplicationAdopts() {
        try (ApplicationContext previous = ApplicationContext.run()) {
            assertTrue(PythonContextRuntime.isInitialized(), "an application ran in this JVM");
        }
        assertNull(PythonApplicationRuntime.current(), "no application is running");
        assertThrows(IllegalStateException.class, PythonContextRuntime::getContext,
            "the application that shut down leaves no permission to bootstrap a context behind");

        ApplicationContextBuilder builder = ApplicationContext.builder();
        Context bootstrapped = PythonContextRuntime.getContext();
        try {
            assertTrue(PythonContextRuntime.isInitialized(), "the default context is installed");
            assertEquals(3, bootstrapped.eval(PYTHON, "1 + 2").asInt(), "the default context runs Python");
            try (ApplicationContext applicationContext = builder.start()) {
                assertSame(bootstrapped, applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON)),
                    "the application adopts the context bootstrapped before it started");
                assertSame(bootstrapped, PythonContextRuntime.getContext());
                assertTrue(applicationContext.getBean(PythonApplicationRuntime.class).owns(bootstrapped));
            }
            assertNull(PythonApplicationRuntime.current(), "closing the application uninstalls the adopted runtime");
            assertClosed(bootstrapped, "the application closes the context it adopted");
        } finally {
            PythonContextRuntime.resetContext();
        }
    }

    /**
     * Generated code reached after the application that owned the runtime closed fails loudly instead
     * of silently building a Python runtime of its own: a stub held in a JVM-wide singleton, or any
     * other leftover reference, is not a platform entry point running before an application.
     */
    @Test
    void generatedCodeReachedAfterTheApplicationClosedBootstrapsNothing() {
        Context applicationGraalPyContext;
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            applicationGraalPyContext = applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON));
        }
        assertNull(PythonApplicationRuntime.current(), "closing the application uninstalls its runtime");

        IllegalStateException e = assertThrows(IllegalStateException.class, PythonContextRuntime::getContext);

        assertTrue(e.getMessage().startsWith("GraalPy context has not been initialized"), e.getMessage());
        assertFalse(PythonContextRuntime.isInitialized(), "no context was bootstrapped for the leftover reference");
        assertClosed(applicationGraalPyContext, "the context of the application is closed");
    }

    /**
     * A context bootstrapped for an entry point outside an application that no application ever
     * adopts is closed with the engine created for it when it is dropped: nothing else owns either of
     * them, so the engine (native memory and compiler threads) would otherwise live for the life of
     * the JVM.
     */
    @Test
    void aBootstrappedContextNoApplicationAdoptsIsClosedWithItsEngineWhenItIsDropped() {
        ApplicationContext.builder(); // an application is on its way, and its entry point reaches Python
        Context bootstrapped = PythonContextRuntime.getContext();
        Engine engine = bootstrapped.getEngine();

        // the application never starts, as for a test whose application fails to start
        PythonContextRuntime.resetContext();

        assertNull(PythonApplicationRuntime.current());
        assertClosed(bootstrapped, "the bootstrapped context is closed when it is dropped");
        assertThrows(IllegalStateException.class, () -> Context.newBuilder(PYTHON).engine(engine).build(),
            "the engine created for the bootstrapped context is closed with it");
    }

    /**
     * A context closed with cancellation reports its closure as a cancelled polyglot exception; one
     * closed without does as an illegal state.
     */
    private static void assertClosed(Context context, String message) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> context.eval(PYTHON, "1"), message);
        assertTrue(e instanceof IllegalStateException || (e instanceof PolyglotException polyglotException && polyglotException.isCancelled()), message + ": " + e);
    }
}
