package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.netty.channel.Channel
import io.netty.util.AttributeKey
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification

class DefaultNettyHttpClientRegistrySpec extends Specification {
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
}
