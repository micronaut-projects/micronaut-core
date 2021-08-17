package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification
import jakarta.inject.Singleton

class AnnotationPlaceholderSpec extends Specification {

    void "test placeholder binding to a string array"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['from.config': ['a', 'b', 'c'], 'more.values': ['d', 'e']])
        BeanDefinition beanDefinition = ctx.getBeanDefinition(Test)

        when:
        String[] values = beanDefinition.stringValues(StringArrayValue)

        then:
        values == ['a', 'b', 'c', 'd', 'e'] as String[]

        when:
        values = beanDefinition.getValue(StringArrayValue, String[].class).get()

        then:
        values == ['a', 'b', 'c', 'd', 'e'] as String[]
    }

    @Singleton
    @StringArrayValue(['${from.config}', '${more.values}'])
    static class Test {

    }
}
