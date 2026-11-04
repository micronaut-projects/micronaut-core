package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicStreamChannel
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors

class PcapLoggingSpec extends Specification {
    def 'pcap logging'() {
        given:
        def tmp = Files.createTempDirectory("micronaut-http-server-netty-test-pcap-logging-spec")
        def ctx = ApplicationContext.run([
                'micronaut.http.client.pcap-logging-path-pattern': tmp.toString() + '/{localAddress}-{remoteAddress}-{qualifier}-{random}-{timestamp}.pcap',
                'micronaut.ssl.enabled': true,
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.server.ssl.port': -1,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI)

        expect:
        Files.list(tmp).collect(Collectors.toList()).isEmpty()

        when:
        try {
            Flux.from(client.exchange('/')).blockLast()
        } catch (ignored) {
            // don't actually care about the response
        }
        def names = Files.list(tmp).map(p -> p.fileName.toString()).sorted().collect(Collectors.toList())
        then:
        names.size() == 2
        names[0].matches('127\\.0\\.0\\.1_\\d+-127\\.0\\.0\\.1_\\d+-outer-\\w+-\\d+-\\d+-\\d+T\\d+_\\d+_\\d+\\.\\d+Z\\.pcap')
        names[1].matches('127\\.0\\.0\\.1_\\d+-127\\.0\\.0\\.1_\\d+-tls-unwrapped-\\w+-\\d+-\\d+-\\d+T\\d+_\\d+_\\d+\\.\\d+Z\\.pcap')

        cleanup:
        server.close()
        client.close()
        deleteTree(tmp)
    }

    def 'pcap logging of a QUIC stream names the file after the addresses of its connection: #description'() {
        given:
        def tmp = Files.createTempDirectory("micronaut-http-client-test-pcap-logging-spec-quic")
        def ctx = ApplicationContext.run([
                'micronaut.http.client.pcap-logging-path-pattern': tmp.toString() + '/{localAddress}-{remoteAddress}-{qualifier}.pcap',
                'micronaut.http.client.alpn-modes': ['h3'],
        ])
        def client = ctx.createBean(HttpClient, new URI('https://localhost:1'))
        def connection = Stub(QuicChannel) {
            localSocketAddress() >> connectionLocal
            remoteSocketAddress() >> connectionRemote
        }
        def stream = Stub(QuicStreamChannel) {
            localAddress() >> null
            remoteAddress() >> null
            parent() >> connection
        }

        when:
        def handler = ((DefaultHttpClient) client).connectionManager().createPcapLoggingHandler(stream, 'quic')
        def names = Files.list(tmp).map(p -> p.fileName.toString()).collect(Collectors.toList())

        then: "an address the connection does not have is left in the pattern"
        handler != null
        names == [expected]

        cleanup:
        client?.close()
        ctx?.close()
        deleteTree(tmp)

        where:
        description             | connectionLocal                                             | connectionRemote                                            | expected
        'both addresses known'  | new InetSocketAddress(InetAddress.getLoopbackAddress(), 4433) | new InetSocketAddress(InetAddress.getLoopbackAddress(), 8443) | '127.0.0.1_4433-127.0.0.1_8443-quic.pcap'
        'no addresses yet'      | null                                                        | null                                                        | '{localAddress}-{remoteAddress}-quic.pcap'
    }

    private static void deleteTree(Path tmp) {
        Files.walkFileTree(tmp, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
