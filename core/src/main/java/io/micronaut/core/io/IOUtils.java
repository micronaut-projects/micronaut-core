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
package io.micronaut.core.io;

import io.micronaut.core.annotation.Blocking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Utility methods for I/O operations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class IOUtils {

    private static final Logger LOG = LoggerFactory.getLogger(IOUtils.class);
    private static final int BUFFER_MAX = 8192;

    /**
     * Read the content of the BufferedReader and return it as a String in a blocking manner.
     * The BufferedReader is closed afterwards.
     *
     * @param reader a BufferedReader whose content we want to read
     * @return a String containing the content of the buffered reader
     * @throws IOException if an IOException occurs.
     * @since 1.0
     */
    @Blocking
    public static String readText(BufferedReader reader) throws IOException {
        StringBuilder answer = new StringBuilder();
        if (reader == null) {
            return answer.toString();
        }
        // reading the content of the file within a char buffer
        // allow to keep the correct line endings
        char[] charBuffer = new char[BUFFER_MAX];
        int nbCharRead /* = 0*/;
        try {
            while ((nbCharRead = reader.read(charBuffer)) != -1) {
                // appends buffer
                answer.append(charBuffer, 0, nbCharRead);
            }
            Reader temp = reader;
            reader = null;
            temp.close();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to close reader: " + e.getMessage(), e);
                }
            }
        }
        return answer.toString();
    }
}
