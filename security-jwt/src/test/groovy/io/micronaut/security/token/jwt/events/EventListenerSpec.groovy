package io.micronaut.security.token.jwt.events

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.jwt.event.AccessTokenGeneratedEvent
import io.micronaut.security.token.jwt.event.RefreshTokenGeneratedEvent
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton

class EventListenerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'eventlistener',
            'endpoints.health.enabled': true,
            'endpoints.health.sensitive': true,
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.signatures.secret.generator.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            'micronaut.security.endpoints.login.enabled': true,
    ], "test")
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "successful login publishes AccessTokenGeneratedEvent and RefreshTokenGeneratedEvent if JWT authentication enabled"() {
        when:
        HttpRequest request = HttpRequest.POST("/login", new UsernamePasswordCredentials("user", "password"))
        client.toBlocking().exchange(request)

        then:
        embeddedServer.applicationContext.getBean(RefreshTokenGeneratedEventListener).events.size() ==
                old(embeddedServer.applicationContext.getBean(RefreshTokenGeneratedEventListener).events.size()) + 1

        and:
        embeddedServer.applicationContext.getBean(AccessTokenGeneratedEventListener).events.size() ==
                old(embeddedServer.applicationContext.getBean(AccessTokenGeneratedEventListener).events.size()) + 1
    }

    @Requires(property = "spec.name", value = "eventlistener")
    @Singleton
    static class RefreshTokenGeneratedEventListener implements ApplicationEventListener<RefreshTokenGeneratedEvent> {
        List<RefreshTokenGeneratedEvent> events = []
        @Override
        void onApplicationEvent(RefreshTokenGeneratedEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "eventlistener")
    @Singleton
    static class AccessTokenGeneratedEventListener implements ApplicationEventListener<AccessTokenGeneratedEvent> {
        List<AccessTokenGeneratedEvent> events = []
        @Override
        void onApplicationEvent(AccessTokenGeneratedEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "eventlistener")
    @Singleton
    static class CustomAuthenticationProvider implements AuthenticationProvider {

        @Override
        Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
            if ( authenticationRequest.identity == 'user' && authenticationRequest.secret == 'password' ) {
                return Flowable.just(new UserDetails('user', []))
            }
            return Flowable.just(new AuthenticationFailed())
        }
    }

}