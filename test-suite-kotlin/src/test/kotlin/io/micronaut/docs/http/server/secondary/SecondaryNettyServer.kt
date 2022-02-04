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
    companion object {
        const val SERVER_ID = "another" // <1>
    }

    @Named(SERVER_ID)
    @Context
    @Bean(preDestroy = "close") // <2>
    @Requires(beans = [Environment::class])
    fun nettyEmbeddedServer(
        serverFactory: NettyEmbeddedServerFactory // <3>
    ) : NettyEmbeddedServer {
        val configuration = NettyHttpServerConfiguration() // <4>
        val sslConfiguration = ServerSslConfiguration() // <5>

        sslConfiguration.setBuildSelfSigned(true)
        sslConfiguration.isEnabled = true
        sslConfiguration.port = -1 // random port

        // configure server programmatically
        val embeddedServer = serverFactory.build(configuration, sslConfiguration) // <6>
        embeddedServer.start() // <7>
        return embeddedServer // <8>
    }

    @Bean
    fun serviceInstanceList( // <9>
        @Named(SERVER_ID) nettyEmbeddedServer: NettyEmbeddedServer
    ): ServiceInstanceList {
        return StaticServiceInstanceList(
            SERVER_ID, setOf(nettyEmbeddedServer.uri)
        )
    }
}
// end::class[]