package io.micronaut.validation.validator.pojo

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

import javax.validation.ConstraintViolationException

class PojoConfigurationPropertiesSpec extends Specification {

    void "test @Valid on config props property"() {
        ApplicationContext context = ApplicationContext.run([
            'test.valid.pojos': [
                    [name: '']
            ]
        ])

        when:
        context.getBean(PojoConfigProps)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.message.contains("pojos[0].name - must not be blank")
    }
}
