package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ConcurrentLinkedQueue

@Timeout(120)
class PoolEvictionSpec extends Specification {
    def 'pools for distinct hosts are evicted when their connections expire'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'PoolEvictionSpec',
                'micronaut.http.client.connection-pool-idle-timeout': '200ms',
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = (DefaultHttpClient) ctx.createBean(HttpClient)
        def hosts = ['localhost', '127.0.0.1']

        when:
        for (String host : hosts) {
            assert client.toBlocking().retrieve("http://$host:$server.port/pool-eviction") == 'ok'
        }

        then:
        client.connectionManager.poolCount() == hosts.size()
        new PollingConditions(timeout: 10).eventually {
            assert client.connectionManager.poolCount() == 0
        }

        when:
        for (String host : hosts) {
            assert client.toBlocking().retrieve("http://$host:$server.port/pool-eviction") == 'ok'
        }

        then:
        client.connectionManager.poolCount() == hosts.size()

        cleanup:
        client.close()
        server.stop()
        ctx.close()
    }

    def 'concurrent requests are not lost while pools are evicted'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'PoolEvictionSpec',
                // expire connections almost immediately, so that pools retire all the time
                'micronaut.http.client.connection-pool-idle-timeout': '1ms',
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = (DefaultHttpClient) ctx.createBean(HttpClient)
        def hosts = ['localhost', '127.0.0.1']
        def errors = new ConcurrentLinkedQueue<Throwable>()

        when:
        List<Thread> threads = (0..<4).collect { t ->
            Thread.start {
                try {
                    sendRequests(client, hosts, server.port, t, 50)
                } catch (Throwable e) {
                    errors.add(e)
                }
            }
        }
        threads*.join(60_000)

        then:
        threads.every { !it.isAlive() }
        errors.isEmpty()
        new PollingConditions(timeout: 10).eventually {
            assert client.connectionManager.poolCount() == 0
        }

        cleanup:
        client.close()
        server.stop()
        ctx.close()
    }

    private static void sendRequests(DefaultHttpClient client, List<String> hosts, int port, int offset, int count) {
        for (int i = 0; i < count; i++) {
            def host = hosts[(offset + i) % hosts.size()]
            assert client.toBlocking().retrieve("http://$host:$port/pool-eviction") == 'ok'
        }
    }

    @Requires(property = 'spec.name', value = 'PoolEvictionSpec')
    @Controller('/pool-eviction')
    static class PoolEvictionController {
        @Get
        String get() {
            return 'ok'
        }
    }
}
