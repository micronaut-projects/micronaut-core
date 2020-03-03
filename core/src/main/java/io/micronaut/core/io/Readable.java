/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.naming.Named;
import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Abstraction over {@link java.io.File} and {@link java.net.URL} based I/O.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Immutable
public interface Readable extends Named {

    /**
     * Represent this Readable as an input stream.
     *
     * @return The input stream
     * @throws IOException if an I/O exception occurs
     */
    @Nonnull InputStream asInputStream() throws IOException;

    /**
     * Does the underlying readable resource exist.
     *
     * @return True if it does
     */
    boolean exists();

    /**
     * Obtain a {@link Reader} for this readable using {@link StandardCharsets#UTF_8}.
     *
     * @return The reader
     * @throws IOException if an I/O error occurs
     */
    default Reader asReader() throws IOException {
        return asReader(StandardCharsets.UTF_8);
    }

    /**
     * Obtain a {@link Reader} for this readable.
     *
     * @param charset The charset to use
     * @return The reader
     * @throws IOException if an I/O error occurs
     */
    default Reader asReader(Charset charset) throws IOException {
        ArgumentUtils.requireNonNull("charset", charset);
        return new InputStreamReader(asInputStream(), charset);
    }

    /**
     * Create a {@link Readable} for the given URL.
     *
     * @param url The URL
     * @return The readable.
     */
    static @Nonnull Readable of(@Nonnull URL url) {
        return new UrlReadable(url);
    }

    /**
     * Create a {@link Readable} for the given file.
     *
     * @param file The file
     * @return The readable.
     */
    static @Nonnull Readable of(@Nonnull File file) {
        ArgumentUtils.requireNonNull("file", file);
        return new FileReadable(file);
    }

    /**
     * Create a {@link Readable} for the given path.
     *
     * @param path The path
     * @return The readable.
     */
    static @Nonnull Readable of(@Nonnull Path path) {
        ArgumentUtils.requireNonNull("path", path);
        return new FileReadable(path.toFile());
    }
}
