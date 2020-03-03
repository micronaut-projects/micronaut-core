package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

class HeaderValidationSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer, ["spec.name": HeaderValidationSpec.simpleName])

    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test hello"() {
        HttpRequest<String> request = HttpRequest.GET("/hello");
        String body = client.toBlocking().retrieve(request);

        expect:
        body == "Hello World"
    }

    /**
     * This test demonstrates self exploitation.
     * Not really practical, but it proves the point.
     */
    void "test self exploitation"() {
        HttpRequest<String> request = HttpRequest.GET("/hello/self-exploit");
        String body = client.toBlocking().retrieve(request);

        expect:
        body == "Hello World"
    }

    /**
     * This test demonstrates an example of this vulnerability actually being abused since an attacker
     * could make the server make an additional unexpected request.
     */
    void "test external exploit"() {
        List<String> headerData = [
                "Connection: Keep-Alive", // This keeps the connection open so another request can be stuffed in.
                "",
                "",
                "POST /hello/super-secret HTTP/1.1",
                "Host: 127.0.0.1",
                "Content-Length: 31",
                "",
                "{\"new\":\"json\",\"content\":\"here\"}",
                "",
                ""
        ]
        String fullHeaderValue = String.join("\r\n", headerData);
        String headerValue = "H\r\n" + fullHeaderValue;
        URI theURI =
                UriBuilder
                        .of("/hello/external-exploit")
                        .queryParam("header-value", headerValue)
                        .build();
        HttpRequest<String> request = HttpRequest.GET(theURI)
        String body = client.toBlocking().retrieve(request)

        expect:
        body == "Hello World"
    }


    @Requires(property = "spec.name", value = "HeaderValidationSpec")
    @Controller("/hello")
    static class HelloController {

        /**
         * Imagine that this client actually points to another micro-service instead of pointing back to itself.
         */
        @Inject
        @Client("/")
        HttpClient client

        /**
         * Imagine in a micro-services architecture, this method exists in some other application.
         * This method is safe to be called by the other services that use it.
         */
        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String index() {
            "Hello World"
        }

        /**
         * Imagine in a micro-services architecture, this method exists in some other application.
         * This method is <b>incredibly sensitive</b> and should <b>not</b> be called by outside code, ever.
         */
        @Post("/super-secret")
        @Produces(MediaType.TEXT_PLAIN)
        String superSecretEndpoint(@Body String body) {
            println("This method was called but it shouldn't have been!")
            println(body)

            body
        }

        /**
         * This is a simple demonstration of exploiting ourselves.
         * Totally impractical, but this demonstrates the vulnerability in the simplest way.
         */
        @Get("/self-exploit")
        @Produces(MediaType.TEXT_PLAIN)
        String selfExploit() {
            List<String> headerData = [
                    "Connection: Keep-Alive", // This keeps the connection open so another request can be stuffed in.
                    "",
                    "",
                    "POST /hello/super-secret HTTP/1.1",
                    "Host: 127.0.0.1",
                    "Content-Length: 31",
                    "",
                    "{\"new\":\"json\",\"content\":\"here\"}",
                    "",
                    ""
            ]
            String fullHeaderValue = String.join("\r\n", headerData)
            String headerValue = "H\r\n" + fullHeaderValue

            HttpRequest request = HttpRequest.GET("/hello")
            try {
                request.header("Test", headerValue)
            } catch (IllegalArgumentException e) {
            }
            client.toBlocking().retrieve(request)
        }

        /**
         * This is a more practical example of simplified user code that could reasonably exist in the wild.
         * This method demonstrates how external user supplied data flowing to a header value could be dangerous.
         */
        @Get("/external-exploit")
        @Produces(MediaType.TEXT_PLAIN)
        String externalExploit(@QueryValue("header-value") String headerValue) {
            HttpRequest request = HttpRequest.GET("/hello")
            try {
                request.header("Test", headerValue)
            } catch (IllegalArgumentException e) {
            }
            client.toBlocking().retrieve(request)
        }
    }
}
