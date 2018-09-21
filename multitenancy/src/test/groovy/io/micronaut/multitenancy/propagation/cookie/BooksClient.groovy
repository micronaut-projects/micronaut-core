package io.micronaut.multitenancy.propagation.cookie

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client

@Client("books")
@Requires(property = 'spec.name', value = 'multitenancy.cookie.gateway')
interface BooksClient extends BookFetcher {

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/api/books")
    List<String> findAll()
}
