package io.micronaut.docs.server.endpoint;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Specification

class AlertsEndpointSpec extends Specification {

    void "test adding an alert"() {
        Map<String, Object> map = new HashMap<>()
        map.put("spec.name", AlertsEndpointSpec.simpleName)
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map)
        HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())

        when:
        Flux.from(client.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.UNAUTHORIZED

        cleanup:
        server.close()
    }

    void "test adding an alert not sensitive"() {
        Map<String, Object> map = new HashMap<>();
        map.put("spec.name", AlertsEndpointSpec.class.getSimpleName());
        map.put("endpoints.alerts.add.sensitive", false);
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map);
        HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

        when:
        HttpResponse<?> response = Flux.from(client.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String.class)).blockFirst();

        then:
        response.status() == HttpStatus.OK

        when:
        List<String> alerts = Flux.from(client.retrieve(HttpRequest.GET("/alerts"), Argument.LIST_OF_STRING)).blockFirst();

        then:
        alerts.get(0) == "First alert"

        cleanup:
        server.close()
    }

    void "test clearing alerts"() {
        Map<String, Object> map = new HashMap<>()
        map.put("spec.name", AlertsEndpointSpec.class.getSimpleName())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map)
        HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())

        when:
        Flux.from(client.exchange(HttpRequest.DELETE("/alerts"), String.class)).blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.UNAUTHORIZED

        cleanup:
        server.close()
    }
}
