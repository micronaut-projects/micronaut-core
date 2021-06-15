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

import io.micronaut.inject.ast.Element;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for testing Groovy visitors.
 *
 * @since 3.0.0
 * @author graemerocher
 */
public class InMemoryClassWriterOutputVisitor implements ClassWriterOutputVisitor {
    private final Map<String, ByteArrayOutputStream> classStreams = new LinkedHashMap<>();
    private final InMemoryByteCodeGroovyClassLoader classLoader;

    /**
     * @param classLoader The in-memory classloader
     */
    public InMemoryClassWriterOutputVisitor(InMemoryByteCodeGroovyClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public OutputStream visitClass(String classname, Element... originatingElements) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        classStreams.put(classname, stream);
        return stream;
    }

    @Override
    public void visitServiceDescriptor(String type, String classname) {

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
    public void finish() {
        classStreams.forEach((name, stream) ->
            classLoader.addClass(name, stream.toByteArray())
        );
        classStreams.clear();
    }
}
