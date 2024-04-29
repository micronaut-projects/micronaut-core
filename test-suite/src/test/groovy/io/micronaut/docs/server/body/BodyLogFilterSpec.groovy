package io.micronaut.docs.server.body

import ch.qos.logback.classic.Logger
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MemoryAppender
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class BodyLogFilterSpec extends Specification {
    def 'simple'() {
        given:
        MemoryAppender appender = new MemoryAppender()
        Logger l = (Logger) LoggerFactory.getLogger("io.micronaut.docs.server.body")
        l.addAppender(appender)
        appender.start()

        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'BodyLogFilterSpec'])
        def client = server.applicationContext.createBean(HttpClient, server.URI).toBlocking()

        when:
        def body = '{"firstName": "foo", "lastName": "bar"}'
        def status = client.retrieve(HttpRequest.POST("/person", body), HttpStatus)
        def msgs = [appender.headLog(1), appender.headLog(1)].toSet()
        then:
        status == HttpStatus.OK || status == HttpStatus.NO_CONTENT
        msgs == [
                "Received body: " + Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)),
                "Creating person Person[firstName=foo, lastName=bar]"
        ].toSet()

        cleanup:
        client.close()
        server.close()
    }
}
