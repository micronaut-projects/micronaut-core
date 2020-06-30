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
package io.micronaut.core.io.file;

import io.micronaut.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads resources from the file system.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class DefaultFileSystemResourceLoader implements FileSystemResourceLoader {

    private final Optional<Path> baseDirPath;

    /**
     * Default constructor.
     */
    public DefaultFileSystemResourceLoader() {
        this.baseDirPath = Optional.empty();
    }

    /**
     * @param baseDirPath The base directory
     */
    public DefaultFileSystemResourceLoader(File baseDirPath) {
        this.baseDirPath = Optional.of(baseDirPath.toPath());
    }

    /**
     * @param path The path
     */
    public DefaultFileSystemResourceLoader(String path) {
        this.baseDirPath = Optional.of(Paths.get(normalize(path)));
    }

    /**
     * @param path The path
     */
    public DefaultFileSystemResourceLoader(Path path) {
        this.baseDirPath = Optional.of(path);
    }

    @Override
    public Optional<InputStream> getResourceAsStream(String path) {
        Path filePath = getFilePath(normalize(path));
        try {
            return Optional.of(Files.newInputStream(filePath));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<URL> getResource(String path) {
        Path filePath = getFilePath(normalize(path));
        if (Files.exists(filePath) && Files.isReadable(filePath) && !Files.isDirectory(filePath)) {
            try {
                URL url = filePath.toUri().toURL();
                return Optional.of(url);
            } catch (MalformedURLException e) {
            }
        }
        return Optional.empty();
    }

    @Override
    public Stream<URL> getResources(String name) {
        return getResource(name).map(Stream::of).orElseGet(Stream::empty);
    }

    /**
     * @param basePath The path to load resources
     * @return The resource loader
     */
    @Override
    public ResourceLoader forBase(String basePath) {
        return new DefaultFileSystemResourceLoader(basePath);
    }

    @SuppressWarnings("MagicNumber")
    private static String normalize(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("file:")) {
            path = path.substring(5);
        }
        return path;
    }

    private Path getFilePath(String path) {
        return baseDirPath.map(dir -> dir.resolve(path)).orElseGet(() -> Paths.get(path));
    }
}
