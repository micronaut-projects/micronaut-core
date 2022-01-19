package io.micronaut.docs.http.server.secondary;

// tag::imports[]
import java.util.Collections;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.http.server.netty.NettyEmbeddedServer;
import io.micronaut.http.server.netty.NettyEmbeddedServerFactory;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;
import jakarta.inject.Named;
// end::imports[]

@Requires(property = "secondary.enabled", value = StringUtils.TRUE)
// tag::class[]
@Factory
public class SecondaryNettyServer {
    public static final String SERVER_ID = "another"; // <1>

    @Named(SERVER_ID)
    @Context
    @Bean(preDestroy = "close") // <2>
    @Requires(beans = Environment.class)
    NettyEmbeddedServer nettyEmbeddedServer(NettyEmbeddedServerFactory serverFactory) { // <3>
        // configure server programmatically
        final NettyHttpServerConfiguration configuration =
                new NettyHttpServerConfiguration(); // <4>
        final ServerSslConfiguration sslConfiguration = new ServerSslConfiguration(); // <5>
        sslConfiguration.setBuildSelfSigned(true);
        sslConfiguration.setEnabled(true);
        sslConfiguration.setPort(-1); // random port
        final NettyEmbeddedServer embeddedServer = serverFactory.build(configuration, sslConfiguration); // <6>
        embeddedServer.start(); // <7>
        return embeddedServer; // <8>
    }

    @Bean
    ServiceInstanceList serviceInstanceList( // <9>
            @Named(SERVER_ID) NettyEmbeddedServer nettyEmbeddedServer) {
        return new StaticServiceInstanceList(
                SERVER_ID,
                Collections.singleton(nettyEmbeddedServer.getURI())
        );
    }
}
// end::class[]