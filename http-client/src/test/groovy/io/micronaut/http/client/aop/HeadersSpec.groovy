package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
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

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'foo.bar'  : 'Another',
            'spec.name': 'HeadersSpec',
    ])

    UserClient userClient = server.applicationContext.getBean(UserClient)

    void "test send and receive header"() {
        given:
        User user = userClient.get("Freddy")

        expect:
        user.myparam == 'Another'
        user.username == "Freddy"
        user.age == 10
    }

    @Headers([
            @Header(name = "X-Username", value = 'Freddy'),
            @Header(name = "X-MyParam", value = 'test')
    ])
    @Client('/headersTest')
    @Requires(property = 'spec.name', value = 'HeadersSpec')
    static interface UserClient {

        @Headers([
                @Header(name = "X-Username", value = 'Freddy'),
                @Header(name = "X-MyParam", value = '${foo.bar}'),
                @Header(name = "X-Age", value = "10")
        ])
        @Get("/user")
        User get(@Header('X-Username') String username)

    }

    @Controller('/headersTest')
    @Requires(property = 'spec.name', value = 'HeadersSpec')
    static class UserController {

        @Get('/user')
        User get(@Header('X-Username') String username,
                 @Header('X-MyParam') String myparam,
                 @Header('X-Age') Integer age) {
            return new User(username: username, age: age, myparam: myparam)
        }
    }

    static class User {
        String username
        Integer age
        String myparam
    }

}
