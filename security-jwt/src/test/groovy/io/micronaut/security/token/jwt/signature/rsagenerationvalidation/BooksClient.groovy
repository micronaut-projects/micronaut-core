package io.micronaut.security.token.jwt.signature.rsagenerationvalidation

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client

@Requires(property = "spec.name", value = "rsajwtgateway")
@Client("books")
interface BooksClient {

    @Get("/books")
    List<Book> findAll(@Header("Authorization") String authorization)
}
