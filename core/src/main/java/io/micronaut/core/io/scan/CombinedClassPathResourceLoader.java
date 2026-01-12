/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.core.io.scan;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.ResourceLoader;
import org.jspecify.annotations.NullMarked;

import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A simple {@link ClassPathResourceLoader} that is combining multiple loaders.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@NullMarked
@Experimental
public class CombinedClassPathResourceLoader implements ClassPathResourceLoader {

    private final ClassPathResourceLoader[] loaders;

    private CombinedClassPathResourceLoader(ClassPathResourceLoader... loaders) {
        this.loaders = loaders;
        if (loaders.length == 0) {
            throw new IllegalArgumentException("At least one loader must be provided");
        }
    }

    /**
     * The factory method.
     * @param loaders The loaders.
     * @return the combined loader.
     */
    public static ClassPathResourceLoader of(ClassPathResourceLoader... loaders) {
        return new CombinedClassPathResourceLoader(loaders);
    }

    @Override
    public ClassLoader getClassLoader() {
        return loaders[0].getClassLoader();
    }

    @Override
    public Optional<InputStream> getResourceAsStream(String path) {
        return Arrays.stream(loaders)
            .flatMap(loader -> loader.getResourceAsStream(path).stream())
            .findFirst();
    }

    @Override
    public Optional<URL> getResource(String path) {
        return Arrays.stream(loaders)
            .flatMap(loader -> loader.getResource(path).stream())
            .findFirst();
    }

    @Override
    public Stream<URL> getResources(String name) {
        return Arrays.stream(loaders)
            .flatMap(loader -> loader.getResources(name));
    }

    @Override
    public ResourceLoader forBase(String basePath) {
        return new CombinedClassPathResourceLoader(
            Arrays.stream(loaders)
                .map(loader -> loader.forBase(basePath))
                .toArray(ClassPathResourceLoader[]::new)
        );
    }
}
