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
package io.micronaut.ast.groovy.utils;

import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Utility class for testing Groovy visitors.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public class InMemoryClassWriterOutputVisitor implements ClassWriterOutputVisitor {
    private final Map<String, ByteArrayOutputStream> classStreams = new LinkedHashMap<>();
    private final Map<String, Set<String>> serviceDescriptors = new LinkedHashMap<>();
    private final InMemoryByteCodeGroovyClassLoader classLoader;

    /**
     * @param classLoader The in-memory classloader
     */
    public InMemoryClassWriterOutputVisitor(InMemoryByteCodeGroovyClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public OutputStream visitClass(String classname, Element... originatingElements) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        classStreams.put(classname, stream);
        return stream;
    }

    @Override
    public final void visitServiceDescriptor(String type, String classname) {
        if (StringUtils.isNotEmpty(type) && StringUtils.isNotEmpty(classname)) {
            serviceDescriptors.computeIfAbsent(type, s -> new LinkedHashSet<>()).add(classname);
        }
    }

    @Override
    public void visitServiceDescriptor(String type, String classname, Element originatingElement) {
        if (StringUtils.isNotEmpty(type) && StringUtils.isNotEmpty(classname)) {
            serviceDescriptors.computeIfAbsent(type, s -> new LinkedHashSet<>()).add(classname);
        }
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements) {
        return Optional.empty();
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        return Optional.empty();
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path, Element... originatingElements) {
        return Optional.empty();
    }

    @Override
    public void finish() {
        classStreams.forEach((name, stream) ->
            classLoader.addClass(name, stream.toByteArray())
        );
        classStreams.clear();
        serviceDescriptors.forEach((name, files) -> {
            try {
                classLoader.addService(name, files);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        });
        serviceDescriptors.clear();
    }
}
