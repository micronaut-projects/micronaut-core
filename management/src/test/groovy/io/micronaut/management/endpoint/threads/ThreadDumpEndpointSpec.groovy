package io.micronaut.management.endpoint.threads

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ThreadDumpEndpointSpec extends Specification {

    void "test thread dump endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': getClass().simpleName, 'endpoints.threaddump.sensitive': false], "test")
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.GET("/threaddump"), Argument.listOf(Map)).blockFirst()
        def result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        result.size() > 0
        result[0].containsKey("threadName")
        result[0].containsKey("threadId")

        cleanup:
        rxClient.close()
        embeddedServer?.close()
    }
}
