package io.micronaut.docs.aop.retry

import io.micronaut.retry.annotation.CircuitBreaker
import io.micronaut.retry.annotation.Retryable
import io.reactivex.Flowable

class BookService {

    // tag::simple[]
    @Retryable
    List<Book> listBooks() {
        // ...
    // end::simple[]
        [new Book("The Stand")]
    }

    // tag::circuit[]
    @CircuitBreaker(reset = "30s")
    List<Book> findBooks() {
        // ...
    // end::circuit[]
        [new Book("The Stand")]
    }

    // tag::attempts[]
    @Retryable( attempts = "5",
                delay = "2s" )
    Book findBook(String title) {
        // ...
    // end::attempts[]
        new Book(title)
    }

    // tag::config[]
    @Retryable( attempts = "\${book.retry.attempts:3}",
            delay = "\${book.retry.delay:1s}")
    Book getBook(String title) {
        // ...
    // end::config[]
        new Book(title)
    }

    // tag::reactive[]
    @Retryable
    Flowable<Book> streamBooks() {
        // ...
    // end::reactive[]
        Flowable.just(new Book("The Stand"))
    }
}
