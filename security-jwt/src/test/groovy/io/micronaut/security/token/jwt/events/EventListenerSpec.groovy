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
package io.micronaut.security.token.jwt.events

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
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
            'spec.name': "io.micronaut.security.token.jwt.events.EventListenerSpec",
            'endpoints.beans.enabled': true,
            'endpoints.beans.sensitive': true,
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.signatures.secret.generator.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            'micronaut.security.endpoints.login.enabled': true,
    ], Environment.TEST)
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

    @Requires(property = "spec.name", value = "io.micronaut.security.token.jwt.events.EventListenerSpec")
    @Singleton
    static class RefreshTokenGeneratedEventListener implements ApplicationEventListener<RefreshTokenGeneratedEvent> {
        List<RefreshTokenGeneratedEvent> events = []
        @Override
        void onApplicationEvent(RefreshTokenGeneratedEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "io.micronaut.security.token.jwt.events.EventListenerSpec")
    @Singleton
    static class AccessTokenGeneratedEventListener implements ApplicationEventListener<AccessTokenGeneratedEvent> {
        List<AccessTokenGeneratedEvent> events = []
        @Override
        void onApplicationEvent(AccessTokenGeneratedEvent event) {
            events.add(event)
        }
    }

    @Requires(property = "spec.name", value = "io.micronaut.security.token.jwt.events.EventListenerSpec")
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