/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.core.io

import io.micronaut.core.io.file.DefaultFileSystemResourceLoader
import io.micronaut.core.io.file.FileSystemResourceLoader
import spock.lang.Specification

import java.nio.file.Paths

class FileSystemResourceLoaderSpec extends Specification {

    void "test resolving a resource"() {
        given:
        FileSystemResourceLoader loader = new DefaultFileSystemResourceLoader(base)

        expect:
        Paths.get(loader.getResource(resource).get().toURI()).toFile().isDirectory()

        where:
        base        | resource
        "."         | "src"
        "."         | "file:src/main"
        "src"       | "main"
        "file:src"  | "main"
        "file:src"  | "file:main"
        "file:src"  | "file:/main"
    }
}
