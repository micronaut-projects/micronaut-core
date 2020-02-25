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
package io.micronaut.web.router

import io.micronaut.core.io.ResourceResolver
import io.micronaut.web.router.resource.StaticResourceConfiguration
import io.micronaut.web.router.resource.StaticResourceResolver
import spock.lang.Specification

class StaticResourceResolverSpec extends Specification {

    void "test the path is not mangled between resolution attempts"() {
        given:
        ResourceResolver rr = new ResourceResolver()
        StaticResourceConfiguration config1 = new StaticResourceConfiguration(rr)
        config1.setPaths(["classpath:public"])
        config1.setMapping("/**")
        StaticResourceConfiguration config2 = new StaticResourceConfiguration(rr)
        config2.setPaths(["classpath:other"])
        config2.setMapping("/other/**")
        StaticResourceResolver resolver = new StaticResourceResolver([config1, config2])

        when:
        URL url = resolver.resolve("/").get()

        then:
        url.toString().endsWith("public/index.html")

        when:
        url = resolver.resolve("/other").get()

        then:
        url.toString().endsWith("other/index.html")
    }

    void "test add static route programmatically"() {
        given:
        ResourceResolver rr = new ResourceResolver()
        StaticResourceResolver resolver = new StaticResourceResolver([])

        when:
        Optional<URL> url = resolver.resolve("/")

        then:
        !url.isPresent()

        when:
        resolver.addRoute("/other/**", [rr.getLoaderForBasePath("classpath:other").get()])
        url = resolver.resolve("/other")

        then:
        url.isPresent()
        url.get().toString().endsWith("other/index.html")
    }

    void "test remove static route programmatically"() {
        given:
        ResourceResolver rr = new ResourceResolver()
        StaticResourceConfiguration config1 = new StaticResourceConfiguration(rr)
        config1.setPaths(["classpath:public"])
        config1.setMapping("/**")
        StaticResourceConfiguration config2 = new StaticResourceConfiguration(rr)
        config2.setPaths(["classpath:other"])
        config2.setMapping("/other/**")
        StaticResourceResolver resolver = new StaticResourceResolver([config1, config2])

        when:
        URL url = resolver.resolve("/").get()

        then:
        url.toString().endsWith("public/index.html")

        when:
        url = resolver.resolve("/other").get()

        then:
        url.toString().endsWith("other/index.html")

        when:
        boolean removed = resolver.removeRoute("/other/**")
        Optional<URL> nurl = resolver.resolve("/other")

        then:
        removed
        !nurl.isPresent()
    }
}
