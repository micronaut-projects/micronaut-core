package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.pool.AbstractChannelPoolMap
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.http.client.annotation.Client
import javax.inject.Inject
import java.lang.reflect.Field
import java.util.concurrent.CompletableFuture

class ConnectionTTLSpec extends Specification {


    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()


    void "Should close the connections accoridng to connect-ttl"() {
        setup:
        ApplicationContext clientContext = ApplicationContext.run(
          'my.port':embeddedServer.getPort(),
          'micronaut.http.client.connect-ttl':'100ms',
          'micronaut.http.client.pool.enabled':true
        )
        DefaultHttpClient httpClient = clientContext.createBean(DefaultHttpClient, embeddedServer.getURL())


        when:"make bunch of requests"
        (1..50).collect{httpClient.retrieve(HttpRequest.GET('/connectTTL/'),String).blockingFirst()}


        then:"ensure that all connections are closed"
        getQueuedChannels(httpClient).size() == 0

        cleanup:
        httpClient.close()
        clientContext.close()
    }


    Deque getQueuedChannels(RxHttpClient client) {
        AbstractChannelPoolMap poolMap = client.poolMap
        Field mapField = AbstractChannelPoolMap.getDeclaredField("map")
        mapField.setAccessible(true)
        Map innerMap = mapField.get(poolMap)
        return innerMap.values().first().delegatePool.deque
    }
    @Controller('/connectTTL')
    static class GetController {
        @Inject
        ConnectTTLClient ConnectTTLClient

        @Get(value = "/", produces = MediaType.TEXT_PLAIN)
        String index() {
            Thread.sleep(99)
            return "success"
        }
    }

    @Client('/connectTTL')
    static interface ConnectTTLClient {

        @Get("/get")
        CompletableFuture<String> getFuture()
    }

}
