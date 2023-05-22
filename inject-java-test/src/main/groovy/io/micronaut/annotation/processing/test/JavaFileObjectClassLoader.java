/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.annotation.processing.test;

import org.codehaus.groovy.runtime.IOGroovyMethods;

import javax.tools.JavaFileObject;
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
import java.util.stream.Collectors;

/**
 * A custom classloader that loads from JavaFileObject instances.
 */
final class JavaFileObjectClassLoader extends ClassLoader {

    private final Collection<JavaFileObject> files = new ArrayList<>();

    public JavaFileObjectClassLoader(Iterable<? extends JavaFileObject> files) {
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
                byte[] bytes = IOGroovyMethods.getBytes(io);
                return super.defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                return super.findClass(name);
            }
        }
        return super.findClass(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        String fileName = "/CLASS_OUTPUT/" + name;
        List<JavaFileObject> generated = files.stream()
                .filter((JavaFileObject it) -> it.getName().startsWith(fileName))
                .toList();
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
        }).collect(Collectors.toList()));
    }
}
