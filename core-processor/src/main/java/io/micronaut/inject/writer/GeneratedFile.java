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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;

/**
 * A common interface to allow referencing a generated file in either Groovy or Java.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface GeneratedFile {

    /**
     * The URI to write to.
     *
     * @return The URI
     */
    URI toURI();

    /**
     * @return The name of the file
     */
    String getName();

    /**
     * Gets an InputStream for this file object.
     *
     * @return an InputStream
     * @throws IllegalStateException         if this file object was opened for writing and does not support reading
     * @throws UnsupportedOperationException if this kind of file object does not support byte access
     * @throws IOException                   if an I/O error occurred
     */
    InputStream openInputStream() throws IOException;

    /**
     * Gets an OutputStream for this file object.
     *
     * @return an OutputStream
     * @throws IllegalStateException         if this file object was opened for reading and does not support writing
     * @throws UnsupportedOperationException if this kind of file object does not support byte access
     * @throws IOException                   if an I/O error occurred
     */
    OutputStream openOutputStream() throws IOException;

    /**
     * Gets a reader for this object.  The returned reader will replace bytes that cannot be decoded with the default
     * translation character.  In addition, the reader may report a diagnostic unless {@code ignoreEncodingErrors}
     * is true.
     *
     * @return a Reader
     * @throws IllegalStateException         if this file object was opened for writing and does not support reading
     * @throws UnsupportedOperationException if this kind of file object does not support character access
     * @throws IOException                   if an I/O error occurred
     */
    Reader openReader() throws IOException;

    /**
     * Gets the character content of this file object, if available. Any byte that cannot be decoded will be replaced
     * by the default translation character.  In addition, a diagnostic may be reported unless
     * {@code ignoreEncodingErrors} is true.
     *
     * @return a CharSequence if available; {@code null} otherwise
     * @throws IllegalStateException         if this file object was opened for writing and does not support reading
     * @throws UnsupportedOperationException if this kind of file object does not support character access
     * @throws IOException                   if an I/O error occurred
     */
    CharSequence getTextContent() throws IOException;

    /**
     * Gets a Writer for this file object.
     *
     * @return a Writer
     * @throws IllegalStateException         if this file object was opened for reading and does not support writing
     * @throws UnsupportedOperationException if this kind of file object does not support character access
     * @throws IOException                   if an I/O error occurred
     */
    Writer openWriter() throws IOException;

}
