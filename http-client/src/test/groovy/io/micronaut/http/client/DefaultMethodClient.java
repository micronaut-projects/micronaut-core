package io.micronaut.http.client;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.aop.Mutating;

@Client("/aop")
public interface DefaultMethodClient {

    @Get(produces = MediaType.TEXT_PLAIN, consumes = MediaType.TEXT_PLAIN)
    String index();

    @Mutating
    default String defaultMethod() {
        return index() + " from default method";
    }
}
