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
import io.micronaut.core.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Utility methods for I/O operations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("java:S1118")
public class IOUtils {

    private static final int BUFFER_MAX = 8192;

    /**
     * Iterates over each directory in a JAR or file system.
     * @param url The URL
     * @param path The path
     * @param consumer The consumer
     *                 @since 3.5.0
     */
    @Blocking
    @SuppressWarnings({"java:S2095", "S1141"})
    public static void eachFile(@NonNull URL url, String path, @NonNull Consumer<Path> consumer) {
        try {
            eachFile(url.toURI(), path, consumer);
        } catch (URISyntaxException e) {
            // ignore and proceed
        }
    }

    /**
     * Iterates over each directory in a JAR or file system.
     * @param uri The URI
     * @param path The path
     * @param consumer The consumer
     * @since 3.5.0
     */
    @Blocking
    @SuppressWarnings({"java:S2095", "java:S1141", "java:S3776"})
    public static void eachFile(@NonNull URI uri, String path, @NonNull Consumer<Path> consumer) {
        try {
            String scheme = uri.getScheme();
            FileSystem fileSystem = null;

            try {
                if ("jar".equals(scheme)) {
                    synchronized (IOUtils.class) {
                        final String[] jarFiles = uri.toString().split("!");
                        try {
                            fileSystem = FileSystems.getFileSystem(uri);
                        } catch (FileSystemNotFoundException e) {
                            //no-op
                        }
                        if (fileSystem == null || !fileSystem.isOpen()) {
                            try {
                                fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                            } catch (FileSystemAlreadyExistsException e) {
                                fileSystem = FileSystems.getFileSystem(uri);
                            }
                        }
                        Path currentJar = null;
                        if (jarFiles.length > 2) {
                            for (int i = 1; i < (jarFiles.length -1); i++) {
                                final Path nestedJar = fileSystem.getPath(jarFiles[i]);
                                final Path extractedJar = Files.createTempFile("jar-" + i, ".jar");
                                Files.copy(nestedJar, extractedJar, StandardCopyOption.REPLACE_EXISTING);
                                fileSystem.close();
                                if (currentJar != null) {
                                    Files.delete(currentJar);
                                }
                                currentJar = extractedJar;
                                fileSystem = FileSystems.newFileSystem(URI.create("jar:file:" + extractedJar), Collections.emptyMap());
                            }
                        }
                        try {
                            walkFiles(consumer,  fileSystem.getPath(path));
                        } finally {
                            if (fileSystem != null && fileSystem.isOpen()) {
                                try {
                                    fileSystem.close();
                                } catch (ClosedFileSystemException e) {
                                    // no-op, because it is already closed
                                }
                            }
                        }
                    }

                } else if ("file".equals(scheme)) {
                    walkFiles(consumer, Paths.get(uri).resolve(path));
                } else {
                    // graal resource: case
                    walkFiles(consumer, Paths.get(uri));
                }
            } catch (FileSystemNotFoundException e) {
                // no-op, can't log because class is used in compiler
            }


        } catch (IOException e) {
            // ignore, can't do anything here and can't log because class used in compiler
        }
    }

    private static void walkFiles(Consumer<Path> consumer, Path myPath) throws IOException {
        if (myPath != null) {
            try (Stream<Path> walk = Files.walk(myPath, 1)) {
                for (Iterator<Path> it = walk.iterator(); it.hasNext();) {
                    final Path currentPath = it.next();
                    if (currentPath.equals(myPath) || Files.isHidden(currentPath) || currentPath.getFileName().startsWith(".")) {
                        continue;
                    }
                    consumer.accept(currentPath);
                }
            }
        }
    }

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
                if (IOLogging.LOG.isWarnEnabled()) {
                    IOLogging.LOG.warn("Failed to close reader: " + e.getMessage(), e);
                }
            }
        }
        return answer.toString();
    }

    private static final class IOLogging {
        private static final Logger LOG = LoggerFactory.getLogger(IOLogging.class);
    }
}
