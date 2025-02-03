package io.micronaut.logback.clients;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;

@Client("/teapot")
public interface TeapotClient {

    @Get("/custom-status")
    String customStatus();

    @Get("/sync-teapot")
    String syncTeapot();

    @Get("/async-teapot")
    CompletableFuture<String> asyncTeapot();

    @Get("/reactive-teapot")
    Publisher<String> reactiveTeapot();
}
