package io.micronaut.validation.validator.reactive;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
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
    Mono<@Valid Book> rxSimple(Mono<@NotBlank String> title) {
        return title.map(Book::new);
    }

    @Executable
    Mono<@Valid Book> rxValid(Mono<@Valid Book> book) {
        return book;
    }

    @Executable
    Mono<@Valid Book> rxReturnInvalid(Mono<@Valid Book> book) {
        return book.map(b -> new Book(""));
    }


}


