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
package io.micronaut.http.server.netty.consumes

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.netty.AbstractMicronautSpec

import static io.micronaut.http.MediaType.*

class ConsumesMatchesRouteSpec extends AbstractMicronautSpec {

    void "test routes are filtered by consumes"() {
        when:
        String body = rxClient.retrieve(HttpRequest.POST("/test-consumes", [x: 1]).contentType(APPLICATION_JSON_TYPE)).blockingFirst()

        then:
        noExceptionThrown()
        body == "json"

        when:
        body = rxClient.retrieve(HttpRequest.POST("/test-consumes", "abc").contentType(APPLICATION_GRAPHQL_TYPE)).blockingFirst()

        then:
        noExceptionThrown()
        body == "graphql"

        when:
        URL url1 = embeddedServer.getURL()
        URL url = new URL(url1.getProtocol(), url1.getHost(), url1.getPort(), url1.getFile() + "/test-consumes", null)
        URLConnection connection = url.openConnection()
        connection.setRequestMethod("POST")
        connection.setRequestProperty('Content-Type', TEXT_PLAIN_TYPE.getName())
        connection.doOutput = true

        def writer = new OutputStreamWriter(connection.outputStream)
        writer.write("abc")
        writer.flush()
        writer.close()
        connection.connect()

        body = connection.content.text

        then:
        noExceptionThrown()
        body == "all"
    }

    void "test routes are not filtered by content-type when consumes=all"() {
        when:
        String body = rxClient.retrieve(HttpRequest.POST("/test-consumes-all", "true").contentType(APPLICATION_JSON_TYPE)).blockingFirst()

        then:
        noExceptionThrown()
        body == "all:true"

        when:
        body = rxClient.retrieve(HttpRequest.POST("/test-consumes-all", "graphql").contentType(APPLICATION_GRAPHQL_TYPE)).blockingFirst()

        then:
        noExceptionThrown()
        body == "all:graphql"

        when:
        URL url1 = embeddedServer.getURL()
        URL url = new URL(url1.getProtocol(), url1.getHost(), url1.getPort(), url1.getFile() + "/test-consumes-all", null)
        URLConnection connection = url.openConnection()
        connection.setRequestMethod("POST")
        connection.setRequestProperty('Content-Type', TEXT_PLAIN_TYPE.getName())
        connection.doOutput = true

        def writer = new OutputStreamWriter(connection.outputStream)
        writer.write("abc")
        writer.flush()
        writer.close()
        connection.connect()

        body = connection.content.text

        then:
        noExceptionThrown()
        body == "all:abc"
    }

    void "test accept matching has priority over route complexity"() {
        when:
        String body = rxClient.retrieve(HttpRequest.POST("/test-accept/foo", [x: 1]).contentType(APPLICATION_JSON_TYPE)).blockingFirst()

        then:
        noExceptionThrown()
        body == "json"
    }

    @Requires(property = "spec.name", value = "ConsumesMatchesRouteSpec")
    @Controller("/test-consumes")
    static class MyController  {

        @Post(consumes = APPLICATION_JSON)
        HttpResponse posta(@Body String body) {
            HttpResponse.ok("json")
        }

        @Post(consumes = APPLICATION_GRAPHQL)
        HttpResponse postb(@Body String body) {
            HttpResponse.ok("graphql")
        }

        @Post(consumes = ALL)
        HttpResponse postc(@Body String body) {
            HttpResponse.ok("all")
        }
    }

    @Requires(property = "spec.name", value = "ConsumesMatchesRouteSpec")
    @Controller("/test-consumes-all")
    static class MyControllerForAll  {

        @Post(consumes = ALL)
        HttpResponse posta(@Body String body) {
            HttpResponse.ok("all:" + body)
        }
    }

    @Requires(property = "spec.name", value = "ConsumesMatchesRouteSpec")
    @Controller("/test-accept")
    static class MyOtherController  {

        //If the content type is json, this method should be chosen even for /foo
        @Post(uri = "/{name}", consumes = APPLICATION_JSON)
        HttpResponse posta(@Body String body) {
            HttpResponse.ok("json")
        }

        @Post(uri = "/foo", consumes = ALL)
        HttpResponse postc(@Body String body) {
            HttpResponse.ok("all")
        }
    }
}
