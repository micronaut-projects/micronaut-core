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
package io.micronaut.context.env

import io.micronaut.context.DefaultApplicationContextBuilder
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.io.scan.ClassPathResourceLoader
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.stream.Stream

class DefaultEnvironmentLoadPropertySourceFromAbstractLoaderSpec extends Specification {

    void "loadPropertySourceFromAbstractLoader tolerates null getResources()"() {
        given:
        def loader = new InMemoryClassPathResourceLoader(
                resources: ["application.yml": "foo: bar\n"],
                resourcesByName: [:],
                returnNullResourceStream: true
        )

        def configuration = new DefaultApplicationContextBuilder() {
            @Override
            ClassPathResourceLoader getResourceLoader() {
                return loader
            }
        }

        when:
        def env = new DefaultEnvironment(configuration).start()

        then:
        env.getProperty("foo", String).orElse(null) == "bar"

        cleanup:
        env?.close()
    }

    void "loadPropertySourceFromAbstractLoader de-dupes identical URLs returned by getResources()"() {
        given:
        URL url = new URL("file:/test/application.yml")
        def loader = new InMemoryClassPathResourceLoader(
                resources: ["application.yml": "foo: bar\n"],
                resourcesByName: ["application.yml": [url, url]],
                returnNullResourceStream: false
        )
        def configuration = new DefaultApplicationContextBuilder() {
            @Override
            ClassPathResourceLoader getResourceLoader() {
                return loader
            }
        }

        when:
        def env = new DefaultEnvironment(configuration).start()

        then:
        env.getProperty("foo", String).orElse(null) == "bar"

        cleanup:
        env?.close()
    }

    void "loadPropertySourceFromAbstractLoader fails fast when both application.yml and application.yaml exist"() {
        given:
        URL yml = new URL("file:/test/application.yml")
        URL yaml = new URL("file:/test/application.yaml")
        def loader = new InMemoryClassPathResourceLoader(
                resources: [:],
                resourcesByName: [
                        "application.yml" : [yml],
                        "application.yaml": [yaml]
                ],
                returnNullResourceStream: false
        )
        def configuration = new DefaultApplicationContextBuilder() {
            @Override
            ClassPathResourceLoader getResourceLoader() {
                return loader
            }
        }

        when:
        new DefaultEnvironment(configuration).start()

        then:
        def e = thrown(ConfigurationException)
        e.message.contains("Duplicate configuration resource 'application'")
        e.message.contains("application.yml")
        e.message.contains("application.yaml")
    }

    void "loadPropertySourceFromAbstractLoader keeps FIRST_MATCH semantics across yml/yaml when warnings are disabled"() {
        given:
        URL yml = new URL("file:/test/application.yml")
        URL yaml = new URL("file:/test/application.yaml")
        def loader = new InMemoryClassPathResourceLoader(
                resources: [
                        "application.yml" : "foo: from-yml\n",
                        "application.yaml": "foo: from-yaml\n",
                ],
                resourcesByName: [
                        "application.yml" : [yml],
                        "application.yaml": [yaml]
                ],
                returnNullResourceStream: false
        )
        def configuration = new DefaultApplicationContextBuilder() {
            @Override
            ClassPathResourceLoader getResourceLoader() {
                return loader
            }
        }.configurationLoadingStrategy { b ->
            b.type(ConfigurationLoadStrategyType.FIRST_MATCH)
            b.warnOnDuplicates(false)
        }

        when:
        def env = new DefaultEnvironment(configuration).start()

        then:
        env.getProperty("foo", String).orElse(null) == "from-yml"

        cleanup:
        env?.close()
    }

    private static final class InMemoryClassPathResourceLoader implements ClassPathResourceLoader {
        Map<String, String> resources = [:]
        Map<String, List<URL>> resourcesByName = [:]
        boolean returnNullResourceStream

        @Override
        Optional<InputStream> getResourceAsStream(String path) {
            String content = resources.get(path)
            if (content == null) {
                return Optional.empty()
            }
            return Optional.of(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
        }

        @Override
        Optional<URL> getResource(String path) {
            List<URL> urls = resourcesByName.get(path)
            if (urls == null || urls.isEmpty()) {
                return Optional.empty()
            }
            return Optional.of(urls.get(0))
        }

        @Override
        Stream<URL> getResources(String name) {
            if (returnNullResourceStream) {
                return null
            }
            List<URL> urls = resourcesByName.get(name)
            if (urls == null) {
                return Stream.empty()
            }
            return urls.stream()
        }

        @Override
        boolean supportsPrefix(String path) {
            return false
        }

        @Override
        ResourceLoader forBase(String basePath) {
            return this
        }

        @Override
        ClassLoader getClassLoader() {
            return getClass().getClassLoader()
        }
    }
}
