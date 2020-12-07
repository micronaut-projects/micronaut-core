/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jackson.deserialize

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class StringTrimModuleSpec extends Specification {

    def "test trim strings on deserialize with property enabled"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName,
                'jackson.trim-strings': true
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        String json = '{"id":1, "email":" abc ","name":" Micronaut ", "city":" ", "active":true, "createdAt":"2020-01-31"}'
        HttpRequest httpRequest = HttpRequest.POST("/trim/pojo", json).contentType(MediaType.APPLICATION_JSON_TYPE)
        HttpResponse<Map> response = client.toBlocking().exchange(httpRequest, Map)

        then:
        noExceptionThrown()
        response.status() == HttpStatus.OK

        when:
        Map m = response.getBody().get()

        then:
        m.id == 1
        m.email == 'abc'
        m.name == 'Micronaut'
        m.city == null
        m.active == true

        cleanup:
        server.close()
    }

    def "test don't trim strings on deserialize with property disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName,
                'jackson.trim-strings': false
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

        when:
        String json = '{"id":1, "email":" abc ","name":" Micronaut ", "city":" ", "active":true, "createdAt":"2020-01-31"}'
        HttpRequest httpRequest = HttpRequest.POST("/trim/pojo", json).contentType(MediaType.APPLICATION_JSON_TYPE)
        HttpResponse<Map> response = client.toBlocking().exchange(httpRequest, Map)

        then:
        noExceptionThrown()
        response.status() == HttpStatus.OK

        when:
        Map m = response.getBody().get()

        then:
        m.id == 1
        m.email == ' abc '
        m.name == ' Micronaut '
        m.city == ' '
        m.active == true

        cleanup:
        server.close()
    }
}
