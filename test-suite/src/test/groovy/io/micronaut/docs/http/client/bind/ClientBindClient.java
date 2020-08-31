package io.micronaut.docs.http.client.bind;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

@Client("/")
public interface ClientBindClient {

    @Get("/client/bind")
    String get(Metadata metadata);
}
