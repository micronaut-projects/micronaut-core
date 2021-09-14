package io.micronaut.docs.http.server.secondary

// tag::imports[]
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.discovery.ServiceInstanceList
import io.micronaut.discovery.StaticServiceInstanceList
import io.micronaut.http.server.netty.NettyEmbeddedServer
import io.micronaut.http.server.netty.NettyEmbeddedServerFactory
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import jakarta.inject.Named
// end::imports[]

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
        // configure server programmatically
        val embeddedServer = serverFactory.build(configuration) // <5>
        embeddedServer.start() // <6>
        return embeddedServer // <7>
    }

    @Bean
    fun serviceInstanceList( // <8>
        @Named(SERVER_ID) nettyEmbeddedServer: NettyEmbeddedServer
    ): ServiceInstanceList {
        return StaticServiceInstanceList(
            SERVER_ID, setOf(nettyEmbeddedServer.uri)
        )
    }
}
// end::class[]