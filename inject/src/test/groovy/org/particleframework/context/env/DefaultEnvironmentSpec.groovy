package org.particleframework.context.env

import spock.lang.Specification

/**
 * Created by graemerocher on 12/06/2017.
 */
class DefaultEnvironmentSpec extends Specification {

    void "test environment system property resolve"() {

        given:
        System.setProperty("test.foo.bar", "10")
        Environment env = new DefaultEnvironment("test").start()
        expect:
        env.getProperty("test.foo.bar", Integer).get() == 10
        env.getRequiredProperty("test.foo.bar", Integer) == 10
        env.getProperty("test.foo.bar", Integer, 20) == 10
        env.getProperty("user", String).isPresent()

        cleanup:
        System.setProperty("test.foo.bar", "")
    }

    void "test environment sub property resolve"() {

        given:
        System.setProperty("test.foo.bar", "10")
        System.setProperty("test.bar.foo", "30")
        System.setProperty("test.foo.baz", "20")
        Environment env = new DefaultEnvironment("test").start()

        expect:
        env.getProperty("test.foo", Map.class).get() == [bar:"10", baz:"20"]

        cleanup:
        System.setProperty("test.foo.bar", "")
        System.setProperty("test.bar.foo", "")
        System.setProperty("test.foo.baz", "")
    }

    void "test environment system property refresh"() {

        when:
        System.setProperty("test.foo.bar", "10")
        Environment env = new DefaultEnvironment("test").start()

        then:
        env.getProperty("test.foo.bar", Integer).get() == 10
        env.getRequiredProperty("test.foo.bar", Integer) == 10
        env.getProperty("test.foo.bar", Integer, 20) == 10

        when:
        System.setProperty("test.foo.bar", "30")
        env= env.refresh()

        then:
        env.getProperty("test.foo.bar", Integer).get() == 30
        env.getRequiredProperty("test.foo.bar", Integer) == 30
        env.getProperty("test.foo.bar", Integer, 20) == 30

        cleanup:
        System.setProperty("test.foo.bar", "")
        System.setProperty("test.bar.foo", "")
        System.setProperty("test.foo.baz", "")
    }
}
