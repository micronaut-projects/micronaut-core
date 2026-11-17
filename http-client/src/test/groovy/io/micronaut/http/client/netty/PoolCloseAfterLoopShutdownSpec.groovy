package io.micronaut.http.client.netty

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.exceptions.HttpClientException
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

@Timeout(15)
class PoolCloseAfterLoopShutdownSpec extends Specification {
    def 'closing the pool after its event loop shut down fails local waiters instead of throwing'() {
        given:
        def group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        def config = new DefaultHttpClientConfiguration()
        config.connectionPoolConfiguration.maxPendingConnections = 0
        config.connectionPoolConfiguration.connectionLocality =
                HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS
        // the application supplies the group, so the client does not shut it down itself
        def client = DefaultHttpClient.builder().configuration(config).eventLoopGroup(group).build()
        def holder = client.connectionManager().createPool(
                new NettyHttpClient.RequestKey(client.nettyHttpClient, URI.create('http://localhost')), group)
        Pool49 pool = (Pool49) holder.pool
        pool.pickPreferredPoolOverride = { it[0] }
        def request = pool.createPendingRequest(null)
        request.dispatch()
        group.next().submit {}.sync()
        assert pool.localPools.sum { it.localPendingRequests.size() } == 1

        when:
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
        holder.shutdown()

        then:
        noExceptionThrown()
        pool.localPools.sum { it.localPendingRequests.size() } == 0
        request.flow().tryCompleteError() instanceof HttpClientException

        cleanup:
        client.close()
    }
}
