/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.value.random

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.io.socket.SocketUtils
import spock.lang.Specification

class RandomPropertySpec extends Specification {

    void "test random int"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'my.int':'${random.int}'
        )

        expect:
        context.getProperty('my.int', Integer).isPresent()
        // Validate that the random int is resolved to the same value all the time
        context.getProperty('my.int', Integer).get() == context.getProperty('my.int', Integer).get()
        context.getProperty('my.int', Integer).get() == context.getProperty('my.int', Integer).get()
        context.getProperty('my.int', Integer).get() == context.getProperty('my.int', Integer).get()
        context.getProperty('my.int', Integer).get() == context.getProperty('my.int', Integer).get()

        cleanup:
        context.close()
    }

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

        cleanup:
        context.close()
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
        context.getProperty('my.address', String).get() == context.getProperty('my.address', String).get()
        context.getProperty('my.addresses', String).get() == context.getProperty('my.addresses', String).get()

        cleanup:
        context.close()
    }

    void "test random integer"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'my.number':'${random.int}'
        )

        expect:
        context.getProperty('my.number', Integer).isPresent()
        context.getProperty('my.number', Integer).get() == context.getProperty('my.number', Integer).get()

        cleanup:
        context.close()
    }

    void "test random invalid"() {
        when:
        ApplicationContext context = ApplicationContext.run(
                'my.number':'${random.blah}'
        )
        context.getProperty('my.number', Integer).isPresent()

        then:
        def e = thrown(ConfigurationException)
        e.message == 'Invalid random expression: random.blah'

        cleanup:
        context.close()
    }

}
