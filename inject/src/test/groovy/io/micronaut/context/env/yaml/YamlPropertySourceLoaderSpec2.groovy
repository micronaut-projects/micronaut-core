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

import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.io.scan.ClassPathResourceLoader
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.stream.Stream
/**
 * not limited by groovy version
 */
class YamlPropertySourceLoaderSpec2 extends Specification {

    void "test yaml value conversion"(String literal, Class<?> type, Object expected) {
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
                        if (path.endsWith("application.yml")) {
                            return Optional.of(new ByteArrayInputStream("""\
foo: $literal
""".bytes))
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
        env.get("foo", type).get() == expected

        where:
        // note: string->Date is not supported

        literal                       | type           | expected
        // YMD
        '2022-08-12'                  | LocalDate      | LocalDate.of(2022, 8, 12)
        '"2022-08-12"'                | LocalDate      | LocalDate.of(2022, 8, 12)
        '2022-08-12'                  | Date           | Date.from(LocalDate.of(2022, 8, 12).atTime(0, 0).atOffset(ZoneOffset.UTC).toInstant())
        // YMD HMS
        '2022-08-12T10:12:34'         | LocalDateTime  | LocalDateTime.of(2022, 8, 12, 10, 12, 34)
        '"2022-08-12T10:12:34"'       | LocalDateTime  | LocalDateTime.of(2022, 8, 12, 10, 12, 34)
        '2022-08-12T10:12:34'         | Date           | Date.from(LocalDateTime.of(2022, 8, 12, 10, 12, 34).atOffset(ZoneOffset.UTC).toInstant())
        // YMD HMS Z
        '2022-08-12T10:12:34+05:00'   | OffsetDateTime | LocalDateTime.of(2022, 8, 12, 10, 12, 34).atOffset(ZoneOffset.ofHours(5))
        '"2022-08-12T10:12:34+05:00"' | OffsetDateTime | LocalDateTime.of(2022, 8, 12, 10, 12, 34).atOffset(ZoneOffset.ofHours(5))
        '2022-08-12T10:12:34+05:00'   | Date           | Date.from(LocalDateTime.of(2022, 8, 12, 10, 12, 34).atOffset(ZoneOffset.ofHours(5)).toInstant())
    }
}
