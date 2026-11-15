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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void findsTheBuilderInTheClassLoaderOfTheAnnotationType(@TempDir Path services) throws Exception {
        register(services);

        try (URLClassLoader library = new ChildFirstLoader(services, getClass().getClassLoader())) {
            @SuppressWarnings("unchecked")
            Class<Annotation> annotationType = (Class<Annotation>) library.loadClass(ChildMark.class.getName());
            assertNotSame(ChildMark.class, annotationType);

            // the application is the class loader of the test, which sees neither this annotation type nor its builder
            AnnotationBuilder<Annotation> builder = AnnotationBuilder.find(annotationType, getClass().getClassLoader()).orElseThrow();
            assertSame(annotationType, builder.annotationType());
            assertSame(annotationType, builder.build(Map.of("value", "library")).annotationType());
            assertSame(annotationType, AnnotationBuilder.find(annotationType, null).orElseThrow().annotationType());
        }
    }

    private static void register(Path services) throws IOException {
        Path entry = services.resolve("META-INF/micronaut/" + AnnotationBuilder.class.getName() + "/" + BUILDER);
        Files.createDirectories(entry.getParent());
        Files.createFile(entry);
    }

    /**
     * Defines the annotation type and its builder itself, from the classes of the test, so that both are its own.
     */
    private static final class ChildFirstLoader extends URLClassLoader {

        private static final Set<String> OWN = Set.of(ChildMark.class.getName(), BUILDER);

        ChildFirstLoader(Path services, ClassLoader parent) throws IOException {
            super(new URL[] {services.toUri().toURL()}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!OWN.contains(name) && !name.startsWith(BUILDER + "$")) {
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
