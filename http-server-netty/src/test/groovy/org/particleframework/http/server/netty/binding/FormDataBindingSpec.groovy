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

import okhttp3.FormBody
import okhttp3.Request
import okhttp3.RequestBody
import org.particleframework.http.HttpStatus
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Body
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
        RequestBody formBody = new FormBody.Builder()
                .add("name", "Fred")
                .add("age", "10").build()
        def request = new Request.Builder()
                .url("$server/form/simple")
                .post(formBody)

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == "name: Fred, age: 10"

    }

    void "test pojo body parsing"() {

        when:
        RequestBody formBody = new FormBody.Builder()
                .add("name", "Fred")
                .add("something", "else")
                .add("age", "10").build()
        def request = new Request.Builder()
                .url("$server/form/pojo")
                .post(formBody)

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == "name: Fred, age: 10"

    }

    void "test simple string-based body parsing with missing data"() {

        when:
        RequestBody formBody = new FormBody.Builder()
                .add("name", "Fred")
                .build()
        def request = new Request.Builder()
                .url("$server/form/simple")
                .post(formBody)

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.BAD_REQUEST.code

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
