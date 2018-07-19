package io.micronaut.inject.env

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EnvironmentConfigurableSpec extends Specification {

    void "test that running two apps with different environments results in distinct annotation metadata"() {
        when:"A first application is run"
        ApplicationContext applicationContext = ApplicationContext.run('myBean.value': 'foo')

        then:'the value is correct'
        applicationContext.getBean(MyBean).value == 'foo'

        when:"Another instance is run"
        ApplicationContext applicationContext2 = ApplicationContext.run('myBean.value': 'bar')

        then:'the value is correct'
        applicationContext2.getBean(MyBean).value == 'bar'
        applicationContext2.getBean(MyBean).value != applicationContext.getBean(MyBean).value
    }

}
