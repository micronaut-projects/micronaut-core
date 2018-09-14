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
package io.micronaut.http.server.netty.binding

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class FormDataBindingSpec extends AbstractMicronautSpec {

    void "test simple string-based body parsing"() {

        when:
        def response = rxClient.exchange(HttpRequest.POST('/form/simple', [
                name:"Fred",
                age:"10"
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.body.isPresent()
        response.body.get() == "name: Fred, age: 10"
    }

    void "test pojo body parsing"() {
        when:
        def response = rxClient.exchange(HttpRequest.POST('/form/pojo', [
                name:"Fred",
                age:"10",
                something: "else"
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.body.isPresent()
        response.body.get() == "name: Fred, age: 10"
    }

    void "test simple string-based body parsing with missing data"() {
        when:
        rxClient.exchange(HttpRequest.POST('/form/simple', [
                name:"Fred"
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST
    }
    
    @Controller(value = '/form', consumes = MediaType.APPLICATION_FORM_URLENCODED)
    static class FormController {
        @Post('/simple')
        String simple(String name, Integer age) {
            "name: $name, age: $age"
        }

        @Post('/pojo')
        String pojo(@Body Person person) {
            "name: $person.name, age: $person.age"
        }

        static class Person {
            String name
            Integer age
        }
    }
}
