/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ssl.CertificateProvider
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.exceptions.WebSocketSessionException
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification

import java.security.KeyStore
import java.time.Duration

/**
 * A websocket client must never fall back to plaintext when its SSL context cannot be built.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/12610")
class WebsocketSslContextUnavailableSpec extends Specification {

    void "wss connection fails fast when the SSL context is unavailable"() {
        given: "a client whose certificate provider never emits, so no SSL context is ever built"
        EmbeddedServer embeddedServer = ApplicationContext.builder([
                'spec.name': 'WebsocketSslContextUnavailableSpec',
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.port': -1,
                'micronaut.server.ssl.build-self-signed': true,
                'micronaut.http.client.ssl.enabled': true,
                'micronaut.http.client.ssl.trust-name': 'never-emits',
        ]).run(EmbeddedServer)
        def uri = embeddedServer.getURI()
        uri = new URI('wss', uri.schemeSpecificPart, uri.fragment) // apply wss scheme
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, uri)

        when:
        Flux.from(wsClient.connect(EchoClient, "/ws-ssl-unavailable")).blockFirst(Duration.ofSeconds(30))

        then: "the connection is rejected instead of sending plaintext to the TLS port"
        def e = thrown WebSocketSessionException
        e.message.contains('Cannot send WSS request. SSL context is unavailable')

        cleanup:
        wsClient.close()
        embeddedServer.close()
    }

    @Singleton
    @Named('never-emits')
    @Requires(property = 'spec.name', value = 'WebsocketSslContextUnavailableSpec')
    static class NeverEmittingCertificateProvider implements CertificateProvider {
        @Override
        String getName() {
            return 'never-emits'
        }

        @Override
        Publisher<KeyStore> getKeyStore() {
            return Flux.never()
        }
    }

    @ClientWebSocket("/ws-ssl-unavailable")
    @Requires(property = 'spec.name', value = 'WebsocketSslContextUnavailableSpec')
    static class EchoClient implements AutoCloseable {
        @OnMessage
        void onMessage(String message) {
        }

        @Override
        void close() throws Exception {
        }
    }
}
