package io.micronaut.inject.scope.custom

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.exceptions.BeanCreationException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CustomScopeSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run();

    void "test custom scope with no scoped proxy"() {
        when:
        def scope = context.getBean(SpecialBeanScope)

        then:
        scope.beans.isEmpty()

        when:
        def test = context.getBean(TestService)

        then:
        !test.destroyed
        scope.beans.size() == 1

        when:
        scope.remove(scope.beans.keySet().first())

        then:
        test.destroyed
        scope.beans.size() == 0
    }

    void "test custom scope with bean creation exception"() {
        when:
        context.getBean(FaultyBean)

        then:
        def e = thrown(BeanCreationException)
    }

    void "test custom scope with bean registration resolution"() {
        when:
        def registration = context.findBeanRegistration(new NonFaultyBean())

        then:
        !registration.isPresent()

        when:
        def bean = context.getBean(NonFaultyBean)

        then:
        context.findBeanRegistration(bean).isPresent()
    }
}
