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
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A simple {@link ClassPathResourceLoader} that uses a {@link Class}.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Experimental
@NullMarked
public class ClassClassPathResourceLoader implements ClassPathResourceLoader {

    private final Class<?> clazz;
    private final String basePath;

    public ClassClassPathResourceLoader(Class<?> clazz) {
        this(clazz, "");
    }

    public ClassClassPathResourceLoader(Class<?> clazz, String basePath) {
        this.clazz = clazz;
        this.basePath = basePath;
    }

    @Override
    public ClassLoader getClassLoader() {
        return clazz.getClassLoader();
    }

    @Override
    public Optional<InputStream> getResourceAsStream(String path) {
        return Optional.ofNullable(clazz.getResourceAsStream(basePath + path));
    }

    @Override
    public Optional<URL> getResource(String path) {
        return Optional.ofNullable(clazz.getResource(path));
    }

    @Override
    public Stream<URL> getResources(String name) {
        return clazz.getClassLoader().resources(name);
    }

    @Override
    public ResourceLoader forBase(String basePath) {
        return new ClassClassPathResourceLoader(clazz, this.basePath + basePath);
    }
}
