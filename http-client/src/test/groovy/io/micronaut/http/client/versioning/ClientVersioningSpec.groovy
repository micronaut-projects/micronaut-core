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
package io.micronaut.http.client.versioning

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.PropertySource
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ClientVersioningSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer,[
            'spec.name':  'ClientVersioningSpec',
            "micronaut.http.client.versioning./.headers"         : ['X-API-VERSION'],
            "micronaut.http.client.versioning./.parameters"      : ['api-version'],
            "micronaut.http.client.versioning.default.parameters": ['version'],
    ])

    def "should has 'api-version' parameter inside client request"() {
        when:
        def client = server.applicationContext.getBean(VersionedClient)
        def response = client.withParameter()
        then:
        response == '1'
    }

    def "should has 'X-API-VERSION' header inside client request"() {
        when:
        def client = server.applicationContext.getBean(VersionedClient)
        def response = client.withHeader()
        then:
        response == '1'
    }

    def "should has 'X-API-VERSION' header from class level '@Version'"() {
        when:
        def client = server.applicationContext.getBean(VersionedClient)
        def response = client.overrideWithClass()
        then:
        response == '0'
    }

    def "should populate with method level '@Version'"() {
        when:
        def client = server.applicationContext.getBean(VersionedClient)
        def response = client.overrideWithClass()
        then:
        response == '0'
    }

    def "should fallback to default configuration"() {
        when:
        def client = server.applicationContext.getBean(DefaulConfVersionedClient)
        def response = client.request1()
        then:
        response == '1'
    }

    def "should ignore empty version string"() {
        when:
        def client = server.applicationContext.getBean(VersionedClient)
        def response = client.withoutVersion()
        then:
        response == null
    }

    @Requires(property = 'spec.name', value = 'ClientVersioningSpec')
    @Version("0")
    @Client("/")
    static interface VersionedClient {

        @Version("1")
        @Get("param")
        String withParameter();

        @Version("1")
        @Get("header")
        String withHeader();

        @Get("override")
        String overrideWithClass();

        @Version("")
        @Get("empty")
        String withoutVersion();

    }

    @Requires(property = 'spec.name', value = 'ClientVersioningSpec')
    @Client("/notconfigured")
    static interface DefaulConfVersionedClient {

        @Version("1")
        @Get()
        String request1();

        @Version("2")
        @Get()
        String request2();
    }

    @Requires(property = 'spec.name', value = 'ClientVersioningSpec')
    @Controller("/")
    static class TestController {

        @Get("param")
        String param(HttpRequest<?> request) {
            return request.parameters.get("api-version")
        }

        @Get("header")
        String header(HttpRequest<?> request) {
            return request.headers.get("X-API-VERSION")
        }

        @Get("override")
        String override(HttpRequest<?> request) {
            return request.headers.get("X-API-VERSION")
        }

        @Get("notconfigured")
        String notConfigured(HttpRequest<?> request) {
            return request.parameters.get("version")
        }

        @Get("empty")
        String empty(HttpRequest<?> request) {
            return request.parameters.get("version")
        }

    }

}