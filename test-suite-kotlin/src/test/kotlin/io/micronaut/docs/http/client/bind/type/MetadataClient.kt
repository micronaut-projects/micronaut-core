package io.micronaut.docs.http.client.bind.type

import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

//tag::clazz[]
@Client("/")
interface MetadataClient {
    @Get("/client/bind")
    operator fun get(metadata: Metadata?): String?
}
//end::clazz[]
