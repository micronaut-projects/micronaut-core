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
import io.micronaut.core.io.ResourceLoadStrategy
import io.micronaut.core.io.ResourceLoadStrategyType
import io.micronaut.core.io.scan.ClassPathResourceLoader
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
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
        }.configurationLoadingStrategy(ResourceLoadStrategy.builder()
            .type(ResourceLoadStrategyType.FIRST_MATCH)
            .warnOnDuplicates(false))

        when:
        def env = new DefaultEnvironment(configuration).start()

        then:
        env.getProperty("foo", String).orElse(null) == "from-yml"

        cleanup:
        env?.close()
    }

    void "loadPropertySourceFromAbstractLoader MERGE_ALL merges duplicate resources"() {
        given:
        def jar1 = createJarWithEntry("lib-1.jar", "application.yml", "foo: one\nbar: one\n")
        def jar2 = createJarWithEntry("app-1.jar", "application.yml", "foo: two\nbaz: two\n")
        def loader = new InMemoryClassPathResourceLoader(
                resources: [:],
                resourcesByName: ["application.yml": [
                        jarUrl(jar1, "application.yml"),
                        jarUrl(jar2, "application.yml")
                ]],
                returnNullResourceStream: false
        )
        def configuration = new DefaultApplicationContextBuilder() {
            @Override
            ClassPathResourceLoader getResourceLoader() {
                return loader
            }
        }.configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.MERGE_ALL))

        when:
        def env = new DefaultEnvironment(configuration).start()

        then:
        env.getProperty("foo", String).orElse(null) == "two"
        env.getProperty("bar", String).orElse(null) == "one"
        env.getProperty("baz", String).orElse(null) == "two"

        cleanup:
        env?.close()
    }

    void "loadPropertySourceFromAbstractLoader MERGE_ALL mergeOrder affects merge order"() {
        given:
        def jarLib = createJarWithEntry("lib-1.jar", "application.yml", "foo: lib\n")
        def jarApp = createJarWithEntry("app-1.jar", "application.yml", "foo: app\n")
        def loader = new InMemoryClassPathResourceLoader(
                resources: [:],
                // Intentionally reversed so mergeOrder has an effect
                resourcesByName: ["application.yml": [
                        jarUrl(jarApp, "application.yml"),
                        jarUrl(jarLib, "application.yml")
                ]],
                returnNullResourceStream: false
        )
        def configuration = new DefaultApplicationContextBuilder() {
            @Override
            ClassPathResourceLoader getResourceLoader() {
                return loader
            }
        }.configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.MERGE_ALL)
                .mergeOrder("lib-.*\\.jar", "app-.*\\.jar"))

        when:
        def env = new DefaultEnvironment(configuration).start()

        then:
        env.getProperty("foo", String).orElse(null) == "app"

        cleanup:
        env?.close()
    }

    void "loadPropertySourceFromAbstractLoader MERGE_ALL rejects invalid mergeOrder patterns"() {
        given:
        def jar1 = createJarWithEntry("lib-1.jar", "application.yml", "foo: one\n")
        def jar2 = createJarWithEntry("app-1.jar", "application.yml", "foo: two\n")
        def loader = new InMemoryClassPathResourceLoader(
                resources: [:],
                resourcesByName: ["application.yml": [
                        jarUrl(jar1, "application.yml"),
                        jarUrl(jar2, "application.yml")
                ]],
                returnNullResourceStream: false
        )
        def configuration = new DefaultApplicationContextBuilder() {
            @Override
            ClassPathResourceLoader getResourceLoader() {
                return loader
            }
        }.configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.MERGE_ALL)
                .mergeOrder("["))

        when:
        new DefaultEnvironment(configuration).start()

        then:
        def e = thrown(ConfigurationException)
        e.message.contains("Invalid mergeOrder regex pattern")
    }

    void "loadPropertySourceFromAbstractLoader FAIL_ON_DUPLICATE fails for duplicates within the same extension"() {
        given:
        def jar1 = createJarWithEntry("a.jar", "application.yml", "foo: one\n")
        def jar2 = createJarWithEntry("b.jar", "application.yml", "foo: two\n")
        def loader = new InMemoryClassPathResourceLoader(
                resources: [:],
                resourcesByName: ["application.yml": [
                        jarUrl(jar1, "application.yml"),
                        jarUrl(jar2, "application.yml")
                ]],
                returnNullResourceStream: false
        )
        def configuration = new DefaultApplicationContextBuilder() {
            @Override
            ClassPathResourceLoader getResourceLoader() {
                return loader
            }
        }.configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.FAIL_ON_DUPLICATE))

        when:
        new DefaultEnvironment(configuration).start()

        then:
        def e = thrown(ConfigurationException)
        e.message.contains("Duplicate configuration resource 'application.yml'")
    }

    private static File createJarWithEntry(String fileName, String entryName, String content) {
        File jar = Files.createTempFile(fileName.replace('.jar', ''), ".jar").toFile()
        jar.deleteOnExit()
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))
        try {
            JarEntry entry = new JarEntry(entryName)
            jos.putNextEntry(entry)
            jos.write(content.getBytes(StandardCharsets.UTF_8))
            jos.closeEntry()
        } finally {
            jos.close()
        }
        return jar
    }

    private static URL jarUrl(File jar, String entryName) {
        URL fileUrl = jar.toURI().toURL()
        return new URL("jar:" + fileUrl.toExternalForm() + "!/" + entryName)
    }

    private static final class InMemoryClassPathResourceLoader implements ClassPathResourceLoader {
        Map<String, String> resources = [:]
        Map<String, List<URL>> resourcesByName = [:]
        boolean returnNullResourceStream

        @Override
        Optional<InputStream> getResourceAsStream(String path) {
            String content = resources.get(path)
            if (content == null) {
                List<URL> urls = resourcesByName.get(path)
                if (urls == null || urls.isEmpty()) {
                    return Optional.empty()
                }
                try {
                    return Optional.of(urls.get(0).openStream())
                } catch (IOException e) {
                    return Optional.empty()
                }
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
