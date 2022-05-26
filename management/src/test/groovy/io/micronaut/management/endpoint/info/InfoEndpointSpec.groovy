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
package io.micronaut.management.endpoint.info

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

class InfoEndpointSpec extends Specification {

    void "test the info endpoint returns expected result"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false, 'info.test': 'foo'], Environment.TEST)
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().test == "foo"

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }


    void "test ordering of info sources"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false, 'info.ordered': 'second'], Environment.TEST)
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().ordered == "first"

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }

    void "test info sources"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false], Environment.TEST)
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockFirst()

        then: "git info is returned"
        response.code() == HttpStatus.OK.code
        response.body().git.branch == "master"
        response.body().git.commit.id == "97ef2a6753e1ce4999a19779a62368bbca997e53"
        response.body().git.commit.time == "1522546237"

        and: "build info is returned"
        response.code() == HttpStatus.OK.code
        response.body().build.artifact == "test"
        response.body().build.group == "io.micronaut"
        response.body().build.name == "test"

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }


    void "test the git endpoint with alternate location"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false, 'endpoints.info.git.location': 'othergit.properties'], Environment.TEST)
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().git.branch == "master2"

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }


    @Singleton
    static class TestingInfoSource implements InfoSource {

        @Override
        Publisher<PropertySource> getSource() {
            return Flux.just(new MapPropertySource("foo", [ordered: 'first']))
        }

        @Override
        int getOrder() {
            return HIGHEST_PRECEDENCE
        }
    }

}

