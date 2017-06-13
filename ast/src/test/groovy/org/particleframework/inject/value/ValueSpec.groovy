package org.particleframework.inject.value

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.annotation.Value
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 13/06/2017.
 */
class ValueSpec extends Specification {

    void "test configuration injection with @Value"() {
        given:
        System.setProperty("foo.bar", "8080")
        ApplicationContext context = new DefaultApplicationContext("test").start()
        A a = context.getBean(A)
        B b = context.getBean(B)

        expect:
        a.port == 8080
        a.fieldPort == 8080
        a.anotherPort == 8080
        a.defaultPort == 9090
        b.fromConstructor == 8080
        b.a != null

        cleanup:
        System.setProperty("foo.bar", "")
    }


    @Singleton
    static class A {
        int fromConstructor
        A(@Value('foo.bar') int port) {
            this.fromConstructor = port
        }

        @Value('foo.bar')
        int port

        private int anotherPort

        @Value('foo.bar') protected int fieldPort

        @Value('default.port:9090') protected int defaultPort

        @Inject void setAnotherPort(@Value('foo.bar') int port) {
            anotherPort = port
        }

        int getAnotherPort() {
            return anotherPort
        }

        int getFieldPort() {
            return fieldPort
        }

        int getDefaultPort() {
            return defaultPort
        }
    }

    @Singleton
    static class B {
        int fromConstructor
        A a
        B(A a, @Value('foo.bar')int port) {
            this.fromConstructor = port
            this.a = a
        }
    }
}
