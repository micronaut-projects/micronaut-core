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

package io.micronaut.configuration.lettuce

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import redis.embedded.RedisServer
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class RedisClientFactorySpec extends Specification{

    void "test redis server config by port"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        RedisServer redisServer = new RedisServer(port)
        redisServer.start()

        when:
        ApplicationContext applicationContext = ApplicationContext.run('redis.port':port)
        StatefulRedisConnection connection = applicationContext.getBean(StatefulRedisConnection)

        then:
        // tag::commands[]
        RedisCommands<String, String> commands = connection.sync()
        commands.set("foo", "bar")
        commands.get("foo") == "bar"
        // end::commands[]

        cleanup:redisServer.stop()
    }

    void "test redis server config by URI"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        RedisServer redisServer = new RedisServer(port)
        redisServer.start()

        when:
        ApplicationContext applicationContext = ApplicationContext.run('redis.uri':"redis://localhost:$port")
        StatefulRedisConnection client = applicationContext.getBean(StatefulRedisConnection)
        def command = client.sync()
        then:
        command.set("foo", "bar")
        command.get("foo") == "bar"

        cleanup:redisServer.stop()
    }
}
