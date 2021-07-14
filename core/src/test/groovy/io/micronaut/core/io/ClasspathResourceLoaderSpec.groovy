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

import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.core.io.scan.DefaultClassPathResourceLoader
import spock.lang.Specification

class ClasspathResourceLoaderSpec extends Specification {

    void "test resolving a resource"() {
        given:
        ClassPathResourceLoader loader = new DefaultClassPathResourceLoader(getClass().getClassLoader(), base)

        expect:
        loader.getResource(resource).get().text == "bar.txt"

        where:
        base             | resource
        null             | "foo/bar.txt"
        null             | "classpath:foo/bar.txt"
        "foo"            | "bar.txt"
        "/foo"           | "bar.txt"
        "classpath:foo"  | "bar.txt"
        "classpath:foo"  | "classpath:bar.txt"
        "classpath:/foo" | "classpath:bar.txt"
        "classpath:/foo" | "classpath:/bar.txt"
        "classpath:foo"  | "classpath:/bar.txt"
    }

    void "test resolving a classpath resource with a relative path"() {
        given:
        ClassPathResourceLoader loader = new DefaultClassPathResourceLoader(getClass().getClassLoader(), base)

        expect:
        loader.getResource(resource).isPresent() == present
        loader.getResourceAsStream(resource).map({ InputStream io -> io.close(); io})
                .isPresent() == present
        loader.getResources(resource).findFirst().isPresent() == present

        where:
        base             | resource                                        | present
        "classpath:foo"  | "classpath:../foo/bar.txt"                      | true
        "classpath:foo"  | "classpath:../other/../foo/bar.txt"             | true
        "classpath:foo"  | "classpath:../foo/../other/shouldNotAccess.txt" | false
        "classpath:foo"  | "classpath:../other/shouldNotAccess.txt"        | false
        "classpath:foo"  | "classpath:foo/../../other/shouldNotAccess.txt" | false
    }
}
