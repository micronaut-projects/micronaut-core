package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@Retry
class IdleTimeoutSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'IdleTimeoutSpec'])

    def "should close connection according to connection-pool-idle-timeout"() {
        setup:
        ApplicationContext clientContext = ApplicationContext.run(
                'my.port': embeddedServer.getPort(),
                'micronaut.http.client.connection-pool-idle-timeout': '1800ms',
                'micronaut.http.client.pool.enabled': true
        )
        HttpClient httpClient = clientContext.createBean(HttpClient, embeddedServer.getURL())

        when: "make first request"
        httpClient.toBlocking().retrieve(HttpRequest.GET('/idleTimeout/'), String)
        Channel ch1 = getQueuedChannels(httpClient).get(0)

        then: "ensure that connection is open as connection-pool-idle-timeout is not reached"
        getQueuedChannels(httpClient).size() == 1
        ch1.isOpen()
        new PollingConditions(timeout: 2).eventually {
            !ch1.isOpen()
        }

        when: "make another request"
        httpClient.toBlocking().retrieve(HttpRequest.GET('/idleTimeout'), String)

        then:
        new PollingConditions().eventually {
            assert getQueuedChannels(httpClient).size() > 0
        }

        when:
        Channel ch2 = getQueuedChannels(httpClient).get(0)

        then: "ensure channel 2 is open and channel 2 != channel 1"
        getQueuedChannels(httpClient).size() == 1
        ch1 != ch2
        ch2.isOpen()
        new PollingConditions(timeout: 2).eventually {
            !ch2.isOpen()
        }

        cleanup:
        httpClient.close()
        clientContext.close()
    }

    def "shouldn't close connection if connection-pool-idle-timeout is not passed"() {
        setup:
        ApplicationContext clientContext = ApplicationContext.run(
                'my.port': embeddedServer.getPort(),
                'micronaut.http.client.pool.enabled': true
        )
        HttpClient httpClient = clientContext.createBean(HttpClient, embeddedServer.getURL())

        when: "make first request"
        httpClient.toBlocking().retrieve(HttpRequest.GET('/idleTimeout/'), String)
        List<Channel> deque = getQueuedChannels(httpClient)
        Channel ch1 = deque.get(0)

        then: "ensure that connection is open as connection-pool-idle-timeout is not reached"
        deque.size() == 1
        new PollingConditions().eventually {
            deque.get(0).isOpen()
        }

        when: "make another request"
        httpClient.toBlocking().retrieve(HttpRequest.GET('/idleTimeout'), String)

        then:
        new PollingConditions().eventually {
            assert deque.size() > 0
        }

        when:
        Channel ch2 = deque.get(0)

        then: "ensure channel is still open"
        ch1 == ch2
        deque.size() == 1
        new PollingConditions().eventually {
            deque.get(0).isOpen()
        }

        cleanup:
        httpClient.close()
        clientContext.close()
    }

    List<Channel> getQueuedChannels(HttpClient client) {
        return client.connectionManager.channels
    }

    @Requires(property = 'spec.name', value = 'IdleTimeoutSpec')
    @Controller('/idleTimeout')
    static class GetController {

        @Get(value = "/", produces = MediaType.TEXT_PLAIN)
        String get() {
            return "success"
        }
    }

}
