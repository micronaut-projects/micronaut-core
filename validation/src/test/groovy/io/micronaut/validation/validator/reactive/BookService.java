package io.micronaut.validation.validator.reactive;

import io.micronaut.context.annotation.Executable;
import io.reactivex.Single;

import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
class BookService {
    @Executable
    CompletionStage<@Valid Book> futureSimple(CompletionStage<@NotBlank String> title) {
        return title.thenApply(Book::new);
    }

    @Executable
    CompletableFuture<@Valid Book> futureValid(CompletableFuture<@Valid Book> book) {
        return book;
    }

    @Executable
    Single<@Valid Book> rxSimple(Single<@NotBlank String> title) {
        return title.map(Book::new);
    }

    @Executable
    Single<@Valid Book> rxValid(Single<@Valid Book> book) {
        return book;
    }

    @Executable
    Single<@Valid Book> rxReturnInvalid(Single<@Valid Book> book) {
        return book.map(b -> new Book(""));
    }


}


