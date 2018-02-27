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
package org.particleframework.http.server.netty.binding

import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpStatus
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Body
import org.particleframework.http.client.exceptions.HttpClientResponseException
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Post

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class FormDataBindingSpec extends AbstractParticleSpec {


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
    @Controller(consumes = MediaType.APPLICATION_FORM_URLENCODED)
    static class FormController {
        @Post
        String simple(String name, Integer age) {
            "name: $name, age: $age"
        }

        @Post
        String pojo(@Body Person person) {
            "name: $person.name, age: $person.age"
        }

        static class Person {
            String name
            Integer age
        }
    }
}
