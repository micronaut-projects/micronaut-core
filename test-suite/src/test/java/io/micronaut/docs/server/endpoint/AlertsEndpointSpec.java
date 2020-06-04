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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AlertsEndpointSpec {

    @Test
    public void testAddingAnAlert() {
        Map<String, Object> map = new HashMap<>();
        map.put("spec.name", AlertsEndpointSpec.class.getSimpleName());
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());

        try {
            rxClient.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String.class).blockingFirst();
        } catch (HttpClientResponseException e) {
            assertEquals(401, e.getStatus().getCode());
        } catch (Exception e) {
            fail("Wrong exception thrown");
        } finally {
            server.close();
        }
    }

    @Test
    public void testAddingAnAlertNotSensitive() {
        Map<String, Object> map = new HashMap<>();
        map.put("spec.name", AlertsEndpointSpec.class.getSimpleName());
        map.put("endpoints.alerts.add.sensitive", false);
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());

        try {
            HttpResponse<?> response = rxClient.exchange(HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE), String.class).blockingFirst();
            assertEquals(response.status(), HttpStatus.OK);
        } catch (Exception e) {
            fail("Wrong exception thrown");
        }

        try {
            HttpResponse<List<String>> response = rxClient.exchange(HttpRequest.GET("/alerts"), Argument.LIST_OF_STRING).blockingFirst();
            assertEquals(response.status(), HttpStatus.OK);
            assertEquals(response.body().get(0), "First alert");
        } catch (Exception e) {
            fail("Wrong exception thrown");
        } finally {
            server.close();
        }
    }

    @Test
    public void testClearingAlerts() {
        Map<String, Object> map = new HashMap<>();
        map.put("spec.name", AlertsEndpointSpec.class.getSimpleName());
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, map);
        RxHttpClient rxClient = server.getApplicationContext().createBean(RxHttpClient.class, server.getURL());

        try {
            rxClient.exchange(HttpRequest.DELETE("/alerts"), String.class).blockingFirst();
        } catch (HttpClientResponseException e) {
            assertEquals(401, e.getStatus().getCode());
        } catch (Exception e) {
            fail("Wrong exception thrown");
        } finally {
            server.close();
        }
    }
}
