package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.NettyEmbeddedServerFactory
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.http.ssl.ServerSslConfiguration
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import jakarta.inject.Singleton
import spock.lang.Specification

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class ExplicitServerSslFactorySpec extends Specification {
    def 'explicit SSL uses the replacement factory when context SSL is disabled'() {
        given:
        def context = ApplicationContext.run([
            'spec.name': 'ExplicitServerSslFactorySpec',
            'micronaut.server.ssl.enabled': false,
            'micronaut.ssl.enabled': false,
        ])
        def replacement = context.getBean(RecordingFactory)
        def configuration = new NettyHttpServerConfiguration()
        def ssl = new ServerSslConfiguration()
        ssl.enabled = true
        ssl.buildSelfSigned = true
        ssl.port = -1
        ssl.protocols = ['TLSv1.3'] as String[]
        assert !context.containsBean(ServerSslBuilder)
        def server = context.getBean(NettyEmbeddedServerFactory).build(configuration, ssl)
        def clientContext = SSLContext.getInstance('TLS')
        clientContext.init(null, InsecureTrustManagerFactory.INSTANCE.trustManagers, null)

        when:
        server.start()
        def socket = clientContext.socketFactory.createSocket('localhost', server.port) as SSLSocket
        socket.enabledProtocols = ['TLSv1.3'] as String[]
        socket.startHandshake()

        then:
        socket.session.protocol == 'TLSv1.3'
        replacement.calls == 1
        replacement.configuration.is(configuration)
        replacement.sslConfiguration.is(ssl)
        replacement.resourceResolver.is(context.getBean(ResourceResolver))

        cleanup:
        socket?.close()
        server?.close()
        context.close()
    }

    def 'replacement rejection of explicit SSL propagates without stock fallback'() {
        given:
        def context = ApplicationContext.run(['spec.name': 'ExplicitServerSslFactorySpec'])
        def replacement = context.getBean(RecordingFactory)
        def rejection = new ConfigurationException('unsupported explicit TLS policy')
        replacement.rejection = rejection
        def ssl = new ServerSslConfiguration()
        ssl.enabled = true
        ssl.buildSelfSigned = true

        when:
        context.getBean(NettyEmbeddedServerFactory).build(new NettyHttpServerConfiguration(), ssl)

        then:
        def failure = thrown(ConfigurationException)
        failure.is(rejection)
        replacement.calls == 1

        cleanup:
        context.close()
    }

    def 'stock factory retains the explicit #builderType.simpleName configuration'(boolean selfSigned, Class<?> builderType) {
        given:
        def configuration = new NettyHttpServerConfiguration()
        def ssl = new ServerSslConfiguration()
        ssl.buildSelfSigned = selfSigned

        when:
        def builder = new NettyServerSslFactory().legacyBuilder(configuration, ssl, new ResourceResolver())

        then:
        builderType.isInstance(builder)
        builder.sslConfiguration.is(ssl)

        where:
        selfSigned | builderType
        false      | CertificateProvidedSslBuilder
        true       | SelfSignedSslBuilder
    }

    def 'absent or disabled explicit SSL retains the default service path'(boolean absent) {
        given:
        def context = ApplicationContext.run(['spec.name': 'ExplicitServerSslFactorySpec'])
        def ssl = absent ? null : new ServerSslConfiguration()
        def replacement = context.getBean(RecordingFactory)

        when:
        def server = context.getBean(NettyEmbeddedServerFactory).build(new NettyHttpServerConfiguration(), ssl)

        then:
        replacement.calls == 0
        server.scheme == 'http'

        cleanup:
        server?.close()
        context.close()

        where:
        absent << [true, false]
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ExplicitServerSslFactorySpec')
    @Replaces(NettyServerSslFactory)
    static class RecordingFactory extends NettyServerSslFactory {
        int calls
        HttpServerConfiguration configuration
        ServerSslConfiguration sslConfiguration
        ResourceResolver resourceResolver
        ConfigurationException rejection

        @Override
        ServerSslBuilder legacyBuilder(HttpServerConfiguration configuration, ServerSslConfiguration sslConfiguration,
                                       ResourceResolver resourceResolver) {
            calls++
            this.configuration = configuration
            this.sslConfiguration = sslConfiguration
            this.resourceResolver = resourceResolver
            if (rejection != null) {
                throw rejection
            }
            super.legacyBuilder(configuration, sslConfiguration, resourceResolver)
        }
    }
}
