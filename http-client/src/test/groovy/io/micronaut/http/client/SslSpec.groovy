package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import spock.lang.Ignore
import spock.lang.Specification

class SslSpec extends Specification {

    @Ignore // service down at the moment
    void "test that clients work with self signed certificates"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        HttpClient client = ctx.createBean(HttpClient, new URL("https://httpbin.org"))

        expect:
        client.toBlocking().retrieve('/get')

        cleanup:
        ctx.close()
        client.close()
    }
}
