package io.micronaut.inject.field

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.field.inheritance.Listener
import spock.lang.Specification

class FieldInheritanceInjectionSpec extends Specification {

    void "test injecting into super abstract class"() {
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.getBean(Listener).someBean != null

        cleanup:
        ctx.close()
    }
}
