/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.context.env.groovy

import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.core.io.ResourceLoader
import spock.lang.Specification

/**
 * Created by graemerocher on 15/06/2017.
 */
class ConfigurationEvaluatorSpec extends Specification {

    void "test groovy property source loader"() {
        given:
        GroovyPropertySourceLoader loader = new GroovyPropertySourceLoader() {
            @Override
            protected Optional<InputStream> readInput(ResourceLoader resourceLoader, String fileName) {
                return Optional.of(new ByteArrayInputStream('''
hibernate {
    cache {
        queries = false
    }
}
dataSource.pooled = true
dataSource.password = ''
dataSource.something = [1,2]
'''.bytes))
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

    void "test evaluate config simple nested assignment"() {
        when:
        def result = new ConfigurationEvaluator().evaluate('''
foo.bar = 10
foo.bar.baz=20
foo="test"
''')

        then:
        result.get("foo") == "test"
        result.get('foo.bar') == 10
        result.get('foo.bar.baz') == 20
    }

    void "test evaluate config nested closures"() {
        when:
        def result = new ConfigurationEvaluator().evaluate('''
foo {
    bar = 10
    bar.baz=20
    bar {
        stuff = 30
        more.stuff = 40
    }
}
foo = "test"
''')

        then:
        result.get("foo") == "test"
        result.get('foo.bar') == 10
        result.get('foo.bar.baz') == 20
        result.get('foo.bar.stuff') == 30
        result.get('foo.bar.more.stuff') == 40
    }
}
