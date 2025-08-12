package io.micronaut.http.server.netty.nativetransport

import io.micronaut.http.HttpRequest
import io.micronaut.http.netty.channel.EventLoopGroupFactory
import io.micronaut.http.netty.channel.NettyChannelType
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.netty.channel.epoll.EpollServerSocketChannel
import spock.lang.Requires
import spock.util.environment.OperatingSystem

@Requires({ os.family == OperatingSystem.Family.LINUX })
class NewLinuxNativeTransportSpec extends AbstractMicronautSpec {

    void "test a basic request works with epoll"() {
        when:
        String body = httpClient.toBlocking().retrieve(HttpRequest.GET("/native-transport"))

        then:
        noExceptionThrown()
        body == "works"

        expect:
        applicationContext.getBean(EventLoopGroupFactory).channelClass(NettyChannelType.SERVER_SOCKET) == EpollServerSocketChannel.class
    }

    @Override
    Map<String, Object> getConfiguration() {
        super.getConfiguration() << [
                'micronaut.server.netty.transport': 'epoll',
                'spec': 'TransportSpec']
    }
}
