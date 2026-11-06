package io.micronaut.context

import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

/**
 * The prefix a configuration bean reads under, which tells whether a change of configuration concerns a retained bean.
 */
class ConfigurationPrefixSpec extends Specification {

    void "the prefix of #description is #expected"() {
        given:
        BeanDefinition<?> definition = Stub(BeanDefinition) {
            hasStereotype(ConfigurationReader) >> reader
            stringValue(ConfigurationReader, ConfigurationReader.PREFIX) >> Optional.ofNullable(prefix)
        }

        expect:
        DefaultBeanContext.configurationPrefixOf(definition) == expected

        where:
        description                       | reader | prefix          | expected
        'a bean that reads no config'     | false  | 'app.pool'      | null
        'a reader without a prefix'       | true   | null            | null
        'a plain prefix'                  | true   | 'app.pool'      | 'app.pool'
        'an @EachProperty map'            | true   | 'app.pools.*'   | 'app.pools'
        'an @EachProperty list'           | true   | 'app.pools[*]'  | 'app.pools'
        'a wildcard alone'                | true   | '*'             | null
        'a wildcard in the middle'        | true   | 'app.*.pool'    | 'app'
    }
}
