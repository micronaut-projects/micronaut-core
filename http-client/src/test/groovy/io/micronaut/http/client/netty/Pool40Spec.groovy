package io.micronaut.http.client.netty

import io.micronaut.http.client.HttpClientConfiguration
import io.netty.channel.DefaultEventLoopGroup
import org.slf4j.LoggerFactory
import spock.lang.Specification

class Pool40Spec extends Specification {
    def "http2 earmark uses the full stream limit"() {
        given:
        def config = new HttpClientConfiguration.ConnectionPoolConfiguration()
        config.maxConcurrentRequestsPerHttp2Connection = configLimit
        def group = new DefaultEventLoopGroup(1)
        def pool = new Pool40(null, LoggerFactory.getLogger(Pool40Spec), config, group)
        def entry = pool.createHttp2PoolEntry(group.next(), null)
        entry.onConnectionEstablished(streamLimit)

        expect:
        (1..expectedLimit).each {
            assert entry.tryEarmarkForRequest()
        }
        !entry.tryEarmarkForRequest()

        when:
        entry.markAvailable()

        then:
        entry.tryEarmarkForRequest()
        !entry.tryEarmarkForRequest()

        cleanup:
        group.shutdownGracefully()

        where:
        configLimit       | streamLimit | expectedLimit
        1                 | 1           | 1
        1                 | 100         | 1
        Integer.MAX_VALUE | 1           | 1
        3                 | 100         | 3
        100               | 3           | 3
    }
}
