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
package io.micronaut.management.endpoint.info

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.Specification
import javax.inject.Singleton

class InfoEndpointSpec extends Specification {

    void "test the info endpoint returns expected result"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false, 'info.test': 'foo'], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().test == "foo"

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }


    void "test ordering of info sources"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false, 'info.ordered': 'second'], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().ordered == "first"

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }

    void "test info sources"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockingFirst()

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
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.info.sensitive': false, 'endpoints.info.git.location': 'othergit.properties'], 'test')
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/info"), Map).blockingFirst()

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
            return Flowable.just(new MapPropertySource("foo", [ordered: 'first']))
        }

        @Override
        int getOrder() {
            return HIGHEST_PRECEDENCE
        }
    }

}

