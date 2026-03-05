package io.micronaut.inject.configproperties.condition

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.configproperties.condition.data.ConditionalBean
import io.micronaut.inject.configproperties.condition.data.DemoConfig
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ConditionFindBeanWithInterfaceConfigSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run([
            'spec.name': getClass().simpleName,
            'demo.name': 'Acme',
            'demo.count': 42
    ])

    void "test configuration interface bean is bound and condition finds it"() {
        expect:
        context.containsBean(DemoConfig)
        context.getBean(DemoConfig).name == 'Acme'
        context.getBean(DemoConfig).count == 42

        and: 'bean with @Requires(condition=...) is enabled because condition uses findBean'
        context.containsBean(ConditionalBean)
        context.getBean(ConditionalBean)
    }
}
