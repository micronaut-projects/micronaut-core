/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.configuration.rabbitmq

import com.rabbitmq.client.ConnectionFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import spock.lang.Specification

class RabbitConfigurationSpec extends Specification {

    void "default rabbit configuration"() {
        given:
            ApplicationContext applicationContext = new DefaultApplicationContext("test")
            applicationContext.start()

        expect: "connection factory bean is available"
            applicationContext.containsBean(ConnectionFactory)

        when: "when the connection factory is returned"
            ConnectionFactory cf = applicationContext.getBean(ConnectionFactory)

        then: "default configuration is available"
            cf.getUsername() == "guest"
            cf.getPassword() == "guest"
            cf.getVirtualHost() == "/"
            cf.getHost() == "localhost"
            cf.getPort() == 5672
            cf.getRequestedChannelMax() == 0
            cf.getRequestedFrameMax() == 0
            cf.getRequestedHeartbeat() == 60
            cf.getConnectionTimeout() == 60000
            cf.getHandshakeTimeout() == 10000
            cf.getShutdownTimeout() == 10000

        cleanup:
            applicationContext.close()
    }

    void "default rabbit configuration is overridden when configuration properties are passed in"() {
        given:
            ApplicationContext applicationContext = ApplicationContext.run(
                    ["rabbitmq.username": "guest1",
                     "rabbitmq.password": "guest1",
                     "rabbitmq.virtualHost": "/guest1",
                     "rabbitmq.host": "guesthost",
                     "rabbitmq.port": 9999,
                     "rabbitmq.requestedChannelMax": 50,
                     "rabbitmq.requestedFrameMax": 50,
                     "rabbitmq.requestedHeartbeat": 50,
                     "rabbitmq.connectionTimeout": 50,
                     "rabbitmq.handshakeTimeout": 50,
                     "rabbitmq.shutdownTimeout": 50],
                    "test"
            )

        expect: "connection factory bean is available"
            applicationContext.containsBean(ConnectionFactory)
            applicationContext.containsBean(RabbitConnectionFactoryConfig)

        when: "when the connection factory is returned and values are overridden"
            ConnectionFactory cf = applicationContext.getBean(RabbitConnectionFactoryConfig)

        then: "default configuration is available"
            cf.getUsername() == "guest1"
            cf.getPassword() == "guest1"
            cf.getVirtualHost() == "/guest1"
            cf.getHost() == "guesthost"
            cf.getPort() == 9999
            cf.getRequestedChannelMax() == 50
            cf.getRequestedFrameMax() == 50
            cf.getRequestedHeartbeat() == 50
            cf.getConnectionTimeout() == 50
            cf.getHandshakeTimeout() == 50
            cf.getShutdownTimeout() == 50

        cleanup:
            applicationContext.close()
    }
}
