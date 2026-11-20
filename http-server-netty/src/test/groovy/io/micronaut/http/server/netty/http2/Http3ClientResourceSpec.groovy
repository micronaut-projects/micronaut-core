package io.micronaut.http.server.netty.http2

import com.sun.management.UnixOperatingSystemMXBean
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import jakarta.inject.Named
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Timeout

import java.lang.management.ManagementFactory
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@Timeout(value = 180, unit = TimeUnit.SECONDS)
class Http3ClientResourceSpec extends Specification {

    def "requests beyond the QUIC stream limit wait for a stream instead of spinning"() {
        given:
        def serverCtx = ApplicationContext.run([
                'spec.name'                                               : 'Http3ClientResourceSpec',
                'micronaut.server.ssl.enabled'                            : true,
                'micronaut.server.ssl.buildSelfSigned'                    : true,
                'micronaut.server.netty.listeners.a.family'               : 'QUIC',
                'micronaut.server.netty.listeners.a.port'                 : -1,
                'micronaut.server.netty.http3.initial-max-streams-bidirectional': 2,
        ])
        def server = serverCtx.getBean(EmbeddedServer)
        server.start()
        def clientCtx = ApplicationContext.run([
                "micronaut.http.client.alpn-modes"                        : ["h3"],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.read-timeout'                      : '10s',
        ])
        def client = clientCtx.createBean(HttpClient, server.URI)
        // warm up the connection
        client.toBlocking().retrieve("/h3-limit/slow")
        def os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.operatingSystemMXBean

        when:
        long cpuBefore = os.processCpuTime
        long start = System.nanoTime()
        List<String> results = Flux.range(0, 8)
                .flatMap({ i ->
                    Mono.fromCallable { client.toBlocking().retrieve("/h3-limit/slow") }
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume { Mono.just("error: " + it) }
                }, 8)
                .collectList()
                .block(Duration.ofSeconds(60))
        long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        long cpuMs = TimeUnit.NANOSECONDS.toMillis(os.processCpuTime - cpuBefore)
        println "EVIDENCE h3 stream limit 2, 8 concurrent 500 ms calls: wall=${wallMs} ms cpu=${cpuMs} ms results=${results.countBy { it }}"

        then:
        results.every { it == "ok" }
        cpuMs < 4000

        cleanup:
        client?.close()
        clientCtx?.close()
        server?.close()
        serverCtx?.close()
    }

    // counts the open file descriptors, which only a Unix JVM reports
    @IgnoreIf({ !(ManagementFactory.operatingSystemMXBean instanceof UnixOperatingSystemMXBean) })
    def "a failed QUIC handshake does not leave a UDP socket open"() {
        given:
        def serverCtx = ApplicationContext.run([
                'micronaut.server.ssl.enabled'             : true,
                'micronaut.server.ssl.buildSelfSigned'     : true,
                'micronaut.server.netty.listeners.a.family': 'QUIC',
                'micronaut.server.netty.listeners.a.port'  : -1,
        ])
        def server = serverCtx.getBean(EmbeddedServer)
        server.start()
        // the client does not trust the self-signed certificate, so every handshake fails
        def clientCtx = ApplicationContext.run([
                "micronaut.http.client.alpn-modes": ["h3"],
                'micronaut.http.client.read-timeout': '5s',
        ])
        def client = clientCtx.createBean(HttpClient, server.URI)
        def os = ManagementFactory.operatingSystemMXBean
        int failures = 0
        // one attempt first, so that lazily created resources are counted in the baseline
        try {
            client.toBlocking().retrieve("/")
        } catch (Exception ignored) {
        }
        long before = ((UnixOperatingSystemMXBean) os).openFileDescriptorCount

        when:
        for (int i = 0; i < 40; i++) {
            try {
                client.toBlocking().retrieve("/")
            } catch (Exception ignored) {
                failures++
            }
        }
        Thread.sleep(1000)
        long after = ((UnixOperatingSystemMXBean) os).openFileDescriptorCount
        println "EVIDENCE h3 failed handshakes: failures=$failures open fds before=$before after=$after"

        then:
        failures == 40
        after - before < 10

        cleanup:
        client?.close()
        clientCtx?.close()
        server?.close()
        serverCtx?.close()
    }

    @Controller('/h3-limit')
    @Requires(property = 'spec.name', value = 'Http3ClientResourceSpec')
    static class SlowController {
        private final Scheduler scheduler

        SlowController(@Named(TaskExecutors.SCHEDULED) ExecutorService executor) {
            // Reactor's shared parallel threads may have been started by an earlier spec, and
            // would charge the response buffers to that spec's closed leak detection scope
            scheduler = Schedulers.fromExecutorService(executor)
        }

        @Get('/slow')
        Mono<String> slow() {
            return Mono.delay(Duration.ofMillis(500), scheduler).thenReturn("ok")
        }
    }
}
