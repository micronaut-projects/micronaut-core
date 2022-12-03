package io.micronaut.inject.foreach.introduction

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EachBeanIntrospectionSpec extends Specification {

    void "test inject correct qualifier into interceptor downstream of EachBean"() {
        given:
        def context = ApplicationContext.run(
                'datasources.one.test': 1,
                'datasources.two.test': 2
        )

        def bean = context.getBean(MyBean)

        expect:
        bean.sessionOne.name() == 'one'
        bean.sessionTwo.name() == 'two'

        cleanup:
        context.close()
    }
}
