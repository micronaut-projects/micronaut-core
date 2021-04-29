package io.micronaut.validation.validator.pojo

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification


class PojoConfigurationPropertiesSpec extends Specification {

    ApplicationContext context = ApplicationContext.run([
            'test.valid.pojos': [
                    [name: '']
            ]
    ])

    void "test @Valid on config props property"() {
        when:
        context.getBean(PojoConfigProps)

        then:
        def ex = thrown(BeanInstantiationException)
        ex.message == "pojos[0]<E Pojo>.name - must not be blank"
    }
}
