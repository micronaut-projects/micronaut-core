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
package io.micronaut.jackson.core.env

import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.io.scan.ClassPathResourceLoader
import spock.lang.Specification

import java.util.stream.Stream
/**
 * @author Graeme Rocher
 * @since 1.0
 */
class JsonPropertySourceLoaderSpec extends Specification {
    void "test json env property source loader"() {
        given:
        EnvJsonPropertySourceLoader loader = new EnvJsonPropertySourceLoader() {
            @Override
            protected String getEnvValue() {
                return '''\
{ "hibernate":
    { "cache":
        { "queries": false }
    },
  "dataSource":
    { "pooled": true,
      "driverClassName": "org.h2.Driver",
      "username": "sa",
      "password": "",
      "something": [1,2]
    }
}
'''
            }
        }

        when:
        Environment env = Mock(Environment)
        env.isPresent(_) >> true
        env.getActiveNames() >> ([] as Set)

        def result = loader.load(env)

        then:
        result.isPresent()

        when:
        PropertySource propertySource = result.get()

        then:
        propertySource.get("hibernate.cache.queries") == false
        propertySource.get("dataSource.pooled") == true
        propertySource.get("dataSource.password") == ''
        propertySource.get("dataSource.something") == [1,2]


    }

    void "test json property source loader"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.addURL(JsonPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
        Environment env = Environment.create(new ApplicationContextConfiguration() {
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
                        if(path.endsWith('-test.json')) {
                            return Optional.of(new ByteArrayInputStream('''\
{ "dataSource":
    { "jmxExport": true,
      "username": "sa",
      "password": "test"
    }
}
'''.bytes))
                        }
                        else if(path.endsWith("application.json")) {
                            return Optional.of(new ByteArrayInputStream('''\
{ "hibernate":
    { "cache":
        { "queries": false }
    },
  "dataSource":
    { "pooled": true,
      "driverClassName": "org.h2.Driver",
      "username": "sa",
      "password": "",
      "something": [1,2]
    }
}
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
        env.get("data-source.something", List).get() == [1,2]



    }
}
