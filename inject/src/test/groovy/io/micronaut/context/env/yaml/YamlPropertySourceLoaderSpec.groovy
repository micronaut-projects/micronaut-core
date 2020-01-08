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
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySourceLoader
import io.micronaut.core.io.service.ServiceDefinition
import io.micronaut.core.io.service.SoftServiceLoader
import spock.lang.Specification

/**
 * Created by graemerocher on 15/06/2017.
 */
class YamlPropertySourceLoaderSpec extends Specification {

    void "test load yaml properties source"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new YamlPropertySourceLoader()

        Environment env = new DefaultEnvironment(["test"] as String[]) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addClass(YamlPropertySourceLoader)
                gcl.addURL(YamlPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if(path.endsWith('-test.yml')) {
                    return Optional.of(new ByteArrayInputStream('''\
dataSource:
    jmxExport: true
    username: sa
    password: 'test'
'''.bytes))
                }
                else if(path.endsWith("application.yml")) {
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

        }


        when:
        env.start()

        then:
        env.get("hibernate.cache.queries", Boolean).get() == false
        env.get("data-source.pooled", Boolean).get() == true
        env.get("data-source.password", String).get() == 'test'
        env.get("data-source.jmx-export", boolean).get() == true
    }

    void "test datasources default"() {
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new YamlPropertySourceLoader()

        Environment env = new DefaultEnvironment(["test"] as String[]) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addClass(YamlPropertySourceLoader)
                gcl.addURL(YamlPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if(path.endsWith('-test.yml')) {
                    return Optional.of(new ByteArrayInputStream('''\
datasources.default: {}
'''.bytes))
                }
                else if(path.endsWith("application.yml")) {
                    return Optional.of(new ByteArrayInputStream('''\
datasources.default: {}    
'''.bytes))
                }

                return Optional.empty()
            }

        }


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
