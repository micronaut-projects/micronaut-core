/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.events

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
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
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton

class EventListenerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': "io.micronaut.security.events.EventListenerSpec",
            'endpoints.beans.enabled': true,
            'endpoints.beans.sensitive': true,
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login.enabled': true,
            'micronaut.security.endpoints.logout.enabled': true,
    ], Environment.TEST)
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "failed login publishes LoginFailedEvent"() {
        when:
        println "sending request to login with bogus/password"
        HttpRequest request = HttpRequest.POST("/login", new UsernamePasswordCredentials("bogus", "password"))
        client.toBlocking().exchange(request)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
        new PollingConditions().eventually {
            embeddedServer.applicationContext.getBean(LoginFailedEventListener).events.size() == 1
        }
    }

    def "successful login publishes LoginSuccessfulEvent"() {
        when:
        println "sending request to login with user/password"
        HttpRequest request = HttpRequest.POST("/login", new UsernamePasswordCredentials("user", "password"))
        client.toBlocking().exchange(request)

        then:
        new PollingConditions().eventually {
            embeddedServer.applicationContext.getBean(LoginSuccessfulEventListener).events.size() == 1
        }
    }

    def "accessing a secured endpoints, validates Basic auth token and triggers TokenValidatedEvent"() {
        when:
        HttpRequest request = HttpRequest.GET("/beans").basicAuth("user", "password")
        client.toBlocking().exchange(request)

        then:
        new PollingConditions().eventually {
            embeddedServer.applicationContext.getBean(TokenValidatedEventListener).events.size() == 1
        }
    }

    def "invoking logout triggers LogoutEvent"() {
        when:
        HttpRequest request = HttpRequest.POST("/logout", "").basicAuth("user", "password")
        client.toBlocking().exchange(request)

        then:
        thrown(HttpClientResponseException)
        new PollingConditions().eventually {
            embeddedServer.applicationContext.getBean(LogoutEventListener).events.size() == 1
            (embeddedServer.applicationContext.getBean(LogoutEventListener).events*.getSource() as List<Authentication>).any { it.name == 'user'}
        }
    }

    @Requires(property = "spec.name", value = "io.micronaut.security.events.EventListenerSpec")
    @Singleton
    static class LoginSuccessfulEventListener implements ApplicationEventListener<LoginSuccessfulEvent> {
        List<LoginSuccessfulEvent> events = []
        @Override
        void onApplicationEvent(LoginSuccessfulEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "io.micronaut.security.events.EventListenerSpec")
    @Singleton
    static class LogoutEventListener implements ApplicationEventListener<LogoutEvent> {
        List<LogoutEvent> events = []

        @Override
        void onApplicationEvent(LogoutEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "io.micronaut.security.events.EventListenerSpec")
    @Singleton
    static class LoginFailedEventListener implements ApplicationEventListener<LoginFailedEvent> {
        volatile List<LoginFailedEvent> events = []
        @Override
        void onApplicationEvent(LoginFailedEvent event) {
            println "received login failed event"
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "io.micronaut.security.events.EventListenerSpec")
    @Singleton
    static class TokenValidatedEventListener implements ApplicationEventListener<TokenValidatedEvent> {
        List<TokenValidatedEvent> events = []
        @Override
        void onApplicationEvent(TokenValidatedEvent event) {
            println "received token validated event"
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "io.micronaut.security.events.EventListenerSpec")
    @Singleton
    static class LogoutFailedEventListener implements ApplicationEventListener<LogoutEvent> {
        List<LogoutEvent> events = []
        @Override
        void onApplicationEvent(LogoutEvent event) {
            println "received logout event"
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "io.micronaut.security.events.EventListenerSpec")
    @Singleton
    static class CustomAuthenticationProvider implements AuthenticationProvider {

        @Override
        Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
            System.out.println(authenticationRequest.identity)
            System.out.println(authenticationRequest.secret)
            if ( authenticationRequest.identity == 'user' && authenticationRequest.secret == 'password' ) {
                System.out.println("returning a new user details")
                return Flowable.just(new UserDetails('user', []))
            }
            System.out.println("returning authentication failed")
            return Flowable.just(new AuthenticationFailed())
        }
    }

    @Requires(property = "spec.name", value = "io.micronaut.security.events.EventListenerSpec")
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