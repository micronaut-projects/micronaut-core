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
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.BeanDefinitionsProvider;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.runtime.prefetch.FixtureReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micronaut.runtime.BeanDefinitionPrefetchTest.FAILS_AT_RUNTIME;
import static io.micronaut.runtime.BeanDefinitionPrefetchTest.FAILS_TO_LINK;
import static io.micronaut.runtime.BeanDefinitionPrefetchTest.FAILS_WITH_NO_SUCH_FIELD;
import static io.micronaut.runtime.BeanDefinitionPrefetchTest.FIRST;
import static io.micronaut.runtime.BeanDefinitionPrefetchTest.REFERENCES;
import static io.micronaut.runtime.BeanDefinitionPrefetchTest.SECOND;
import static io.micronaut.runtime.BeanDefinitionPrefetchTest.names;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the switch, the stand-downs and the hand-over of the bean definition prefetch in
 * {@link Micronaut}, each in a fresh JVM, since the prefetch starts in the static initializer of
 * {@link Micronaut}.
 */
@Tag("child-jvm")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class BeanDefinitionPrefetchStartupTest {
    private static final String ON = "-D" + BeanDefinitionPrefetch.PROPERTY + "=true";
    // The fewest pool threads the prefetch starts with, whatever the machine running the test has
    private static final String PARALLELISM = "-Djava.util.concurrent.ForkJoinPool.common.parallelism=";
    private static final String THREE_THREADS = PARALLELISM + "3";
    private static final String TASK = BeanDefinitionPrefetch.class.getName();

    @TempDir
    Path temp;

    @Test
    void offLoadsNothingNewAndLeavesTheProviderAlone() throws IOException {
        ChildJvm initialized = run(List.of(), "-Dprefetch-test.mode=init", "-verbose:class", THREE_THREADS);
        ChildJvm started = run(List.of(), THREE_THREADS);

        assertEquals("0", initialized.value("POOL_SIZE"), initialized.output());
        assertTrue(initialized.output().contains(Micronaut.class.getName() + " source:"), initialized.output());
        assertFalse(initialized.output().contains(TASK + " source:"), "the task class was loaded:\n" + initialized.output());
        assertEquals(DefaultBeanDefinitionsProvider.class.getName(), started.value("PROVIDER"), started.output());
    }

    @Test
    void onHandsTheReferencesOfTheDefaultProviderToTheContext() throws IOException {
        ChildJvm initialized = run(List.of(), "-Dprefetch-test.mode=init", ON, THREE_THREADS);
        ChildJvm started = run(List.of(), ON, THREE_THREADS);
        ChildJvm off = run(List.of(), THREE_THREADS);

        assertNotEquals("0", initialized.value("POOL_SIZE"), initialized.output());
        assertEquals(TASK, started.value("PROVIDER"), started.output());
        assertEquals(off.value("REFERENCES"), started.value("REFERENCES"));
        assertNotEquals("org.slf4j.helpers.NOPLogger", started.value("REFLECTION_LOGGER"));
    }

    @Test
    void standsDownBelowThreePoolThreadsAndInANativeImage() throws IOException {
        for (String standDown : List.of(PARALLELISM + "1", PARALLELISM + "2", "-Dorg.graalvm.nativeimage.imagecode=runtime")) {
            ChildJvm child = run(List.of(), "-Dprefetch-test.mode=init", ON, THREE_THREADS, standDown);
            assertEquals("0", child.value("POOL_SIZE"), standDown + "\n" + child.output());
        }
    }

    @Test
    void keepsAProviderThatTheApplicationOrAConfigurerSet() throws IOException {
        Path configurer = Files.createDirectories(temp.resolve("configurer/META-INF/services"));
        Files.writeString(configurer.resolve(ApplicationContextConfigurer.class.getName()), ProviderConfigurer.class.getName());

        ChildJvm application = run(List.of(), ON, THREE_THREADS, "-Dprefetch-test.provider=true");
        ChildJvm configured = run(List.of(temp.resolve("configurer")), ON, THREE_THREADS);

        assertEquals(OwnProvider.class.getName(), application.value("PROVIDER"), application.output());
        assertEquals(OwnProvider.class.getName(), configured.value("PROVIDER"), configured.output());
    }

    /**
     * A reference whose static initializer throws stops the application with the prefetch as it
     * does without it: the same exit status and the same chain of causes.
     */
    @Test
    void aFailingInitializerStopsTheApplicationAsItDoesWithoutThePrefetch() throws IOException {
        for (String failing : List.of(FAILS_AT_RUNTIME, FAILS_WITH_NO_SUCH_FIELD)) {
            List<Path> entries = List.of(entries(failing));
            ChildJvm on = run(entries, ON, THREE_THREADS);
            ChildJvm off = run(entries, THREE_THREADS);

            assertNotEquals(0, off.exitCode(), off.output());
            assertEquals(off.exitCode(), on.exitCode(), on.output());
            assertTrue(off.causes().stream().anyMatch(cause -> cause.contains(failing)), off.output());
            assertEquals(off.causes(), on.causes(), on.output());
            // The failure comes from the task with the prefetch on, and from the context's own read without it
            assertTrue(on.output().contains("at " + TASK + ".compute("), on.output());
            assertFalse(off.output().contains("at " + TASK + ".compute("), off.output());
        }
    }

    /**
     * A {@link TypeConverterRegistrar} that throws stops the application with the prefetch as it
     * does without it: the same exit status and the same chain of causes, and no warning about the
     * prefetch. With the prefetch, it throws on the pool thread that builds the shared conversion
     * service, and the context then fails on the class that this left erroneous: Micronaut
     * rethrows what the pool thread met.
     */
    @Test
    void aFailingTypeConverterRegistrarStopsTheApplicationAsItDoesWithoutThePrefetch() throws IOException {
        List<Path> classPath = List.of(services(Map.of(TypeConverterRegistrar.class, FailingRegistrar.class)));
        ChildJvm on = run(classPath, ON, THREE_THREADS, "-Dprefetch-test.await=true");
        ChildJvm off = run(classPath, THREE_THREADS);

        assertNotEquals(0, off.exitCode(), off.output());
        assertEquals(off.exitCode(), on.exitCode(), on.output());
        assertTrue(off.causes().stream().anyMatch(cause -> cause.contains(FailingRegistrar.MESSAGE)), off.output());
        assertEquals(off.causes(), on.causes(), on.output());
        // The registrar failed on the task's thread with the prefetch on, and on the main thread without it
        assertTrue(on.output().contains("at " + TASK + ".convert("), on.output());
        assertFalse(off.output().contains("at " + TASK + "."), off.output());
        assertFalse(on.output().contains("The bean definition prefetch ("), on.output());
    }

    @Test
    void aReferenceThatFailsToLinkIsSkippedAsItIsWithoutThePrefetch() throws IOException {
        List<Path> entries = List.of(entries(FIRST, FAILS_TO_LINK));
        ChildJvm on = run(entries, ON, THREE_THREADS);
        ChildJvm off = run(entries, THREE_THREADS);

        assertEquals(0, on.exitCode(), on.output());
        assertEquals(TASK, on.value("PROVIDER"));
        assertTrue(on.value("REFERENCES").contains(FIRST), on.output());
        assertFalse(on.value("REFERENCES").contains(FAILS_TO_LINK), on.output());
        assertEquals(off.value("REFERENCES"), on.value("REFERENCES"));
    }

    /**
     * Only the context class loader of the main thread sees a {@link StaticOptimizations.Loader}
     * of a static service table here, as Micronaut AOT registers one, and the task reads the
     * references that table lists. {@link ClassUtils}, which the main thread initializes before it
     * starts the task, reads {@link StaticOptimizations}, so the loader runs on the main thread, as
     * it does without the prefetch. The task builds the shared conversion service on its own
     * thread, with the context class loader of the main thread.
     */
    @Test
    void theTaskSeesTheStaticOptimizationsOfTheContextClassLoader() throws IOException {
        Path services = Files.createDirectories(temp.resolve("optimizations/META-INF/services"));
        Files.writeString(services.resolve(StaticOptimizations.Loader.class.getName()), StaticServices.class.getName());
        Path registrar = Files.createDirectories(temp.resolve("registrar/META-INF/services"));
        Files.writeString(registrar.resolve(TypeConverterRegistrar.class.getName()), ContextClassLoaderRegistrar.class.getName());
        List<Path> classPath = List.of(temp.resolve("registrar"));
        String contextClassLoader = "-Dprefetch-test.ccl=" + temp.resolve("optimizations");

        ChildJvm on = run(classPath, ON, THREE_THREADS, contextClassLoader, "-Dprefetch-test.await=true");
        ChildJvm off = run(classPath, THREE_THREADS, contextClassLoader);

        assertEquals(TASK, on.value("PROVIDER"), on.output());
        // Only the static service table lists the two fixtures
        assertTrue(List.of(on.value("REFERENCES").split(",")).containsAll(List.of(FIRST, SECOND)), on.output());
        assertEquals("main", on.value("OPTIMIZATIONS_THREAD"), on.output());
        assertEquals(off.value("OPTIMIZATIONS_THREAD"), on.value("OPTIMIZATIONS_THREAD"), off.output());
        assertEquals(off.value("REFERENCES"), on.value("REFERENCES"));
        assertTrue(on.value("REGISTRAR_THREAD").startsWith("ForkJoinPool.commonPool-worker-"), on.output());
        assertEquals("true", on.value("REGISTRAR_CONTEXT_CLASS_LOADER"), on.output());
        assertEquals("true", off.value("REGISTRAR_CONTEXT_CLASS_LOADER"), off.output());
    }

    /**
     * The main thread configures logging, as it creates the logger of {@link Micronaut}, and
     * initializes {@link ClassUtils} before it starts the task, so no pool thread creates a logger
     * while logging is being configured: SLF4J hands out no substitute logger, and the application
     * logs as it does without the prefetch.
     */
    @Test
    void logsAsItDoesWithoutThePrefetch() throws IOException {
        ChildJvm on = run(List.of(), ON, THREE_THREADS, "-verbose:class");
        ChildJvm off = run(List.of(), THREE_THREADS, "-verbose:class");

        assertEquals(TASK, on.value("PROVIDER"), on.output());
        for (String substitute : List.of("org.slf4j.helpers.SubstituteLogger", "org.slf4j.event.EventRecordingLogger")) {
            assertFalse(on.output().contains(substitute + " source:"), substitute + " was loaded:\n" + on.output());
        }
        assertEquals(off.value("REFLECTION_LOGGER"), on.value("REFLECTION_LOGGER"), on.output());
        assertFalse(off.logged().isEmpty(), off.output());
        assertEquals(off.logged(), on.logged(), on.output());
    }

    /**
     * A failed task that the context did not take is reported once, with the property that turns
     * the prefetch off.
     */
    @Test
    void warnsOnceAboutAFailedTaskThatTheContextDidNotTake() throws IOException {
        ChildJvm child = run(List.of(entries(FAILS_AT_RUNTIME)), ON, THREE_THREADS, "-Dprefetch-test.provider=true", "-Dprefetch-test.await=true");

        assertEquals(0, child.exitCode(), child.output());
        String warning = "The bean definition prefetch (" + BeanDefinitionPrefetch.PROPERTY + "=true) failed and was not handed to the application context";
        assertEquals(1, child.output().lines().filter(line -> line.contains(warning)).count(), child.output());
        assertTrue(child.output().contains("Start without " + BeanDefinitionPrefetch.PROPERTY + "=true"), child.output());
    }

    private Path entries(String... registered) throws IOException {
        Path entries = Files.createTempDirectory(temp, "entries");
        Path directory = Files.createDirectories(entries.resolve(REFERENCES));
        for (String name : registered) {
            Files.createFile(directory.resolve(name));
        }
        return entries;
    }

    /**
     * A class path entry that registers the given services.
     */
    private Path services(Map<Class<?>, Class<?>> services) throws IOException {
        Path root = Files.createTempDirectory(temp, "services");
        Path directory = Files.createDirectories(root.resolve("META-INF/services"));
        for (Map.Entry<Class<?>, Class<?>> service : services.entrySet()) {
            Files.writeString(directory.resolve(service.getKey().getName()), service.getValue().getName());
        }
        return root;
    }

    private static ChildJvm run(List<Path> extraClassPath, String... jvmArgs) {
        StringBuilder classPath = new StringBuilder(System.getProperty("java.class.path"));
        for (Path path : extraClassPath) {
            classPath.append(File.pathSeparator).append(path);
        }
        TrainingRunTest.ChildJvm child = TrainingRunTest.ChildJvm.run(Main.class, classPath.toString(), Map.of(), jvmArgs);
        return new ChildJvm(child.exitCode(), child.output());
    }

    record ChildJvm(int exitCode, String output) {
        // The time that the pattern of the test logback.xml, or a Logback status line, starts with
        private static final Pattern LOGGED_AT = Pattern.compile("\\d{2}:\\d{2}:\\d{2}[.,]\\d{3} (.*)");

        String value(String key) {
            String prefix = key + "=";
            return output.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + key + " in the output of the child JVM:\n" + output));
        }

        /**
         * What logging wrote, without the time: the lines of Logback and its status lines, and
         * the reports of SLF4J itself.
         */
        List<String> logged() {
            List<String> logged = new ArrayList<>();
            for (String line : output.lines().toList()) {
                Matcher matcher = LOGGED_AT.matcher(line);
                if (matcher.matches()) {
                    logged.add(matcher.group(1));
                } else if (line.startsWith("SLF4J")) {
                    logged.add(line);
                }
            }
            return logged;
        }

        List<String> causes() {
            List<String> causes = new ArrayList<>();
            for (String line : output.lines().toList()) {
                if (line.startsWith("Exception in thread \"main\" ") || line.startsWith("Caused by: ")) {
                    causes.add(line);
                }
            }
            return causes;
        }
    }

    /**
     * A provider of the application's own, which reads the references itself.
     */
    static final class OwnProvider implements BeanDefinitionsProvider {
        @Override
        public List<BeanDefinitionReference<?>> provide(ClassLoader classLoader) {
            return new DefaultBeanDefinitionsProvider().provide(classLoader);
        }
    }

    /**
     * Sets {@link OwnProvider}, as a configurer service.
     */
    public static final class ProviderConfigurer implements ApplicationContextConfigurer {
        @Override
        public void configure(ApplicationContextBuilder builder) {
            builder.beanDefinitionsProvider(new OwnProvider());
        }
    }

    /**
     * A static service table, as Micronaut AOT supplies one. It lists the bean definition
     * references of the class path and two fixtures that no entry registers.
     */
    public static final class StaticServices implements StaticOptimizations.Loader<SoftServiceLoader.Optimizations> {
        static volatile String thread;

        @Override
        public SoftServiceLoader.Optimizations load() {
            thread = Thread.currentThread().getName();
            SoftServiceLoader.StaticServiceLoader<BeanDefinitionReference<?>> references = predicate -> {
                List<SoftServiceLoader.StaticDefinition<BeanDefinitionReference<?>>> definitions = new ArrayList<>();
                for (String name : scan()) {
                    BeanDefinitionReference<?> reference = instantiate(name);
                    if (reference != null) {
                        definitions.add(SoftServiceLoader.StaticDefinition.of(name, () -> reference));
                    }
                }
                definitions.add(SoftServiceLoader.StaticDefinition.of(FIRST, FixtureReference.First::new));
                definitions.add(SoftServiceLoader.StaticDefinition.of(SECOND, FixtureReference.Second::new));
                return definitions.stream().filter(definition -> predicate.test(definition.getName()));
            };
            return new SoftServiceLoader.Optimizations(Map.of(BeanDefinitionReference.class.getName(), references));
        }

        private static Set<String> scan() {
            try {
                return MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(StaticServices.class.getClassLoader(), BeanDefinitionReference.class.getName());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Skips a reference whose class cannot be loaded, as the scan does.
         */
        private static BeanDefinitionReference<?> instantiate(String name) {
            try {
                return Class.forName(name).asSubclass(BeanDefinitionReference.class).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError e) {
                return null;
            }
        }
    }

    /**
     * Records the thread that registers its converters first, for the shared conversion service,
     * and whether that thread carries the context class loader that {@link Main} set. The
     * conversion service of the context registers them again later, on the main thread.
     */
    public static final class ContextClassLoaderRegistrar implements TypeConverterRegistrar {
        static volatile String thread;
        static volatile boolean contextClassLoader;

        @Override
        public void register(MutableConversionService conversionService) {
            synchronized (ContextClassLoaderRegistrar.class) {
                if (thread == null) {
                    Thread current = Thread.currentThread();
                    contextClassLoader = current.getContextClassLoader() == Main.contextClassLoader;
                    thread = current.getName();
                }
            }
        }
    }

    /**
     * Fails to register its converters.
     */
    public static final class FailingRegistrar implements TypeConverterRegistrar {
        static final String MESSAGE = "probe failure of " + FailingRegistrar.class.getName();

        @Override
        public void register(MutableConversionService conversionService) {
            throw new IllegalStateException(MESSAGE);
        }
    }

    /**
     * The application of the child JVM. It does not use {@link Micronaut} before it has set the
     * context class loader that a test asks for.
     */
    static final class Main {
        static volatile ClassLoader contextClassLoader;

        public static void main(String[] args) throws Exception {
            String path = System.getProperty("prefetch-test.ccl");
            if (path != null) {
                contextClassLoader = new URLClassLoader(new URL[] {Path.of(path).toUri().toURL()}, Main.class.getClassLoader());
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
            Class.forName(Micronaut.class.getName(), true, Main.class.getClassLoader());
            if ("init".equals(System.getProperty("prefetch-test.mode"))) {
                System.out.println("POOL_SIZE=" + ForkJoinPool.commonPool().getPoolSize());
                return;
            }
            if (Boolean.getBoolean("prefetch-test.await")) {
                // Only watches the pool: a join or a quiescence wait could run the task on this thread
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
                while (!ForkJoinPool.commonPool().isQuiescent() && System.nanoTime() < deadline) {
                    Thread.sleep(5);
                }
            }
            Micronaut micronaut = Micronaut.build(args).properties(Map.of("spec.name", "BeanDefinitionPrefetchStartupTest"));
            if (Boolean.getBoolean("prefetch-test.provider")) {
                micronaut.beanDefinitionsProvider(new OwnProvider());
            }
            try (ApplicationContext context = micronaut.start()) {
                System.out.println("PROVIDER=" + micronaut.getBeanDefinitionsProvider().getClass().getName());
                System.out.println("REFERENCES=" + String.join(",", names(context.getBeanDefinitionReferences())));
                System.out.println("REFLECTION_LOGGER=" + ClassUtils.REFLECTION_LOGGER.getClass().getName());
                System.out.println("OPTIMIZATIONS_THREAD=" + StaticServices.thread);
                System.out.println("REGISTRAR_THREAD=" + ContextClassLoaderRegistrar.thread);
                System.out.println("REGISTRAR_CONTEXT_CLASS_LOADER=" + ContextClassLoaderRegistrar.contextClassLoader);
            }
        }
    }
}
