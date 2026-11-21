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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A generation that fails to start is stopped at once, so that nothing it created outlives it, while what was retained
 * from the generation before it waits for the next one. {@link Launch} tells each generation how to fail.
 */
class FailedStartBeansTest {

    private static final String SPEC = "FailedStartBeansTest";
    private static final String BROKEN = "failed-start.broken";
    private static final String BROKEN_STARTUP_LISTENER = "failed-start.broken-startup-listener";
    private static final String BROKEN_WRAP = "failed-start.broken-wrap";
    private static final String BROKEN_SHUTDOWN_LISTENER = "failed-start.broken-shutdown-listener";
    private static final String NO_POOL = "failed-start.no-pool";
    private static final Duration TIMEOUT = Duration.ofMinutes(1);

    @TempDir
    Path project;

    private DevRuntime runtime;
    private volatile Launch next = Launch.RUNS;

    @BeforeEach
    void reset() {
        Pool.CREATED.set(0);
        Pool.DESTROYED.set(0);
        Holder.CREATED.set(0);
        Holder.DESTROYED.set(0);
        Fresh.CREATED.set(0);
        Fresh.DESTROYED.set(0);
        Owned.DESTROYED.set(0);
        ShutdownCounter.HEARD.set(0);
        ClassChangeListener.CREATED.set(0);
        FailingCondition.fail = false;
    }

    @AfterEach
    void close() {
        if (runtime != null) {
            runtime.close();
        }
    }

    @Test
    void aFirstGenerationThatFailsDestroysWhatItCreatedRetainableOrNot() throws Exception {
        next = Launch.FAILS_IN_A_BEAN;
        runtime = runtime("");
        assertNull(runtime.startAwaitingChange(new String[0]));
        assertTrue(runtime.isStartFailed());
        assertEquals(1, Fresh.CREATED.get());
        assertEquals(1, Fresh.DESTROYED.get(), "the beans the failed context created");
        assertEquals(1, Pool.CREATED.get());
        assertEquals(1, Pool.DESTROYED.get(), "a pool the failed context created is not one retained from a generation that ran");

        next = Launch.RUNS;
        runtime.restart();
        ApplicationContext second = runtime.awaitGeneration(2, TIMEOUT);
        assertTrue(second.isRunning());
        assertEquals(2, Pool.CREATED.get());
        assertEquals(1, Pool.DESTROYED.get());
    }

    @Test
    void aRestartThatFailsDestroysWhatItCreatedAndTheNextGenerationAdoptsWhatWasRetained() throws Exception {
        runtime = runtime("");
        ApplicationContext first = runtime.start(new String[0]);
        Pool pool = first.getBean(Pool.class);
        Holder holder = first.getBean(Holder.class);
        assertEquals(1, Fresh.CREATED.get());

        next = Launch.FAILS_IN_A_BEAN;
        failedRestart(2);
        assertEquals(2, Fresh.CREATED.get());
        assertEquals(2, Fresh.DESTROYED.get(), "the first generation's stop, and the failed context");
        assertEquals(1, Pool.CREATED.get());
        assertEquals(0, Pool.DESTROYED.get(), "the failed context did not own the retained pool");

        next = Launch.RUNS;
        runtime.restart();
        ApplicationContext third = runtime.awaitGeneration(3, TIMEOUT);
        assertSame(pool, third.getBean(Pool.class));
        assertSame(holder, third.getBean(Holder.class));
        assertEquals(1, Pool.CREATED.get());
        assertEquals(0, Pool.DESTROYED.get());
        assertEquals(3, Fresh.CREATED.get());
        assertEquals(2, Fresh.DESTROYED.get());

        // retained as any other from here on
        runtime.restart();
        assertSame(pool, runtime.awaitGeneration(4, TIMEOUT).getBean(Pool.class));

        runtime.close();
        assertEquals(1, Pool.CREATED.get());
        assertEquals(1, Pool.DESTROYED.get());
        assertEquals(4, Fresh.DESTROYED.get());
        assertEquals(1, Owned.DESTROYED.get(), "the retained bean's prototype goes with it, once");
    }

    @Test
    void theRetainedBeansWaitThroughEveryWayAGenerationFails() throws Exception {
        runtime = runtime("");
        ApplicationContext first = runtime.start(new String[0]);
        Pool pool = first.getBean(Pool.class);
        Holder holder = first.getBean(Holder.class);
        int generation = 1;
        for (Launch failing : List.of(Launch.FAILS_IN_A_BEAN, Launch.MAIN_FAILS_FIRST, Launch.MAIN_FAILS_AFTER_BUILDING, Launch.MAIN_FAILS_AFTER_CONFIGURING, Launch.FAILS_IN_A_STARTUP_LISTENER,
            Launch.FAILS_WRAPPING_THE_ADOPTED, Launch.FAILS_ADOPTING, Launch.FAILS_IN_A_SHUTDOWN_LISTENER_TOO)) {
            next = failing;
            FailingCondition.fail = failing == Launch.FAILS_ADOPTING;
            generation++;
            failedRestart(generation);
            assertEquals(0, Pool.DESTROYED.get(), failing + " destroyed the retained pool");
            assertEquals(0, Holder.DESTROYED.get(), failing + " destroyed the retained holder");
            assertEquals(1, Pool.CREATED.get(), failing + " created a pool");
            assertEquals(Fresh.CREATED.get(), Fresh.DESTROYED.get(), failing + " left a bean it created alive");
        }
        FailingCondition.fail = false;
        next = Launch.RUNS;
        runtime.restart();
        ApplicationContext running = runtime.awaitGeneration(generation + 1, TIMEOUT);
        assertSame(pool, running.getBean(Pool.class));
        assertSame(holder, running.getBean(Holder.class));
        assertEquals(1, Pool.CREATED.get());
        assertEquals(0, Pool.DESTROYED.get());

        runtime.close();
        assertEquals(1, Pool.DESTROYED.get());
        assertEquals(1, Holder.DESTROYED.get());
        assertEquals(1, Owned.DESTROYED.get());
        assertEquals(Fresh.CREATED.get(), Fresh.DESTROYED.get());
    }

    @Test
    void theShutdownEventOfAFailedContextIsHeard() throws Exception {
        runtime = runtime("");
        runtime.start(new String[0]);
        next = Launch.FAILS_IN_A_BEAN;
        failedRestart(2);
        assertEquals(2, ShutdownCounter.HEARD.get(), "the first generation's stop, and the failed context's");
    }

    @Test
    void aShutdownListenerThatCannotBeCreatedStopsNothingFromBeingDestroyed() throws Exception {
        runtime = runtime("");
        runtime.start(new String[0]);
        next = Launch.FAILS_IN_A_SHUTDOWN_LISTENER_TOO;
        failedRestart(2);
        assertEquals(2, Fresh.CREATED.get());
        assertEquals(2, Fresh.DESTROYED.get());
        assertEquals(0, Pool.DESTROYED.get());
    }

    @Test
    void aRetainedBeanTheFailedContextCouldNotAdoptAndDestroyedIsNotAdoptedAgain() throws Exception {
        runtime = runtime("");
        Pool pool = runtime.start(new String[0]).getBean(Pool.class);

        // this generation has no definition for the pool: it destroys it, then fails
        next = Launch.FAILS_WITH_NO_POOL;
        failedRestart(2);
        assertEquals(1, Pool.DESTROYED.get());
        // what holds the pool cannot be adopted without it
        assertEquals(1, Holder.DESTROYED.get());

        next = Launch.RUNS;
        runtime.restart();
        Pool second = runtime.awaitGeneration(3, TIMEOUT).getBean(Pool.class);
        assertNotSame(pool, second);
        assertEquals(2, Pool.CREATED.get());
        assertEquals(1, Pool.DESTROYED.get());
    }

    @Test
    void aConfigurationFileChangedSinceTheFailureReleasesTheRetainedBeans() throws Exception {
        Path config = Files.createDirectories(project.resolve("src/main/resources")).resolve("application.properties");
        Files.writeString(config, "a=b\n");
        runtime = runtime("micronaut.dev.resources.config=src/main/resources\n");
        Pool pool = runtime.start(new String[0]).getBean(Pool.class);

        next = Launch.FAILS_IN_A_BEAN;
        failedRestart(2);
        assertEquals(0, Pool.DESTROYED.get());

        // no context runs to tell what the edit changed: as it could be what the retained beans were made from, they go
        next = Launch.RUNS;
        Files.writeString(config, "a=c\n");
        runtime.awaitBatch(runtime.enqueue(new Pending(Map.of(), Map.of(ResourceKind.CONFIG, new SourceChanges(Set.of(config), Set.of())), false)));
        Pool second = runtime.awaitGeneration(3, TIMEOUT).getBean(Pool.class);
        assertNotSame(pool, second);
        assertEquals(2, Pool.CREATED.get());
        assertEquals(1, Pool.DESTROYED.get());

        runtime.close();
        assertEquals(2, Pool.DESTROYED.get());
    }

    @Test
    void aBatchThatChangesNoConfigurationKeepsTheRetainedBeans() throws Exception {
        runtime = runtime("");
        Pool pool = runtime.start(new String[0]).getBean(Pool.class);

        next = Launch.FAILS_IN_A_BEAN;
        failedRestart(2);

        next = Launch.RUNS;
        Path file = project.resolve("app.css");
        runtime.awaitBatch(runtime.enqueue(new Pending(Map.of(), Map.of(ResourceKind.STATIC, new SourceChanges(Set.of(file), Set.of())), false)));
        assertSame(pool, runtime.awaitGeneration(3, TIMEOUT).getBean(Pool.class));
        assertEquals(1, Pool.CREATED.get());
        assertEquals(0, Pool.DESTROYED.get());
    }

    @Test
    void closingWhileAGenerationFailedDestroysWhatWasRetainedForTheNext() throws Exception {
        runtime = runtime("");
        runtime.start(new String[0]);

        next = Launch.FAILS_IN_A_BEAN;
        failedRestart(2);
        assertEquals(0, Pool.DESTROYED.get());

        runtime.close();
        assertEquals(1, Pool.DESTROYED.get());
        assertEquals(1, Holder.DESTROYED.get());
        assertEquals(1, Owned.DESTROYED.get());
        assertNull(DevRuntime.current());
    }

    @Test
    void aRuntimeThatFailsItsFirstStartAndClosesLeavesNothingAlive() throws Exception {
        next = Launch.FAILS_IN_A_BEAN;
        runtime = runtime("");
        assertThrows(IllegalStateException.class, () -> runtime.start(new String[0]));
        assertEquals(1, Fresh.DESTROYED.get());
        assertEquals(1, Pool.DESTROYED.get());
    }

    @Test
    void theClassChangeOfAFailedGenerationCreatesNothingInItsStoppedContext() throws Exception {
        runtime = runtime("");
        runtime.start(new String[0]);
        next = Launch.FAILS_IN_A_BEAN;
        failedRestart(2);

        // the listener is only created when a generation is told of a class change: not the stopped context of this one
        next = Launch.RUNS;
        int created = ClassChangeListener.CREATED.get();
        runtime.restart();
        runtime.awaitGeneration(3, TIMEOUT);
        assertEquals(created, ClassChangeListener.CREATED.get());
    }

    private void failedRestart(int generation) {
        runtime.restart();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> runtime.awaitGeneration(generation, TIMEOUT));
        assertTrue(e.getMessage().contains("failed to start"), e.getMessage());
        assertTrue(runtime.isStartFailed());
    }

    private DevRuntime runtime(String extra) throws IOException {
        Files.createDirectories(project.resolve("build/classes"));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            """ + extra);
        DevManifest manifest = DevManifest.load(manifestFile);
        DevClassLoader loader = new DevClassLoader(FailedStartBeansTest.class.getClassLoader(), manifest.reloadableRoots(), project.resolve("build/generations"));
        return new DevRuntime(manifest, loader, (generation, mainClass, args) -> launch(generation), Map.of());
    }

    private void launch(ClassLoader generation) {
        Launch launch = next;
        if (launch == Launch.MAIN_FAILS_FIRST) {
            throw new IllegalStateException("main failed before it built a context");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spec.name", SPEC);
        if (launch.property != null) {
            properties.put(launch.property, true);
        }
        ApplicationContext context = ApplicationContext.builder().classLoader(generation).properties(properties).build();
        if (launch == Launch.MAIN_FAILS_AFTER_BUILDING) {
            throw new IllegalStateException("main failed before it started its context");
        }
        if (launch == Launch.MAIN_FAILS_AFTER_CONFIGURING) {
            context.getEnvironment().start();
            ((ConfigurableBeanContext) context).configure();
            throw new IllegalStateException("main failed with a context it configured and did not start");
        }
        context.start();
    }

    /**
     * How a generation starts, or fails to.
     */
    enum Launch {
        RUNS(null),
        // a bean created as the context starts
        FAILS_IN_A_BEAN(BROKEN),
        // main fails: before any context, or with one that never started
        MAIN_FAILS_FIRST(null),
        MAIN_FAILS_AFTER_BUILDING(null),
        MAIN_FAILS_AFTER_CONFIGURING(null),
        // once the runtime was told the context started
        FAILS_IN_A_STARTUP_LISTENER(BROKEN_STARTUP_LISTENER),
        // the retained beans are adopted, and a listener fails on one
        FAILS_WRAPPING_THE_ADOPTED(BROKEN_WRAP),
        // part way through the adoption
        FAILS_ADOPTING(null),
        // and its shutdown event finds a listener that cannot be created
        FAILS_IN_A_SHUTDOWN_LISTENER_TOO(BROKEN_SHUTDOWN_LISTENER),
        // has no definition for the retained pool, and fails
        FAILS_WITH_NO_POOL(NO_POOL);

        final String property;

        Launch(String property) {
            this.property = property;
        }
    }

    /**
     * What a generation retains: an expensive resource.
     */
    @Singleton
    @Retain
    @Requires(property = "spec.name", value = SPEC)
    @Requires(missingProperty = NO_POOL)
    static class Pool {
        static final AtomicInteger CREATED = new AtomicInteger();
        static final AtomicInteger DESTROYED = new AtomicInteger();

        Pool() {
            CREATED.incrementAndGet();
        }

        @PreDestroy
        void close() {
            DESTROYED.incrementAndGet();
        }
    }

    /**
     * A prototype a retained bean owns, whose definition is looked up as the bean is adopted.
     */
    @Prototype
    @Requires(condition = FailingCondition.class)
    static class Owned {
        static final AtomicInteger DESTROYED = new AtomicInteger();

        @PreDestroy
        void close() {
            DESTROYED.incrementAndGet();
        }
    }

    /**
     * Fails where a context asks whether a retained bean's prototype is enabled, as it adopts the bean.
     */
    public static final class FailingCondition implements Condition {
        static volatile boolean fail;

        @Override
        public boolean matches(ConditionContext context) {
            if (fail) {
                throw new IllegalStateException("adoption failed");
            }
            return true;
        }
    }

    /**
     * A second retained bean, which holds the first and owns a prototype.
     */
    @Singleton
    @Retain
    @Requires(property = "spec.name", value = SPEC)
    static class Holder {
        static final AtomicInteger CREATED = new AtomicInteger();
        static final AtomicInteger DESTROYED = new AtomicInteger();
        final Pool pool;
        final Owned owned;

        Holder(Pool pool, Owned owned) {
            this.pool = pool;
            this.owned = owned;
            CREATED.incrementAndGet();
        }

        @PreDestroy
        void close() {
            DESTROYED.incrementAndGet();
        }
    }

    /**
     * A third, which holds the second: destroyed before it, and so adopted before it.
     */
    @Singleton
    @Retain
    @Requires(property = "spec.name", value = SPEC)
    static class Outer {
        final Holder holder;

        Outer(Holder holder) {
            this.holder = holder;
        }
    }

    /**
     * What each context creates of its own, as it starts: not retained, so it dies with its context.
     */
    @Context
    @Requires(property = "spec.name", value = SPEC)
    static class Fresh {
        static final AtomicInteger CREATED = new AtomicInteger();
        static final AtomicInteger DESTROYED = new AtomicInteger();

        Fresh(Outer outer) {
            CREATED.incrementAndGet();
        }

        @PreDestroy
        void close() {
            DESTROYED.incrementAndGet();
        }
    }

    /**
     * Fails the start of its context, once it has what the context created for it.
     */
    @Context
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = BROKEN)
    static class Broken {
        Broken(Fresh fresh) {
            throw new IllegalStateException("broken");
        }
    }

    /**
     * Also fails the start of its context, so that {@link BrokenShutdownListener} is what is left to create.
     */
    @Context
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = BROKEN_SHUTDOWN_LISTENER)
    static class BrokenWithAListener {
        BrokenWithAListener(Fresh fresh) {
            throw new IllegalStateException("broken");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = BROKEN_STARTUP_LISTENER)
    static class BrokenStartupListener implements ApplicationEventListener<StartupEvent> {
        @Override
        public void onApplicationEvent(StartupEvent event) {
            throw new IllegalStateException("broken startup listener");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = BROKEN_WRAP)
    static class BrokenWrap implements BeanCreatedEventListener<Pool> {
        @Override
        public Pool onCreated(BeanCreatedEvent<Pool> event) {
            throw new IllegalStateException("broken listener of the pool");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = BROKEN_SHUTDOWN_LISTENER)
    static class BrokenShutdownListener implements ApplicationEventListener<ShutdownEvent> {
        BrokenShutdownListener() {
            throw new IllegalStateException("broken shutdown listener");
        }

        @Override
        public void onApplicationEvent(ShutdownEvent event) {
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class ShutdownCounter implements ApplicationEventListener<ShutdownEvent> {
        static final AtomicInteger HEARD = new AtomicInteger();

        @Override
        public void onApplicationEvent(ShutdownEvent event) {
            HEARD.incrementAndGet();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = BROKEN)
    static class ClassChangeListener implements ApplicationEventListener<ClassChangeEvent> {
        static final AtomicInteger CREATED = new AtomicInteger();

        ClassChangeListener() {
            CREATED.incrementAndGet();
        }

        @Override
        public void onApplicationEvent(ClassChangeEvent event) {
        }
    }
}
