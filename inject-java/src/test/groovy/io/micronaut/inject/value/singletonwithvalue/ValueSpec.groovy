package io.micronaut.inject.value.singletonwithvalue

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import spock.lang.Specification

class ValueSpec extends Specification {

    void "test configuration injection with @Value"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                "foo.bar":"8080"
        )
        A a = context.getBean(A)
        B b = context.getBean(B)

        expect:
        a.port == 8080
        a.optionalPort.get() == 8080
        !a.optionalPort2.isPresent()
        a.fieldPort == 8080
        a.anotherPort == 8080
        a.defaultPort == 9090
        b.fromConstructor == 8080
        b.a != null

    }
}
