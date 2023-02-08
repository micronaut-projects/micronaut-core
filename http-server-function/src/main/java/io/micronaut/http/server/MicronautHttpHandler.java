package io.micronaut.http.server;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

@FunctionalInterface
public interface MicronautHttpHandler {
    HttpResponse<?> handle(HttpRequest<?> request);
}
