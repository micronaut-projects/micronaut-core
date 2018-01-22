/*
 * Copyright 2017 original authors
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
package org.particleframework.core.io.file;

import org.particleframework.core.io.ResourceLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads resources from the file system.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class FileSystemResourceLoader implements ResourceLoader {

    private final File baseDir;

    public FileSystemResourceLoader() {
        this(new File("."));
    }

    public FileSystemResourceLoader(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public Optional<InputStream> getResourceAsStream(String path) {
        File file = forPath(path);
        try {
            return Optional.of(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<URL> getResource(String path) {
        File file = forPath(path);
        if (file.exists() && file.canRead()) {
            try {
                URL url = file.toURI().toURL();
                return Optional.of(url);
            } catch (MalformedURLException e) {}
        }
        return Optional.empty();
    }

    @Override
    public Stream<URL> getResources(String path) {
        File file = forPath(path);
        //TODO: Implement file listing
        return Stream.empty();
    }

    protected File forPath(String path) {
        return new File(baseDir, path);
    }
}
