package io.micronaut.http.server.netty.binding.generic;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import reactor.core.publisher.Mono;

import java.io.Serializable;

public class GenericController<T, ID extends Serializable> {
    @Post
    Mono<HttpResponse<T>> save(@Body T entity) {
        assert entity instanceof Status;
        return Mono.just(HttpResponse.created((entity)));
    }
}