package io.micronaut.validation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.Pattern

class WebSocketClientValidationSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5332')
    def 'test validation of request bean'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder('spec.name':'WebSocketClientValidationSpec').run(EmbeddedServer)
        def client = embeddedServer.applicationContext.createBean(WebSocketClient, new URI(embeddedServer.URI.toString()))
        def holderBean = embeddedServer.applicationContext.getBean(HolderBean)
        def conditions = new PollingConditions(timeout: 5)

        expect:
        holderBean.seenData == null
        holderBean.seenError == null

        when:
        Flux.from(client.connect(ClientHandler, '/validated?foo=bar')).blockFirst().close()
        then:
        conditions.eventually {
            assert holderBean.seenData != null
            assert holderBean.seenData.foo == 'bar'
            assert holderBean.seenError == null
        }

        when:
        Flux.from(client.connect(ClientHandler, '/validated?foo=baz')).blockFirst().close()
        then:
        conditions.eventually {
            assert holderBean.seenData != null
            assert holderBean.seenData.foo == 'bar' // unchanged
            assert holderBean.seenError != null
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'WebSocketClientValidationSpec')
    static class HolderBean {
        ValidatedData seenData = null
        ConstraintViolationException seenError = null
    }

    @ClientWebSocket('/validated')
    @Requires(property = 'spec.name', value = 'WebSocketClientValidationSpec')
    static abstract class ClientHandler implements Closeable {
        @OnMessage
        void onMessage(byte[] message) {
        }
    }

    @Introspected
    static class ValidatedData {
        private String foo

        void setFoo(String foo) {
            this.foo = foo
        }

        @Pattern(regexp = 'bar')
        String getFoo() {
            return foo
        }
    }
}
