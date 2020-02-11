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

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.Specification

class BasicAuthSpec extends Specification {

    def "basicAuth() sets Authorization Header with Basic base64(username:password)"() {
        when:
        // tag::basicAuth[]
        HttpRequest request = HttpRequest.GET("/home").basicAuth('sherlock', 'password')
        // end::basicAuth[]

        then:
        request.headers.get('Authorization')
        request.headers.get('Authorization') == "Basic ${'sherlock:password'.bytes.encodeBase64().toString()}"
    }

    void "test user in absolute URL"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        ApplicationContext ctx = server.getApplicationContext()

        def httpClient = ctx.createBean(RxHttpClient, new URL("http://sherlock:password@localhost:${server.port}"))
        def client = httpClient.retrieve("/basic-auth")
        when:
        String resp = client.blockingFirst()

        then:
        resp == "sherlock:password"

        cleanup:
        httpClient.close()
        ctx.close()
    }
}
