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
package io.micronaut.core.io.file;

import io.micronaut.core.io.ResourceLoader;

/**
 * Abstraction to load resources from the file system.
 */
public interface FileSystemResourceLoader extends ResourceLoader {

    /**
     * Creation method.
     * @return loader
     */
    static FileSystemResourceLoader defaultLoader() {
        return new DefaultFileSystemResourceLoader();
    }

    /**
     * Does the loader support a prefix.
     * @param path The path to a resource including a prefix
     *             appended by a colon. Ex (classpath:, file:)
     * @return boolean
     */
    @Override
    default boolean supportsPrefix(String path) {
        return path.startsWith("file:");
    }
}
