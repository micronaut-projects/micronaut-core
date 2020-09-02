package io.micronaut.docs.http.client.bind.annotation

import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

import java.util.Map

//tag::clazz[]
@Client("/")
interface MetadataClient {

    @Get("/client/bind")
    String get(@Metadata Map metadata)
}
//end::clazz[]
