/*
 * Copyright 2017 original authors
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
package org.particleframework.configuration.lettuce.cache

import org.particleframework.cache.SyncCache
import org.particleframework.context.ApplicationContext
import org.particleframework.core.io.socket.SocketUtils
import org.particleframework.inject.qualifiers.Qualifiers
import redis.embedded.RedisServer
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SyncCacheSpec extends Specification{

    void "test cacheable annotations"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        RedisServer redisServer = new RedisServer(port)
        redisServer.start()
        ApplicationContext applicationContext = ApplicationContext.run(
                'particle.redis.port':port,
                'particle.redis.caches.counter.server':'default',
                'particle.redis.caches.counter2.server':'default'
        )

        when:
        CounterService counterService = applicationContext.getBean(CounterService)

        then:
        counterService.flowableValue("test").toIterable().iterator().next() == 0
        counterService.singleValue("test").toFuture().get() == 0

        when:
        counterService.reset()
        def result =counterService.increment("test")

        then:
        result == 1
        counterService.flowableValue("test").toIterable().iterator().next() == 1
        counterService.futureValue("test").get() == 1
        counterService.singleValue("test").toFuture().get() == 1
        counterService.getValue("test") == 1
        counterService.getValue("test") == 1

        when:
        result = counterService.incrementNoCache("test")

        then:
        result == 2
        counterService.flowableValue("test").toIterable().iterator().next() == 1
        counterService.futureValue("test").get() == 1
        counterService.getValue("test") == 1

        when:
        counterService.reset("test")
        then:
        counterService.getValue("test") == 0

        when:
        counterService.reset("test")
        then:
        counterService.futureValue("test").get() == 0



        when:
        counterService.set("test", 3)

        then:
        counterService.getValue("test") == 3
        counterService.futureValue("test").get() == 3

        when:
        result = counterService.increment("test")

        then:
        result == 4
        counterService.getValue("test") == 4
        counterService.futureValue("test").get() == 4

        when:
        result = counterService.futureIncrement("test").get()

        then:
        result == 5
        counterService.getValue("test") == 5
        counterService.futureValue("test").get() == 5

        when:
        counterService.reset()

        then:
        !counterService.getOptionalValue("test").isPresent()
        counterService.getValue("test") == 0
        counterService.getOptionalValue("test").isPresent()
        counterService.getValue2("test") == 0

        when:
        counterService.increment("test")
        counterService.increment("test")

        then:
        counterService.getValue("test") == 2
        counterService.getValue2("test") == 0

        when:
        counterService.increment2("test")

        then:
        counterService.getValue("test") == 1
        counterService.getValue2("test") == 1


        cleanup:
        redisServer.stop()
    }


}
