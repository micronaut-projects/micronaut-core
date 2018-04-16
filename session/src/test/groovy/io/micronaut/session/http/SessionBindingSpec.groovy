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
package io.micronaut.session.http

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.session.Session
import io.micronaut.session.annotation.SessionValue
import io.reactivex.Flowable
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SessionBindingSpec extends Specification {

    void "test bind simple session argument using HTTP header processing"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/sessiontest/simple"), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.getBody().get() == "not in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)

        when:
        def sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO)
        flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/sessiontest/simple")
                        .header(HttpHeaders.AUTHORIZATION_INFO, sessionId)
                , String
        ))
        response = flowable.blockingFirst()

        then:
        response.getBody().get() == "value in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)

        cleanup:
        embeddedServer.stop()
    }


    void "test bind simple session argument using Cookie processing"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/sessiontest/simple"), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.getBody().get() == "not in session"
        response.header(HttpHeaders.SET_COOKIE)

        when:
        def sessionId = response.header(HttpHeaders.SET_COOKIE)
        flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/sessiontest/simple")
                        .header(HttpHeaders.COOKIE, sessionId)
                , String
        ))
        response = flowable.blockingFirst()

        then:
        response.getBody().get() == "value in session"
        response.header(HttpHeaders.SET_COOKIE)

        cleanup:
        embeddedServer.stop()
    }

    void "test bind optional session"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/sessiontest/optional"), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.getBody().get() == "no session"
        !response.header(HttpHeaders.AUTHORIZATION_INFO)

        when:
        flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/sessiontest/simple"), String
        ))
        response = flowable.blockingFirst()

        then:
        response.getBody().get() == "not in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)

        when:
        def sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO)

        flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/sessiontest/optional")
                        .header(HttpHeaders.AUTHORIZATION_INFO, sessionId)
                , String
        ))
        response = flowable.blockingFirst()

        then:
        response.getBody().get() == "value in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)

        when:
        flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/sessiontest/value")
                        .header(HttpHeaders.AUTHORIZATION_INFO, sessionId)
                , String
        ))
        response = flowable.blockingFirst()

        then:
        response.getBody().get() == "value in session"
        response.header(HttpHeaders.AUTHORIZATION_INFO)

        cleanup:
        embeddedServer.stop()
    }

    @Controller('/sessiontest')
    @Singleton
    static class SessionController {

        @Get
        String simple(Session session) {
            return session.get("myValue").orElseGet({
                session.put("myValue", "value in session")
                "not in session"
            })
        }

        @Get
        String value(@SessionValue Optional<String> myValue) {
            return myValue.orElse(
                "no value in session"
            )
        }


        @Get
        String optional(Optional<Session> session) {
            if(session.isPresent()) {
                def s = session.get()
                return s.get("myValue").orElseGet({
                    s.put("myValue", "value in session")
                    "not in session"
                })
            }
            else {
                return "no session"
            }
        }
    }
}


