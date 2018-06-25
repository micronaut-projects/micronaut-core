package io.micronaut.inject.value.random

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.io.socket.SocketUtils
import spock.lang.Specification

class RandomPropertySpec extends Specification {

    void "test random port"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'my.port':'${random.port}'
        )

        expect:
        context.getProperty('my.port', Integer).isPresent()
        context.getProperty('my.port', Integer).get() == context.getProperty('my.port', Integer).get()

        context.getProperty('my.port', Integer).get() < SocketUtils.MAX_PORT_RANGE
        context.getProperty('my.port', Integer).get() > SocketUtils.MIN_PORT_RANGE

    }

    void "test random localhost port"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'my.address':'localhost:${random.port}',
                'my.addresses':'localhost:${random.port},localhost:${random.port}'
        )

        expect:
        context.getProperty('my.address', String).isPresent()
        context.getProperty('my.address', String).get() ==~ /localhost:\d+/
        context.getProperty('my.addresses', String).isPresent()
        context.getProperty('my.addresses', String).get() ==~ /localhost:\d+,localhost:\d+/

    }

    void "test random integer"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'my.number':'${random.int}'
        )

        expect:
        context.getProperty('my.number', Integer).isPresent()
        context.getProperty('my.number', Integer).get() == context.getProperty('my.number', Integer).get()

    }

    void "test random invalid"() {
        when:
        ApplicationContext context = ApplicationContext.run(
                'my.number':'${random.blah}'
        )

        then:
        def e = thrown(ConfigurationException)
        e.message == 'Invalid random expression ${random.blah} for property: my.number'

    }

}
