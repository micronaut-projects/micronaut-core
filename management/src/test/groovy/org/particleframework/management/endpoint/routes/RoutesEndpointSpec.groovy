/*
 * Copyright 2017 original authors
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
package org.particleframework.management.endpoint.routes

import groovy.json.JsonSlurper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.particleframework.context.ApplicationContext
import org.particleframework.context.annotation.Requires
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Controller
import org.particleframework.runtime.server.EmbeddedServer
import org.particleframework.web.router.annotation.Get
import org.particleframework.web.router.annotation.Post
import org.particleframework.web.router.annotation.Put
import spock.lang.Specification

/**
 * @author James Kleeh
 * @since 1.0
 */
class RoutesEndpointSpec extends Specification {

    void "test routes endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': getClass().simpleName])
        OkHttpClient client = new OkHttpClient()

        when:
        def response = client.newCall(new Request.Builder().url(new URL(embeddedServer.getURL(), "/routes")).build()).execute()
        def result = new JsonSlurper().parseText(response.body().string())

        then:
        response.code() == HttpStatus.OK.code
        result['{[/refresh],method=[POST],produces=[application/json]}']['method'] == "[Ljava.lang.String; org.particleframework.management.endpoint.refresh.RefreshEndpoint.refresh()"
        result['{[/test],method=[GET],produces=[application/json]}']['method'] == "java.lang.String org.particleframework.management.endpoint.routes.RoutesEndpointSpec\$TestController.index()"
        result['{[/test/generics],method=[PUT],produces=[application/json]}']['method'] == "java.util.Map<java.lang.Integer, java.lang.String> org.particleframework.management.endpoint.routes.RoutesEndpointSpec\$TestController.generics()"
        result['{[/routes],method=[GET],produces=[application/json]}']['method'] == "org.reactivestreams.Publisher org.particleframework.management.endpoint.routes.RoutesEndpoint.getRoutes()"
        result['{[/test/post],method=[POST],produces=[application/json]}']['method'] == "org.particleframework.http.HttpResponse org.particleframework.management.endpoint.routes.RoutesEndpointSpec\$TestController.post(java.lang.Integer number, java.lang.String text)"

        cleanup:
        embeddedServer.close()
    }

    @Controller("/test")
    @Requires(property = 'spec.name', value = 'RoutesEndpointSpec')
    static class TestController {

        @Get
        String index() {
            ""
        }

        @Post
        HttpResponse post(Integer number, String text) {
            HttpResponse.ok()
        }

        @Put
        Map<String, Integer> generics() {
            Collections.emptyMap()
        }
    }
}
