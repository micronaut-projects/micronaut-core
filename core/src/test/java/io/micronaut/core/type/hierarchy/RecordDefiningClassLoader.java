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
package io.micronaut.core.type.hierarchy;

import io.micronaut.core.type.TypeHierarchies;
import io.micronaut.core.type.TypeHierarchy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * A class loader that lists the service entries of a directory and defines the records itself, from the bytes its
 * parent sees, so that each such loader has its own record classes of the classes of its parent.
 */
public final class RecordDefiningClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    private final ClassLoader source;

    public RecordDefiningClassLoader(Path services, ClassLoader parent) {
        super(new URL[]{toUrl(services)}, parent);
        this.source = parent;
    }

    /**
     * Finds a record in a new loader, twice, and drops the loader, which is to be collected (written in Java, as a
     * Groovy call site would keep the class of the record it was called on).
     *
     * @param services The directory of the service entries
     * @param parent   The parent loader
     * @param typeName The name of the type the loader has a record of
     * @return The loader, held weakly
     */
    public static WeakReference<ClassLoader> findInDisposableLoader(Path services, ClassLoader parent, String typeName) {
        RecordDefiningClassLoader classLoader = new RecordDefiningClassLoader(services, parent);
        TypeHierarchy hierarchy = TypeHierarchies.find(typeName, classLoader).orElseThrow();
        if (hierarchy.getClass().getClassLoader() != classLoader) {
            throw new AssertionError("The record is not defined by the loader: " + hierarchy.getClass().getClassLoader());
        }
        if (TypeHierarchies.find(typeName, classLoader).orElseThrow() != hierarchy) {
            throw new AssertionError("The record is not found once");
        }
        return new WeakReference<>(classLoader);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.endsWith(TypeHierarchies.RECORD_SUFFIX)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                byte[] bytes;
                try (InputStream in = source.getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (in == null) {
                        throw new ClassNotFoundException(name);
                    }
                    bytes = in.readAllBytes();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                loaded = defineClass(name, bytes, 0, bytes.length);
            }
            return loaded;
        }
    }

    private static URL toUrl(Path services) {
        try {
            return services.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
