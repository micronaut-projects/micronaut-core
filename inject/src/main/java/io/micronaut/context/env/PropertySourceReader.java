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
package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Interface for classes that read and process properties sources.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PropertySourceReader {

    /**
     * Read a property source from an input stream.
     *
     * @param name  The name of the property source
     * @param input The bytes
     * @return A map of string to values
     * @throws IOException if there is an error processing the property source
     */
    Map<String, Object> read(String name, InputStream input) throws IOException;

    /**
     * @return The extensions this reader supports.
     */
    default Set<String> getExtensions() {
        return Collections.emptySet();
    }

    /**
     * Read a property source from bytes.
     *
     * @param name  The name of the property source
     * @param bytes The bytes
     * @return A map of string to values
     */
    default Map<String, Object> read(String name, byte[] bytes) {
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            return read(name, input);
        } catch (Throwable e) {
            throw new ConfigurationException("Error reading property source [" + name + "]: " + e.getMessage(), e);
        }
    }
}
