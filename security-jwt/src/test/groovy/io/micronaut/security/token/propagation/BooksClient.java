package io.micronaut.security.token.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import io.reactivex.Flowable;

@Requires(property = "spec.name", value = "tokenpropagation.gateway")
@Client("books")
public interface BooksClient {
    @Get("/api/books")
    Flowable<Book> fetchBooks();
}
