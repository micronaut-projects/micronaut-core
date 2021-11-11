package io.micronaut.docs.http.server.secondary

// tag::imports[]
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.util.StringUtils
import io.micronaut.discovery.ServiceInstanceList
import io.micronaut.discovery.StaticServiceInstanceList
import io.micronaut.http.server.netty.NettyEmbeddedServer
import io.micronaut.http.server.netty.NettyEmbeddedServerFactory
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.http.ssl.ServerSslConfiguration
import jakarta.inject.Named
// end::imports[]

@Requires(property = "secondary.enabled", value = StringUtils.TRUE)
// tag::class[]
@Factory
class SecondaryNettyServer {
    static final String SERVER_ID = "another" // <1>

    @Named(SERVER_ID)
    @Context
    @Bean(preDestroy = "stop") // <2>
    @Requires(beans = Environment.class)
    NettyEmbeddedServer nettyEmbeddedServer(NettyEmbeddedServerFactory serverFactory) { // <3>
        def configuration =
                new NettyHttpServerConfiguration() // <4>
        def sslConfiguration = new ServerSslConfiguration() // <5>
        sslConfiguration.setBuildSelfSigned(true)
        sslConfiguration.enabled = true
        sslConfiguration.port = -1 // random port
        // configure server programmatically
        final NettyEmbeddedServer embeddedServer = serverFactory.build(configuration, sslConfiguration) // <5>
        embeddedServer.start() // <6>
        return embeddedServer // <7>
    }

    @Bean
    ServiceInstanceList serviceInstanceList( // <8>
                                             @Named(SERVER_ID) NettyEmbeddedServer nettyEmbeddedServer) {
        return new StaticServiceInstanceList(
                SERVER_ID,
                [ nettyEmbeddedServer.URI ]
        )
    }
}
// end::class[]