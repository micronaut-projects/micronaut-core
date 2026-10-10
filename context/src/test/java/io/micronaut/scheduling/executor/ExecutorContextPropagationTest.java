package io.micronaut.scheduling.executor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.propagation.instrument.execution.ContextPropagatingExecutorService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextConfiguration;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorContextPropagationTest {

    @AfterEach
    void cleanup() {
        PropagatedContextConfiguration.reset();
    }

    @ParameterizedTest
    @ValueSource(strings = {"thread-local", "scoped-value"})
    void namedExecutorRunsTasksWithTheCallersContext(String mode) throws Exception {
        try (ApplicationContext context = run(mode, Map.of(
            "micronaut.executors.single.type", "fixed",
            "micronaut.executors.single.number-of-threads", 1
        ))) {
            ExecutorService executor = context.getBean(ExecutorService.class, Qualifiers.byName("single"));
            assertTrue(ContextPropagatingExecutorService.isInstrumented(executor));

            PropagatedContext propagatedContext = PropagatedContext.getOrEmpty().plus(new TestElement("value"));
            CompletableFuture<String> runnable = new CompletableFuture<>();
            propagatedContext.propagate(() -> executor.execute(() -> runnable.complete(currentValue())));
            assertEquals("value", runnable.get(5, TimeUnit.SECONDS));

            Future<String> callable = propagatedContext.propagate(() -> executor.submit(ExecutorContextPropagationTest::currentValue));
            assertEquals("value", callable.get(5, TimeUnit.SECONDS));

            // the single worker thread doesn't keep the context once the tasks are done
            assertEquals("none", executor.submit(ExecutorContextPropagationTest::currentValue).get(5, TimeUnit.SECONDS));
            assertFalse(PropagatedContext.exists());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"thread-local", "scoped-value"})
    void scheduledExecutorRunsTasksWithTheCallersContext(String mode) throws Exception {
        try (ApplicationContext context = run(mode, Map.of(
            "micronaut.executors.timer.type", "scheduled",
            "micronaut.executors.timer.core-pool-size", 1
        ))) {
            ScheduledExecutorService executor = (ScheduledExecutorService) context.getBean(ExecutorService.class, Qualifiers.byName("timer"));

            PropagatedContext propagatedContext = PropagatedContext.getOrEmpty().plus(new TestElement("scheduled"));
            Future<String> future = propagatedContext.propagate(() ->
                executor.schedule(ExecutorContextPropagationTest::currentValue, 10, TimeUnit.MILLISECONDS));
            assertEquals("scheduled", future.get(5, TimeUnit.SECONDS));

            assertEquals("none", executor.schedule(ExecutorContextPropagationTest::currentValue, 1, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS));

            // as documented, scheduling from an empty context doesn't keep the caller's context
            Future<String> detached = propagatedContext.propagate(() -> PropagatedContext.empty().propagate(() ->
                executor.schedule(ExecutorContextPropagationTest::currentValue, 1, TimeUnit.MILLISECONDS)));
            assertEquals("none", detached.get(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {TaskExecutors.IO, TaskExecutors.SCHEDULED, TaskExecutors.BLOCKING, TaskExecutors.VIRTUAL})
    void builtInExecutorsPropagateTheContext(String name) throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ExecutorService executor = context.getBean(ExecutorService.class, Qualifiers.byName(name));
            assertTrue(ContextPropagatingExecutorService.isInstrumented(executor));

            PropagatedContext propagatedContext = PropagatedContext.getOrEmpty().plus(new TestElement(name));
            Future<String> future = propagatedContext.propagate(() -> executor.submit(ExecutorContextPropagationTest::currentValue));
            assertEquals(name, future.get(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"thread-local", "scoped-value"})
    void propagationCanBeDisabledPerExecutor(String mode) throws Exception {
        try (ApplicationContext context = run(mode, Map.of(
            "micronaut.executors.plain.type", "fixed",
            "micronaut.executors.plain.number-of-threads", 1,
            "micronaut.executors.plain.propagate-context", false
        ))) {
            ExecutorService executor = context.getBean(ExecutorService.class, Qualifiers.byName("plain"));
            assertFalse(ContextPropagatingExecutorService.isInstrumented(executor));

            // other executors are unaffected
            assertTrue(ContextPropagatingExecutorService.isInstrumented(context.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO))));

            PropagatedContext propagatedContext = PropagatedContext.getOrEmpty().plus(new TestElement("value"));
            Future<String> future = propagatedContext.propagate(() -> executor.submit(ExecutorContextPropagationTest::currentValue));
            assertEquals("none", future.get(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"thread-local", "scoped-value"})
    void tasksAreNotWrappedWithoutAContext(String mode) throws Exception {
        try (ApplicationContext context = run(mode, Map.of())) {
            ExecutorService executor = context.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO));
            ContextPropagatingExecutorService instrumented = assertInstanceOf(ContextPropagatingExecutorService.class, executor);
            Runnable task = () -> { };
            assertSame(task, instrumented.instrument(task));
            assertEquals("none", executor.submit(ExecutorContextPropagationTest::currentValue).get(5, TimeUnit.SECONDS));
        }
    }

    private static ApplicationContext run(String mode, Map<String, Object> properties) {
        Map<String, Object> all = new HashMap<>(properties);
        all.put("micronaut.propagation", mode);
        return ApplicationContext.run(all);
    }

    private static String currentValue() {
        return PropagatedContext.find()
            .flatMap(ctx -> ctx.find(TestElement.class))
            .map(TestElement::value)
            .orElse("none");
    }

    record TestElement(String value) implements PropagatedContextElement {
    }
}
