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
package io.micronaut.inject.writer;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.inject.ast.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Interface to be consumed by class writers allowing visiting file names and returning appropriate streams.
 *
 * @author Graeme Rocher
 */
public interface ClassWriterOutputVisitor {

    /**
     * Visits a new class and returns the output stream with which should be written the bytes of the class to be
     * generated.
     * <p>
     * Note that this method should only be called from a {@link io.micronaut.inject.visitor.TypeElementVisitor.VisitorKind#AGGREGATING} visitor from within the {@link io.micronaut.inject.visitor.TypeElementVisitor#finish(io.micronaut.inject.visitor.VisitorContext)} method. If the file
     *
     * @param classname the fully qualified classname
     * @return the output stream to write to
     * @throws IOException if an error occurs creating the output stream
     */
    default OutputStream visitClass(String classname) throws IOException {
        return visitClass(classname, Element.EMPTY_ELEMENT_ARRAY);
    }

    /**
     * Visits a new class and returns the output stream with which should be written the bytes of the class to be
     * generated.
     *
     * @param classname          the fully qualified classname
     * @param originatingElement The originating element
     * @return the output stream to write to
     * @throws IOException if an error occurs creating the output stream
     */
    default OutputStream visitClass(String classname, @Nullable Element originatingElement) throws IOException {
        return visitClass(classname, new Element[]{originatingElement});
    }

    /**
     * Visits a new class and returns the output stream with which should be written the bytes of the class to be
     * generated.
     * <p>
     * Note that this method should only be called from a {@link io.micronaut.inject.visitor.TypeElementVisitor.VisitorKind#AGGREGATING} visitor from within the {@link io.micronaut.inject.visitor.TypeElementVisitor#finish(io.micronaut.inject.visitor.VisitorContext)} method. If the file
     *
     * @param classname           the fully qualified classname
     * @param originatingElements The originating elements
     * @return the output stream to write to
     * @throws IOException if an error occurs creating the output stream
     */
    OutputStream visitClass(String classname, Element... originatingElements) throws IOException;

    /**
     * Allows adding a class that will be written to the {@code META-INF/services} file under the given type and class
     * name.
     *
     * @param type      the fully qualified service name
     * @param classname the fully qualified classname
     * @throws IOException If the file couldn't be created
     */
    void visitServiceDescriptor(String type, String classname);

    /**
     * Visit a file within the META-INF directory of the classes directory.
     *
     * @param path The path to the file
     * @return An optional file it was possible to create it
     * @throws IOException If the file couldn't be created
     * @deprecated Visiting a file should supply the originating elements. Use {@link #visitMetaInfFile(String, Element...)} instead
     */
    @Deprecated
    default Optional<GeneratedFile> visitMetaInfFile(String path) {
        return visitMetaInfFile(path, Element.EMPTY_ELEMENT_ARRAY);
    }

    /**
     * Visit a file within the META-INF directory of the classes directory.
     *
     * @param path The path to the file
     * @param originatingElements The originating elements
     * @return An optional file it was possible to create it
     * @throws IOException If the file couldn't be created
     */
    Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements);

    /**
     * Visit a file that will be generated within the generated sources directory.
     *
     * @param path The path
     * @return The file if it was possible to create it
     */
    Optional<GeneratedFile> visitGeneratedFile(String path);

    /**
     * Finish writing and flush any service entries to disk.
     */
    void finish();

    /**
     * The META-INF/services entries to write.
     *
     * @return A map of service to class entries
     */
    default Map<String, Set<String>> getServiceEntries() {
        return Collections.emptyMap();
    }

    /**
     * Allows adding a class that will be written to the {@code META-INF/services} file under the given type and class
     * name.
     *
     * @param type      The service type
     * @param classname the fully qualified classname
     * @throws IOException If the file couldn't be created
     */
    default void visitServiceDescriptor(Class type, String classname) {
        visitServiceDescriptor(type.getName(), classname);
    }
}
