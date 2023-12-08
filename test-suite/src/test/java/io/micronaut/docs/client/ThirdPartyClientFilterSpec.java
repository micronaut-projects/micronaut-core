/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import spock.lang.Retry;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Retry
class ThirdPartyClientFilterSpec {

    private static final String token = "XXXX";
    private static final String username = "john";

    private static ApplicationContext context;
    private static EmbeddedServer server;
    private static HttpClient client;

    private String result;

    @BeforeAll
    static void setupServer() {
        Map<String, Object> map = new HashMap<>();
        map.put("spec.name", ThirdPartyClientFilterSpec.class.getSimpleName());
        map.put("bintray.username", username);
        map.put("bintray.token", token);
        map.put("bintray.organization", "grails");
        server = ApplicationContext.run(EmbeddedServer.class, map);
        context = server.getApplicationContext();
        client = context.createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            client.stop();
        }
    }

    @Test
    void aClientFilterIsAppliedToTheRequestAndAddsTheAuthroizationHeader() {

        BintrayService bintrayService = context.getBean(BintrayService.class);

        bintrayService.fetchRepositories()
                      .subscribe(str -> result = str.body());

        String encoded = Base64.getEncoder().encodeToString((username + ":" + token).getBytes());
        String expected = "Basic " + encoded;

        await().atMost(3, SECONDS).until(() ->
                null != result
        );
        assertEquals(expected, result);

    }

    @Controller("/repos")
    static class HeaderController {

        @Get("/grails")
        String echoAuthorization(@Header String authorization) {
            return authorization;
        }
    }
}

//tag::bintrayService[]
@Singleton
class BintrayService {
    final HttpClient client;
    final String org;

    BintrayService(
            @Client(BintrayApi.URL) HttpClient client,           // <1>
            @Value("${bintray.organization}") String org ) {
        this.client = client;
        this.org = org;
    }

    Flux<HttpResponse<String>> fetchRepositories() {
        return Flux.from(client.exchange(HttpRequest.GET(
                "/repos/" + org), String.class)); // <2>
    }

    Flux<HttpResponse<String>> fetchPackages(String repo) {
        return Flux.from(client.exchange(HttpRequest.GET(
                "/repos/" + org + "/" + repo + "/packages"), String.class)); // <2>
    }
}
//end::bintrayService[]

@Requires(property = "spec.name", value = "ThirdPartyClientFilterSpec")
//tag::bintrayFilter[]
@ClientFilter("/repos/**") // <1>
class BintrayFilter {

    final String username;
    final String token;

    BintrayFilter(
            @Value("${bintray.username}") String username, // <2>
            @Value("${bintray.token}") String token ) { // <2>
        this.username = username;
        this.token = token;
    }

    @RequestFilter
    public void filter(MutableHttpRequest<?> request) {
        request.basicAuth(username, token); // <3>
    }
}
//end::bintrayFilter[]

/*
//tag::bintrayApiConstants[]
class BintrayApi {
    public static final String URL = 'https://api.bintray.com'
}
//end::bintrayApiConstants[]
*/

class BintrayApi {
    public static final String URL = "/";
}
