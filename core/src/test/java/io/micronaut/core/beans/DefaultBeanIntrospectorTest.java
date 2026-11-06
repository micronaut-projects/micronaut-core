package io.micronaut.core.beans;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBeanIntrospectorTest {

    private static final String CONTEXT_CLASSLOADER_PROPERTY = "micronaut.introspections.use.context.classloader";

    @TempDir
    Path tempDir;

    @Test
    void sharedIntrospectorFallsBackToBeanTypeClassLoader() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        compileChildLoaderIntrospection(classesDir);
        writeMicronautServiceEntry(classesDir);

        ClassLoader parentClassLoader = getClass().getClassLoader();
        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        String previousProperty = System.getProperty(CONTEXT_CLASSLOADER_PROPERTY);
        try (URLClassLoader childClassLoader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() }, parentClassLoader)) {
            thread.setContextClassLoader(parentClassLoader);
            System.clearProperty(CONTEXT_CLASSLOADER_PROPERTY);

            Class<?> beanType = childClassLoader.loadClass("example.ChildBean");

            assertTrue(BeanIntrospector.SHARED.findIntrospection(beanType).isPresent());
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
            if (previousProperty == null) {
                System.clearProperty(CONTEXT_CLASSLOADER_PROPERTY);
            } else {
                System.setProperty(CONTEXT_CLASSLOADER_PROPERTY, previousProperty);
            }
        }
    }

    @Test
    void beanClassLoaderWithItsOwnCoreIsALookupMiss() throws Exception {
        // the Fn testing harness runs Micronaut in its own class loader while the test types come from the application
        // class loader, whose introspections implement a BeanIntrospectionReference that is not the one of the introspector
        Path classesDir = tempDir.resolve("classes");
        compileChildLoaderIntrospection(classesDir);
        writeMicronautServiceEntry(classesDir);

        String previousProperty = System.getProperty(CONTEXT_CLASSLOADER_PROPERTY);
        try (URLClassLoader isolatedCoreClassLoader = isolatedCoreClassLoader();
             URLClassLoader childClassLoader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() }, getClass().getClassLoader())) {
            System.clearProperty(CONTEXT_CLASSLOADER_PROPERTY);
            Class<?> isolatedIntrospector = isolatedCoreClassLoader.loadClass(BeanIntrospector.class.getName());
            assertNotEquals(BeanIntrospector.class, isolatedIntrospector);
            Object shared = isolatedIntrospector.getField("SHARED").get(null);

            Class<?> beanType = childClassLoader.loadClass("example.ChildBean");
            Optional<?> introspection = (Optional<?>) isolatedIntrospector.getMethod("findIntrospection", Class.class).invoke(shared, beanType);

            assertTrue(introspection.isEmpty());
        } finally {
            restoreProperty(previousProperty);
        }
    }

    @Test
    void contextClassLoaderWithItsOwnCoreIsIgnored() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        compileChildLoaderIntrospection(classesDir);
        writeMicronautServiceEntry(classesDir);

        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        String previousProperty = System.getProperty(CONTEXT_CLASSLOADER_PROPERTY);
        try (URLClassLoader isolatedCoreClassLoader = isolatedCoreClassLoader();
             URLClassLoader childClassLoader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() }, getClass().getClassLoader())) {
            System.setProperty(CONTEXT_CLASSLOADER_PROPERTY, "true");
            thread.setContextClassLoader(childClassLoader);
            Class<?> isolatedIntrospector = isolatedCoreClassLoader.loadClass(BeanIntrospector.class.getName());
            Object shared = isolatedIntrospector.getField("SHARED").get(null);

            Class<?> beanType = childClassLoader.loadClass("example.ChildBean");
            Optional<?> introspection = (Optional<?>) isolatedIntrospector.getMethod("findIntrospection", Class.class).invoke(shared, beanType);
            Collection<?> introspectedTypes = (Collection<?>) isolatedIntrospector.getMethod("findIntrospectedTypes", Predicate.class)
                .invoke(shared, (Predicate<Object>) ref -> true);

            assertTrue(introspection.isEmpty());
            assertFalse(introspectedTypes.contains(beanType));
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
            restoreProperty(previousProperty);
        }
    }

    /**
     * A class loader that loads Micronaut core again, from the test class path, apart from the class loader of this test.
     * Its {@link BeanIntrospector} is initialized with the class loader as the context class loader, since the static
     * optimizations it initializes are service loaded through the context class loader.
     */
    private static URLClassLoader isolatedCoreClassLoader() throws Exception {
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = Path.of(entries[i]).toUri().toURL();
        }
        URLClassLoader classLoader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            Class.forName(BeanIntrospector.class.getName(), true, classLoader);
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
        }
        return classLoader;
    }

    private static void restoreProperty(String previousProperty) {
        if (previousProperty == null) {
            System.clearProperty(CONTEXT_CLASSLOADER_PROPERTY);
        } else {
            System.setProperty(CONTEXT_CLASSLOADER_PROPERTY, previousProperty);
        }
    }

    @Test
    void findIntrospectionsIteratesInAStableOrder() {
        // enough types that an order randomized per JVM, as Map.copyOf is, cannot come out equal to the order
        // of a HashMap of the names, which is the same on every run and the order consumers came to depend on
        List<Class<?>> beanTypes = List.of(
            java.util.zip.CRC32.class, String.class, java.time.ZonedDateTime.class, Integer.class,
            java.util.concurrent.atomic.AtomicLong.class, Long.class, java.util.UUID.class, Short.class,
            java.net.URI.class, Byte.class, java.math.BigDecimal.class, Character.class,
            java.time.Duration.class, Boolean.class, java.util.BitSet.class, Double.class,
            java.util.Locale.class, Float.class, java.time.Instant.class, StringBuilder.class,
            java.math.BigInteger.class, Thread.class, java.util.Date.class, Object.class,
            java.time.LocalDate.class, Number.class, java.util.Optional.class, Runtime.class,
            java.nio.file.Path.class, Math.class, java.time.LocalTime.class, System.class
        );
        List<BeanIntrospectionReference<Object>> references = beanTypes.stream()
            .<BeanIntrospectionReference<Object>>map(StubReference::new)
            .toList();
        Map<String, BeanIntrospectionReference<Object>> byName = new HashMap<>();
        for (BeanIntrospectionReference<Object> reference : references) {
            byName.put(reference.getName(), reference);
        }
        List<BeanIntrospectionReference<Object>> expectedOrder = List.copyOf(byName.values());
        assertNotEquals(references, expectedOrder, "the hash order must differ from the provider order to prove anything");

        BeanIntrospectionsProvider previous = BeanIntrospectionProviders.set(classLoader -> references);
        try {
            DefaultBeanIntrospector introspector = new DefaultBeanIntrospector(getClass().getClassLoader());

            assertEquals(
                expectedOrder.stream().map(BeanIntrospectionReference::load).toList(),
                List.copyOf(introspector.findIntrospections(ref -> true))
            );
            assertEquals(
                expectedOrder.stream().map(BeanIntrospectionReference::getBeanType).toList(),
                List.copyOf(introspector.findIntrospectedTypes(ref -> true))
            );
            Predicate<BeanIntrospectionReference<?>> secondHalf = ref -> beanTypes.indexOf(ref.getBeanType()) >= 16;
            assertEquals(
                expectedOrder.stream().filter(secondHalf).map(BeanIntrospectionReference::load).toList(),
                List.copyOf(introspector.findIntrospections(secondHalf))
            );
        } finally {
            BeanIntrospectionProviders.set(previous);
        }
    }

    private static final class StubReference implements BeanIntrospectionReference<Object> {

        private final Class<Object> beanType;
        private final BeanIntrospection<Object> introspection;

        @SuppressWarnings("unchecked")
        StubReference(Class<?> beanType) {
            this.beanType = (Class<Object>) beanType;
            this.introspection = (BeanIntrospection<Object>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {BeanIntrospection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBeanType" -> beanType;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "BeanIntrospection(" + beanType.getName() + ")";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
            );
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public Class<Object> getBeanType() {
            return beanType;
        }

        @Override
        public BeanIntrospection<Object> load() {
            return introspection;
        }

        @Override
        public String getName() {
            return beanType.getName();
        }
    }

    private void compileChildLoaderIntrospection(Path classesDir) throws Exception {
        Path sourceDir = tempDir.resolve("src/example");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);

        Path beanSource = sourceDir.resolve("ChildBean.java");
        Files.writeString(beanSource, """
            package example;

            public class ChildBean {
            }
            """, StandardCharsets.UTF_8);

        Path introspectionSource = sourceDir.resolve("$ChildBean$Introspection.java");
        Files.writeString(introspectionSource, """
            package example;

            import io.micronaut.core.annotation.AnnotationMetadata;
            import io.micronaut.core.beans.BeanIntrospection;
            import io.micronaut.core.beans.BeanIntrospectionReference;
            import io.micronaut.core.beans.BeanProperty;
            import io.micronaut.core.reflect.exception.InstantiationException;
            import java.lang.annotation.Annotation;
            import java.util.Collection;
            import java.util.List;
            import java.util.Optional;

            public final class $ChildBean$Introspection implements BeanIntrospection<ChildBean>, BeanIntrospectionReference<ChildBean> {
                @Override
                public boolean isPresent() {
                    return true;
                }

                @Override
                public Class<ChildBean> getBeanType() {
                    return ChildBean.class;
                }

                @Override
                public BeanIntrospection<ChildBean> load() {
                    return this;
                }

                @Override
                public String getName() {
                    return ChildBean.class.getName();
                }

                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return AnnotationMetadata.EMPTY_METADATA;
                }

                @Override
                public Collection<BeanProperty<ChildBean, Object>> getBeanProperties() {
                    return List.of();
                }

                @Override
                public Collection<BeanProperty<ChildBean, Object>> getIndexedProperties(Class<? extends Annotation> annotationType) {
                    return List.of();
                }

                @Override
                public Optional<BeanProperty<ChildBean, Object>> getIndexedProperty(
                        Class<? extends Annotation> annotationType,
                        String annotationValue) {
                    return Optional.empty();
                }

                @Override
                public Builder<ChildBean> builder() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ChildBean instantiate() throws InstantiationException {
                    return new ChildBean();
                }

                @Override
                public ChildBean instantiate(boolean strictNullable, Object... arguments) throws InstantiationException {
                    return new ChildBean();
                }
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "No system Java compiler available");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjects(beanSource.toFile(), introspectionSource.toFile());
            Boolean compiled = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                List.of("-classpath", System.getProperty("java.class.path"), "-d", classesDir.toString()),
                null,
                sources
            ).call();
            if (!Boolean.TRUE.equals(compiled)) {
                throw new AssertionError(diagnostics.getDiagnostics().toString());
            }
        }
    }

    private static void writeMicronautServiceEntry(Path classesDir) throws Exception {
        Path serviceDirectory = classesDir.resolve("META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference");
        Files.createDirectories(serviceDirectory);
        Files.writeString(serviceDirectory.resolve("example.$ChildBean$Introspection"), "", StandardCharsets.UTF_8);
    }
}
