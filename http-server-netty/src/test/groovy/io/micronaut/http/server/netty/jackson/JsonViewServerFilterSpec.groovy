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
package io.micronaut.http.server.netty.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JsonViewServerFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'jackson.json-view.enabled': true
            ],
            "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())


    def "invoking /jsonview/none does not specify @JsonView, thus, all properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/none', TestModel)

        then:
        rsp.body() == JsonViewController.TEST_MODEL
    }

    def "invoking /jsonview/public specifies 'public' @JsonView, thus, only public properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/public', TestModel)

        then:
        rsp.body().firstName == JsonViewController.TEST_MODEL.firstName
        rsp.body().lastName == JsonViewController.TEST_MODEL.lastName
        rsp.body().birthdate == null
        rsp.body().password == null
    }

    def "invoking /jsonview/reactive specifies 'public' @JsonView, thus, only public properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/reactive', TestModel)

        then:
        rsp.body().firstName == JsonViewController.TEST_MODEL.firstName
        rsp.body().lastName == JsonViewController.TEST_MODEL.lastName
        rsp.body().birthdate == null
        rsp.body().password == null
    }


    def "invoking /jsonview/reactive/single specifies 'public' @JsonView, thus, only public properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/reactive/single', TestModel)

        then:
        rsp.body().firstName == JsonViewController.TEST_MODEL.firstName
        rsp.body().lastName == JsonViewController.TEST_MODEL.lastName
        rsp.body().birthdate == null
        rsp.body().password == null
    }

    def "invoking /jsonview/reactive/multiple specifies 'public' @JsonView, thus, only public properties are returned"() {
        when:
        List<TestModel> rsp = client.toBlocking().retrieve(HttpRequest.GET('/jsonview/reactive/multiple'), Argument.listOf(TestModel))

        then:
        rsp.get(0).firstName == JsonViewController.TEST_MODEL.firstName
        rsp.get(0).lastName == JsonViewController.TEST_MODEL.lastName
        rsp.get(0).birthdate == null
        rsp.get(0).password == null
        rsp.get(1).firstName == JsonViewController.TEST_MODEL.firstName
        rsp.get(1).lastName == JsonViewController.TEST_MODEL.lastName
        rsp.get(1).birthdate == null
        rsp.get(1).password == null
    }

    def "invoking /jsonview/internal specifies 'internal' @JsonView, thus, only internal properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/internal', TestModel)

        then:
        rsp.body().firstName == JsonViewController.TEST_MODEL.firstName
        rsp.body().lastName == JsonViewController.TEST_MODEL.lastName
        rsp.body().birthdate == JsonViewController.TEST_MODEL.birthdate
        rsp.body().password == null
    }

    def "invoking /jsonview/admin specifies 'admin' @JsonView, thus, admin (all) properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/admin', TestModel)

        then:
        rsp.body() == JsonViewController.TEST_MODEL
    }
}
