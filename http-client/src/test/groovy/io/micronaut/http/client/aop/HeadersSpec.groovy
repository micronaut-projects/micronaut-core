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
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Headers
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @author rvanderwerf
 * @since 1.0
 */

class HeadersSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            'foo.bar':'Another'
    )

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test send and receive header"() {
        given:
        UserClient userClient = context.getBean(UserClient)
        User user = userClient.get("Freddy")


        expect:
        user.myparam == 'Another'
        user.username == "Freddy"
        user.age == 10
    }


    @Headers([
        @Header(name="X-Username",value='Freddy'),
        @Header(name="X-MyParam",value='test')
    ])
    @Client('/headersTest')
    static interface UserClient {

        @Headers([
                @Header(name="X-Username",value='Freddy'),
                @Header(name="X-MyParam",value='${foo.bar}'),
                @Header(name="X-Age",value="10")
        ])
        @Get("/user")
        User get(@Header('X-Username') String username)

    }

    @Controller('/headersTest')
    static class UserController {

        @Get('/user')
        User get(@Header('X-Username') String username,
                 @Header('X-MyParam') String myparam,
                 @Header('X-Age') Integer age) {
            return new User(username:username, age: age, myparam:myparam)
        }
    }

    static class User {
        String username
        Integer age
        String myparam
    }

}
