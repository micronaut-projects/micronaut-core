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
package io.micronaut.websocket.context

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.BeanDefinition
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import spock.lang.Specification

/**
 * The methods of a non-singleton WebSocket bean are read once and kept on its definition.
 */
class WebSocketBeanRegistryKeptMethodsSpec extends Specification {

    void 'the methods of a prototype WebSocket bean are read once for all its connections'() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'WebSocketBeanRegistryKeptMethodsSpec'])
        WebSocketBeanRegistry registry = WebSocketBeanRegistry.forServer(context)
        BeanDefinition<KeptMethodsSocket> definition = context.getBeanDefinition(KeptMethodsSocket)

        when:
        WebSocketBean<KeptMethodsSocket> first = registry.getWebSocket(KeptMethodsSocket)
        WebSocketMethods kept = definition.getCachedUnevaluatedValue(WebSocketMethods, d -> null)
        WebSocketBean<KeptMethodsSocket> second = registry.getWebSocket(KeptMethodsSocket)

        then:
        !first.getTarget().is(second.getTarget())
        kept != null
        definition.getCachedUnevaluatedValue(WebSocketMethods, d -> null).is(kept)
        first.messageMethod().isPresent()
        second.messageMethod().isPresent()

        cleanup:
        context.close()
    }

    @Prototype
    @ServerWebSocket('/kept-methods')
    @Requires(property = 'spec.name', value = 'WebSocketBeanRegistryKeptMethodsSpec')
    static class KeptMethodsSocket {
        @OnMessage
        String onMessage(String message) {
            return message
        }
    }
}
