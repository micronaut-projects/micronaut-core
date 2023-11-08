package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class HeaderSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name':'HeaderSpec',
    ])

    UserClient userClient = server.applicationContext.getBean(UserClient)

    void "test send and receive header"() {
        given:
        User user = userClient.get("Fred")

        expect:
        user.username == "Fred"
        user.age == 10

    }

    @Client('/headers')
    @Requires(property = 'spec.name', value = 'HeaderSpec')
    static interface UserClient extends MyApi {

    }

    @Controller('/headers')
    @Requires(property = 'spec.name', value = 'HeaderSpec')
    static class UserController implements MyApi {

        @Override
        User get(@Header('X-Username') String username) {
            return new User(username:username, age: 10)
        }
    }

    static interface MyApi {

        @Get('/user')
        User get(@Header('X-Username') String username)
    }

    static class User {
        String username
        Integer age
    }

}
