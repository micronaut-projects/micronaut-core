from typing import Annotated

import java

# tag::imports[]
from jakarta.inject import Named
from micronaut.context.annotation import Bean, Context, Factory, Requires
from micronaut.context.env import Environment
from micronaut.core.util import StringUtils
from micronaut.discovery import ServiceInstanceList, StaticServiceInstanceList
from micronaut.http.server.netty import NettyEmbeddedServer, NettyEmbeddedServerFactory
from micronaut.http.server.netty.configuration import NettyHttpServerConfiguration
from micronaut.http.ssl import ServerSslConfiguration
# end::imports[]

Collections = java.type("java.util.Collections")


@Requires(property="secondary.enabled", value=StringUtils.TRUE)
# tag::class[]
@Factory
class SecondaryNettyServer:
    SERVER_ID = "another"  # <1>

    @Named("another")
    @Context
    @Bean(preDestroy="close")  # <2>
    @Requires(beans=Environment)
    def nettyEmbeddedServer(
        self, serverFactory: NettyEmbeddedServerFactory
    ) -> NettyEmbeddedServer:  # <3>
        configuration = NettyHttpServerConfiguration()  # <4>
        sslConfiguration = ServerSslConfiguration()  # <5>
        sslConfiguration.setBuildSelfSigned(True)
        sslConfiguration.setEnabled(True)
        sslConfiguration.setPort(-1)  # random port
        embeddedServer = serverFactory.build(configuration, sslConfiguration)  # <6>
        embeddedServer.start()  # <7>
        return embeddedServer  # <8>

    @Bean
    def serviceInstanceList(  # <9>
        self,
        nettyEmbeddedServer: Annotated[NettyEmbeddedServer, Named("another")],
    ) -> ServiceInstanceList:
        return StaticServiceInstanceList(
            self.SERVER_ID,
            Collections.singleton(nettyEmbeddedServer.getURI()),
        )
# end::class[]
