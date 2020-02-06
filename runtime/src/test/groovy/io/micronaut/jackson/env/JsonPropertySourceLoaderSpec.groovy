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
package io.micronaut.jackson.env

import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourceLoader
import io.micronaut.core.io.service.ServiceDefinition
import io.micronaut.core.io.service.SoftServiceLoader
import spock.lang.Specification

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
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new JsonPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addClass(JsonPropertySourceLoader)
                gcl.addURL(JsonPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
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
        }


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
