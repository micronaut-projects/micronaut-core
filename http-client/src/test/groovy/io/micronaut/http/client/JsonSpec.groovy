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

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Specification

@Property(name = 'spec.name', value = 'JsonSpec')
@MicronautTest
class JsonSpec extends Specification {

    @Inject
    JsonClient myClient

    void readJson() {
        when:
        Flux<SomeDto> fluxResponse = myClient.getSome()

        then:
        List<SomeDto> someDtos = fluxResponse.collectList().block()
        someDtos.size() == 4
        someDtos[0].data == "111"
        someDtos[1].data == "222"
        someDtos[2].data == "333"
        someDtos[3].data == "444"
    }

    @Requires(property = 'spec.name', value = 'JsonSpec')
    @Client('/data')
    static interface JsonClient {

        @Get
        Flux<SomeDto> getSome();

    }

    @Requires(property = 'spec.name', value = 'JsonSpec')
    @Controller('/data')
    static class JsonController {

        @Get(produces = MediaType.APPLICATION_JSON)
        String data() {
            return """
                                [{
                                    "data": "111"
                                }, {
                                    "data": "222"
                                }, {
                                    "data": "333"
                                }, {
                                    "data": "444"
                                }]
                                """
        }
    }

    @Introspected
    static class SomeDto {
        private String data

        String getData() {
            return data
        }

        void setData(String data) {
            this.data = data
        }
    }
}
