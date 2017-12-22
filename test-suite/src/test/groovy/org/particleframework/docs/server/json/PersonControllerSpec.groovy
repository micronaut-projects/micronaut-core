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
package org.particleframework.docs.server.json

import groovy.json.JsonSlurper
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpStatus
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Stepwise
class PersonControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer)

    void "test global error handler"() {
        given:
        // TODO: Replace with Particle HTTP client when written
        OkHttpClient client = new OkHttpClient()
        Request.Builder request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/people/error"))

        when:
        Response response = client.newCall(request.build()).execute()


        then:
        response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code

        when:
        def json = new JsonSlurper().parseText(response.body().string())

        then:
        json.message == 'Bad Things Happened: Something went wrong'
    }

    void "test save person"() {
        given:
        // TODO: Replace with Particle HTTP client when written
        OkHttpClient client = new OkHttpClient()
        String body = '{"firstName":"Fred","lastName":"Flintstone","age":45}'
        Request.Builder request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/people"))
                .post(RequestBody.create(MediaType.parse(org.particleframework.http.MediaType.APPLICATION_JSON), body))// <2>
        Response response = client.newCall(request.build()).execute()


        expect:
        response.body().string() == body // <2>
        response.code() == HttpStatus.CREATED.code
    }

    void "test retrieve person"() {
        given:
        // TODO: Replace with Particle HTTP client when written
        OkHttpClient client = new OkHttpClient()

        when:
        Request.Builder request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/people/Fred"))

        Response response = client.newCall(request.build()).execute()


        then:
        response.code() == HttpStatus.OK.code

        when:
        def json = new JsonSlurper().parseText(response.body().string())

        then:
        json.firstName == "Fred"

        when:
        request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/people/Barney"))

        response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.NOT_FOUND.code

    }


}
