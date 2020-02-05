/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.core.io;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Interface for types that can be written to an {@link java.io.OutputStream}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Streamable {

    /**
     * Write this object to the given {@link OutputStream} using {@link StandardCharsets#UTF_8} by default.
     *
     * @param outputStream The output stream
     * @param charset      The charset to use. Defaults to {@link StandardCharsets#UTF_8}
     * @throws IOException if an error occurred while outputting data to the writer
     */
    void writeTo(OutputStream outputStream, @Nullable Charset charset) throws IOException;

    /**
     * Write this {@link Writable} to the given {@link File}.
     *
     * @param file The file
     * @throws IOException if an error occurred while outputting data to the writer
     */
    default void writeTo(File file) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(file.toPath())) {
            writeTo(outputStream);
        }
    }

    /**
     * Write this object to the given {@link OutputStream} using {@link StandardCharsets#UTF_8} by default.
     *
     * @param outputStream The output stream
     * @throws IOException if an error occurred while outputting data to the writer
     */
    default void writeTo(OutputStream outputStream) throws IOException {
        writeTo(outputStream, StandardCharsets.UTF_8);
    }
}
