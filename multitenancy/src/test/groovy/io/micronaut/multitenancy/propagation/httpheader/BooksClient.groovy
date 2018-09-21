package io.micronaut.multitenancy.propagation.httpheader

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client

@Client("books")
@Requires(property = 'spec.name', value = 'multitenancy.httpheader.gateway')
interface BooksClient extends BookFetcher {

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/api/books")
    List<String> findAll()
}
