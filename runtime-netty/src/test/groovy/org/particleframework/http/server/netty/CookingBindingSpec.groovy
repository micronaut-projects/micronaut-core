package org.particleframework.http.server.netty

import okhttp3.Request
import org.particleframework.http.HttpHeaders
import org.particleframework.http.MediaType
import org.particleframework.http.binding.annotation.Cookie
import org.particleframework.http.binding.annotation.Header
import org.particleframework.http.cookie.Cookies
import org.particleframework.stereotype.Controller
import org.particleframework.web.router.annotation.Get
import spock.lang.Unroll

/**
 * Created by graemerocher on 25/07/2017.
 */
class CookingBindingSpec extends AbstractParticleSpec {


    @Unroll
    void "test bind HTTP cookies for URI #uri"() {
        expect:
        def request = new Request.Builder()
                .url("$server$uri")

        for (header in headers) {
            request = request.header(header.key, header.value)
        }
        client.newCall(
                request.build()
        ).execute().body().string() == result



        where:
        uri                | result              | headers
        '/cookie/simple'   | "Cookie Value: bar" | ['Cookie': 'myVar=bar']
        '/cookie/optional' | "Cookie Value: 10"  | ['Cookie': 'myVar=10']
        '/cookie/optional' | "Cookie Value: -1"  | ['Cookie': 'myVar=foo']
        '/cookie/optional' | "Cookie Value: -1"  | [:]
        '/cookie/all'      | "Cookie Value: bar" | ['Cookie': 'myVar=bar']
    }

    @Controller
    static class CookieController {

        @Get
        String simple(@Cookie String myVar) {
            "Cookie Value: $myVar"
        }


        @Get
        String optional(@Cookie Optional<Integer> myVar) {
            "Cookie Value: ${myVar.orElse(-1)}"
        }


        @Get
        String all(Cookies cookies) {
            "Cookie Value: ${cookies.get('myVar')?.value}"
        }
    }
}
