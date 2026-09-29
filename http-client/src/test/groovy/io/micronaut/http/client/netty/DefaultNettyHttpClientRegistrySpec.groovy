package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationMetadataDelegate
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import io.netty.util.AttributeKey
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification

class DefaultNettyHttpClientRegistrySpec extends Specification {
    def 'client key is computed once per annotation metadata instance'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.http.services.cached-key.url': 'https://micronaut.io'
        ])
        def registry = ctx.getBean(DefaultNettyHttpClientRegistry)
        def metadata = new CountingMetadata(delegate: AnnotationMetadata.EMPTY_METADATA)

        when:
        def first = registry.getClient(metadata)
        int lookups = metadata.lookups
        def second = registry.getClient(metadata)

        then:
        lookups > 0
        first.is(second)
        metadata.lookups == lookups

        cleanup:
        ctx.close()
    }

    def 'distinct annotation metadata instances get their own client keys'() {
        given:
        def ctx = ApplicationContext.run()
        def registry = ctx.getBean(DefaultNettyHttpClientRegistry)
        def first = new CountingMetadata(delegate: AnnotationMetadata.EMPTY_METADATA)
        def second = new CountingMetadata(delegate: AnnotationMetadata.EMPTY_METADATA)

        when:
        registry.getClient(first)
        registry.getClient(second)
        int firstLookups = first.lookups
        int secondLookups = second.lookups
        registry.getClient(first)
        registry.getClient(second)

        then: 'each instance is resolved once and then served from the cache'
        firstLookups > 0
        secondLookups > 0
        first.lookups == firstLookups
        second.lookups == secondLookups
        registry.clientKeyCache.size() == 2
        registry.clientKeyCache.containsKey(first)
        registry.clientKeyCache.containsKey(second)

        cleanup:
        ctx.close()
    }

    def 'client key of metadata with evaluated expressions is not cached'() {
        given:
        def ctx = ApplicationContext.run()
        def registry = ctx.getBean(DefaultNettyHttpClientRegistry)
        def metadata = new EvaluatedCountingMetadata(delegate: AnnotationMetadata.EMPTY_METADATA)

        when:
        def first = registry.getClient(metadata)
        int lookups = metadata.lookups
        def second = registry.getClient(metadata)

        then: 'the key is recomputed on every call but still resolves the same client'
        lookups > 0
        metadata.lookups > lookups
        first.is(second)
        registry.clientKeyCache.isEmpty()

        cleanup:
        ctx.close()
    }

    def 'declarative client with an expression in @Client does not grow the key cache'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'DefaultNettyHttpClientRegistrySpec.expression'
        ])
        def ctx = ApplicationContext.run([
                'spec.name': 'DefaultNettyHttpClientRegistrySpec.expression',
                'micronaut.http.services.expression-client.url': server.URL.toString()
        ])
        def registry = ctx.getBean(DefaultNettyHttpClientRegistry)
        def client = ctx.getBean(ExpressionClient)

        expect:
        (1..5).collect { client.get() } == ['ok'] * 5
        registry.clientKeyCache.isEmpty()
        registry.unbalancedClients.size() == 1

        cleanup:
        ctx.close()
        server.close()
    }

    static class CountingMetadata implements AnnotationMetadataDelegate {
        AnnotationMetadata delegate
        int lookups

        @Override
        AnnotationMetadata getAnnotationMetadata() {
            lookups++
            return delegate
        }
    }

    static class EvaluatedCountingMetadata extends CountingMetadata {
        @Override
        boolean hasEvaluatedExpressions() {
            return true
        }
    }

    @Requires(property = 'spec.name', value = 'DefaultNettyHttpClientRegistrySpec.expression')
    @Client("#{ 'expression-' + 'client' }")
    static interface ExpressionClient {
        @Get('/registry-expression')
        String get()
    }

    @Requires(property = 'spec.name', value = 'DefaultNettyHttpClientRegistrySpec.expression')
    @Controller
    static class ExpressionController {
        @Get('/registry-expression')
        String get() {
            return 'ok'
        }
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/4829')
    def 'ChannelPipelineCustomizer invoked for declarative clients'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'DefaultNettyHttpClientRegistrySpec',
                'micronaut.http.services.test-client.url': 'https://micronaut.io'
        ])
        def customizer = ctx.getBean(MyCustomizer)

        expect:
        customizer.connected == 0

        when:
        def client = ctx.getBean(DeclarativeClient)
        try {
            client.index()
        } catch (Exception e) {
            // usually onConnect should still be called, but print anyway in case this test fails
            e.printStackTrace()
        }

        then:
        customizer.connected == 1

        when:
        def client2 = ctx.getBean(ClientHolder).client
        try {
            client2.toBlocking().retrieve('/')
        } catch (Exception e) {
            // usually onConnect should still be called, but print anyway in case this test fails
            e.printStackTrace()
        }

        then:'no duplicate customization'
        !customizer.duplicate
    }

    def 'duplicate customization for normal client'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'DefaultNettyHttpClientRegistrySpec',
                'micronaut.http.services.test-client.url': 'https://micronaut.io'
        ])
        def customizer = ctx.getBean(MyCustomizer)

        when:
        def client1 = ctx.getBean(ClientHolder).client
        def client2 = ctx.getBean(ClientHolder2).client
        try {
            client1.toBlocking().retrieve('/')
            client2.toBlocking().retrieve('/')
        } catch (Exception e) {
            // usually onConnect should still be called, but print anyway in case this test fails
            e.printStackTrace()
        }

        then:'no duplicate customization'
        !customizer.duplicate
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/pull/9999")
    def 'same client with protocol customizations'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'DefaultNettyHttpClientRegistrySpec',
                'micronaut.http.services.test-client2.url': 'https://micronaut.io'
        ])
        def registry = ctx.getBean(HttpClientRegistry)
        def introspection = BeanIntrospector.SHARED.findIntrospection(DeclarativeClient2).get()

        when:
        def client1 = registry.getClient(introspection)
        def client2 = registry.getClient(introspection)
        then:
        client1 == client2

        cleanup:
        ctx.close()
    }

    @Requires(property = 'spec.name', value = 'DefaultNettyHttpClientRegistrySpec')
    @Client('test-client')
    static interface DeclarativeClient {
        @Get('/')
        String index();
    }

    @Requires(property = 'spec.name', value = 'DefaultNettyHttpClientRegistrySpec')
    @Singleton
    static class MyCustomizer implements BeanCreatedEventListener<NettyClientCustomizer.Registry> {
        static final AttributeKey<Boolean> CUSTOMIZED = AttributeKey.valueOf('micronaut.test.customized')

        def connected = 0
        def duplicate = false

        @Override
        NettyClientCustomizer.Registry onCreated(BeanCreatedEvent<NettyClientCustomizer.Registry> event) {
            event.bean.register(new NettyClientCustomizer() {
                @Override
                NettyClientCustomizer specializeForChannel(Channel channel, NettyClientCustomizer.ChannelRole role) {
                    if (role == NettyClientCustomizer.ChannelRole.CONNECTION) {
                        if (channel.hasAttr(CUSTOMIZED)) {
                            duplicate = true
                        }
                        channel.attr(CUSTOMIZED).set(true)
                        connected++
                    }
                    return this
                }
            })
            return event.bean
        }
    }

    @Requires(property = 'spec.name', value = 'DefaultNettyHttpClientRegistrySpec')
    @Singleton
    static class ClientHolder {
        @Inject
        @Client('test-client')
        HttpClient client;
    }

    @Requires(property = 'spec.name', value = 'DefaultNettyHttpClientRegistrySpec')
    @Singleton
    static class ClientHolder2 {
        @Inject
        @Client('test-client')
        HttpClient client;
    }

    @Requires(property = 'spec.name', value = 'DefaultNettyHttpClientRegistrySpec')
    @Introspected
    @Client(value = 'test-client2', alpnModes = ["h2"], plaintextMode = HttpVersionSelection.PlaintextMode.HTTP_1)
    static interface DeclarativeClient2 {
    }
}
