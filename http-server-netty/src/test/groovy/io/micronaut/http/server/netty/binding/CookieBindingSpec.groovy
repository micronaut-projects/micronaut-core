package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.client.Client
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.cookie.Cookies
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by graemerocher on 25/07/2017.
 */
class CookieBindingSpec extends AbstractMicronautSpec {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

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

    void "test setting HTTP cookies for client"() {
        setup:
        context.getBean(EmbeddedServer).start()
        CookieClient client = context.createBean(CookieClient, server)
        def result

        when:
        result = client.simple("foo")

        then:
        result
    }

    @Client('/cookie')
    static interface CookieClient extends CookieApi {
    }

    @Controller('/cookie')
    static class CookieController implements CookieApi {

        @Override
        String simple(@CookieValue String myVar) {
            "Cookie Value: $myVar"
        }

        @Override
        String optional(@CookieValue Optional<Integer> myVar) {
            "Cookie Value: ${myVar.orElse(-1)}"
        }

        @Override
        String all(Cookies cookies) {
            "Cookie Value: ${cookies.get('myVar')?.value}"
        }
    }


    static interface CookieApi {

        @Get('/simple')
        String simple(@CookieValue String myVar)

        @Get
        String optional(@CookieValue Optional<Integer> myVar)

        @Get
        String all(Cookies cookies)
    }

}
