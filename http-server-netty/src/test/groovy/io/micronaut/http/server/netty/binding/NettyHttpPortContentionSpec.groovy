package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.powermock.modules.junit4.PowerMockRunnerDelegate
import org.spockframework.runtime.Sputnik
import spock.lang.IgnoreIf
import spock.lang.Retry
import spock.lang.Specification
import spock.util.environment.Jvm

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(Sputnik.class)
@PowerMockIgnore(["javax.net.ssl.*"])
@PrepareForTest([SocketUtils.class])
// due to the nature of port binding here there is a likelyhood this will sometimes fails to bind port on Travis
@Retry(count = 5, delay = 200)
// This test causes issues on Java 9+ due to use of javax.xml.parsers
// In general this test os overly complex and uses things we don't use elsewhere like powermock
// and should be rewritten and simplified
@IgnoreIf({ Jvm.current.isJava9Compatible() })
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
