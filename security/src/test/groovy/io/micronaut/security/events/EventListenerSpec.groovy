package io.micronaut.security.events

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.Authentication
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
            'micronaut.security.endpoints.login': true,
            'micronaut.security.endpoints.logout': true,
    ], "test")
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "failed login publishes LoginFailedEvent"() {
        when:
        HttpRequest request = HttpRequest.POST("/login", new UsernamePasswordCredentials("bogus", "password"))
        client.toBlocking().exchange(request)

        then:
        thrown(HttpClientResponseException)
        embeddedServer.applicationContext.getBean(LoginFailedEventListener).events.size() ==
                old(embeddedServer.applicationContext.getBean(LoginFailedEventListener).events.size()) + 1
    }

    def "successful login publishes LoginSuccessfulEvent"() {
        when:
        HttpRequest request = HttpRequest.POST("/login", new UsernamePasswordCredentials("user", "password"))
        client.toBlocking().exchange(request)

        then:
        embeddedServer.applicationContext.getBean(LoginSuccessfulEventListener).events.size() ==
                old(embeddedServer.applicationContext.getBean(LoginSuccessfulEventListener).events.size()) + 1
    }

    def "accessing a secured endpoints, validates Basic auth token and triggers TokenValidatedEvent"() {
        when:
        HttpRequest request = HttpRequest.GET("/health").basicAuth("user", "password")
        client.toBlocking().exchange(request)

        then:
        embeddedServer.applicationContext.getBean(TokenValidatedEventListener).events.size() ==
                old(embeddedServer.applicationContext.getBean(TokenValidatedEventListener).events.size()) + 1
    }

    def "invoking logout triggers LogoutEvent"() {
        when:
        HttpRequest request = HttpRequest.POST("/logout", "").basicAuth("user", "password")
        client.toBlocking().exchange(request)

        then:
        thrown(HttpClientResponseException)
        embeddedServer.applicationContext.getBean(LogoutEventListener).events.size() ==
                old(embeddedServer.applicationContext.getBean(LogoutEventListener).events.size()) + 1
        (embeddedServer.applicationContext.getBean(LogoutEventListener).events*.getSource() as List<Authentication>).find { it.name == 'user'}
    }

    @Requires(property = "spec.name", value = "eventlistener")
    @Singleton
    static class LoginSuccessfulEventListener implements ApplicationEventListener<LoginSuccessfulEvent> {
        List<LoginSuccessfulEvent> events = []
        @Override
        void onApplicationEvent(LoginSuccessfulEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "eventlistener")
    @Singleton
    static class LogoutEventListener implements ApplicationEventListener<LogoutEvent> {
        List<LogoutEvent> events = []

        @Override
        void onApplicationEvent(LogoutEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "eventlistener")
    @Singleton
    static class LoginFailedEventListener implements ApplicationEventListener<LoginFailedEvent> {
        List<LoginFailedEvent> events = []
        @Override
        void onApplicationEvent(LoginFailedEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "eventlistener")
    @Singleton
    static class TokenValidatedEventListener implements ApplicationEventListener<TokenValidatedEvent> {
        List<TokenValidatedEvent> events = []
        @Override
        void onApplicationEvent(TokenValidatedEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "eventlistener")
    @Singleton
    static class LogoutFailedEventListener implements ApplicationEventListener<LogoutEvent> {
        List<LogoutEvent> events = []
        @Override
        void onApplicationEvent(LogoutEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "eventlistener")
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

    @Requires(property = "spec.name", value = "eventlistener")
    @Singleton
    static class CustomLoginHandler implements LoginHandler {

        @Override
        HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
            HttpResponse.ok()
        }

        @Override
        HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
            HttpResponse.unauthorized()
        }
    }
}