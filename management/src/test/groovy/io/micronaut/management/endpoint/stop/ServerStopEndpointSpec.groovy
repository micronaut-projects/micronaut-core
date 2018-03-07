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
package io.micronaut.management.endpoint.stop

import io.micronaut.context.ApplicationContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author James Kleeh
 * @since 1.0
 */
class ServerStopEndpointSpec extends Specification {

    void "test the endpoint is disabled by default"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, 'test')

        OkHttpClient client = new OkHttpClient()

        when:
        def response = client.newCall(new Request.Builder().url(new URL(embeddedServer.getURL(), "/stop")).build()).execute()

        then:
        response.code() == HttpStatus.NOT_FOUND.code
    }

    void "test the server is stopped after exercising the endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.stop.enabled': true], 'test')
        OkHttpClient client = new OkHttpClient()
        def conditions = new PollingConditions(timeout: 10, initialDelay: 3, delay: 1, factor: 1)

        when:
        def response = client.newCall(new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/stop"))
                .post(RequestBody.create(MediaType.parse("text/plain"), "")).build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == '{"message":"Server shutdown started"}'
        conditions.eventually {
            assert !embeddedServer.isRunning()
        }
    }
}
