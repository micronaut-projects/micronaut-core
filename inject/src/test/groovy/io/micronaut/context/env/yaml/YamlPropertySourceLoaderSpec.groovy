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
package io.micronaut.context.env.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.io.scan.ClassPathResourceLoader
import spock.lang.Specification

import java.util.stream.Stream
/**
 * Created by graemerocher on 15/06/2017.
 */
class YamlPropertySourceLoaderSpec extends Specification {

    void "test load yaml properties source"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.addURL(YamlPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
        Environment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() {
                return ["test"]
            }

            @Override
            ClassLoader getClassLoader() {
                return gcl
            }

            @Override
            ClassPathResourceLoader getResourceLoader() {
                return new ClassPathResourceLoader() {
                    @Override
                    ClassLoader getClassLoader() {
                        return gcl
                    }

                    @Override
                    Optional<InputStream> getResourceAsStream(String path) {
                        if (path.endsWith('-test.yml')) {
                            return Optional.of(new ByteArrayInputStream('''\
dataSource:
    jmxExport: true
    username: sa
    password: 'test'
'''.bytes))
                        } else if (path.endsWith("application.yml")) {
                            return Optional.of(new ByteArrayInputStream('''\
hibernate:
    cache:
        queries: false
dataSource:
    pooled: true
    driverClassName: org.h2.Driver
    username: sa
    password: ''
'''.bytes))
                        }
                        return Optional.empty()
                    }

                    @Override
                    Optional<URL> getResource(String path) {
                        return Optional.empty()
                    }

                    @Override
                    Stream<URL> getResources(String name) {
                        return Stream.empty()
                    }

                    @Override
                    ResourceLoader forBase(String basePath) {
                        return this
                    }
                }
            }
        })

        when:
        env.start()

        then:
        env.get("hibernate.cache.queries", Boolean).get() == false
        env.get("data-source.pooled", Boolean).get() == true
        env.get("data-source.password", String).get() == 'test'
        env.get("data-source.jmx-export", boolean).get() == true
    }

    void "test datasources default"() {
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.addURL(YamlPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
        Environment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() {
                return ["test"]
            }

            @Override
            ClassLoader getClassLoader() {
                return gcl
            }

            @Override
            ClassPathResourceLoader getResourceLoader() {
                return new ClassPathResourceLoader() {
                    @Override
                    ClassLoader getClassLoader() {
                        return gcl
                    }

                    @Override
                    Optional<InputStream> getResourceAsStream(String path) {
                        if (path.endsWith('-test.yml')) {
                            return Optional.of(new ByteArrayInputStream('''\
datasources.default: {}
'''.bytes))
                        } else if (path.endsWith("application.yml")) {
                            return Optional.of(new ByteArrayInputStream('''\
datasources.default: {}
'''.bytes))
                        }
                        return Optional.empty()
                    }

                    @Override
                    Optional<URL> getResource(String path) {
                        return Optional.empty()
                    }

                    @Override
                    Stream<URL> getResources(String name) {
                        return Stream.empty()
                    }

                    @Override
                    ResourceLoader forBase(String basePath) {
                        return this
                    }
                }
            }
        })

        when:
        env.start()

        then:
        env.get("datasources.default", String).get() == "{}"
        env.get("datasources.default", Map).get() == [:]

    }

    void "test properties are resolved from yaml files"() {
        ApplicationContext ctx = ApplicationContext.run("other")

        expect:
        ctx.containsProperty("other-config")
    }

    void "test properties with spaces"() {
        ApplicationContext ctx = ApplicationContext.run("spaces")

        expect:
        ctx.containsProperties("test")
        ctx.containsProperty("test.Key with space")
        ctx.containsProperty("test.key-with-space")
    }
}
