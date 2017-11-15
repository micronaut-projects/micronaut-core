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
package org.particleframework.management.endpoint

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.particleframework.context.ApplicationContext
import org.particleframework.core.util.Toggleable
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Body
import org.particleframework.runtime.ParticleApplication
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SimpleEndpointSpec extends Specification {

    void "test read simple endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.simple.myValue':'foo']
        )

        OkHttpClient client = new OkHttpClient()

        when:
        def request = new Request.Builder()
                .url( new URL(server.URL, "/simple"))

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'test foo'

        cleanup:
        server.close()
    }

    void "test read simple endpoint with argument"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.simple.myValue':'foo']
        )


        OkHttpClient client = new OkHttpClient()

        when:
        def request = new Request.Builder()
                .url(new URL(server.URL, "/simple/baz"))

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'test baz'

        cleanup:
        server.close()
    }

    void "test write simple endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.simple.myValue':'foo']
        )

        OkHttpClient client = new OkHttpClient()

        when:
        def request = new Request.Builder()
                .url(new URL(server.URL, "/simple"))
                .post(RequestBody.create(MediaType.parse("text/plain"), "bar"))
        def response = client.newCall(
                request.build()
        ).execute()


        then:
        response.code() == HttpStatus.OK.code

        when:
        request = new Request.Builder()
                .url(new URL(server.URL, "/simple"))

        response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'test bar'

        cleanup:
        server.close()
    }

    void "test disable endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.simple.enabled':false]
        )


        OkHttpClient client = new OkHttpClient()

        when:
        def request = new Request.Builder()
                .url(new URL(server.URL, "/simple"))

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.NOT_FOUND.code

        cleanup:
        server.close()
    }
}

@Endpoint('simple')
class Simple implements Toggleable {

    private final ApplicationContext applicationContext

    Simple(ApplicationContext applicationContext) {
        assert  applicationContext != null
        this.applicationContext = applicationContext
    }
    boolean enabled = true
    String myValue

    @Read
    String value() {
        "test $myValue"
    }

    @Read
    String named(String name) {
        "test $name"
    }

    @Write(consumes = 'text/plain')
    void value(@Body String val) {
        this.myValue = val
    }
}
