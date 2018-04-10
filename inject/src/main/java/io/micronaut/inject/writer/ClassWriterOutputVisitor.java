/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.writer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Interface to be consumed by class writers allowing visiting file names and returning appropriate streams
 *
 * @author Graeme Rocher
 */
public interface ClassWriterOutputVisitor {

    /**
     * @param classname the fully qualified classname
     * @return the output stream to write to
     * @throws IOException if an error occurs creating the output stream
     */
    OutputStream visitClass(String classname) throws IOException;

    /**
     * @param classname the fully qualified classname
     * @return An optional file it was possible to create it
     * @throws IOException If the file couldn't be created
     */
    Optional<File> visitServiceDescriptor(String classname) throws IOException;

    /**
     * Visit a file within the META-INF directory
     *
     * @param path The path to the file
     * @return An optional file it was possible to create it
     * @throws IOException If the file couldn't be created
     */
    Optional<File> visitMetaInfFile(String path) throws IOException;

    /**
     * @param type The service type
     * @return the output directory
     * @throws IOException If the file couldn't be created
     */
    default Optional<File> visitServiceDescriptor(Class type) throws IOException {
        return visitServiceDescriptor(type.getName());
    }
}
