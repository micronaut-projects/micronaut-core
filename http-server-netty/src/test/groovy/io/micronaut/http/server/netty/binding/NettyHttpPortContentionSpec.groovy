package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Ignore
import spock.lang.Specification

@Ignore
class NettyHttpPortContentionSpec extends Specification{
    void "test server url is correct when having to rebind on port contention"() {
        given: "we have some ports to use"
        def randomPort = SocketUtils.findAvailableTcpPort()
        def randomPort2 = SocketUtils.findAvailableTcpPort()

        and: "we mock out SocketUtils to make port contention happen"
        PowerMockito.mockStatic(SocketUtils.class)
        Mockito.when(SocketUtils.findAvailableTcpPort())
            .thenReturn(randomPort, randomPort, randomPort, randomPort2)

        when: "we spin up 2 servers"
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        EmbeddedServer embeddedServer2 = ApplicationContext.run(EmbeddedServer)

        then:
        embeddedServer.getURL().toString().contains(randomPort as String)
        embeddedServer.getPort() == randomPort

        and:
        embeddedServer2.getURL().toString().contains(randomPort2 as String)
        embeddedServer2.getPort() == randomPort2

        cleanup:
        embeddedServer.applicationContext.stop()
        embeddedServer2.applicationContext.stop()
    }

    void "test server url is correct when having to rebind on port contention when using dual port"() {
        given: "we have some ports to use"
        def randomPortInsecure = SocketUtils.findAvailableTcpPort()
        def randomPortSecure = SocketUtils.findAvailableTcpPort()
        def randomPortInsecure2 = SocketUtils.findAvailableTcpPort()
        def randomPortSecure2 = SocketUtils.findAvailableTcpPort()

        and: "we mock out SocketUtils to make port contention happen"
        PowerMockito.mockStatic(SocketUtils.class)
        Mockito.when(SocketUtils.findAvailableTcpPort())
            .thenReturn(randomPortSecure, randomPortInsecure, randomPortSecure, randomPortSecure, randomPortSecure2, randomPortInsecure, randomPortInsecure, randomPortInsecure2)

        when: "we spin up 2 servers"
        PropertySource propertySource = PropertySource.of(
                'micronaut.server.port': -1,
                'micronaut.ssl.port': -1,
                'micronaut.ssl.enabled': true,
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.server.dualProtocol':true
        )
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, propertySource)
        EmbeddedServer embeddedServer2 = ApplicationContext.run(EmbeddedServer, propertySource)


        then:
        embeddedServer.getURL().toString().contains(randomPortSecure as String)
        embeddedServer.getPort() == randomPortSecure

        and:
        embeddedServer2.getURL().toString().contains(randomPortSecure2 as String)
        embeddedServer2.getPort() == randomPortSecure2

        cleanup:
        embeddedServer.applicationContext.stop()
        embeddedServer2.applicationContext.stop()
    }
}
