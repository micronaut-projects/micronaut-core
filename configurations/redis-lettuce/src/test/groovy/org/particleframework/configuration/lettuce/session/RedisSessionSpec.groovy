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
package org.particleframework.configuration.lettuce.session

import org.particleframework.context.ApplicationContext
import org.particleframework.context.event.ApplicationEventListener
import org.particleframework.core.io.socket.SocketUtils
import org.particleframework.session.event.AbstractSessionEvent
import org.particleframework.session.event.SessionCreatedEvent
import org.particleframework.session.event.SessionDeletedEvent
import redis.embedded.RedisServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class RedisSessionSpec extends Specification {

    RedisServer redisServer
    int redisPort
    def setup() {
        redisPort = SocketUtils.findAvailableTcpPort()
        def builder = RedisServer.builder()
        // enable keyspace events
        builder.port(redisPort)
               .setting("notify-keyspace-events Egx")
        redisServer = builder.build()
        redisServer.start()
    }

    def cleanup() {
        redisServer?.stop()
    }

    void "test redis session create"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'particle.redis.port':redisPort,
                'particle.redis.session.enabled':'true'
        )
        RedisSessionStore sessionStore = applicationContext.getBean(RedisSessionStore)
        TestListener listener = applicationContext.getBean(TestListener)

        when:"A new session is created and saved"
        RedisSession session = sessionStore.newSession()
        session.put("username", "fred")
        session.put("foo", new Foo(name: "Fred", age: 10))

        RedisSession saved = sessionStore.save(session).get()

        then:"The session created event is fired and the session is valid"
        listener.events.size() == 1
        listener.events.first() instanceof SessionCreatedEvent
        saved != null
        !saved.isExpired()
        saved.maxInactiveInterval
        saved.creationTime
        saved.id
        saved.get("username").get() == "fred"
        saved.get("foo", Foo).get().name == "Fred"
        saved.get("foo", Foo).get().age == 10

        when:"A session is located"
        listener.events.clear()
        RedisSession retrieved = sessionStore.findSession(saved.id).get().get()

        then:"Then the session is valid"
        retrieved != null
        !retrieved.isExpired()
        retrieved.maxInactiveInterval
        retrieved.creationTime
        retrieved.id
        retrieved.get("username", String).get() == "fred"
        retrieved.get("foo", Foo).get().name == "Fred"
        retrieved.get("foo", Foo).get().age == 10

        when:"A session is modified"
        retrieved.remove("username")
        retrieved.put("more", "stuff")
        def now = Instant.now()
        retrieved.setLastAccessedTime(now)
        retrieved.setMaxInactiveInterval(Duration.of(10, ChronoUnit.MINUTES))
        sessionStore.save(retrieved).get()

        retrieved = sessionStore.findSession(retrieved.id).get().get()

        then:"Then the session is valid"
        retrieved != null
        !retrieved.isExpired()
        retrieved.maxInactiveInterval == Duration.of(10, ChronoUnit.MINUTES)
        retrieved.creationTime == saved.creationTime
        retrieved.lastAccessedTime == now
        retrieved.id
        !retrieved.contains("username")
        retrieved.get("more", String).get() == "stuff"
        retrieved.get("foo", Foo).get().name == "Fred"
        retrieved.get("foo", Foo).get().age == 10


        when:"A session is deleted"
        boolean result = sessionStore.deleteSession(saved.id).get()
        def conditions = new PollingConditions(timeout: 10)

        then:"A session deleted event is fired"

        result
        conditions.eventually {
            listener.events.size() == 1
            listener.events.first() instanceof SessionDeletedEvent
        }


        when:"The deleted session is looked up"
        def found = sessionStore.findSession(saved.id).get()

        then:"It is no longer present"
        !found.isPresent()
    }

    static class Foo implements Serializable{
        String name
        Integer age
    }

    @Singleton
    static class TestListener implements ApplicationEventListener<AbstractSessionEvent> {
        List<AbstractSessionEvent> events = []
        @Override
        void onApplicationEvent(AbstractSessionEvent event) {
            events.add(event)
        }
    }
}
