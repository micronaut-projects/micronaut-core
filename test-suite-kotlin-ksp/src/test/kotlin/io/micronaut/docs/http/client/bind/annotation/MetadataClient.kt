package io.micronaut.docs.http.client.bind.annotation

import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

//tag::clazz[]
@Client("/")
interface MetadataClient {

    @Get("/client/bind")
    operator fun get(@Metadata metadata: Map<String, Any>): String
}
//end::clazz[]
