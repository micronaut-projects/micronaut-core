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

package io.micronaut.validation.validator.pojo

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.Nullable
import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

class PojoBodyParameterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                                                           ['spec.name': 'customValidatorPOJO'],
                                                           Environment.TEST)
    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient, embeddedServer.getURL())

    void 'test custom constraints in a Pojo are taken into account when it is used as a controller parameter'() {
        given:
        HttpRequest req = HttpRequest.POST("/search/", new Search())

        when:
        client.toBlocking().exchange(req, Argument.of(HttpResponse), Argument.of(HttpResponse))

        then:
        HttpClientResponseException e = thrown()
        e.response.status() == HttpStatus.BAD_REQUEST
    }

    void "test only sub properties are bound"() {
        HttpRequest req = HttpRequest.POST("/search/sub", '{"name":"X","search":{"lastName":"Jones"}}')

        when:
        Search search = client.toBlocking().retrieve(req, Search)

        then:
        search.name == null
        search.lastName == "Jones"
    }

    void "should fail on missing name"() {
        given:
            HttpRequest req = HttpRequest.POST("/search/extended", '{"type":"NAME","requiredVal":"xxx"}')

        when:
            client.toBlocking().exchange(req)

        then:
            def e = thrown(HttpClientResponseException)
            e.status == HttpStatus.BAD_REQUEST
    }

    void "should not fail on missing name"() {
        given:
            HttpRequest req = HttpRequest.POST("/search/extended", '{"type":"NAME","requiredVal":"xxx", "name": "MyName"}')

        when:
            def response = client.toBlocking().exchange(req)

        then:
            response.status() == HttpStatus.OK
    }

    void "should fail on missing requiredVal"() {
        given:
            HttpRequest req = HttpRequest.POST("/search/extended", '{"type":"NAME", "name": "MyName"}')

        when:
            client.toBlocking().exchange(req)

        then:
            def e = thrown(HttpClientResponseException)
            e.status == HttpStatus.BAD_REQUEST
    }
}


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)

@Introspected
@JsonSubTypes([
    @JsonSubTypes.Type(value = ByName.class, name = "NAME"),
    @JsonSubTypes.Type(value = ByAge.class, name = "AGE")])
abstract class SearchBy {
    @NotEmpty
    String requiredVal;

}

@Introspected
class ByName extends SearchBy {
    @NotNull
    String name
}

@Introspected
class ByAge extends SearchBy {
    @NotNull
    Integer age;
}

@Controller("/search")
@Requires(property = "spec.name", value = "customValidatorPOJO")
class SearchController {

    @Post("/")
    HttpResponse search(@Nullable @Header("X-Temp") String temp, @Body @NotNull @Valid Search search) {
        return HttpResponse.ok()
    }

    @Post("/sub")
    HttpResponse search(@Body("search") Search search) {
        return HttpResponse.ok(search)
    }

    @Post("/extended")
    HttpResponse extendedSearch(@Valid @Body SearchBy search) {
        return HttpResponse.ok(search)
    }

    @Error(exception = ConstraintViolationException.class)
    HttpResponse validationError(ConstraintViolationException ex) {
        return HttpResponse.badRequest()
    }
}

