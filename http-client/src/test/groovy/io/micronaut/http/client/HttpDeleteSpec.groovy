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
package io.micronaut.http.client

import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.http.annotation.Delete
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

/**
 * TODO: Javadoc description
 *
 * @author graemerocher
 * @since 1.0
 */
@MicronautTest
class HttpDeleteSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    MyDeleteClient myDeleteClient

    void "test http delete"() {
        when:
        def res = Flowable.fromPublisher(client.exchange(HttpRequest.DELETE('/delete/simple'))).blockingFirst()

        then:
        res.status == HttpStatus.NO_CONTENT
    }

    void "test http delete with blocking client"() {
        when:
        def res = Flowable.fromPublisher(client.exchange(HttpRequest.DELETE('/delete/simple'))).blockingFirst()

        then:
        res.status == HttpStatus.NO_CONTENT
    }

    void "test http delete with body"() {
        when:
        def res = Flowable.fromPublisher(client.exchange(
                HttpRequest.DELETE('/delete/body', 'test')
                           .contentType(MediaType.TEXT_PLAIN) , String)).blockingFirst()
        def body = res.body
        then:
        res.status == HttpStatus.ACCEPTED
        body.isPresent()
        body.get() == 'test'
    }

    void "test multiple uris"() {
        def client = myDeleteClient

        when:
        String val = client.multiple()

        then:
        val == "multiple mappings"

        when:
        val = client.multipleMappings()

        then:
        val == "multiple mappings"
    }

    void "test delete annotation with HttpRequest<Void> injection"() {
        def client = embeddedServer.applicationContext.getBean(MyDeleteClient)

        when:
        String val = client.voidRequest()

        then:
        val == "ok"
    }

    @Controller("/delete")
    static class DeleteController {

        @Delete("/simple")
        HttpResponse simple() {
            HttpResponse.noContent()
        }

        @Delete(value = "/body", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        HttpResponse simple(@Body String content) {

            HttpResponse.accepted()
                        .body(content)
        }

        @Delete(uris = ["/multiple", "/multiple/mappings"])
        String multipleMappings() {
            return "multiple mappings"
        }

        @Delete("/void")
        String voidRequest(HttpRequest<Void> req) {
            "ok"
        }
    }

    @Client("/delete")
    static interface MyDeleteClient {

        @Delete("/multiple")
        String multiple()

        @Delete("/multiple/mappings")
        String multipleMappings()

        @Delete("/void")
        String voidRequest()
    }
}
