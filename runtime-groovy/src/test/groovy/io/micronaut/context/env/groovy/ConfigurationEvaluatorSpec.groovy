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
