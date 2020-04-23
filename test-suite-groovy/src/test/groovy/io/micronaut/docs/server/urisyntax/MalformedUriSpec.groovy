package io.micronaut.docs.server.urisyntax

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MalformedUriSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer);

    void testMalformedUriReturns400() {
        when:
        HttpURLConnection connection = (HttpURLConnection) new URL("$embeddedServer.URL/malformed/[]").openConnection()
        connection.connect()

        then:
        connection.getResponseCode() == 400
        connection.getResponseMessage() == HttpStatus.BAD_REQUEST.reason
        connection.getErrorStream().getText().contains('"message":"Malformed URI:')
    }
}
