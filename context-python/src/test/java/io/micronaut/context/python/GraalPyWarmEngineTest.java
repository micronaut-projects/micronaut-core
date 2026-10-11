/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test mode of a development launcher keeps the GraalPy engine warm across the application contexts the tests of
 * one class loader generation start, and a context of another generation retires it.
 */
final class GraalPyWarmEngineTest {

    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(30);

    @AfterEach
    void reset() {
        GraalPyWarmEngine.reset();
    }

    @Test
    void theContextsTheTestsOfAGenerationStartShareTheWarmEngine() {
        Engine first;
        HostAccess firstAccess;
        try (ApplicationContext context = run(testMode(), null)) {
            first = engine(context);
            firstAccess = hostAccess(context);
            assertEquals(2, eval(context, "1 + 1"));
        }
        // the context closed, the engine stays open for the next one
        assertTrue(isOpen(first, firstAccess), "the warm engine was closed with the first context");
        try (ApplicationContext context = run(testMode(), null)) {
            assertSame(first, engine(context));
            assertSame(firstAccess, hostAccess(context));
            assertEquals(3, eval(context, "1 + 2"));
        }
    }

    @Test
    void switchedOffEveryContextCreatesAndClosesItsOwnEngine() {
        Map<String, Object> properties = testMode();
        properties.put(GraalPyWarmEngine.PROPERTY, false);
        assertEachContextHasItsOwnEngine(properties);
    }

    @Test
    void outsideTestModeNothingIsKeptWarm() {
        assertEachContextHasItsOwnEngine(new HashMap<>());
    }

    @Test
    void developmentModeKeepsNothingWarm() {
        // a generation of development mode starts one context, with nothing to share its engine with
        assertEachContextHasItsOwnEngine(new HashMap<>(Map.of(DevelopmentMode.PROPERTY, true)));
    }

    @Test
    void changedEngineConfigurationReplacesTheWarmEngine() throws InterruptedException {
        Engine first;
        HostAccess firstAccess;
        try (ApplicationContext context = run(testMode(), null)) {
            first = engine(context);
            firstAccess = hostAccess(context);
        }
        Map<String, Object> properties = testMode();
        properties.put("graalpy.engine.options", Map.of("engine.WarnInterpreterOnly", "false"));
        try (ApplicationContext context = run(properties, null)) {
            assertNotSame(first, engine(context));
            assertEquals(2, eval(context, "1 + 1"));
        }
        awaitClosed(first, firstAccess);
    }

    @Test
    void aContextOfAnotherGenerationRetiresTheWarmEngineAndItsGeneration() throws InterruptedException {
        ClassLoader parent = GraalPyWarmEngineTest.class.getClassLoader();
        URLClassLoader second = new URLClassLoader("generation-2", new URL[0], parent);
        WeakReference<ClassLoader> retired;
        WeakReference<Engine> retiredEngine;
        Engine first;
        HostAccess firstAccess;
        {
            URLClassLoader generation = new URLClassLoader("generation-1", new URL[0], parent);
            try (ApplicationContext context = run(testMode(), generation)) {
                first = engine(context);
                firstAccess = hostAccess(context);
                assertEquals(2, eval(context, "1 + 1"));
            }
            retired = new WeakReference<>(generation);
            retiredEngine = new WeakReference<>(first);
        }
        try (ApplicationContext context = run(testMode(), second)) {
            Engine engine = engine(context);
            assertNotSame(first, engine);
            assertEquals(2, eval(context, "1 + 1"));
            // the new generation's engine is the warm one now
            try (ApplicationContext next = run(testMode(), second)) {
                assertSame(engine, engine(next));
            }
        }
        awaitClosed(first, firstAccess);
        first = null;
        firstAccess = null;
        awaitCollected(retiredEngine, "the engine of the retired generation");
        awaitCollected(retired, "the class loader of the retired generation");
    }

    @Test
    void aStatefulTargetTypeMappingGetsAnEngineOfItsOwn() {
        List<TargetTypeMapping<?>> stateful = List.of(new StatefulMapping("state"));
        ClassLoader loader = GraalPyWarmEngineTest.class.getClassLoader();
        HostAccess first = GraalPyWarmEngine.hostAccess(loader, stateful, List.of(), () -> HostAccess.ALL);
        HostAccess second = GraalPyWarmEngine.hostAccess(loader, stateful, List.of(), () -> HostAccess.EXPLICIT);
        assertNotSame(first, second, "a host access converting with a stateful mapping was shared");
        try (Engine engine = GraalPyWarmEngine.engine(second, Map.of(), Engine::create)) {
            assertFalse(GraalPyWarmEngine.isWarm(engine), "the engine of a stateful mapping was kept warm");
        }
    }

    private static void assertEachContextHasItsOwnEngine(Map<String, Object> properties) {
        Engine first;
        HostAccess firstAccess;
        try (ApplicationContext context = run(properties, null)) {
            first = engine(context);
            firstAccess = hostAccess(context);
            assertEquals(2, eval(context, "1 + 1"));
        }
        try (ApplicationContext context = run(properties, null)) {
            assertNotSame(first, engine(context));
        }
        try {
            awaitClosed(first, firstAccess);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static Map<String, Object> testMode() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(DevelopmentMode.TEST_PROPERTY, true);
        properties.put("micronaut.python.pool.enabled", false);
        return properties;
    }

    private static ApplicationContext run(Map<String, Object> properties, ClassLoader classLoader) {
        var builder = ApplicationContext.builder().properties(properties);
        if (classLoader != null) {
            builder.classLoader(classLoader);
        }
        return builder.start();
    }

    private static Engine engine(ApplicationContext context) {
        return context.getBean(Engine.class, Qualifiers.byName(PYTHON));
    }

    private static HostAccess hostAccess(ApplicationContext context) {
        return context.getBean(HostAccess.class, Qualifiers.byName(PYTHON));
    }

    private static int eval(ApplicationContext context, String expression) {
        Value value = context.getBean(Context.class, Qualifiers.byName(PYTHON)).eval(PYTHON, expression);
        return value.asInt();
    }

    private static boolean isOpen(Engine engine, HostAccess hostAccess) {
        // an engine takes one host access for all its contexts
        try (Context probe = Context.newBuilder(PYTHON).engine(engine).allowHostAccess(hostAccess).build()) {
            return probe.eval(PYTHON, "1").asInt() == 1;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static void awaitClosed(Engine engine, HostAccess hostAccess) throws InterruptedException {
        long deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && isOpen(engine, hostAccess)) {
            Thread.sleep(100);
        }
        assertFalse(isOpen(engine, hostAccess), "the engine is still open");
    }

    private static void awaitCollected(WeakReference<?> reference, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (reference.get() != null && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(200);
        }
        assertNull(reference.get(), what + " is still reachable");
    }

    private static final class StatefulMapping implements TargetTypeMapping<String> {
        private final String state;

        private StatefulMapping(String state) {
            this.state = state;
        }

        @Override
        public Class<String> targetType() {
            return String.class;
        }

        @Override
        public String convert(Value value) {
            return state;
        }
    }
}
