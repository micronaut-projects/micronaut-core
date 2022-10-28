/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.ast.groovy.utils;

import groovy.lang.GroovyClassLoader;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extended version of {@link GroovyClassLoader} that can be used to test dependency injection compilation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class InMemoryByteCodeGroovyClassLoader extends GroovyClassLoader {

    private Map<String, byte[]> generatedClasses = new ConcurrentHashMap<>();
    private List<URL> generatedUrls = new ArrayList<>();
    private Map<String, Class> loadedClasses = new ConcurrentHashMap<>();

    /**
     * Default constructor.
     */
    public InMemoryByteCodeGroovyClassLoader() {
        clearCache();
        AbstractAnnotationMetadataBuilder.clearCaches();
        AbstractAnnotationMetadataBuilder.clearMutated();
    }

    /**
     * @param loader The {@link ClassLoader}
     */
    public InMemoryByteCodeGroovyClassLoader(ClassLoader loader) {
        super(loader);
        clearCache();
        AbstractAnnotationMetadataBuilder.clearCaches();
        AbstractAnnotationMetadataBuilder.clearMutated();
    }

    /**
     * @param parent The {@link GroovyClassLoader}
     */
    public InMemoryByteCodeGroovyClassLoader(GroovyClassLoader parent) {
        super(parent);
        clearCache();
        AbstractAnnotationMetadataBuilder.clearCaches();
        AbstractAnnotationMetadataBuilder.clearMutated();
    }

    /**
     * @param parent                    The parent {@link ClassLoader}
     * @param config                    The {@link CompilerConfiguration}
     * @param useConfigurationClasspath Whether to use the configuration classpath
     */
    public InMemoryByteCodeGroovyClassLoader(ClassLoader parent, CompilerConfiguration config, boolean useConfigurationClasspath) {
        super(parent, config, useConfigurationClasspath);
        clearCache();
    }

    /**
     * @param loader The {@link ClassLoader}
     * @param config The {@link CompilerConfiguration}
     */
    public InMemoryByteCodeGroovyClassLoader(ClassLoader loader, CompilerConfiguration config) {
        super(loader, config);
        clearCache();
    }

    /**
     * @param name The name of the class
     * @param code The code
     */
    public void addClass(String name, byte[] code) {
        if (name != null && code != null) {
            generatedClasses.put(name, code);
        }
    }

    /**
     * Adds one or many services that can be loaded via {@link java.util.ServiceLoader}.
     * @param name The name of the service
     * @param classes The classes
     * @throws MalformedURLException If the name is not valid
     */
    public void addService(String name, Set<String> classes) throws MalformedURLException {
        generatedUrls.add(new URL(null, "mem:META-INF/services/" + name, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return new URLConnection(u) {
                    @Override
                    public void connect() {
                        // only implement getInputStream
                    }

                    @Override
                    public InputStream getInputStream() {
                        byte[] data = String.join("\n", classes).getBytes(StandardCharsets.UTF_8);
                        return new ByteArrayInputStream(data);
                    }
                };
            }
        }));
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (loadedClasses.containsKey(name)) {
            return loadedClasses.get(name);
        } else if (generatedClasses.containsKey(name)) {
            final Class cls = defineClass(name, generatedClasses.get(name));
            loadedClasses.put(name, cls);
            return cls;
        } else {
            return super.loadClass(name);
        }
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        final Optional<URL> first = generatedUrls.stream().filter(url -> url.getPath().equals(name)).findFirst();
        if (first.isPresent()) {
            return Collections.enumeration(Collections.singletonList(first.get()));
        } else {
            return super.findResources(name);
        }
    }

    public final Map<String, byte[]> getGeneratedClasses() {
        return generatedClasses;
    }
}
