/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.core.io.service

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class MicronautMetaServiceLoaderUtilsInvalidateSpec extends Specification {

    @TempDir
    Path root

    void "invalidating a loader makes the next lookup scan the index again"() {
        given:
        Path service = Files.createDirectories(root.resolve("META-INF/micronaut/example.Service"))
        Files.writeString(service.resolve("a.A"), "")
        URLClassLoader loader = new URLClassLoader([root.toUri().toURL()] as URL[], null)

        expect:
        MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(loader, "example.Service") == ["a.A"] as Set

        when: "an entry appears while the loader's entries are cached"
        Files.writeString(service.resolve("b.B"), "")

        then: "the cache still answers"
        MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(loader, "example.Service") == ["a.A"] as Set

        when:
        MicronautMetaServiceLoaderUtils.invalidate(loader)

        then:
        MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(loader, "example.Service") == ["a.A", "b.B"] as Set

        cleanup:
        loader.close()
    }
}
