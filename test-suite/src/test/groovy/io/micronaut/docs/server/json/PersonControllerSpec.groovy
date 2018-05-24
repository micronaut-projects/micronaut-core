/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.docs.server.json

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
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

    @Shared @AutoCleanup RxHttpClient client = RxHttpClient.create(embeddedServer.URL)

    void "test global error handler"() {

        when:
        client.exchange("/people/error", Map.class)
              .blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.INTERNAL_SERVER_ERROR

        when:
        def json = response.body.orElse(null)

        then:
        json
        json.message == 'Bad Things Happened: Something went wrong'
    }

    void "test save person"() {
        given:
        String body = '{"firstName":"Fred","lastName":"Flintstone","age":45}'
        HttpResponse<String> response = client.exchange(HttpRequest.POST('/people', body), String)
                                              .blockingFirst()

        expect:
        response.body.isPresent()
        response.body.get() == body
        response.status == HttpStatus.CREATED
    }



    void "test retrieve person"() {

        when:
        HttpResponse<Map> response = client.exchange('/people/Fred', Map).blockingFirst()

        then:
        response.status == HttpStatus.OK

        when:
        def json = response.body
                        .orElseThrow({-> new AssertionError("body expected")})

        then:
        json.firstName == "Fred"

        when:
        client.exchange('/people/Barney')
              .blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.NOT_FOUND

    }

    void "test save person with args"() {
        given:
        String body = '{"firstName":"Fred","lastName":"Flintstone","age":45}'
        HttpResponse<String> response = client.exchange(
                HttpRequest.POST("/people/saveWithArgs", body), String
        ).blockingFirst()

        expect:
        response.body.isPresent()
        response.body.get() == body
        response.status == HttpStatus.CREATED
    }
}
