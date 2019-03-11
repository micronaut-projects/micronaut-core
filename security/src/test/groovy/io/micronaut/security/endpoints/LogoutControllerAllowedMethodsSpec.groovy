/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.security.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.handlers.LogoutHandler
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton

class LogoutControllerAllowedMethodsSpec extends Specification {

    @Shared
    Map<String, Object> config = [
            'spec.name': 'logoutcontrollerallowedmethodsspec',
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.logout.enabled': true,
    ]

    void "LogoutController does not accept GET requests by default"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config, Environment.TEST)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        client.toBlocking().exchange(HttpRequest.GET("/logout").basicAuth("user", "password"))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.METHOD_NOT_ALLOWED

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "LogoutController can accept GET requests if micronaut.security.endpoints.logout.get-allowed=true"() {
        given:
        Map<String, Object> m = new HashMap<>()
        m.putAll(config)
        m.put('micronaut.security.endpoints.logout.get-allowed', true)

        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, m, Environment.TEST)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        client.toBlocking().exchange(HttpRequest.GET("/logout").basicAuth("user", "password"))

        then:
        noExceptionThrown()

        cleanup:
        client.close()
        embeddedServer.close()
    }

    @Requires(property = 'spec.name', value = 'logoutcontrollerallowedmethodsspec')
    @Singleton
    static class CustomLogoutHandler implements LogoutHandler {
        @Override
        HttpResponse logout(HttpRequest<?> request) {
            return HttpResponse.ok()
        }
    }

    @Requires(property = 'spec.name', value = 'logoutcontrollerallowedmethodsspec')
    @Singleton
    static class CustomAuthenticationProvider implements AuthenticationProvider {

        @Override
        Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
            return Flowable.just(new UserDetails("user", []))
        }
    }
}
