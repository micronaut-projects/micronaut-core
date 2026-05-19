/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.compiler;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.tools.JavaFileObject;

/**
 * A custom classloader that loads from JavaFileObject instances.
 */
final class JavaFileObjectClassLoader extends ClassLoader {

    private final Collection<JavaFileObject> files = new ArrayList<>();

    public JavaFileObjectClassLoader(Iterable<? extends JavaFileObject> files) {
        this(files, JavaFileObjectClassLoader.class.getClassLoader());
    }

    public JavaFileObjectClassLoader(Iterable<? extends JavaFileObject> files, ClassLoader parent) {
        super(parent);
        for (JavaFileObject file : files) {
            this.files.add(file);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String fileName = name.replace('.', '/') + ".class";
        JavaFileObject generated = files.stream()
                .filter((JavaFileObject it) -> it.getName().endsWith(fileName))
                .findFirst().orElse(null);
        if (generated != null) {
            try (InputStream io = generated.openInputStream()) {
                byte[] bytes = io.readAllBytes();
                return super.defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                return super.findClass(name);
            }
        }
        return super.findClass(name);
    }

    @Override
    public @Nullable URL getResource(String name) {
        try {
            Enumeration<URL> resources = findResources(name);
            if (resources.hasMoreElements()) {
                return resources.nextElement();
            } else {
                return super.getResource(name);
            }
        } catch (IOException e) {
            return super.getResource(name);
        }
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> resources = findResources(name);
        if (resources.hasMoreElements()) {
            return resources;
        } else {
            return super.getResources(name);
        }
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        String expectedPath = "/CLASS_OUTPUT/" + name;
        List<JavaFileObject> generated = new ArrayList<>();
        for (JavaFileObject file : files) {
            String uriPath = file.toUri() != null ? file.toUri().getPath() : null;
            String fileName = file.getName();
            boolean matches = false;
            if (uriPath != null) {
                // Typical in-memory URIs look like: mem:///CLASS_OUTPUT/META-INF/...
                matches = uriPath.endsWith("/" + name) || uriPath.equals(expectedPath) || uriPath.contains(expectedPath);
            }
            if (!matches && fileName != null) {
                // Fallback to name-based matching
                matches = fileName.endsWith("/" + name) || fileName.contains(expectedPath);
            }
            if (matches) {
                generated.add(file);
            }
        }
        if (generated.isEmpty()) {
            return super.findResources(name);
        }
        return Collections.enumeration(generated.stream().map(javaFileObject -> {
            try {
                return new URL(null, javaFileObject.toUri().toString(), new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) {
                        return new URLConnection(u) {
                            @Override
                            public void connect() {
                            }

                            @Override
                            public InputStream getInputStream() throws IOException {
                                return javaFileObject.openInputStream();
                            }
                        };
                    }
                });
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).toList());
    }
}
