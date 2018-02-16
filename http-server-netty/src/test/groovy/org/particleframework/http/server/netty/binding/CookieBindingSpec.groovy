package org.particleframework.http.server.netty.binding

import org.particleframework.context.annotation.Requires
import org.particleframework.http.HttpRequest
import org.particleframework.http.annotation.CookieValue
import org.particleframework.http.cookie.Cookies
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import spock.lang.Unroll

/**
 * Created by graemerocher on 25/07/2017.
 */
class CookieBindingSpec extends AbstractParticleSpec {


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
