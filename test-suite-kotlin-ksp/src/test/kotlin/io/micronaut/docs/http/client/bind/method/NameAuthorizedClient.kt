package io.micronaut.docs.http.client.bind.method;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

//tag::clazz[]
@Client("/")
public interface NameAuthorizedClient {

    @Get("/client/authorized-resource")
    @NameAuthorization(name="Bob") // <1>
    fun get(): String
}
//end::clazz[]
