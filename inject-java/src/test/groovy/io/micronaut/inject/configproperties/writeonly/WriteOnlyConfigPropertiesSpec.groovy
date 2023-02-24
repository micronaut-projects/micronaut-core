package io.micronaut.inject.configproperties.writeonly

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

class WriteOnlyConfigPropertiesSpec extends Specification {

    void "test write-only config properties - valid"() {
        given:
        def context = ApplicationContext.run('test.name':'test')
        def bean = context.getBean(WriteOnlyConfigProperties)

        expect:
        bean.name() == 'test'

        cleanup:
        context.close()
    }

    void "test write-only config properties - invalid"() {
        given:
        def context = ApplicationContext.run('test.name':'  ')

        when:
        def bean = context.getBean(WriteOnlyConfigProperties)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains("Validation failed for bean definition")

        cleanup:
        context.close()
    }
}
