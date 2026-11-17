package io.micronaut.http.client.netty

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.exceptions.HttpClientException
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

@Timeout(15)
class PoolCloseConnectionFailureSpec extends Specification {
    def '#version logs unclaimed connection failures only while open (closed: #closed)'() {
        given:
        def group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        def config = new DefaultHttpClientConfiguration()
        config.connectionPoolConfiguration.version = HttpClientConfiguration.ConnectionPoolConfiguration.PoolVersion.valueOf(version)
        config.loggerName = "pool-close-test-${UUID.randomUUID()}"
        def client = DefaultHttpClient.builder().configuration(config).eventLoopGroup(group).build()
        def holder = client.connectionManager().createPool(
                new NettyHttpClient.RequestKey(client.nettyHttpClient, URI.create('http://localhost')), group)
        def pool = holder.pool
        Logger logger = (Logger) LoggerFactory.getLogger(config.loggerName.get())
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        def loop = group.next()
        if (pool instanceof Pool49) {
            assert pool.openConnectionStep1()
        } else {
            pool.pendingConnectionCount.incrementAndGet()
        }
        if (closed) {
            pool.close()
        }

        when:
        loop.submit {
            if (pool instanceof Pool49) {
                def local = pool.localPoolsByLoop.get(loop)
                local.openConnectionStep2()
                if (closed) {
                    local.openConnectionStep3()
                } else {
                    local.onNewConnectionFailure(new HttpClientException('connect failed'))
                }
                assert local.localPendingConnections == 0
            } else {
                pool.onNewConnectionFailure(new HttpClientException(closed ? 'Connection pool is closed' : 'connect failed'))
            }
        }.sync()

        then:
        appender.list.count { it.level == Level.ERROR && it.formattedMessage == 'Failed to connect to remote' } == (closed ? 0 : 1)
        (pool instanceof Pool49 ? pool.globalStats.get().pendingConnectionCount() : pool.pendingConnectionCount.get()) == 0

        cleanup:
        logger?.detachAppender(appender)
        appender?.stop()
        holder?.shutdown()
        client?.close()
        group?.shutdownGracefully(0, 0, TimeUnit.SECONDS)?.sync()

        where:
        version | closed
        'V4_9'  | true
        'V4_0'  | true
        'V4_9'  | false
        'V4_0'  | false
    }
}
