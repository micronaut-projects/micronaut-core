/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context

import io.micronaut.context.event.ApplicationEventListener
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class PublishEventPublisherCacheSpec extends Specification {

    @AutoCleanup
    DefaultBeanContext context = new DefaultBeanContext().start()

    void "every event published, sync or async, reaches the listeners of its own type"() {
        given:
        PingListener pings = new PingListener()
        PongListener pongs = new PongListener()
        context.registerSingleton(pings)
        context.registerSingleton(pongs)

        when:
        3.times {
            context.publishEvent(new Ping())
            context.publishEvent(new Pong())
        }
        context.publishEventAsync(new Ping()).get(10, TimeUnit.SECONDS)

        then:
        pings.received == 4
        pongs.received == 3
    }

    void "a publisher looked up again after the stop of the context reaches the listeners of the restarted one"() {
        given:
        PingListener first = new PingListener()
        context.registerSingleton(first)
        context.publishEvent(new Ping())

        when:
        context.stop()
        context.start()
        PingListener second = new PingListener()
        context.registerSingleton(second)
        context.publishEvent(new Ping())

        then:
        first.received == 1
        second.received == 1
    }

    static class Ping {
    }

    static class Pong {
    }

    static class PingListener implements ApplicationEventListener<Ping> {
        int received

        @Override
        void onApplicationEvent(Ping event) {
            received++
        }
    }

    static class PongListener implements ApplicationEventListener<Pong> {
        int received

        @Override
        void onApplicationEvent(Pong event) {
            received++
        }
    }
}
