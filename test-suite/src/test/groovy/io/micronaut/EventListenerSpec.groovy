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
package io.micronaut

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEvent
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.order.OrderUtil
import io.micronaut.discovery.event.ServiceReadyEvent
import io.micronaut.discovery.event.ServiceStoppedEvent
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.runtime.server.event.ServerShutdownEvent
import io.micronaut.runtime.server.event.ServerStartupEvent
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class EventListenerSpec extends Specification {

    void "test all events listener is invoked"() {
        given:
        PollingConditions conditions = new PollingConditions(timeout: 10)
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                ['spec.name': getClass().getSimpleName(),
                'micronaut.application.name': 'events-test']
        )

        when:
        TestAllEventsListener t = embeddedServer.getApplicationContext().getBean(TestAllEventsListener)
        def serverStartupListeners = embeddedServer.getApplicationContext().getBeansOfType(ApplicationEventListener.class, Qualifiers.byTypeArguments(ServerStartupEvent))
                    .stream()
                    .sorted(OrderUtil.COMPARATOR_ZERO)
                    .toArray(ApplicationEventListener[]::new)
        def serverShutdownListeners = embeddedServer.getApplicationContext().getBeansOfType(ApplicationEventListener.class, Qualifiers.byTypeArguments(ServerShutdownEvent))
                    .stream()
                    .sorted(OrderUtil.COMPARATOR_ZERO)
                    .toArray(ApplicationEventListener[]::new)
        then:
        serverStartupListeners.last().getClass().name.contains "NettyServiceDiscovery"
            serverShutdownListeners.last().getClass().name.contains "NettyServiceDiscovery"

        conditions.eventually {
            t.events.size() == 3
        }

        embeddedServer.close()

        conditions.eventually {
            t.events.size() == 6
        }

        t.events[0] == StartupEvent
        t.events[1] == ServerStartupEvent
        t.events[2] == ServiceReadyEvent
        t.events[3] == ServerShutdownEvent
        t.events[4] == ServiceStoppedEvent
        t.events[5] == ShutdownEvent

        cleanup:
        embeddedServer.close()
    }

    @Requires(property = "spec.name", value = "EventListenerSpec")
    @Singleton
    static class TestAllEventsListener {

        List<Class> events = new ArrayList<>()

        @EventListener
        void onStartup(ApplicationEvent event) {
            events.add(event.getClass())
        }

    }
}
