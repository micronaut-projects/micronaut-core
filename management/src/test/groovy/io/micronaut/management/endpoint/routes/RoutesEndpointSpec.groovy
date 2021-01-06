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
package io.micronaut.management.endpoint.routes

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * @author James Kleeh
 * @since 1.0
 */
class RoutesEndpointSpec extends Specification {

    void "test routes endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': getClass().simpleName, 'endpoints.routes.sensitive': false], "test")
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange("/routes", Map).blockingFirst()
        def result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        result['{[/refresh],method=[POST],produces=[application/json]}']['method'] == "[Ljava.lang.String; io.micronaut.management.endpoint.refresh.RefreshEndpoint.refresh(java.lang.Boolean force)"
        result['{[/test],method=[GET],produces=[application/json]}']['method'] == "java.lang.String io.micronaut.management.endpoint.routes.RoutesEndpointSpec\$TestController.index()"
        result['{[/test/generics],method=[PUT],produces=[application/json]}']['method'] == "java.util.Map<java.lang.String, java.lang.Integer> io.micronaut.management.endpoint.routes.RoutesEndpointSpec\$TestController.generics()"
        result['{[/routes],method=[GET],produces=[application/json]}']['method'] == "io.reactivex.Single<java.lang.Object> io.micronaut.management.endpoint.routes.RoutesEndpoint.getRoutes()"
        result['{[/test/post],method=[POST],produces=[application/json]}']['method'] == "io.micronaut.http.HttpResponse<java.lang.Object> io.micronaut.management.endpoint.routes.RoutesEndpointSpec\$TestController.post(java.lang.Integer number, java.lang.String text)"

        cleanup:
        rxClient.close()
        embeddedServer?.close()
    }

    @Controller("/test")
    @Requires(property = 'spec.name', value = 'RoutesEndpointSpec')
    static class TestController {

        @Get
        String index() {
            ""
        }

        @Post("/post")
        HttpResponse post(Integer number, String text) {
            HttpResponse.ok()
        }

        @Put("/generics")
        Map<String, Integer> generics() {
            Collections.emptyMap()
        }
    }
}
