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
package io.micronaut.security

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.management.endpoint.health.HealthLevelOfDetail
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.reactivex.Flowable
import io.reactivex.annotations.NonNull
import io.reactivex.functions.Function
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Singleton

class HealthSensitivitySpec extends Specification {

    @Unroll
    void "If endpoints.health.sensitive=true #description => 401"(boolean security, String description) {
        given:
        Map m = [
                'spec.name'                            : 'healthsensitivity',
                'endpoints.health.enabled'             : true,
                'endpoints.health.disk-space.threshold': '9999GB',
                'endpoints.health.sensitive'           : true,
        ]
        if (security) {
            m['micronaut.security.enabled'] = security
        }

        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, m)
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        HttpRequest httpRequest = HttpRequest.GET("/health")
        rxClient.exchange(httpRequest, Map).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        cleanup:
        embeddedServer.close()
        rxClient.close()

        where:
        security << [true, false]
        description = security ? 'with security but unauthenticated' : 'without security'
    }

    @Unroll
    void "#description => #expected"(boolean sensitive, boolean security, boolean authenticated, HealthLevelOfDetail expected, String description) {
        given:
        Map m = [
                'spec.name'                            : 'healthsensitivity',
                'endpoints.health.enabled'             : true,
                'endpoints.health.sensitive'           : sensitive,
                'endpoints.health.disk-space.threshold': '9999GB',
        ]
        if (security) {
            m['micronaut.security.enabled'] = security
        }

        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, m)
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        HttpRequest httpRequest = HttpRequest.GET("/health")
        if (authenticated) {
            httpRequest = httpRequest.basicAuth("user", "password")
        }
        def response = rxClient.exchange(httpRequest, Map)
        .onErrorResumeNext(new Function<Throwable, Publisher<? extends HttpResponse<HealthResult>>>() {
            @Override
            Publisher<? extends HttpResponse<HealthResult>> apply(@NonNull Throwable throwable) throws Exception {

                def response = ((HttpClientResponseException) throwable).response
                response.getBody(Map)
                return Flowable.just(response)
            }
        }).blockingFirst()
        Map result = response.getBody(Map).get()

        then:
        response.code() == HttpStatus.SERVICE_UNAVAILABLE.code
        result.status == "DOWN"
        if (expected == HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS) {
            assert result.containsKey('details')
            assert result.details.diskSpace.status == "DOWN"
            assert result.details.diskSpace.details.error.startsWith("Free disk space below threshold.")
        } else {
            assert !result.containsKey('details')
        }

        cleanup:
        embeddedServer.close()
        rxClient.close()

        where:
        sensitive | security | authenticated | expected
        true      | true     | true          | HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS
        false     | true     | false         | HealthLevelOfDetail.STATUS
        false     | true     | true          | HealthLevelOfDetail.STATUS_DESCRIPTION_DETAILS
        false     | false    | false         | HealthLevelOfDetail.STATUS

        description = "endpoints.health.sensitive=${sensitive} " + (security ? 'micronaut.security.enabled=true ' + (authenticated ? 'authenticated' : 'not authenticated') : '')
    }

    void "test details visible AUTHENTICATED"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                            : 'healthsensitivity',
            'endpoints.health.enabled'             : true,
            'endpoints.health.sensitive'           : false,
            'endpoints.health.detailsVisible'      : 'AUTHENTICATED',
            'micronaut.security.enabled'           : true])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        HttpRequest httpRequest = HttpRequest.GET("/health")
        HttpResponse response = rxClient.exchange(httpRequest, Map).blockingFirst()
        Map result = response.body()

        then: // The details are not included because the user is not authenticated
        response.code() == HttpStatus.OK.code
        result.containsKey('status')
        !result.containsKey('details')


        when:
        httpRequest = HttpRequest.GET("/health").basicAuth("user", "password")
        response = rxClient.exchange(httpRequest, Map).blockingFirst()
        result = response.body()

        then: // The details are included because the user is authenticated
        response.code() == HttpStatus.OK.code
        result.containsKey('status')
        result.containsKey('details')

        cleanup:
        embeddedServer.close()
        rxClient.close()
    }

    void "test details visible ANONYMOUS"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                            : 'healthsensitivity',
                'endpoints.health.enabled'             : true,
                'endpoints.health.sensitive'           : false,
                'endpoints.health.detailsVisible'      : 'ANONYMOUS',
                'micronaut.security.enabled'           : true])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        HttpRequest httpRequest = HttpRequest.GET("/health")
        HttpResponse response = rxClient.exchange(httpRequest, Map).blockingFirst()
        Map result = response.body()

        then: // The details are included because detailsVisible is ANONYMOUS
        response.code() == HttpStatus.OK.code
        result.containsKey('status')
        result.containsKey('details')

        cleanup:
        embeddedServer.close()
        rxClient.close()
    }


    void "test details visible NEVER"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                            : 'healthsensitivity',
                'endpoints.health.enabled'             : true,
                'endpoints.health.sensitive'           : false,
                'endpoints.health.detailsVisible'      : 'NEVER',
                'micronaut.security.enabled'           : true])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        HttpRequest httpRequest = HttpRequest.GET("/health").basicAuth("user", "password")
        HttpResponse response = rxClient.exchange(httpRequest, Map).blockingFirst()
        Map result = response.body()

        then: // The details are not included because detailsVisible is NEVER
        response.code() == HttpStatus.OK.code
        result.containsKey('status')
        !result.containsKey('details')

        cleanup:
        embeddedServer.close()
        rxClient.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'healthsensitivity')
    static class AuthenticationProviderUserPassword implements AuthenticationProvider {

        @Override
        Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
            if (authenticationRequest.identity == 'user' && authenticationRequest.secret == 'password') {
                return Flowable.just(new UserDetails('user', []))
            }
            return Flowable.just(new AuthenticationFailed())
        }
    }
}
