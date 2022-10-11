/*
 * Copyright 2017-2019 original authors
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
import spock.lang.Unroll

class FileSystemResourceLoaderSpec extends Specification {

    @Unroll
    void "test resolving a resource for #base and #resource"() {
        given:
        DefaultFileSystemResourceLoader loader = new DefaultFileSystemResourceLoader(base)

        expect:
        loader.getResource(resource).isPresent() == presentViaResolver
        new File(loader.normalize(base), loader.normalize(resource)).exists() == presentOnDisk

        where:
        base                      | resource                   | presentViaResolver   | presentOnDisk
        "."                       | "src"                      | false                | true
        "."                       | "file:src/main"            | false                | true
        "src"                     | "main"                     | false                | true
        "file:src"                | "main"                     | false                | true
        "file:src"                | "file:main"                | false                | true
        "file:src"                | "file:/main"               | false                | true
        "src/test/resources"      | "foo/bar.txt"              | true                 | true
        "file:src/test/resources" | "../resources/foo/bar.txt" | true                 | true
        "file:src/test/resources" | "../../../build.gradle"    | false                | true
    }
}
