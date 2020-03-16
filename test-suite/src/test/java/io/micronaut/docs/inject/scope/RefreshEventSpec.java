package io.micronaut.docs.inject.scope;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

public class RefreshEventSpec {

    private static EmbeddedServer embeddedServer;

    private static HttpClient client;


    @BeforeClass
    public static void setup(){
        embeddedServer = ApplicationContext.run(EmbeddedServer.class, new HashMap<String, Object>() {{
            put("spec.name", "RefreshEventSpec");
            put("spec.lang", "java");
        }}, Environment.TEST);
        client = HttpClient.create(embeddedServer.getURL());
    }
    @AfterClass
    public static void teardown(){
        if(client != null){
            client.close();
        }
        if(embeddedServer != null){
            embeddedServer.close();
        }
    }

    @Test
    public void publishingARefreshEventDestroysBeanWithRefreshableScope() {
        String firstResponse = fetchForecast();

        assertTrue(firstResponse.contains("{\"forecast\":\"Scattered Clouds"));

        String secondResponse = fetchForecast();

        assertEquals(firstResponse, secondResponse);

        String response = evictForecast();

        assertEquals(
// tag::evictResponse[]
                "{\"msg\":\"OK\"}"
// end::evictResponse[]
                , response);

        AtomicReference<String> thirdResponse = new AtomicReference<>(fetchForecast());
        await().atMost(5, SECONDS).until(() -> {
            if (!thirdResponse.get().equals(secondResponse)) {
                return true;
            }
            thirdResponse.set(fetchForecast());
            return false;
        });

        assertNotEquals(thirdResponse.get(), secondResponse);
        assertTrue(thirdResponse.get().contains("\"forecast\":\"Scattered Clouds"));
    }

    public String fetchForecast() {
        return client.toBlocking().retrieve(HttpRequest.GET("/weather/forecast"));
    }

    public String evictForecast() {
        return client.toBlocking().retrieve(HttpRequest.POST("/weather/evict", new LinkedHashMap()));
    }

    //tag::weatherService[]
    @Refreshable // <1>
    public static class WeatherService {
        private String forecast;

        @PostConstruct
        public void init() {
            forecast = "Scattered Clouds " + new SimpleDateFormat("dd/MMM/yy HH:mm:ss.SSS").format(new Date());// <2>
        }

        public String latestForecast() {
            return forecast;
        }
    }
    //end::weatherService[]

    @Requires(property = "spec.name", value = "RefreshEventSpec")
    @Requires(property = "spec.lang", value = "java")
    @Controller("/weather")
    public static class WeatherController {
        @Inject
        private WeatherService weatherService;
        @Inject
        private ApplicationContext applicationContext;

        @Get(value = "/forecast")
        public HttpResponse<Map<String, String>> index() {
            LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
            map.put("forecast", weatherService.latestForecast());
            return HttpResponse.ok(map);
        }

        @Post("/evict")
        public HttpResponse<Map<String, String>> evict() {
            //tag::publishEvent[]
            applicationContext.publishEvent(new RefreshEvent());
            //end::publishEvent[]
            LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
            map.put("msg", "OK");
            return HttpResponse.ok(map);
        }
    }
}
