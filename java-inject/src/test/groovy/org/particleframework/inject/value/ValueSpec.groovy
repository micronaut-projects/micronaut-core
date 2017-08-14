package org.particleframework.inject.value

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import spock.lang.Specification

class ValueSpec extends Specification {

    void "test configuration injection with @Value"() {
        given:
        System.setProperty("foo.bar", "8080")
        ApplicationContext context = new DefaultApplicationContext("test").start()
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

        cleanup:
        System.setProperty("foo.bar", "")
    }
}
