/*
 * Copyright 2017 original authors
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
package org.particleframework.jackson.env

import org.particleframework.context.env.Environment
import org.particleframework.context.env.PropertySource
import org.particleframework.jackson.env.EnvJsonPropertySourceLoader
import org.particleframework.jackson.env.JsonPropertySourceLoader
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
        JsonPropertySourceLoader loader = new JsonPropertySourceLoader()

        when:
        Environment env = Mock(Environment)
        env.isPresent(_) >> true
        env.getActiveNames() >> (["test"] as Set)
        env.getResourceAsStream("application.json") >> {
            Optional.of(new ByteArrayInputStream('''\
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
        env.getResourceAsStream("application-test.json") >> {
            Optional.of(new ByteArrayInputStream('''\
{ "dataSource":
    { "jmxExport": true,
      "username": "sa",
      "password": "test" 
    }
}
'''.bytes))
        }

        def result = loader.load(env)

        then:
        result.isPresent()

        when:
        PropertySource propertySource = result.get()

        then:
        propertySource.get("hibernate.cache.queries") == false
        propertySource.get("dataSource.pooled") == true
        propertySource.get("dataSource.password") == 'test'
        propertySource.get("dataSource.jmxExport") == true
        propertySource.get("dataSource.something") == [1,2]


    }
}
