package io.micronaut.docs.http.client.bind.method

import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

//tag::clazz[]
@Client("/")
interface NameAuthorizedClient {

    @Get("/client/authorized-resource")
    @NameAuthorization(name="Bob") // <1>
    String get()
}
//end::clazz[]
