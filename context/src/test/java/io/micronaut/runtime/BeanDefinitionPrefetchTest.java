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
package io.micronaut.runtime;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.BeanContextConfiguration;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.runtime.prefetch.FixtureReference;
import io.micronaut.runtime.prefetch.PrefetchProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the task of the bean definition prefetch against Micronaut's own provider. The fixture
 * references are defined again by a class loader made for one test, so every test sees their
 * static initializers run, and each class loader registers only the fixtures its test names. It
 * also sees the references of the test class path, which both sides of a comparison read alike.
 */
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class BeanDefinitionPrefetchTest {
    static final String REFERENCES = "META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/";
    static final String FIRST = FixtureReference.First.class.getName();
    static final String SECOND = FixtureReference.Second.class.getName();
    static final String FAILS_AT_RUNTIME = FixtureReference.FailsAtRuntime.class.getName();
    static final String FAILS_WITH_NO_SUCH_FIELD = FixtureReference.FailsWithNoSuchField.class.getName();
    static final String FAILS_TO_LINK = FixtureReference.FailsToLink.class.getName();
    private static final String BLOCKS = FixtureReference.Blocks.class.getName();
    private static final Set<String> FIXTURES = Set.of(FIRST, SECOND, FAILS_AT_RUNTIME, FAILS_WITH_NO_SUCH_FIELD, FAILS_TO_LINK, BLOCKS);
    private static final String WORKER_PREFIX = "ForkJoinPool.commonPool-worker-";

    @TempDir
    Path temp;

    private final List<URLClassLoader> loaders = new ArrayList<>();

    @BeforeEach
    void reset() {
        PrefetchProbe.reset();
    }

    @AfterEach
    void close() throws IOException {
        // A fixture left blocked would hold a common pool thread for the rest of the test JVM
        PrefetchProbe.release();
        for (URLClassLoader loader : loaders) {
            loader.close();
        }
    }

    @Test
    void handsOverWhatTheDefaultProviderReturnsOnceAndThenDelegatesToIt() throws Exception {
        URLClassLoader loader = loader(FIRST, SECOND);
        URLClassLoader other = loader(FIRST, SECOND);
        BeanDefinitionPrefetch task = done(launch(loader, loader));
        List<PrefetchProbe.Event> initialized = PrefetchProbe.events(PrefetchProbe.INITIALIZED, FIRST);
        assertEquals(1, initialized.size());
        assertTrue(initialized.getFirst().thread().getName().startsWith(WORKER_PREFIX), initialized::toString);

        // Another class loader first: it gets its own references and leaves the result alone
        List<BeanDefinitionReference<?>> elsewhere = task.provide(other);
        assertSame(other, reference(elsewhere, FIRST).getClass().getClassLoader());
        List<BeanDefinitionReference<?>> handedOver = task.provide(loader);

        List<BeanDefinitionReference<?>> expected = new DefaultBeanDefinitionsProvider().provide(loader);
        assertEquals(names(expected), names(handedOver));
        assertTrue(names(handedOver).containsAll(List.of(FIRST, SECOND)), names(handedOver)::toString);
        assertSame(loader, reference(handedOver, FIRST).getClass().getClassLoader());
        // Once by the prefetch, once for the other class loader and once for the expectation: the hand-over constructed nothing
        assertEquals(3, PrefetchProbe.events(PrefetchProbe.CONSTRUCTED, FIRST).size());

        // A second call, as for a later context or after a reset, goes to the default provider
        List<BeanDefinitionReference<?>> again = task.provide(loader);
        assertEquals(names(expected), names(again));
        assertNotSame(reference(handedOver, FIRST), reference(again, FIRST));
        assertEquals(4, PrefetchProbe.events(PrefetchProbe.CONSTRUCTED, FIRST).size());
        assertNull(task.giveUp(), "a task that was handed over has nothing to give up");
    }

    @Test
    void aContextReadsItsReferencesAgainFromTheDefaultProviderAfterAReset() throws Exception {
        ApplicationContextBuilder builder = ApplicationContext.builder();
        ClassLoader loader = ((BeanContextConfiguration) builder).getClassLoader();
        BeanDefinitionPrefetch task = done(launch(loader, loader));
        builder.beanDefinitionsProvider(task);

        try (ApplicationContext context = builder.build(); ApplicationContext plain = ApplicationContext.builder().build()) {
            assertEquals(names(plain.getBeanDefinitionReferences()), names(context.getBeanDefinitionReferences()));
            context.start();
            List<BeanDefinitionReference<?>> handedOver = List.copyOf(context.getBeanDefinitionReferences());
            context.stop();
            context.start();

            List<BeanDefinitionReference<?>> reread = List.copyOf(context.getBeanDefinitionReferences());
            assertEquals(names(handedOver), names(reread));
            assertNotSame(handedOver.getFirst(), reread.getFirst());
        }
    }

    /**
     * A reference whose static initializer throws stops the application: the context gets the
     * exception that the default provider threw on the task's thread, with the very exception of
     * the initializer as the cause of the {@link ExceptionInInitializerError}.
     */
    @Test
    void rethrowsTheVeryExceptionOfAFailingInitializer() throws Exception {
        URLClassLoader loader = loader(FIRST, FAILS_AT_RUNTIME);
        BeanDefinitionPrefetch task = done(launch(loader, loader));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> task.provide(loader));

        ExceptionInInitializerError initializer = assertInstanceOf(ExceptionInInitializerError.class, thrown.getCause());
        assertNotNull(PrefetchProbe.thrown(FAILS_AT_RUNTIME));
        assertSame(PrefetchProbe.thrown(FAILS_AT_RUNTIME), initializer.getCause());
        assertEquals(1, PrefetchProbe.events(PrefetchProbe.INITIALIZED, FAILS_AT_RUNTIME).size());
        // What the default provider throws on a class loader that has not met the fixture yet
        URLClassLoader fresh = loader(FIRST, FAILS_AT_RUNTIME);
        DefaultBeanDefinitionsProvider provider = new DefaultBeanDefinitionsProvider();
        RuntimeException expected = assertThrows(RuntimeException.class, () -> provider.provide(fresh));
        assertEquals(expected.getClass(), thrown.getClass());
        assertEquals(expected.getMessage(), thrown.getMessage());
        assertEquals(expected.getCause().getClass(), thrown.getCause().getClass());
        assertEquals(expected.getCause().getCause().getMessage(), thrown.getCause().getCause().getMessage());
    }

    @Test
    void rethrowsTheErrorOfAFailingInitializer() throws Exception {
        URLClassLoader loader = loader(FIRST, FAILS_WITH_NO_SUCH_FIELD);
        BeanDefinitionPrefetch task = done(launch(loader, loader));

        Throwable thrown = assertThrows(Throwable.class, () -> task.provide(loader));

        URLClassLoader fresh = loader(FIRST, FAILS_WITH_NO_SUCH_FIELD);
        DefaultBeanDefinitionsProvider provider = new DefaultBeanDefinitionsProvider();
        Throwable expected = assertThrows(Throwable.class, () -> provider.provide(fresh));
        assertEquals(chain(expected), chain(thrown));
        assertTrue(chain(thrown).contains(NoSuchFieldError.class.getName() + ": probe field of " + FAILS_WITH_NO_SUCH_FIELD), chain(thrown)::toString);
    }

    /**
     * A reference whose static initializer throws {@link NoClassDefFoundError} is one that the
     * default provider skips, and the prefetch skips it too, because the default provider did.
     */
    @Test
    void skipsAReferenceThatTheDefaultProviderSkips() throws Exception {
        URLClassLoader loader = loader(FIRST, FAILS_TO_LINK, SECOND);
        BeanDefinitionPrefetch task = done(launch(loader, loader));

        List<String> handedOver = names(task.provide(loader));

        assertEquals(1, PrefetchProbe.events(PrefetchProbe.INITIALIZED, FAILS_TO_LINK).size());
        assertTrue(handedOver.containsAll(List.of(FIRST, SECOND)), handedOver::toString);
        assertFalse(handedOver.contains(FAILS_TO_LINK), handedOver::toString);
        URLClassLoader fresh = loader(FIRST, FAILS_TO_LINK, SECOND);
        assertEquals(names(new DefaultBeanDefinitionsProvider().provide(fresh)), handedOver);
    }

    /**
     * The thread that runs the task gets its own context class loader back, also when the task
     * fails. On the common pool the worker would reset it anyway, so this runs the task on the
     * thread of the test, as a join from the main thread can.
     */
    @Test
    void putsTheContextClassLoaderBack() throws Exception {
        URLClassLoader captured = loader();
        Thread thread = Thread.currentThread();
        ClassLoader own = thread.getContextClassLoader();
        assertNotSame(captured, own);

        BeanDefinitionPrefetch task = new BeanDefinitionPrefetch(captured, loader(FIRST));
        task.invoke();
        assertSame(own, thread.getContextClassLoader());

        URLClassLoader failing = loader(FAILS_AT_RUNTIME);
        BeanDefinitionPrefetch failed = new BeanDefinitionPrefetch(captured, failing);
        failed.invoke();
        assertSame(own, thread.getContextClassLoader());
        assertThrows(RuntimeException.class, () -> failed.provide(failing));
    }

    @Test
    void aFailedTaskThatWasGivenUpReportsItsFailureOnce() throws Exception {
        URLClassLoader loader = loader(FIRST, FAILS_AT_RUNTIME);
        BeanDefinitionPrefetch task = done(launch(loader, loader));

        Throwable failure = task.giveUp();

        assertNotNull(failure);
        assertSame(PrefetchProbe.thrown(FAILS_AT_RUNTIME), failure.getCause().getCause());
        assertNull(task.giveUp());
        // The class loader has met the failure already: the default provider now skips the fixture
        assertFalse(names(task.provide(loader)).contains(FAILS_AT_RUNTIME));
    }

    @Test
    void aTaskThatWasGivenUpLeavesTheReferencesToTheDefaultProvider() throws Exception {
        URLClassLoader loader = loader(FIRST);
        BeanDefinitionPrefetch task = done(launch(loader, loader));

        assertNull(task.giveUp());

        List<BeanDefinitionReference<?>> references = task.provide(loader);
        assertTrue(names(references).contains(FIRST));
        // The prefetch constructed it once, and the default provider again
        assertEquals(2, PrefetchProbe.events(PrefetchProbe.CONSTRUCTED, FIRST).size());
    }

    /**
     * Giving up never waits for the task, and a task that was still running reports nothing and
     * hands nothing over once it finishes.
     */
    @Test
    void aRunningTaskThatWasGivenUpDropsItsResult() throws Exception {
        URLClassLoader loader = loader(FIRST, BLOCKS);
        BeanDefinitionPrefetch task = launch(loader, loader);
        assertTrue(PrefetchProbe.awaitEntered(), "the task never reached the blocking fixture");

        assertNull(task.giveUp());
        assertFalse(task.isDone());

        PrefetchProbe.release();
        assertSame(task, done(task));
        assertNull(held(task), "the task kept the references it gave up");
        assertNull(task.giveUp());
        assertTrue(names(task.provide(loader)).contains(FIRST));
        assertEquals(2, PrefetchProbe.events(PrefetchProbe.CONSTRUCTED, FIRST).size());
    }

    /**
     * Submits a task to the common pool and returns without waiting for it.
     */
    private static BeanDefinitionPrefetch launch(ClassLoader contextClassLoader, ClassLoader classLoader) {
        BeanDefinitionPrefetch task = new BeanDefinitionPrefetch(contextClassLoader, classLoader);
        ForkJoinPool.commonPool().execute(task);
        return task;
    }

    /**
     * Waits for a task without joining it: a join from this thread could run the task here
     * instead of on the common pool.
     */
    private static BeanDefinitionPrefetch done(BeanDefinitionPrefetch task) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!task.isDone()) {
            assertTrue(System.nanoTime() < deadline, "the prefetch did not finish");
            Thread.sleep(2);
        }
        return task;
    }

    /**
     * The references the task still holds, read without going through its API.
     */
    private static Object held(BeanDefinitionPrefetch task) throws ReflectiveOperationException {
        Field result = BeanDefinitionPrefetch.class.getDeclaredField("result");
        result.setAccessible(true);
        return result.get(task);
    }

    static List<String> names(Collection<? extends BeanDefinitionReference<?>> references) {
        List<String> names = new ArrayList<>(references.size());
        for (BeanDefinitionReference<?> reference : references) {
            names.add(reference.getClass().getName());
        }
        return names;
    }

    private static BeanDefinitionReference<?> reference(List<BeanDefinitionReference<?>> references, String name) {
        for (BeanDefinitionReference<?> reference : references) {
            if (reference.getClass().getName().equals(name)) {
                return reference;
            }
        }
        throw new AssertionError(name + " is not among " + names(references));
    }

    private static List<String> chain(Throwable throwable) {
        List<String> chain = new ArrayList<>();
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            chain.add(t.toString());
        }
        return chain;
    }

    /**
     * A class loader for one test that registers the given fixtures and defines them itself.
     */
    private URLClassLoader loader(String... registered) throws IOException {
        Path entries = Files.createTempDirectory(temp, "entries");
        Path directory = Files.createDirectories(entries.resolve(REFERENCES));
        for (String name : registered) {
            Files.createFile(directory.resolve(name));
        }
        URLClassLoader loader = new FixtureLoader(entries.toUri().toURL());
        loaders.add(loader);
        return loader;
    }

    private static final class FixtureLoader extends URLClassLoader {

        private FixtureLoader(URL entries) {
            super(new URL[] {entries}, BeanDefinitionPrefetchTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!FIXTURES.contains(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) {
                    return loaded;
                }
                try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (in == null) {
                        throw new ClassNotFoundException(name);
                    }
                    byte[] bytes = in.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        }
    }
}
