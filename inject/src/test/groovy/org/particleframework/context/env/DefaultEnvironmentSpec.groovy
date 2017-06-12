package org.particleframework.context.env

import spock.lang.Specification

/**
 * Created by graemerocher on 12/06/2017.
 */
class DefaultEnvironmentSpec extends Specification {

    void "test environment system property resolve"() {

        given:
        System.setProperty("foo.bar", "10")
        Environment env = new DefaultEnvironment("test").start()
        expect:
        env.getProperty("foo.bar", Integer).get() == 10
        env.getRequiredProperty("foo.bar", Integer) == 10
        env.getProperty("foo.bar", Integer, 20) == 10
        env.getProperty("user", String).isPresent()

        cleanup:
        System.setProperty("foo.bar", "")
    }

    void "test environment sub property resolve"() {

        given:
        System.setProperty("foo.bar", "10")
        System.setProperty("bar.foo", "30")
        System.setProperty("foo.baz", "20")
        Environment env = new DefaultEnvironment("test").start()

        expect:
        env.getProperty("foo", Map.class).get() == [bar:"10", baz:"20"]

        cleanup:
        System.setProperty("foo.bar", "")
        System.setProperty("bar.foo", "")
        System.setProperty("foo.baz", "")
    }

    void "test environment system property refresh"() {

        when:
        System.setProperty("foo.bar", "10")
        Environment env = new DefaultEnvironment("test").start()

        then:
        env.getProperty("foo.bar", Integer).get() == 10
        env.getRequiredProperty("foo.bar", Integer) == 10
        env.getProperty("foo.bar", Integer, 20) == 10

        when:
        System.setProperty("foo.bar", "30")
        env= env.refresh()

        then:
        env.getProperty("foo.bar", Integer).get() == 30
        env.getRequiredProperty("foo.bar", Integer) == 30
        env.getProperty("foo.bar", Integer, 20) == 30
    }
}
