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
package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PathVariableSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test send and receive with path variable"() {
        given:
        UserClient userClient = context.getBean(UserClient)
        User user = userClient.get("Fred")

        expect:
        user.username == "Fred"
        user.age == 10

        when:
        user = userClient.findByAge(18)

        then:
        user.username == "John"
        user.age == 18

    }

    @Client('/path-variables')
    static interface UserClient extends MyApi {

    }

    @Controller('/path-variables')
    static class UserController implements MyApi {

        @Override
        User get(String username) {
            return new User(username:username, age: 10)
        }

        @Override
        User findByAge(Integer age) {
            return new User(username:"John", age: 18)
        }
    }

    static interface MyApi {

        @Get('/user/{X-username}')
        User get(@PathVariable('X-username') String username)

        @Get('/user/age/{userAge}')
        User findByAge(@PathVariable('userAge') Integer age)
    }

    static class User {
        String username
        Integer age
    }
}
