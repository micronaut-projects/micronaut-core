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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.NotNull

class PojoBodyParameterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                                                           ['spec.name': 'customValidatorPOJO'],
                                                           Environment.TEST)
    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())

    void 'test custom constraints in a Pojo are taken into account when it is used as a controller parameter'() {
        given:
        HttpRequest req = HttpRequest.POST("/search/", new Search());

        when:
        client.toBlocking().exchange(req, Argument.of(HttpResponse), Argument.of(HttpResponse))

        then:
        HttpClientResponseException e = thrown()
        e.response.status() == HttpStatus.BAD_REQUEST
    }
}


@Controller("/search")
@Requires(property = "spec.name", value = "customValidatorPOJO")
class SearchController {

    @Post("/")
    HttpResponse search(@Body @NotNull @Valid Search search) {
        return HttpResponse.ok()
    }

    @Error(exception = ConstraintViolationException.class)
    HttpResponse validationError(ConstraintViolationException ex) {
        return HttpResponse.badRequest()
    }
}

