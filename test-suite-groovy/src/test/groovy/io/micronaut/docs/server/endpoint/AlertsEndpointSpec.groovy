package io.micronaut.docs.server.endpoint;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Test;
import spock.lang.Specification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

class AlertsEndpointSpec extends Specification {

    void "test adding an alert"() {
        Map<String, Object> map = new HashMap<>()
        map.put("spec.name", AlertsEndpointSpec.simpleName)
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map)
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL())

        when:
        rxClient.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String.class).blockingFirst()

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
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());

        when:
        HttpResponse<?> response = rxClient.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String.class).blockingFirst();

        then:
        response.status() == HttpStatus.OK

        when:
        List<String> alerts = rxClient.retrieve(HttpRequest.GET("/alerts"), Argument.LIST_OF_STRING).blockingFirst();

        then:
        alerts.get(0) == "First alert"

        cleanup:
        server.close()
    }

    void "test clearing alerts"() {
        Map<String, Object> map = new HashMap<>()
        map.put("spec.name", AlertsEndpointSpec.class.getSimpleName())
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map)
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL())

        when:
        rxClient.exchange(HttpRequest.DELETE("/alerts"), String.class).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.UNAUTHORIZED

        cleanup:
        server.close()
    }
}
