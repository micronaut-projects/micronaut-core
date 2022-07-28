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
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
        Path myPath;
        List<Closeable> toClose = new ArrayList<>();
        try {
            String scheme = uri.getScheme();

            try {
                if ("jar".equals(scheme)) {
                    // try to match FileSystems.newFileSystem(URI) semantics for zipfs here.
                    // Basically ignores anything after the !/ if it exists, and uses the part
                    // before as the jar path to extract.
                    String jarUri = uri.getRawSchemeSpecificPart();
                    int sep = jarUri.lastIndexOf("!/");
                    if (sep != -1) {
                        jarUri = jarUri.substring(0, sep);
                    }
                    // now, add the !/ at the end again so that loadNestedJarUri can handle it:
                    jarUri += "!/";
                    myPath = loadNestedJarUri(toClose, jarUri).resolve(path);
                } else if ("file".equals(scheme)) {
                    myPath = Paths.get(uri).resolve(path);
                } else {
                    // graal resource: case
                    myPath = Paths.get(uri);
                }
            } catch (FileSystemNotFoundException e) {
                myPath = null;
            }

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
        } catch (IOException e) {
            // ignore, can't do anything here and can't log because class used in compiler
        } finally {
            for (Closeable closeable : toClose) {
                try {
                    closeable.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Path loadNestedJarUri(List<Closeable> toClose, String jarUri) throws IOException {
        int sep = jarUri.lastIndexOf("!/");
        if (sep == -1) {
            return Paths.get(URI.create(jarUri));
        }
        Path jarPath = loadNestedJarUri(toClose, jarUri.substring(0, sep));
        if (Files.isDirectory(jarPath)) {
            // spring boot creates weird jar URLs, like 'jar:file:/xyz.jar!/BOOT-INF/classes!/abc'
            // This check makes our class loading resilient to that
            return jarPath;
        }
        FileSystem zipfs;
        try {
            // can't use newFileSystem(Path) here (without CL) because it doesn't exist on java 8
            // the CL cast is necessary because since java 13 there is a newFileSystem(Path, Map)
            zipfs = FileSystems.newFileSystem(jarPath, (ClassLoader) null);
            toClose.add(0, zipfs);
        } catch (ProviderNotFoundException e) {
            // java versions earlier than 11 do not support nested zipfs and will fail with this
            // exception. Try to extract the file instead. This is not efficient, but what else can
            // we do?
            Path tmp = Files.createTempFile("micronaut-IOUtils-nested-zip", ".zip");
            toClose.add(0, () -> Files.deleteIfExists(tmp));
            Files.copy(jarPath, tmp, StandardCopyOption.REPLACE_EXISTING);

            zipfs = FileSystems.newFileSystem(tmp, (ClassLoader) null);
            toClose.add(0, zipfs);
        }
        return zipfs.getPath(jarUri.substring(sep + 1));
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
