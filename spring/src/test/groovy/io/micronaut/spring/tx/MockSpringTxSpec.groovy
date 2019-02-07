package io.micronaut.spring.tx

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

// simple tests for tx management. More robust tests exist in github.com/micronaut-projects/micronaut-sql
class MockSpringTxSpec extends Specification {

    void "test spring tx management"() {
        given:
        TransactionalBean transactionalBean = ApplicationContext.build().singletons(new MockTransactionManager())
                          .start().getBean(TransactionalBean)

        expect:
        transactionalBean.doSomething() == 'foo'
    }
}
