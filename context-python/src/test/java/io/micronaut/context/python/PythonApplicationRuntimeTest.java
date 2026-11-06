package io.micronaut.context.python;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * Every test starts from, and leaves behind, a JVM in which an application has run and none is on
     * its way: the permission to bootstrap a context is JVM-wide state.
     */
    @BeforeEach
    @AfterEach
    void noApplicationIsOnItsWay() {
        PythonContextRuntime.setReuseContext(false);
        PythonContextRuntime.resetContext();
    }

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
     * Before the first application of the JVM nothing can be a leftover reference, so a reflective
     * no-arg instantiation gets a context without any builder on its way, and the application that
     * starts next adopts it.
     */
    @Test
    void anEntryPointBeforeTheFirstApplicationOfTheJvmBootstrapsAContext() {
        PythonEntryPointPermission.reset();

        Context bootstrapped = PythonContextRuntime.getContext();

        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            assertSame(bootstrapped, applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON)));
        }
        assertClosed(bootstrapped, "the application closes the context it adopted");
        assertLeftoverReferenceFails();
    }

    /**
     * A builder that is created and abandoned, which is what a test that builds a configuration and
     * never starts it does, stops being an application on its way once it is collected: a leftover
     * reference reached afterwards fails instead of building a GraalPy context and an engine that only
     * a reset would ever close.
     */
    @Test
    void anAbandonedBuilderLeavesNoPermissionBehindOnceItIsCollected() throws InterruptedException {
        WeakReference<ApplicationContextBuilder> abandoned = new WeakReference<>(ApplicationContext.builder());

        for (int i = 0; i < 100 && abandoned.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }

        assertNull(abandoned.get(), "the builder is collected");
        assertLeftoverReferenceFails();
    }

    /**
     * A builder grants the entry points of its own application, which the platform runs on the thread
     * that fills the builder in: a leftover reference of another application reached on another thread
     * in the meantime gets nothing.
     */
    @Test
    void aBuilderGrantsNothingToAnotherThread() throws InterruptedException {
        ApplicationContextBuilder onItsWay = ApplicationContext.builder();

        AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
        Thread leftover = new Thread(PythonApplicationRuntimeTest::assertLeftoverReferenceFails);
        leftover.setUncaughtExceptionHandler((thread, e) -> failure.set(e));
        leftover.start();
        leftover.join();

        assertNull(failure.get(), () -> "the other thread bootstrapped a context: " + failure.get());
        Reference.reachabilityFence(onItsWay);
    }

    /**
     * A builder created while another application runs is on its way however that application ends:
     * the shutdown of the running application takes nothing from it, so its entry point still gets the
     * context its application adopts.
     */
    @Test
    void aBuilderCreatedWhileAnApplicationRunsKeepsItsPermissionWhenThatApplicationCloses() {
        ApplicationContextBuilder next;
        try (ApplicationContext running = ApplicationContext.run()) {
            assertTrue(running.isRunning());
            next = ApplicationContext.builder();
        }
        assertNull(PythonApplicationRuntime.current(), "no application is running");

        Context bootstrapped = PythonContextRuntime.getContext();

        try (ApplicationContext applicationContext = next.start()) {
            assertSame(bootstrapped, applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON)),
                "the application adopts the context its entry point was given");
        }
        assertClosed(bootstrapped, "the application closes the context it adopted");
    }

    /**
     * A builder that produced its application has nothing on its way any more, even though the
     * application context keeps it reachable and whichever thread started it.
     */
    @Test
    void aBuilderThatProducedItsApplicationGrantsNothingAfterThatApplicationClosed() throws InterruptedException {
        ApplicationContextBuilder builder = ApplicationContext.builder();

        AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
        Thread starter = new Thread(() -> builder.start().close());
        starter.setUncaughtExceptionHandler((thread, e) -> failure.set(e));
        starter.start();
        starter.join();

        assertNull(failure.get(), () -> "the application failed: " + failure.get());
        assertLeftoverReferenceFails();
        Reference.reachabilityFence(builder);
    }

    /**
     * Micronaut Test creates a builder and replaces it with the one of a {@code contextBuilder}: the
     * newest builder of a thread is the one on its way, so the replaced one grants nothing once the
     * application of the newest closed, even before it is collected.
     */
    @Test
    void aBuilderThatWasReplacedOnItsThreadGrantsNothingAfterTheApplicationOfTheNewerOneClosed() {
        ApplicationContextBuilder replaced = ApplicationContext.builder();
        ApplicationContextBuilder newest = ApplicationContext.builder();

        newest.start().close();

        assertLeftoverReferenceFails();
        Reference.reachabilityFence(replaced);
    }

    /**
     * A reset takes the JVM back to no application running, which grants nothing: generated code
     * reached after it is a leftover reference exactly as it was before the reset.
     */
    @Test
    void aResetAfterAnApplicationClosedGrantsNothing() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            assertTrue(applicationContext.isRunning());
        }
        assertLeftoverReferenceFails();

        PythonContextRuntime.resetContext();

        assertLeftoverReferenceFails();
    }

    /**
     * A reset also ends what a JVM with no application yet allows: a reference held across it is a
     * leftover reference of the context the reset closed.
     */
    @Test
    void aResetBeforeTheFirstApplicationOfTheJvmGrantsNothing() {
        PythonEntryPointPermission.reset();
        Context bootstrapped = PythonContextRuntime.getContext();

        PythonContextRuntime.resetContext();

        assertClosed(bootstrapped, "the reset closes the context no application adopted");
        assertLeftoverReferenceFails();
    }

    /**
     * A reset also ends what the calling thread had on its way, so a builder it abandoned is not an
     * application on its way until the collector gets to it.
     */
    @Test
    void aResetRevokesTheBuilderOfTheCallingThread() {
        ApplicationContextBuilder abandoned = ApplicationContext.builder();

        PythonContextRuntime.resetContext();

        assertLeftoverReferenceFails();
        Reference.reachabilityFence(abandoned);
    }

    /**
     * A reset that only reloads the modules of a reused context ends the builder of the calling thread
     * all the same.
     */
    @Test
    void aResetWithContextReuseRevokesTheBuilderOfTheCallingThread() {
        ApplicationContextBuilder abandoned = ApplicationContext.builder();
        try (Context reused = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonApplicationRuntime runtime = PythonContextRuntime.setContext(reused, null);
            PythonContextRuntime.setReuseContext(true);

            PythonContextRuntime.resetContext();

            PythonContextRuntime.setReuseContext(false);
            assertTrue(PythonApplicationRuntime.uninstall(runtime));
            assertLeftoverReferenceFails();
        }
        Reference.reachabilityFence(abandoned);
    }

    /**
     * With context reuse the context belongs to the JVM rather than to an application: the applications
     * that use a bootstrapped context neither adopt it nor close it. It stays the bootstrapped one, so
     * it is closed with its engine when it is finally dropped, since no application ever will.
     */
    @Test
    void contextReuseKeepsABootstrappedContextOutOfEveryApplication() {
        ApplicationContextBuilder builder = ApplicationContext.builder();
        Context bootstrapped = PythonContextRuntime.getContext();
        Engine engine = bootstrapped.getEngine();
        PythonContextRuntime.setReuseContext(true);
        try {
            try (ApplicationContext first = builder.start()) {
                assertSame(bootstrapped, first.getBean(Context.class, Qualifiers.byName(PYTHON)));
            }
            assertEquals(3, bootstrapped.eval(PYTHON, "1 + 2").asInt(), "the application leaves the reused context open");
            assertSame(bootstrapped, PythonContextRuntime.getContext(), "the reused context stays installed");
            try (ApplicationContext second = ApplicationContext.run()) {
                assertSame(bootstrapped, second.getBean(Context.class, Qualifiers.byName(PYTHON)));
            }
            PythonContextRuntime.resetContext();
            assertSame(bootstrapped, PythonContextRuntime.getContext(), "with reuse a reset only reloads the modules");
        } finally {
            PythonContextRuntime.setReuseContext(false);
        }

        PythonContextRuntime.resetContext();

        assertNull(PythonApplicationRuntime.current());
        assertClosed(bootstrapped, "the context is closed when it is dropped");
        assertThrows(IllegalStateException.class, () -> Context.newBuilder(PYTHON).engine(engine).build(),
            "the engine created for the context is closed with it");
    }

    /**
     * The configuration of an application that adopts a bootstrapped context was not applied to that
     * context: the adoption still happens, and the application is told what it lost.
     */
    @Test
    void anApplicationThatAdoptsAContextIsWarnedAboutTheConfigurationNotAppliedToIt() {
        ListAppender<ILoggingEvent> warnings = captureWarnings(GraalPyContextFactory.class);
        try {
            ApplicationContextBuilder builder = ApplicationContext.builder().properties(Map.of(
                "graalpy.context.options.python.DontWriteBytecodeFlag", "true",
                "graalpy.context.host-class-lookup", List.of("com.example")
            ));
            Context bootstrapped = PythonContextRuntime.getContext();
            try (ApplicationContext applicationContext = builder.start()) {
                assertSame(bootstrapped, applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON)),
                    "the application adopts the context despite its configuration");
            }
            assertEquals(1, warnings.list.size(), () -> warnings.list.toString());
            String warning = warnings.list.getFirst().getFormattedMessage();
            assertTrue(warning.contains("graalpy.context.options [python.DontWriteBytecodeFlag]"), warning);
            assertTrue(warning.contains("graalpy.context.host-class-lookup [com.example]"), warning);
            assertTrue(warning.contains("are not applied"), warning);
        } finally {
            stopCapturing(GraalPyContextFactory.class, warnings);
        }
    }

    /**
     * An application with nothing of its own to lose adopts the context silently.
     */
    @Test
    void anApplicationWithTheDefaultConfigurationAdoptsAContextWithoutAWarning() {
        ListAppender<ILoggingEvent> warnings = captureWarnings(GraalPyContextFactory.class);
        try {
            ApplicationContextBuilder builder = ApplicationContext.builder();
            Context bootstrapped = PythonContextRuntime.getContext();
            try (ApplicationContext applicationContext = builder.start()) {
                assertSame(bootstrapped, applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON)));
            }
            assertTrue(warnings.list.isEmpty(), () -> warnings.list.toString());
        } finally {
            stopCapturing(GraalPyContextFactory.class, warnings);
        }
    }

    /**
     * A context bootstrapped by another class loader is not the one this application would have
     * built: the application builds its own, is told so, and the first context stays with the entry
     * point until it is dropped.
     */
    @Test
    void anApplicationFromAnotherClassLoaderBuildsItsOwnContextAndIsWarned() {
        ListAppender<ILoggingEvent> warnings = captureWarnings(PythonApplicationRuntime.class);
        try {
            ApplicationContextBuilder builder = ApplicationContext.builder()
                .classLoader(new URLClassLoader(new URL[0], PythonApplicationRuntimeTest.class.getClassLoader()));
            Context bootstrapped = PythonContextRuntime.getContext();
            try (ApplicationContext applicationContext = builder.start()) {
                Context own = applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON));
                assertNotSame(bootstrapped, own, "the application builds a context of its own");
                assertSame(own, PythonContextRuntime.getContext());
            }
            assertEquals(1, warnings.list.size(), () -> warnings.list.toString());
            assertTrue(warnings.list.getFirst().getFormattedMessage().contains("builds a second context"), () -> warnings.list.toString());
            assertSame(bootstrapped, PythonContextRuntime.getContext(), "the first context stays with the entry point");
            assertEquals(3, bootstrapped.eval(PYTHON, "1 + 2").asInt());
        } finally {
            stopCapturing(PythonApplicationRuntime.class, warnings);
        }
    }

    private static void assertLeftoverReferenceFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class, PythonContextRuntime::getContext,
            "a leftover reference bootstrapped a context");
        assertTrue(e.getMessage().startsWith("GraalPy context has not been initialized"), e.getMessage());
        assertFalse(PythonContextRuntime.isInitialized(), "no context was bootstrapped for the leftover reference");
    }

    private static ListAppender<ILoggingEvent> captureWarnings(Class<?> loggingClass) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.addFilter(new ThresholdFilter() {{
            setLevel("WARN");
            start();
        }});
        appender.start();
        ((Logger) LoggerFactory.getLogger(loggingClass)).addAppender(appender);
        return appender;
    }

    private static void stopCapturing(Class<?> loggingClass, ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(loggingClass)).detachAppender(appender);
        appender.stop();
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
