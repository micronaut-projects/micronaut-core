package io.micronaut.test.messageBodyWriter;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

@Property(name = "spec.name", value = "MessageBodyWriterIsWritableTest")
@MicronautTest
public class MessageBodyWriterIsWritableTest {
    @Test
    void isWritableIsChecked(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        String html = client.retrieve(HttpRequest.GET("/html/foo").accept(MediaType.TEXT_HTML), String.class);
        assertEquals("<!DOCTYPE html><html><head></head><body><h1>Foo: Aegon</h1></body></html>", html);
        html = client.retrieve(HttpRequest.GET("/html/bar").accept(MediaType.TEXT_HTML), String.class);
        assertEquals("<!DOCTYPE html><html><head></head><body><h1>Bar: John</h1></body></html>", html);
    }
}
