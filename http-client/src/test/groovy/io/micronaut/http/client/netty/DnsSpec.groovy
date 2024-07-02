package io.micronaut.http.client.netty

import io.micronaut.http.client.HttpClientConfiguration
import io.netty.channel.nio.NioEventLoopGroup
import spock.lang.Specification

import java.util.stream.Stream

class DnsSpec extends Specification {
    def resolution() {
        given:
        def host = "cloudflare.com"
        def choices = Stream.of(InetAddress.getAllByName(host))
                .filter(a -> a instanceof Inet4Address)
                .toList()
        def group = new NioEventLoopGroup(1)
        def loop = group.next()
        def unresolved = InetSocketAddress.createUnresolved(host, 80)

        expect:"Test domain must have multiple records"
        choices.size() > 1

        ConnectionManager.getResolver(HttpClientConfiguration.DnsResolutionMode.NOOP)
                .getResolver(loop)
                .resolve(unresolved).get() == unresolved

        when:
        def defaultAddress = (InetSocketAddress) ConnectionManager.getResolver(HttpClientConfiguration.DnsResolutionMode.DEFAULT)
                .getResolver(loop)
                .resolve(unresolved).get()
        then:
        choices.contains(defaultAddress.address)


        when:
        def roundRobinResolver = ConnectionManager.getResolver(HttpClientConfiguration.DnsResolutionMode.ROUND_ROBIN)
                .getResolver(loop)
        def seen = new HashSet<InetAddress>()
        for (int i = 0; i < 20; i++) {
            def addr = (InetSocketAddress) roundRobinResolver.resolve(unresolved).get()
            if (addr.address instanceof Inet4Address) {
                seen.add(addr.address)
            }
        }
        then:
        choices.containsAll(seen)
        seen.size() > 1

        cleanup:
        group.shutdownGracefully()
    }
}
