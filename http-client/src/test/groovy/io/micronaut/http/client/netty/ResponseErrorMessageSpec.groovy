package io.micronaut.http.client.netty

import io.micronaut.http.HttpRequest
import spock.lang.Specification

class ResponseErrorMessageSpec extends Specification {

    void "a failure without a message is named by the simple name of its class"() {
        given:
        NettyHttpClient client = NettyHttpClient.newBuilder().build()

        when:
        def mapped = client.handleResponseError(HttpRequest.GET('http://localhost/'), null, new LocalizedFailure())

        then:
        mapped.message == 'Error occurred reading HTTP response: LocalizedFailure'

        cleanup:
        client.close()
    }

    static class LocalizedFailure extends RuntimeException {
        @Override
        String getLocalizedMessage() {
            // a localized message only, with dots, as Throwable.toString() reports it
            'see io.example.Thing'
        }
    }
}
