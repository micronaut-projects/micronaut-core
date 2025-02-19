package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.resolver.AbstractAddressResolver
import io.netty.resolver.AddressResolver
import io.netty.resolver.AddressResolverGroup
import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.Promise
import jakarta.inject.Named
import jakarta.inject.Singleton
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

    def namedResolver() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'DnsSpec',
                'micronaut.http.client.address-resolver-group-name': 'test-resolver',
                'micronaut.server.port': -1
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, "http://example.com:" + server.port).toBlocking()

        expect:
        client.retrieve("/dns-spec/foo") == "foo"

        cleanup:
        client.close()
        server.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "DnsSpec")
    @Named("test-resolver")
    static class TestResolverGroup extends AddressResolverGroup<InetSocketAddress> {
        @Override
        protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
            return new AbstractAddressResolver<InetSocketAddress>(executor) {
                @Override
                protected boolean doIsResolved(InetSocketAddress address) {
                    return !address.isUnresolved()
                }

                @Override
                protected void doResolve(InetSocketAddress unresolvedAddress, Promise<InetSocketAddress> promise) throws Exception {
                    if (unresolvedAddress.hostString == "example.com") {
                        promise.trySuccess(new InetSocketAddress("127.0.0.1", unresolvedAddress.port))
                    } else {
                        promise.tryFailure(new Exception("Unexpected address"))
                    }
                }

                @Override
                protected void doResolveAll(InetSocketAddress unresolvedAddress, Promise<List<InetSocketAddress>> promise) throws Exception {
                    if (unresolvedAddress.hostString == "example.com") {
                        promise.trySuccess([new InetSocketAddress("127.0.0.1", unresolvedAddress.port)])
                    } else {
                        promise.tryFailure(new Exception("Unexpected address"))
                    }
                }
            }
        }
    }

    @Controller("/dns-spec")
    @Requires(property = "spec.name", value = "DnsSpec")
    static class MyCtrl {
        @Get("/foo")
        String foo() {
            return "foo"
        }
    }
}
