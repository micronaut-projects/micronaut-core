package io.micronaut.docs.client.filter;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

@BasicAuth
@Client("/message")
public interface BasicAuthClient {

    @Get
    String getMessage();
}
