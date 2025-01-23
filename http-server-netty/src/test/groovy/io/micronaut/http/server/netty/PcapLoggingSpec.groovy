package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
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
                'micronaut.server.netty.pcap-logging-path-pattern': tmp.toString() + '/{localAddress}-{remoteAddress}-{qualifier}-{random}-{timestamp}.pcap',
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
        names[0].matches('127\\.0\\.0\\.1_\\d+-127\\.0\\.0\\.1_\\d+-encapsulated-\\w+-\\d+-\\d+-\\d+T\\d+_\\d+_\\d+\\.\\d+Z\\.pcap')
        names[1].matches('127\\.0\\.0\\.1_\\d+-127\\.0\\.0\\.1_\\d+-ssl-decapsulated-\\w+-\\d+-\\d+-\\d+T\\d+_\\d+_\\d+\\.\\d+Z\\.pcap')

        cleanup:
        server.close()
        client.close()
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
