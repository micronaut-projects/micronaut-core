package io.micronaut.multitenancy.propagation.principal

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

@Client("books")
@Requires(property = 'spec.name', value = 'multitenancy.principal.gateway')
interface BooksClient extends BookFetcher {

    @Get("/api/books")
    List<String> findAll()
}
