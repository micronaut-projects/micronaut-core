package io.micronaut.security.token.jwt.events

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.event.LoginFailedEvent
import io.micronaut.security.event.LoginSuccessfulEvent
import io.micronaut.security.event.LogoutEvent
import io.micronaut.security.event.TokenValidatedEvent
import io.micronaut.security.handlers.LoginHandler
import io.micronaut.security.token.jwt.event.AccessTokenGeneratedEvent
import io.micronaut.security.token.jwt.event.RefreshTokenGeneratedEvent
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
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
            'micronaut.security.endpoints.login': true,
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

    @Singleton
    static class RefreshTokenGeneratedEventListener implements ApplicationEventListener<RefreshTokenGeneratedEvent> {
        List<RefreshTokenGeneratedEvent> events = []
        @Override
        void onApplicationEvent(RefreshTokenGeneratedEvent event) {
            events.add(event)
        }
    }

    @Singleton
    static class AccessTokenGeneratedEventListener implements ApplicationEventListener<AccessTokenGeneratedEvent> {
        List<AccessTokenGeneratedEvent> events = []
        @Override
        void onApplicationEvent(AccessTokenGeneratedEvent event) {
            events.add(event)
        }
    }

    @Singleton
    static class CustomAuthenticationProvider implements AuthenticationProvider {

        @Override
        AuthenticationResponse authenticate(AuthenticationRequest authenticationRequest) {
            if ( authenticationRequest.identity == 'user' && authenticationRequest.secret == 'password' ) {
                return new UserDetails('user', [])
            }
            return new AuthenticationFailed()
        }
    }

}