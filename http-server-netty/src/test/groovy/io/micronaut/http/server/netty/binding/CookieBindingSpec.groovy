package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.cookie.Cookies
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import spock.lang.Unroll

/**
 * Created by graemerocher on 25/07/2017.
 */
class CookieBindingSpec extends AbstractMicronautSpec {


    @Unroll
    void "test bind HTTP cookies for URI #uri"() {
        expect:
        def request = HttpRequest.GET(uri)
        for (header in headers) {
            request = request.header(header.key, header.value)
        }
        rxClient.retrieve(request).blockingFirst() == result


        where:
        uri                | result              | headers
        '/cookie/all'      | "Cookie Value: bar" | ['Cookie': 'myVar=bar']
        '/cookie/simple'   | "Cookie Value: bar" | ['Cookie': 'myVar=bar']
        '/cookie/optional' | "Cookie Value: 10"  | ['Cookie': 'myVar=10']
        '/cookie/optional' | "Cookie Value: -1"  | ['Cookie': 'myVar=foo']
        '/cookie/optional' | "Cookie Value: -1"  | [:]

    }

    @Controller
    @Requires(property = 'spec.name', value = 'CookieBindingSpec')
    static class CookieController {

        @Get
        String simple(@CookieValue String myVar) {
            "Cookie Value: $myVar"
        }


        @Get
        String optional(@CookieValue Optional<Integer> myVar) {
            "Cookie Value: ${myVar.orElse(-1)}"
        }


        @Get
        String all(Cookies cookies) {
            "Cookie Value: ${cookies.get('myVar')?.value}"
        }
    }
}
