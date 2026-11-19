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
package io.micronaut.inject.annotation.childloader;

import io.micronaut.core.annotation.AnnotationBuilder;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finds the builder of an annotation whose builder is registered in a class loader below the one of Micronaut: the
 * class loader of the application, or the one of the annotation type.
 */
class AnnotationBuilderClassLoaderTest {

    private static final String BUILDER = ChildMarkBuilders$io_micronaut_inject_annotation_childloader_ChildMark$AnnotationBuilder.class.getName();

    @Test
    void findsTheBuilderInTheClassLoaderOfTheApplication(@TempDir Path services) throws Exception {
        register(services);

        assertTrue(AnnotationBuilder.find(ChildMark.class).isEmpty());
        assertTrue(AnnotationBuilder.find(ChildMark.class, null).isEmpty());
        try (URLClassLoader application = new URLClassLoader(new URL[] {services.toUri().toURL()}, getClass().getClassLoader())) {
            AnnotationBuilder<ChildMark> builder = AnnotationBuilder.find(ChildMark.class, application).orElseThrow();
            assertSame(ChildMark.class, builder.annotationType());
            assertEquals("child", builder.build(Map.of("value", "child")).value());
        }
    }

    @Test
    void keepsTheBuilderFoundInTheClassLoaderOfTheApplication(@TempDir Path services) throws Exception {
        register(services);

        try (URLClassLoader application = new URLClassLoader(new URL[] {services.toUri().toURL()}, getClass().getClassLoader());
             URLClassLoader other = new URLClassLoader(new URL[] {services.toUri().toURL()}, getClass().getClassLoader())) {
            AnnotationBuilder<ChildMark> builder = AnnotationBuilder.find(ChildMark.class, application).orElseThrow();
            // what the class loader supplies is kept for it, not looked up again
            assertSame(builder, AnnotationBuilder.find(ChildMark.class, application).orElseThrow());
            assertSame(builder, AnnotationBuilder.find(ChildMark.class.getName(), application).orElseThrow());
            // another class loader that registers the same builder class finds the same builder
            assertSame(builder, AnnotationBuilder.find(ChildMark.class, other).orElseThrow());
        }
    }

    @Test
    void keepsNoBuilderFoundForTheClassLoaderOfTheApplication(@TempDir Path services) throws Exception {
        try (URLClassLoader application = new URLClassLoader(new URL[] {services.toUri().toURL()}, getClass().getClassLoader())) {
            assertTrue(AnnotationBuilder.find(ChildMark.class, application).isEmpty());
            assertTrue(AnnotationBuilder.find(ChildMark.class, application).isEmpty());
        }
    }

    @Test
    void readsTheBuilderNamesOfTheClassLoaderOfTheApplicationOnceForAllHitsAndMisses(@TempDir Path services) throws Exception {
        register(services);

        try (CountingLoader application = new CountingLoader(services, getClass().getClassLoader())) {
            // the first lookup reads the names of the builders of the class loader
            assertTrue(AnnotationBuilder.find(Missing.class, application).isEmpty());
            int scanned = application.queries.get();
            assertTrue(scanned > 0);

            // an annotation that no builder of the class loader is named after is answered from those names
            assertTrue(AnnotationBuilder.find(AlsoMissing.class, application).isEmpty());
            assertTrue(AnnotationBuilder.find("io.micronaut.inject.annotation.childloader.Absent", application).isEmpty());
            assertEquals(scanned, application.queries.get());

            // a builder that is named after the annotation is loaded alone
            AnnotationBuilder<ChildMark> builder = AnnotationBuilder.find(ChildMark.class, application).orElseThrow();
            int searched = application.queries.get();
            assertTrue(searched - scanned <= scanned);
            assertTrue(AnnotationBuilder.find("io.micronaut.inject.annotation.childloader.AlsoAbsent", application).isEmpty());
            assertEquals(searched, application.queries.get());

            for (int i = 0; i < 3; i++) {
                assertSame(builder, AnnotationBuilder.find(ChildMark.class, application).orElseThrow());
                assertSame(builder, AnnotationBuilder.find(ChildMark.class.getName(), application).orElseThrow());
                assertTrue(AnnotationBuilder.find(Missing.class, application).isEmpty());
                assertTrue(AnnotationBuilder.find(AlsoMissing.class, application).isEmpty());
            }
            assertEquals(searched, application.queries.get());
        }
    }

    @Test
    void searchesTheClassLoaderOfTheApplicationAgainOnceTheServiceEntriesAreInvalidated(@TempDir Path services) throws Exception {
        try (URLClassLoader application = new URLClassLoader(new URL[] {services.toUri().toURL()}, getClass().getClassLoader())) {
            assertTrue(AnnotationBuilder.find(ChildMark.class, application).isEmpty());

            register(services);
            // the miss is kept until the class path is said to have changed
            assertTrue(AnnotationBuilder.find(ChildMark.class, application).isEmpty());
            MicronautMetaServiceLoaderUtils.invalidate();

            AnnotationBuilder<ChildMark> builder = AnnotationBuilder.find(ChildMark.class, application).orElseThrow();
            assertSame(ChildMark.class, builder.annotationType());
            assertSame(builder, AnnotationBuilder.find(ChildMark.class.getName(), application).orElseThrow());
        }
    }

    @Test
    void doesNotKeepTheClassLoaderOfTheApplicationFromBeingCollected(@TempDir Path services) throws Exception {
        register(services);

        WeakReference<ClassLoader> retired = findInDisposableLoader(services);
        for (int i = 0; i < 50 && retired.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }

        assertNull(retired.get());
    }

    private WeakReference<ClassLoader> findInDisposableLoader(Path services) throws Exception {
        // the application defines the builder itself, so that the builder found is of the application's own class
        try (URLClassLoader application = new ChildFirstLoader(services, getClass().getClassLoader(), Set.of(BUILDER))) {
            AnnotationBuilder<ChildMark> builder = AnnotationBuilder.find(ChildMark.class, application).orElseThrow();
            assertSame(application, builder.getClass().getClassLoader());
            assertSame(builder, AnnotationBuilder.find(ChildMark.class, application).orElseThrow());
            return new WeakReference<>(application);
        }
    }

    @Test
    void findsTheBuilderInTheClassLoaderOfTheAnnotationType(@TempDir Path services) throws Exception {
        register(services);

        try (URLClassLoader library = new ChildFirstLoader(services, getClass().getClassLoader(), Set.of(ChildMark.class.getName(), BUILDER))) {
            @SuppressWarnings("unchecked")
            Class<Annotation> annotationType = (Class<Annotation>) library.loadClass(ChildMark.class.getName());
            assertNotSame(ChildMark.class, annotationType);

            // the application is the class loader of the test, which sees neither this annotation type nor its builder
            AnnotationBuilder<Annotation> builder = AnnotationBuilder.find(annotationType, getClass().getClassLoader()).orElseThrow();
            assertSame(annotationType, builder.annotationType());
            assertSame(annotationType, builder.build(Map.of("value", "library")).annotationType());
            assertSame(annotationType, AnnotationBuilder.find(annotationType, null).orElseThrow().annotationType());
            // what the class loader of the type supplies is kept with the type, not looked up again
            assertSame(builder, AnnotationBuilder.find(annotationType, null).orElseThrow());
            assertSame(builder, AnnotationBuilder.find(annotationType, getClass().getClassLoader()).orElseThrow());
        }
    }

    private static void register(Path services) throws IOException {
        Path entry = services.resolve("META-INF/micronaut/" + AnnotationBuilder.class.getName() + "/" + BUILDER);
        Files.createDirectories(entry.getParent());
        Files.createFile(entry);
    }

    /**
     * An annotation no builder is registered for.
     */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface Missing {
    }

    /**
     * Another annotation no builder is registered for.
     */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface AlsoMissing {
    }

    /**
     * Counts the resources the class loader is asked for, which a search of its service entries asks for.
     */
    private static final class CountingLoader extends URLClassLoader {

        private final AtomicInteger queries = new AtomicInteger();

        CountingLoader(Path services, ClassLoader parent) throws IOException {
            super(new URL[] {services.toUri().toURL()}, parent);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            queries.incrementAndGet();
            return super.getResources(name);
        }

        @Override
        public URL getResource(String name) {
            queries.incrementAndGet();
            return super.getResource(name);
        }
    }

    /**
     * Defines the given classes of the test itself, such as the annotation type and its builder, so that they are its
     * own.
     */
    private static final class ChildFirstLoader extends URLClassLoader {

        private final Set<String> own;

        ChildFirstLoader(Path services, ClassLoader parent, Set<String> own) throws IOException {
            super(new URL[] {services.toUri().toURL()}, parent);
            this.own = own;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!own.contains(name) && !name.startsWith(BUILDER + "$")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream bytes = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (bytes == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] code = bytes.readAllBytes();
                        loaded = defineClass(name, code, 0, code.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
