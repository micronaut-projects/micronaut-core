package io.micronaut.inject.configuration

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class StringArraySpec extends Specification {

    void "test a list property can bind to a string[]"() {
        ApplicationContext ctx = ApplicationContext.run([
                "value.list": ["one", "two"]
        ])

        when:
        String[] values = ctx.getBeanDefinition(StringArrayType).getValue(StringArray, String[].class).get()

        then:
        values.length == 2
        values[0] == "one"
        values[1] == "two"
    }
}
