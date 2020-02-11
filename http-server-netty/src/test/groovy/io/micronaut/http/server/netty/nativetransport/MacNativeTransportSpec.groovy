package io.micronaut.http.server.netty.nativetransport

import io.micronaut.http.HttpRequest
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.netty.channel.EventLoopGroupFactory
import io.netty.channel.kqueue.KQueueServerSocketChannel
import spock.lang.Requires
import spock.util.environment.OperatingSystem

@Requires({ os.family == OperatingSystem.Family.MAC_OS })
class MacNativeTransportSpec extends AbstractMicronautSpec {

    void "test a basic request works with mac native transport"() {
        when:
        String body = rxClient.retrieve(HttpRequest.GET("/native-transport")).blockingFirst()

        then:
        noExceptionThrown()
        body == "works"

        def eventLoopGroupFactory = applicationContext.getBean(EventLoopGroupFactory)

        expect:
        eventLoopGroupFactory.serverSocketChannelClass() == KQueueServerSocketChannel.class
    }

    @Override
    Map<String, Object> getConfiguration() {
        super.getConfiguration() << ['micronaut.server.netty.use-native-transport': true]
    }
}
