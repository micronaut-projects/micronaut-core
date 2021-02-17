package io.micronaut.docs.http.client.bind.annotation

import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

//tag::clazz[]
@Client("/")
interface MetadataClient {

    @Get("/client/bind")
    String get(@Metadata Map metadata)
}
//end::clazz[]
