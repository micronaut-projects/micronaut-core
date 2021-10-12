package io.micronaut.inject.configproperties.itfce

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class ValidatedInterfaceConfigPropsSpec extends Specification {

    void 'test validated interface config with invalid config'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'my.config.name':'',
                'my.config.foo.name':'',
                'my.config.default.name':'',
                'my.config.foo.nested.bar.name':'',
        )

        when:
        context.getBean(MyConfig)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('MyConfig.getName - must not be blank')

        when:
        context.getBean(MyEachConfig, Qualifiers.byName("foo"))

        then:
        e = thrown(BeanInstantiationException)
        e.message.contains('MyEachConfig.getName - must not be blank')

        when:
        context.getBean(MyEachConfig)

        then:
        e = thrown(BeanInstantiationException)
        e.message.contains('MyEachConfig.getName - must not be blank')

        cleanup:
        context.close()
    }
}
