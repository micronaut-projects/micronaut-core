package io.micronaut.docs.server.callbacks

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ReportRoutesSpec extends Specification {

    void "the route sees the outcome and the abandonment"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ReportRoutesSpec'])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)
        Reports reports = server.applicationContext.getBean(Reports)

        when:
        reports.generate("1").complete("report 1")

        then:
        client.toBlocking().retrieve(HttpRequest.GET("/reports/1")) == "report 1"
        !reports.outcome("1").get(10, TimeUnit.SECONDS).isPresent()

        when:
        def report = reports.generate("2")
        new Socket(server.host, server.port).withCloseable { socket ->
            socket.outputStream.write("GET /reports/2 HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            long deadline = System.currentTimeMillis() + 10_000
            while (report.numberOfDependents == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
        }

        then:
        reports.outcome("2").get(10, TimeUnit.SECONDS).isPresent()
        report.isCancelled()

        cleanup:
        client?.close()
        server?.close()
    }
}
