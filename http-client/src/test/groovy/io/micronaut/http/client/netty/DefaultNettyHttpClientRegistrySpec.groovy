package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
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
    }

    @Requires(property = 'spec.name', value = 'DefaultNettyHttpClientRegistrySpec')
    @Client('test-client')
    static interface DeclarativeClient {
        @Get('/')
        String index();
    }

    @Requires(property = 'spec.name', value = 'DefaultNettyHttpClientRegistrySpec')
    @Singleton
    static class MyCustomizer implements BeanCreatedEventListener<ChannelPipelineCustomizer> {
        def connected = 0

        @Override
        ChannelPipelineCustomizer onCreated(BeanCreatedEvent<ChannelPipelineCustomizer> event) {
            event.bean.doOnConnect {
                connected++
                return it
            }
            return event.bean
        }
    }
}
